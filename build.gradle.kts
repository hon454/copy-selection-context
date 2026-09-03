import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("dev.detekt") version "2.0.0-alpha.6"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = "com.github.hon454"
version = "1.4.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.1.3")
    testImplementation("org.snakeyaml:snakeyaml-engine:3.1.1")
    testImplementation("io.mockk:mockk:1.14.11") {
        exclude(group = "org.jetbrains.kotlinx")
    }

    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        testFramework(TestFrameworkType.Bundled)
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = false
    ignoreFailures = false
}

intellijPlatform {
    pluginConfiguration {
        name = "Copy Selection Context"
        version = project.version.toString()

        val changelog = project.changelog // local variable for configuration cache compatibility
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(project.version.toString()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild.set("243")
        }
    }
    
    pluginVerification {
        ides {
            recommended()
        }
    }
    
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        certificateChainFile = providers.environmentVariable("CERTIFICATE_CHAIN_FILE").map { file(it) }
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    repositoryUrl = "https://github.com/hon454/copy-selection-context"
}

val platformStateTestClasses = listOf(
    "com.github.hon454.copyselectioncontext.CopyHistoryPersistenceTest",
    "com.github.hon454.copyselectioncontext.CopySelectionActionFixtureTest",
)

intellijPlatformTesting.testIde.register("platformTest") {
    task {
        description = "Runs IntelliJ Platform application and editor-fixture tests in isolated JVMs."
        useJUnitPlatform()
        filter {
            platformStateTestClasses.forEach(::includeTestsMatching)
        }
        // These classes retain engine-scoped application or fixture state.
        forkEvery = 1
    }
}

tasks {
    processResources {
        val pluginVersion = project.version.toString()
        inputs.property("pluginVersion", pluginVersion)
        filesMatching("META-INF/copy-selection-context-version.properties") {
            expand("pluginVersion" to pluginVersion)
        }
    }

    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    test {
        useJUnitPlatform()
        filter {
            platformStateTestClasses.forEach(::excludeTestsMatching)
        }
        // Pure unit tests share the worker instead of paying one JVM startup per class.
        forkEvery = 0
    }

    val allTests =
        register("allTests") {
            description = "Runs reusable unit tests and isolated IntelliJ Platform tests."
            group = "verification"
            dependsOn(test, "platformTest")
        }

    named("check") {
        dependsOn(allTests)
    }

    buildSearchableOptions {
        jvmArgs("-Xshare:off")
    }

    named<PublishPluginTask>("publishPlugin") {
        providers.gradleProperty("canonicalPluginArchive").orNull?.let { canonicalArchive ->
            archiveFile.set(layout.projectDirectory.file(canonicalArchive))
            // The release workflow already built, signed, verified, checksummed, and
            // attested this exact file. Do not rebuild or re-sign it before upload.
            setDependsOn(emptyList<Any>())
        }
    }
}
