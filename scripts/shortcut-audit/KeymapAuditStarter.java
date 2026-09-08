package audit;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionStubBase;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;

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

    @Override public String getCommandName() { return "csc-keymap-audit"; }
    @Override public boolean isHeadless() { return true; }
    @Override public int getRequiredModality() { return NOT_IN_EDT; }

    @Override public void main(List<String> args) {
        try {
            if (args.size() != 2) throw new IllegalArgumentException("Expected output TSV path");
            List<String> rows = new ArrayList<>();
            ApplicationManager.getApplication().invokeAndWait(() -> collect(rows));
            // Refuse to replace previous evidence. The runner supplies a fresh filename.
            Files.write(Path.of(args.get(1)), rows, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            System.out.println("CSC_KEYMAP_AUDIT_COMPLETE rows=" + rows.size());
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    static void collect(List<String> rows) {
        var manager = ActionManager.getInstance();
        var keymaps = KeymapManagerEx.getInstanceEx();
        var info = ApplicationInfo.getInstance();
        row(rows, "META", "schema", "1");
        row(rows, "META", "timeUtc", Instant.now());
        row(rows, "META", "ide", info.getFullApplicationName());
        row(rows, "META", "build", info.getBuild().asString());
        row(rows, "META", "os", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        row(rows, "META", "activeKeymap", keymaps.getActiveKeymap().getName());
        row(rows, "META", "headless", ApplicationManager.getApplication().isHeadlessEnvironment());
        row(rows, "META", "registeredActionCount", manager.getActionIdList("").size());
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
                chain.add(node.getName());
            }
            row(rows, "KEYMAP", keymap.getName(), keymap.canModify(), String.join(" -> ", chain));
            // Include dormant mappings, action aliases and inherited action IDs as well as
            // registered actions. Keymap.getShortcuts supplies effective inherited values.
            Set<String> ids = new TreeSet<>(manager.getActionIdList(""));
            for (Keymap node = keymap; node != null; node = node.getParent()) ids.addAll(node.getActionIdList());
            for (String command : COMMANDS) ids.add(PREFIX + command);
            for (String id : ids) {
                Shortcut[] shortcuts = keymap.getShortcuts(id);
                if (COMMANDS.stream().anyMatch(command -> id.equals(PREFIX + command))) {
                    row(rows, "BINDING", keymap.getName(), id, Arrays.toString(shortcuts));
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
                            Arrays.toString(shortcuts), action != null,
                            owner == null ? "unknown" : owner.getPluginId(),
                            owner == null ? "unknown" : owner.isBundled());
                    }
                }
            }
        }
        row(rows, "END", "keymaps", maps.size(), "commands", COMMANDS.size());
    }

    private static void row(List<String> rows, Object... cells) {
        List<String> values = new ArrayList<>();
        for (Object cell : cells) values.add(String.valueOf(cell).replace("\\", "\\\\")
            .replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n"));
        rows.add(String.join("\t", values));
    }
}
