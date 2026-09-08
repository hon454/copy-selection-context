package com.github.hon454.copyselectioncontext

import java.awt.Font
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.text.PlainDocument
import jdk.jfr.Configuration
import jdk.jfr.Event
import jdk.jfr.Label
import jdk.jfr.Name
import jdk.jfr.Recording

/** Opt-in headless profiler, not a timing assertion or an IDE GUI test. See the matching init script. */
object ContextCollectionViewerProbe {
    @Name("copyselection.PreviewStage")
    @Label("Collection preview stage")
    class Stage : Event() {
        @JvmField var variant = ""
        @JvmField var input = ""
        @JvmField var stage = ""
        @JvmField var pass = 0
        @JvmField var warmup = false
    }

    private data class Sample(val variant: String, val input: String, val pass: Int, val warmup: Boolean,
        val bytes: Int, val prepare: Double, val installLayout: Double, val paint: Double, val lag: Double)

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3) { "output-directory baseline-SHA candidate-SHA [comma-separated-cases]" }
        val directory = Path.of(args[0])
        Files.createDirectories(directory)
        val inputs = linkedMapOf(
            "small" to "small preview\n",
            "ascii256k" to "x".repeat(262144),
            "korean256k" to "가".repeat(87381) + "x",
            "bidi256k" to "ا".repeat(131072),
            "mixed" to "ASCII\tمرحبا 123 שלום\n한글 😀𝄞 e\u0301\n".repeat(256),
            "bidi4m" to "ا".repeat(2097152),
        ).filterKeys { args.size < 4 || it in args[3].split(',') }
        require(inputs.isNotEmpty())
        val metadata = listOf("baseline=${args[1]}", "candidate=${args[2]}",
            "java=${System.getProperty("java.runtime.version")}", "vm=${System.getProperty("java.vm.name")}",
            "os=${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}",
            "processors=${Runtime.getRuntime().availableProcessors()}", "heap_limit=${Runtime.getRuntime().maxMemory()}",
            "laf=${UIManager.getLookAndFeel().javaClass.name}", "font=Monospaced plain 13", "viewport=500x300",
            "warmups=2 per variant/input", "measured=5 per variant/input", "order=AB,BA alternating per pass",
            "gc=explicit before each sample, outside measured stages", "headless=true; this is not actual IDE GUI evidence")
        Files.writeString(directory.resolve("environment.txt"), metadata.joinToString("\n", postfix = "\n"))
        metadata.forEach(::println)
        val monitor = Executors.newSingleThreadScheduledExecutor()
        val activeLag = AtomicReference<AtomicLong?>()
        monitor.scheduleAtFixedRate({
            activeLag.get()?.let { sample ->
                val posted = System.nanoTime()
                SwingUtilities.invokeLater { sample.accumulateAndGet(System.nanoTime() - posted, ::maxOf) }
            }
        }, 0, 5, TimeUnit.MILLISECONDS)
        val samples = mutableListOf<Sample>()
        val recording = Recording(Configuration.getConfiguration("profile"))
        recording.enable(Stage::class.java).withThreshold(Duration.ZERO)
        recording.start()
        try {
            for ((name, text) in inputs) {
                for (pass in 0 until 7) {
                    for (variant in if (pass % 2 == 0) listOf("baseline", "prepared") else listOf("prepared", "baseline")) {
                        System.gc()
                        val sample = measure(variant, name, text, pass, activeLag)
                        samples.add(sample)
                        println(csv(sample))
                        System.out.flush()
                    }
                }
            }
        } finally {
            activeLag.set(null)
            monitor.shutdownNow()
            SwingUtilities.invokeAndWait {}
            recording.stop()
            recording.dump(directory.resolve("preview.jfr"))
            recording.close()
            Files.writeString(directory.resolve("samples.csv"),
                "variant,input,pass,warmup,bytes,prepare_ms,install_layout_ms,paint_ms,max_edt_lag_ms\n" +
                    samples.joinToString("\n", postfix = "\n", transform = ::csv))
        }
        val summary = StringBuilder("input,variant,stage,median_ms,max_ms\n")
        for ((key, values) in samples.filterNot { it.warmup }.groupBy { it.input to it.variant }) {
            for ((stage, readings) in listOf("prepare" to values.map { it.prepare }, "install_layout" to values.map { it.installLayout },
                "paint" to values.map { it.paint }, "max_edt_lag" to values.map { it.lag })) {
                val sorted = readings.sorted()
                summary.append("${key.first},${key.second},$stage,${sorted[sorted.size / 2]},${sorted.last()}\n")
            }
        }
        Files.writeString(directory.resolve("summary.csv"), summary)
    }

    private fun measure(variant: String, input: String, text: String, pass: Int, monitor: AtomicReference<AtomicLong?>): Sample {
        lateinit var area: JTextArea
        SwingUtilities.invokeAndWait {
            area = (if (variant == "baseline") JTextArea() else ContextCollectionTextArea()).apply {
                isEditable = false
                font = Font(Font.MONOSPACED, Font.PLAIN, 13)
                setSize(500, 300)
            }
        }
        lateinit var font: Font
        lateinit var context: java.awt.font.FontRenderContext
        SwingUtilities.invokeAndWait { font = area.font; context = area.getFontMetrics(font).fontRenderContext }
        val lag = AtomicLong()
        monitor.set(lag)
        lateinit var document: PlainDocument
        val prepare = stage(variant, input, pass, "prepare") {
            document = if (variant == "baseline") PlainDocument().also { doc ->
                // Exact baseline viewer preparation, including its 8192-code-unit inserts.
                for (start in text.indices step 8192) doc.insertString(doc.length, text.substring(start, minOf(start + 8192, text.length)), null)
            } else ContextCollectionTextViewer.prepareDocument(text, font, context) {}
        }
        var install = 0.0
        var paint = 0.0
        SwingUtilities.invokeAndWait {
            val posted = System.nanoTime()
            SwingUtilities.invokeLater { lag.accumulateAndGet(System.nanoTime() - posted, ::maxOf) }
            install = stage(variant, input, pass, "install_layout") {
                area.document = document
                area.caretPosition = 0
                area.preferredSize
                area.doLayout()
            }
        }
        SwingUtilities.invokeAndWait {
            val posted = System.nanoTime()
            SwingUtilities.invokeLater { lag.accumulateAndGet(System.nanoTime() - posted, ::maxOf) }
            val image = BufferedImage(500, 300, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try { paint = stage(variant, input, pass, "paint") { area.paint(graphics) } }
            finally { graphics.dispose() }
        }
        monitor.set(null)
        SwingUtilities.invokeAndWait { area.document = PlainDocument() }
        return Sample(variant, input, pass, pass < 2, text.toByteArray(Charsets.UTF_8).size, prepare, install, paint, lag.get() / 1e6)
    }

    private inline fun stage(variant: String, input: String, pass: Int, name: String, action: () -> Unit): Double {
        val event = Stage().also { it.variant = variant; it.input = input; it.pass = pass; it.warmup = pass < 2; it.stage = name; it.begin() }
        val start = System.nanoTime()
        try { action() } finally { event.end(); event.commit() }
        return (System.nanoTime() - start) / 1e6
    }

    private fun csv(sample: Sample): String = with(sample) {
        "$variant,$input,$pass,$warmup,$bytes,$prepare,$installLayout,$paint,$lag"
    }
}
