package audit;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionStubBase;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.ProjectManager;

import javax.swing.KeyStroke;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** A separate diagnostic plugin. Never packaged in the product ZIP. */
public final class KeymapAuditStarter implements ApplicationStarter {
    private static final String PREFIX = "CopySelectionContext.";
    private static final List<String> COMMANDS = List.of(
        "Copy", "ShowHistory", "AddToCollection", "ShowCollection", "CopyAllCollection",
        "CopyRelativePath", "CopyAbsolutePath", "CopyWithCodeContent", "CopyGitPermalink"
    );
    private static final List<String> PROBES = List.of(
        "control alt shift G", "meta alt shift G",
        "control alt C", "meta alt C", "control alt H", "meta alt H"
    );
    private static final Set<String> BASELINE_ACTIONS = Set.of(
        "IntroduceConstant", "EditorToggleUseSoftWraps"
    );

    @Override public String getCommandName() { return "csc-keymap-audit"; }
    @Override public boolean isHeadless() { return true; }
    @Override public int getRequiredModality() { return NOT_IN_EDT; }

    @Override public void main(List<String> args) {
        try {
            if (args.size() != 2) throw new IllegalArgumentException("Expected output TSV path");
            writeExport(Path.of(args.get(1)));
            System.out.println("CSC_KEYMAP_AUDIT_COMPLETE " + args.get(1));
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    static void writeExport(Path output) throws Exception {
        List<String> rows = new ArrayList<>();
        StrokeInventory inventory = Boolean.getBoolean("csc.audit.strokeInventory") ? new StrokeInventory() : null;
        var application = ApplicationManager.getApplication();
        if (application.isDispatchThread()) collect(rows, inventory);
        else application.invokeAndWait(() -> collect(rows, inventory));
        byte[] audit = (String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
        Path companion = output.resolveSibling(output.getFileName() + ".strokes.tsv");
        // Neither file is a replacement for older evidence. A missing companion
        // after interruption cannot produce a prefix-comparison verdict.
        if (Files.exists(output) || inventory != null && Files.exists(companion)) {
            throw new IllegalStateException("Evidence path already exists");
        }
        Files.write(output, audit, StandardOpenOption.CREATE_NEW);
        if (inventory != null) inventory.write(companion, output.getFileName().toString(), audit);
    }

    static void collect(List<String> rows, StrokeInventory inventory) {
        var manager = ActionManager.getInstance();
        var keymaps = KeymapManagerEx.getInstanceEx();
        var info = ApplicationInfo.getInstance();
        // Initialize the nine product actions before taking the registered-ID
        // snapshot used consistently by both exports and every keymap scan.
        for (String command : COMMANDS) {
            if (manager.getAction(PREFIX + command) == null) {
                throw new IllegalStateException("Product action is not loaded: " + PREFIX + command);
            }
        }
        Set<String> registered = new TreeSet<>(manager.getActionIdList(""));
        if (inventory != null) inventory.registered(registered);
        Set<String> watched = new TreeSet<>(BASELINE_ACTIONS);
        String removedAction = System.getProperty("csc.audit.removedAction");
        if (removedAction != null && !removedAction.isBlank()) watched.add(removedAction);
        row(rows, "META", "schema", "2");
        row(rows, "META", "timeUtc", Instant.now());
        row(rows, "META", "ide", info.getFullApplicationName());
        row(rows, "META", "build", info.getBuild().asString());
        row(rows, "META", "os", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        row(rows, "META", "activeKeymap", keymaps.getActiveKeymap().getName());
        row(rows, "META", "headless", ApplicationManager.getApplication().isHeadlessEnvironment());
        row(rows, "META", "registeredActionCount", registered.size());
        if (inventory != null) row(rows, "META", "strokeInventory", "complete-v1");
        row(rows, "META", "profileId", System.getProperty("csc.audit.profileId"));
        row(rows, "META", "runId", System.getProperty("csc.audit.runId"));
        row(rows, "META", "processId", ProcessHandle.current().pid());
        row(rows, "META", "harnessJarSha256", System.getProperty("csc.audit.harnessJarSha256"));
        row(rows, "META", "harnessManifestSha256", System.getProperty("csc.audit.harnessManifestSha256"));
        row(rows, "META", "watchedActions", "[" + watched.stream().map(KeymapAuditStarter::jsonString)
            .collect(java.util.stream.Collectors.joining(",")) + "]");
        row(rows, "META", "projectRoots", "[" + Arrays.stream(ProjectManager.getInstance().getOpenProjects())
            .map(project -> jsonString(project.getBasePath())).collect(java.util.stream.Collectors.joining(",")) + "]");
        for (String probe : PROBES) row(rows, "PROBE", probe, KeyStroke.getKeyStroke(probe));
        for (String name : List.of("idea.config.path", "idea.system.path", "idea.plugins.path", "idea.log.path")) {
            row(rows, "META", name, System.getProperty(name));
        }
        for (var plugin : PluginManagerCore.getLoadedPlugins()) {
            row(rows, "PLUGIN", plugin.getPluginId(), plugin.getVersion(), plugin.isBundled(), plugin.getPluginPath());
        }
        for (String command : COMMANDS) {
            String id = PREFIX + command;
            var action = manager.getAction(id);
            if (action == null) throw new IllegalStateException("Product action is not loaded: " + id);
            row(rows, "COMMAND", id, action.getClass().getName());
        }
        var maps = new ArrayList<>(Arrays.asList(keymaps.getAllKeymaps()));
        maps.sort(java.util.Comparator.comparing(Keymap::getName));
        for (Keymap keymap : maps) {
            Set<Keymap> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            List<String> chain = new ArrayList<>();
            for (Keymap node = keymap; node != null; node = node.getParent()) {
                if (!visited.add(node)) throw new IllegalStateException("Cyclic keymap parent chain");
                // getParent alone does not initialize a lazy KeymapImpl. Reading
                // shortcuts resolves the parent before following its next edge.
                node.getShortcuts(PREFIX + "Copy");
                chain.add(node.getName());
            }
            row(rows, "KEYMAP", keymap.getName(), keymap.canModify(), "[" + chain.stream()
                .map(KeymapAuditStarter::jsonString).collect(java.util.stream.Collectors.joining(",")) + "]");
            // Include dormant mappings, action aliases and inherited action IDs as well as
            // registered actions. Keymap.getShortcuts supplies effective inherited values.
            Set<String> ids = new TreeSet<>(registered);
            for (Keymap node = keymap; node != null; node = node.getParent()) {
                Set<String> sourceIds = new TreeSet<>(node.getActionIdList());
                ids.addAll(sourceIds);
                if (inventory != null) inventory.source(keymap.getName(), node.getName(), sourceIds);
            }
            for (String command : COMMANDS) ids.add(PREFIX + command);
            ids.addAll(watched);
            for (String id : ids) {
                Shortcut[] shortcuts = keymap.getShortcuts(id);
                if (inventory != null) inventory.action(keymap.getName(), id, shortcuts, manager);
                if (watched.contains(id)
                    || COMMANDS.stream().anyMatch(command -> id.equals(PREFIX + command))) {
                    row(rows, "BINDING", keymap.getName(), id, shortcutsJson(shortcuts));
                }
                for (Shortcut shortcut : shortcuts) {
                    if (!(shortcut instanceof KeyboardShortcut keyboard)) continue;
                    for (String probe : PROBES) {
                        if (!keyboard.getFirstKeyStroke().equals(KeyStroke.getKeyStroke(probe))) continue;
                        var action = manager.getActionOrStub(id);
                        PluginDescriptor owner = action instanceof ActionStubBase stub ? stub.getPlugin()
                            : action == null ? null
                            : PluginManagerCore.getPluginDescriptorOrPlatformByClassName(action.getClass().getName());
                        row(rows, "OCCUPANCY", keymap.getName(), probe, id,
                            keyboard.getFirstKeyStroke(), keyboard.getSecondKeyStroke(),
                            shortcutsJson(shortcuts), action != null,
                            owner == null ? "unknown" : owner.getPluginId(),
                            owner == null ? "unknown" : owner.isBundled());
                    }
                }
            }
            if (inventory != null) inventory.keymap(keymap.getName(), ids.size());
        }
        long bindings = rows.stream().filter(value -> value.startsWith("BINDING\t")).count();
        long occupancies = rows.stream().filter(value -> value.startsWith("OCCUPANCY\t")).count();
        row(rows, "END", "keymaps", maps.size(), "commands", COMMANDS.size(),
            "bindings", bindings, "occupancies", occupancies, "rows", rows.size() + 1);
    }

    static String shortcutsJson(Shortcut[] shortcuts) {
        List<String> values = new ArrayList<>();
        for (Shortcut shortcut : shortcuts) {
            if (shortcut instanceof KeyboardShortcut keyboard) {
                values.add("{\"kind\":\"keyboard\",\"first\":" + jsonString(keyboard.getFirstKeyStroke().toString())
                    + ",\"second\":" + jsonString(keyboard.getSecondKeyStroke() == null ? null : keyboard.getSecondKeyStroke().toString()) + "}");
            } else if (shortcut instanceof MouseShortcut mouse) {
                values.add("{\"kind\":\"mouse\",\"button\":" + mouse.getButton()
                    + ",\"modifiers\":" + mouse.getModifiers() + ",\"clickCount\":" + mouse.getClickCount() + "}");
            } else {
                values.add("{\"kind\":\"other\",\"display\":" + jsonString(shortcut.toString()) + "}");
            }
        }
        return "[" + String.join(",", values) + "]";
    }

    static String jsonString(String value) {
        if (value == null) return "null";
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            if (character == '"' || character == '\\') result.append('\\').append(character);
            else if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
            else result.append(character);
        }
        return result.append('"').toString();
    }

    static void row(List<String> rows, Object... cells) {
        List<String> values = new ArrayList<>();
        for (Object cell : cells) values.add(String.valueOf(cell).replace("\\", "\\\\")
            .replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n"));
        rows.add(String.join("\t", values));
    }
}
