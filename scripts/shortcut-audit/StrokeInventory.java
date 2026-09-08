package audit;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionStubBase;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.extensions.PluginDescriptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Optional complete effective shortcut inventory; the six schema-2 probes stay unchanged. */
final class StrokeInventory {
    private final List<String> rows = new ArrayList<>();
    private final Map<String, Owner> owners = new HashMap<>();
    private int registeredCount, sourceCount, actionCount, keymapCount, shortcutCount;
    private int mapShortcuts, mapKeyboard;

    void registered(Set<String> ids) {
        registeredCount = ids.size();
        for (String id : ids) KeymapAuditStarter.row(rows, "REGISTERED", id);
    }

    void source(String keymap, String ancestor, Set<String> ids) {
        sourceCount++;
        KeymapAuditStarter.row(rows, "SOURCE", keymap, ancestor, "[" + ids.stream()
            .map(KeymapAuditStarter::jsonString).collect(java.util.stream.Collectors.joining(",")) + "]");
    }

    void action(String keymap, String id, Shortcut[] shortcuts, ActionManager manager) {
        Owner owner = owners.computeIfAbsent(id, ignored -> {
            var action = manager.getActionOrStub(id);
            PluginDescriptor plugin = action instanceof ActionStubBase stub ? stub.getPlugin()
                : action == null ? null
                : PluginManagerCore.getPluginDescriptorOrPlatformByClassName(action.getClass().getName());
            return new Owner(action != null, plugin == null ? "unknown" : plugin.getPluginId().getIdString(),
                plugin == null ? "unknown" : Boolean.toString(plugin.isBundled()));
        });
        actionCount++;
        mapShortcuts += shortcuts.length;
        for (Shortcut shortcut : shortcuts) if (shortcut instanceof KeyboardShortcut) mapKeyboard++;
        KeymapAuditStarter.row(rows, "ACTION", keymap, id, KeymapAuditStarter.shortcutsJson(shortcuts),
            owner.registered, owner.plugin, owner.bundled);
    }

    void keymap(String name, int actions) {
        keymapCount++;
        shortcutCount += mapShortcuts;
        KeymapAuditStarter.row(rows, "MAP", name, actions, mapShortcuts, mapKeyboard);
        mapShortcuts = mapKeyboard = 0;
    }

    void write(Path output, String auditFile, byte[] auditBytes) throws Exception {
        List<String> complete = new ArrayList<>();
        KeymapAuditStarter.row(complete, "META", "schema", "1");
        KeymapAuditStarter.row(complete, "META", "auditFile", auditFile);
        KeymapAuditStarter.row(complete, "META", "auditSha256",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(auditBytes)));
        complete.addAll(rows);
        KeymapAuditStarter.row(complete, "END", "keymaps", keymapCount, "registered", registeredCount,
            "sources", sourceCount, "actions", actionCount, "shortcuts", shortcutCount, "rows", complete.size() + 1);
        Files.writeString(output, String.join("\n", complete) + "\n", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW);
    }

    private record Owner(boolean registered, String plugin, String bundled) {}
}
