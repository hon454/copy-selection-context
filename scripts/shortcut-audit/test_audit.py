"""Safety and evidence integrity tests; no IDE process or platform state."""

import argparse
import io
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

import audit


class AuditToolTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.archive = self.root / "product.zip"
        jar_bytes = io.BytesIO()
        with zipfile.ZipFile(jar_bytes, "w") as jar:
            jar.writestr("META-INF/plugin.xml", f"<idea-plugin><id>{audit.PRODUCT_ID}</id><version>1.6.0</version></idea-plugin>")
        with zipfile.ZipFile(self.archive, "w") as archive:
            archive.writestr("copy-selection-context/lib/product.jar", jar_bytes.getvalue())

    def prepare(self, case="pristine"):
        root = self.root / case
        audit.prepare(argparse.Namespace(output=str(root), zip=str(self.archive),
            sha256=audit.digest(self.archive), case=case, parent="Mac OS X 10.5+",
            native_mac=True, removed_action="IntroduceConstant", old_copy="meta alt C", old_history="meta alt H"))
        return root

    def test_existing_profile_is_never_overwritten(self):
        root = self.prepare()
        before = audit.config_snapshot(root)
        with self.assertRaises(FileExistsError):
            self.prepare()
        self.assertEqual(before, audit.config_snapshot(root))

    def test_explicit_old_uses_observed_effective_mac_values(self):
        root = self.prepare("explicit-old")
        keymap = ET.parse(root / "config/keymaps/audit.xml").getroot()
        bindings = {item.attrib["id"]: item.find("keyboard-shortcut").attrib["first-keystroke"]
                    for item in keymap.findall("action")}
        self.assertEqual(bindings[audit.PREFIX + "Copy"], "meta alt C")
        self.assertEqual(bindings[audit.PREFIX + "ShowHistory"], "meta alt H")

    def test_upgrade_clone_preserves_original_and_mouse_payload(self):
        source = self.prepare("custom")
        before = audit.config_snapshot(source)
        target = self.root / "candidate"
        audit.clone_upgrade(argparse.Namespace(source=str(source), output=str(target), zip=str(self.archive),
            sha256=audit.digest(self.archive), commit="a" * 40))
        self.assertEqual(before, audit.config_snapshot(source))
        self.assertEqual(before, audit.config_snapshot(target))
        self.assertEqual((source / "config/keymaps/audit.xml").read_bytes(),
                         (target / "config/keymaps/audit.xml").read_bytes())
        self.assertIn(b'mouse-shortcut keystroke="control button2"',
                      (target / "config/keymaps/audit.xml").read_bytes())
        self.assertNotEqual((source / "idea.properties").read_text(), (target / "idea.properties").read_text())

    def test_unassigned_and_unrelated_cases_keep_distinct_intent(self):
        empty = self.prepare("unassigned")
        entries = ET.parse(empty / "config/keymaps/audit.xml").getroot().findall("action")
        self.assertEqual(len(entries), 2)
        self.assertTrue(all(len(entry) == 0 for entry in entries))
        unrelated = self.prepare("unrelated-only")
        self.assertNotIn(audit.PREFIX, (unrelated / "config/keymaps/audit.xml").read_text())

    def test_incomplete_and_zero_action_runtime_exports_fail(self):
        source = self.root / "incomplete.tsv"
        source.write_text("KEYMAP\t$default\tfalse\t$default\nEND\tkeymaps\t1\n")
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            audit.summarize(source, self.root / "result.json")
        self.assertFalse((self.root / "result.json").exists())

    def test_external_single_and_other_chord_occupancies_are_kept(self):
        source = self.root / "complete.tsv"
        rows = ["COMMAND\t" + audit.PREFIX + name for name in audit.COMMANDS]
        rows += ["KEYMAP\t$default\tfalse\t$default",
                 "OCCUPANCY\t$default\tcontrol alt shift G\tExternal.single\tG\tnull",
                 "OCCUPANCY\t$default\tcontrol alt shift G\tExternal.chord\tG\tQ",
                 "OCCUPANCY\t$default\tcontrol alt shift G\tCopySelectionContext.Copy\tG\tC",
                 "OCCUPANCY\t$default\tcontrol alt C\tIntroduceConstant\tC\tnull",
                 "END\tkeymaps\t1\tcommands\t9"]
        source.write_text("\n".join(rows) + "\n")
        target = self.root / "summary.json"
        audit.summarize(source, target)
        result = json.loads(target.read_text())
        self.assertEqual([row[3] for row in result["externalPrefixOccupancy"]],
                         ["External.single", "External.chord"])
        self.assertEqual(len(result["oldCHOccupancy"]), 1)

    def test_archive_path_traversal_is_rejected(self):
        bad = self.root / "bad.zip"
        with zipfile.ZipFile(bad, "w") as archive:
            archive.writestr("../outside", "unexpected")
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            audit.unpack_product(bad, self.root / "plugins")
        self.assertFalse((self.root / "outside").exists())


if __name__ == "__main__":
    unittest.main()
