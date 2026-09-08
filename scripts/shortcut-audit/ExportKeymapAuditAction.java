package audit;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

import java.nio.file.Path;

/** Optional GUI export after project/plugin initialization; never invokes product actions. */
public final class ExportKeymapAuditAction extends DumbAwareAction {
    @Override public void actionPerformed(AnActionEvent event) {
        try {
            String directory = System.getProperty("csc.audit.output");
            if (directory == null) throw new IllegalStateException("Set csc.audit.output to a test evidence directory");
            // Keymaps are read on EDT. This bounded diagnostic writes only to the
            // explicit test evidence directory; it is never part of the product.
            Path output = Path.of(directory, "keymaps-gui-" + System.currentTimeMillis() + ".tsv");
            KeymapAuditStarter.writeExport(output);
            System.out.println("CSC_KEYMAP_AUDIT_GUI_COMPLETE " + output);
        } catch (Exception error) {
            throw new IllegalStateException("Keymap audit failed", error);
        }
    }
}
