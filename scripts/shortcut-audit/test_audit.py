"""Safety and evidence integrity tests; no IDE process or platform state."""

import argparse
import io
import json
from pathlib import Path
import tempfile
import subprocess
import sys
import uuid
from unittest import mock
import unittest
import xml.etree.ElementTree as ET
import zipfile

import audit
from evidence import PROBES, WATCHED_IDS


def keyboard(first, second=None):
    return {"kind": "keyboard", "first": first, "second": second}


def encode_rows(rows):
    escape = lambda value: str(value).replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")
    return "\n".join("\t".join(escape(cell) for cell in row) for row in rows) + "\n"


def recount(rows):
    rows = [row for row in rows if row[0] != "END"]
    rows.append(["END", "keymaps", sum(r[0] == "KEYMAP" for r in rows),
                 "commands", sum(r[0] == "COMMAND" for r in rows),
                 "bindings", sum(r[0] == "BINDING" for r in rows),
                 "occupancies", sum(r[0] == "OCCUPANCY" for r in rows), "rows", len(rows) + 1])
    return rows


class AuditToolTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
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

    def install_harness(self, profile):
        """Synthetic JAR for validation only; never loaded in an IDE."""
        directory = profile / "plugins/csc-keymap-audit"
        jar = directory / audit.HARNESS_JAR
        jar.parent.mkdir(parents=True)
        with zipfile.ZipFile(jar, "x") as archive:
            archive.writestr("META-INF/plugin.xml", (audit.HERE / "plugin.xml").read_bytes())
            archive.writestr("audit/Synthetic.class", b"SIMULATED validator test; not executable")
        audit.write_json(directory / "manifest.json", {"schema": 1, "pluginId": audit.HARNESS_ID,
            "jarSha256": audit.digest(jar), "sources": audit.exporter_sources(),
            "sourceRevision": None, "sourceTreeDirty": None})
        return directory

    def test_existing_profile_is_never_overwritten(self):
        root = self.prepare()
        before = audit.config_snapshot(root)
        with self.assertRaises(FileExistsError):
            self.prepare()
        self.assertEqual(before, audit.config_snapshot(root))

    def test_headless_java_uses_metadata_or_explicit_inspected_bundled_runtime(self):
        home = self.root / "IDE/Contents"
        java = home / "jbr/Contents/Home/bin/java"
        java.parent.mkdir(parents=True)
        java.write_text("synthetic executable path fixture")
        java.chmod(0o700)
        info = home / "Resources/product-info.json"
        self.assertEqual(audit.headless_java(home, info, {"javaExecutablePath": "../jbr/Contents/Home/bin/java"}),
                         (java, "product-info"))
        self.assertEqual(audit.headless_java(home, info, {}, str(java)), (java, "explicit-bundled-runtime"))

    def test_headless_java_rejects_missing_external_relative_or_conflicting_runtime(self):
        home = self.root / "IDE/Contents"
        home.mkdir(parents=True)
        external = self.root / "java"
        external.write_text("synthetic executable path fixture")
        external.chmod(0o700)
        link = home / "java"
        link.symlink_to(external)
        cases = [({}, None), ({}, "jbr/bin/java"), ({}, str(external)), ({}, str(link)),
                 ({}, str(home / "missing")), ({"javaExecutablePath": "../../java"}, None),
                 ({"javaExecutablePath": "java"}, str(external))]
        for launch, explicit in cases:
            with self.subTest(launch=launch, explicit=explicit), self.assertRaises(ValueError):
                audit.headless_java(home, home / "Resources/product-info.json", launch, explicit)

    def test_explicit_old_uses_observed_effective_mac_values(self):
        root = self.prepare("explicit-old")
        keymap = ET.parse(root / "config/keymaps/audit.xml").getroot()
        bindings = {item.attrib["id"]: item.find("keyboard-shortcut").attrib["first-keystroke"]
                    for item in keymap.findall("action")}
        self.assertEqual(bindings[audit.PREFIX + "Copy"], "meta alt C")
        self.assertEqual(bindings[audit.PREFIX + "ShowHistory"], "meta alt H")

    def export_rows(self, profile=None, context=None, pid=12345, extra=None):
        metadata = audit.load_profile(profile) if profile else {"parent": "$default", "case": "pristine"}
        parent, case = metadata["parent"], metadata["case"]
        active = parent if case == "pristine" else "CSC Audit " + case
        meta = {"schema": "2", "timeUtc": "2026-09-09T00:00:01+00:00", "ide": "Test IDE",
                "build": "IC-243.21565.193", "activeKeymap": active, "headless": "false",
                "registeredActionCount": "500", "profileId": metadata.get("profileId", str(uuid.uuid4())),
                "runId": context["runId"] if context else str(uuid.uuid4()), "processId": str(pid),
                "harnessJarSha256": context["harness"]["jarSha256"] if context else "1" * 64,
                "harnessManifestSha256": context["harness"]["manifestSha256"] if context else "2" * 64,
                "watchedActions": json.dumps(sorted(WATCHED_IDS)),
                "projectRoots": json.dumps([context["project"]] if context else [])}
        meta.update({"idea." + name + ".path": str((profile or self.root) / name)
                     for name in ["config", "system", "plugins", "log"]})
        rows = [["META", key, value] for key, value in meta.items()]
        rows += [["META", "os", "Mac OS X", "15.5", "aarch64"],
                 ["PLUGIN", audit.PRODUCT_ID, "1.6.0", "false", "test/plugin"],
                 ["PLUGIN", audit.HARNESS_ID, "1", "false",
                  context["harness"]["directory"] if context else "test/harness"]]
        probes = {name: name.replace("control", "ctrl").rsplit(" ", 1)[0] + " pressed " + name.rsplit(" ", 1)[1]
                  for name in PROBES}
        rows += [["PROBE", name, value] for name, value in sorted(probes.items())]
        rows += [["COMMAND", action, "test.Action"] for action in sorted(audit.COMMAND_IDS)]
        schemes = [parent] if parent == active else [parent, active]
        for scheme in schemes:
            rows.append(["KEYMAP", scheme, str(scheme != parent).lower(), json.dumps([parent] if scheme == parent else [scheme, parent])])
            values = {action: [] for action in audit.COMMAND_IDS | WATCHED_IDS}
            values[audit.PREFIX + "Copy"] = [keyboard("meta alt pressed C")]
            values[audit.PREFIX + "ShowHistory"] = [keyboard("meta alt pressed H")]
            values["IntroduceConstant"] = [keyboard("meta alt pressed C")]
            if scheme == active:
                if case == "custom":
                    values[audit.PREFIX + "Copy"] = [keyboard("shift meta pressed F11"),
                        {"kind": "mouse", "button": 2, "modifiers": 128, "clickCount": 1}]
                    values[audit.PREFIX + "ShowHistory"] = [keyboard("shift meta pressed F12")]
                elif case == "unassigned":
                    values[audit.PREFIX + "Copy"] = values[audit.PREFIX + "ShowHistory"] = []
                elif case == "unrelated-only":
                    values["EditorToggleUseSoftWraps"] = [keyboard("ctrl alt shift pressed F9")]
                elif case == "removed-ide":
                    values["IntroduceConstant"] = []
            rows += [["BINDING", scheme, action, json.dumps(items)] for action, items in sorted(values.items())]
            values.update(extra or {})
            for action, items in sorted(values.items()):
                for item in items:
                    for name, stroke in probes.items():
                        if item["kind"] == "keyboard" and item["first"] == stroke:
                            rows.append(["OCCUPANCY", scheme, name, action, stroke, item["second"] or "null",
                                         json.dumps(items), "true", audit.PRODUCT_ID if action in audit.COMMAND_IDS else "com.intellij", "false"])
        return recount(rows)

    def gui_fixture(self, source):
        """Synthetic evidence for validator tests only; never real GUI acceptance."""
        metadata = audit.load_profile(source)
        self.install_harness(source)
        run_id = str(uuid.uuid4())
        directory = source / ("gui-run-" + run_id)
        directory.mkdir()
        context = audit.run_context(source, metadata, run_id, "gui", "243.21565.193", str(self.root / "project"))
        start = {**context, "pid": 12345, "startedUtc": "2026-09-09T00:00:00+00:00"}
        audit.write_json(directory / "gui-command.json", context)
        audit.write_json(directory / "process-start.json", start)
        audit.write_json(directory / "process-result.json", {**start, "finishedUtc": "2026-09-09T00:00:02+00:00",
            "exitCode": 0, "interrupted": False, "configSnapshot": audit.config_snapshot(source)})
        export = directory / "keymaps.tsv"
        export.write_text(encode_rows(self.export_rows(source, context)))
        (directory / "observation.png").write_bytes(b"\x89PNG\r\n\x1a\nSIMULATED TEST IMAGE")
        (directory / "observation.txt").write_text("SIMULATED accessibility evidence; validator test only")
        return argparse.Namespace(profile=str(source), run_directory=str(directory), export=str(export),
            observed_keymap=metadata["parent"] if metadata["case"] == "pristine" else "CSC Audit " + metadata["case"],
            performer="synthetic unit test", notes="Not a real GUI observation", confirm_observed=True,
            gui_evidence=[str(directory / "observation.png"), str(directory / "observation.txt")])

    def clone(self, source, target):
        audit.clone_upgrade(argparse.Namespace(source=str(source), output=str(target), zip=str(self.archive),
            sha256=audit.digest(self.archive), commit="a" * 40))

    def test_upgrade_clone_requires_coherent_observed_evidence_and_preserves_mouse(self):
        source = self.prepare("custom")
        args = self.gui_fixture(source)
        audit.accept_baseline(args)
        before = audit.config_snapshot(source)
        target = self.root / "candidate"
        self.clone(source, target)
        self.assertEqual(before, audit.config_snapshot(source))
        self.assertEqual(before, audit.config_snapshot(target))
        self.assertEqual((source / "config/keymaps/audit.xml").read_bytes(),
                         (target / "config/keymaps/audit.xml").read_bytes())
        self.assertIn(b'mouse-shortcut keystroke="control button2"',
                      (target / "config/keymaps/audit.xml").read_bytes())
        self.assertNotEqual((source / "idea.properties").read_text(), (target / "idea.properties").read_text())

    def test_all_six_seed_contracts_accept_consistent_synthetic_observations(self):
        for case in audit.CASES:
            with self.subTest(case=case):
                root = self.prepare(case)
                audit.accept_baseline(self.gui_fixture(root))
                audit.validate_acceptance(root)

    def test_unobserved_or_boolean_marker_does_not_allow_clone(self):
        source = self.prepare()
        target = self.root / "candidate"
        with self.assertRaises(ValueError):
            self.clone(source, target)
        audit.write_json(source / "acceptance.json", {"schema": 1, "state": "gui-accepted"})
        with self.assertRaises(ValueError):
            self.clone(source, target)
        self.assertFalse(target.exists())

    def test_clone_rejects_same_nested_parent_and_symlink_paths_before_writes(self):
        source = self.prepare()
        alias = self.root / "alias"
        alias.symlink_to(source, target_is_directory=True)
        before = audit.config_snapshot(source)
        for target in [source, source / "system/candidate", source.parent, alias / "system/candidate"]:
            with self.subTest(target=target), self.assertRaisesRegex(ValueError, "disjoint canonical"):
                self.clone(source, target)
        self.assertEqual(before, audit.config_snapshot(source))
        self.assertFalse((source / "system/candidate").exists())

    def test_acceptance_rejects_missing_launch_bad_exit_and_identity_mismatches(self):
        source = self.prepare()
        args = self.gui_fixture(source)
        directory = Path(args.run_directory)
        path = directory / "process-result.json"
        original = path.read_bytes()
        mutations = {"pid": 999, "exitCode": 1, "interrupted": True, "runId": str(uuid.uuid4()),
                     "profileId": str(uuid.uuid4()), "profileSha256": "0" * 64,
                     "productSha256": "0" * 64, "finishedUtc": "2026-08-01T00:00:00+00:00"}
        for key, value in mutations.items():
            with self.subTest(key=key):
                modified = json.loads(original)
                modified[key] = value
                path.write_text(json.dumps(modified))
                with self.assertRaises(ValueError):
                    audit.accept_baseline(args)
                self.assertFalse((source / "acceptance.json").exists())
        path.write_bytes(original)
        (directory / "process-start.json").unlink()
        with self.assertRaises(ValueError):
            audit.accept_baseline(args)

    def test_acceptance_rejects_unobserved_cross_run_and_symlink_gui_evidence(self):
        source = self.prepare()
        args = self.gui_fixture(source)
        args.confirm_observed = False
        with self.assertRaises(ValueError):
            audit.accept_baseline(args)
        args.confirm_observed = True
        original = args.gui_evidence[0]
        other = source / "other.png"
        other.write_bytes(Path(original).read_bytes())
        args.gui_evidence[0] = str(other)
        with self.assertRaises(ValueError):
            audit.accept_baseline(args)
        args.gui_evidence[0] = original
        Path(original).unlink()
        Path(original).symlink_to(other)
        with self.assertRaises(ValueError):
            audit.accept_baseline(args)

    def test_native_jpeg_evidence_is_supported_and_wrong_signature_is_rejected(self):
        source = self.prepare()
        args = self.gui_fixture(source)
        image = Path(args.run_directory) / "native.jpeg"
        image.write_bytes(b"SIMULATED wrong signature")
        args.gui_evidence[0] = str(image)
        with self.assertRaisesRegex(ValueError, "screenshot"):
            audit.accept_baseline(args)
        image.write_bytes(b"\xff\xd8\xff\xe0SIMULATED JPEG test fixture")
        audit.accept_baseline(args)
        audit.validate_acceptance(source)

    def test_harness_rejects_missing_stale_changed_wrong_plugin_and_extra_files(self):
        source = self.prepare()
        directory = self.install_harness(source)
        audit.validate_harness(directory)
        manifest = directory / "manifest.json"
        original_manifest = manifest.read_bytes()
        manifest.unlink()
        with self.assertRaises(ValueError):
            audit.validate_harness(directory)
        manifest.write_bytes(original_manifest)
        for key, value in [("pluginId", "wrong.plugin"), ("sources", {"old.java": "1" * 64}),
                           ("jarSha256", "2" * 64)]:
            with self.subTest(key=key):
                data = json.loads(original_manifest)
                data[key] = value
                manifest.write_text(json.dumps(data))
                with self.assertRaises(ValueError):
                    audit.validate_harness(directory)
        manifest.write_bytes(original_manifest)
        jar = directory / audit.HARNESS_JAR
        original_jar = jar.read_bytes()
        jar.write_bytes(original_jar + b"changed")
        with self.assertRaisesRegex(ValueError, "JAR changed"):
            audit.validate_harness(directory)
        jar.write_bytes(original_jar)
        (directory / "extra.jar").write_bytes(b"extra")
        with self.assertRaisesRegex(ValueError, "unexpected harness files"):
            audit.validate_harness(directory)

    def test_wrong_descriptor_rejected_even_when_jar_digest_matches_manifest(self):
        source = self.prepare()
        directory = self.install_harness(source)
        jar = directory / audit.HARNESS_JAR
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/plugin.xml", "<idea-plugin><id>wrong.plugin</id></idea-plugin>")
        manifest = directory / "manifest.json"
        data = json.loads(manifest.read_text())
        data["jarSha256"] = audit.digest(jar)
        manifest.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "wrong harness plugin ID"):
            audit.validate_harness(directory)

    def test_stale_harness_stops_audit_and_gui_before_launch_or_copy(self):
        source = self.prepare()
        directory = self.install_harness(source)
        (directory / "manifest.json").unlink()
        with mock.patch.object(audit, "run_process") as launch:
            with self.assertRaises(ValueError):
                audit.audit(argparse.Namespace(profile=str(source), ide_home=str(self.root / "no-ide"),
                    harness=str(directory), timeout=10))
            with self.assertRaises(ValueError):
                audit.launch_gui(argparse.Namespace(profile=str(source), app=str(self.root / "no-ide"),
                    project=str(self.root / "project")))
            launch.assert_not_called()

    def test_gui_all_os_keymaps_is_explicit_and_recorded_in_launch_provenance(self):
        app = self.root / "Synthetic.app"
        resources = app / "Contents/Resources"
        resources.mkdir(parents=True)
        (resources / "original.vmoptions").write_text("-Xmx512m\n")
        (resources / "product-info.json").write_text(json.dumps({
            "envVarBaseName": "SYNTHETIC", "buildNumber": "test-only",
            "launch": [{"os": "macOS", "arch": "aarch64", "launcherPath": "fake-launcher",
                        "vmOptionsFilePath": "original.vmoptions"}],
        }))
        for enabled in [False, True]:
            with self.subTest(all_os_keymaps=enabled):
                root = self.root / str(enabled)
                audit.prepare(argparse.Namespace(output=str(root), zip=str(self.archive),
                    sha256=audit.digest(self.archive), case=None, parent="Mac OS X 10.5+",
                    native_mac=True, removed_action="IntroduceConstant", old_copy=None, old_history=None))
                self.install_harness(root)
                before = audit.config_snapshot(root)
                with mock.patch.object(audit, "run_process") as launch:
                    audit.launch_gui(argparse.Namespace(app=str(app), profile=str(root),
                        project=str(self.root / "project"), all_os_keymaps=enabled))
                launch.assert_called_once()
                command, _, directory, context, environment = launch.call_args.args
                recorded = json.loads((directory / "gui-command.json").read_text())
                options = (directory / "gui.vmoptions").read_text()
                self.assertTrue(options.startswith("-Xmx512m\n"))
                self.assertEqual(options.count("-Dkeymap.current.os.only=false\n"), int(enabled))
                self.assertIs(recorded["keymapOsFilterOverride"], False if enabled else None)
                self.assertEqual(recorded["keymapOsFilterOverride"], context["keymapOsFilterOverride"])
                self.assertEqual(environment["SYNTHETIC_VM_OPTIONS"], str(directory / "gui.vmoptions"))
                self.assertEqual(command[0], str(resources / "fake-launcher"))
                self.assertEqual(before, audit.config_snapshot(root))

    def test_harness_change_invalidates_accepted_baseline_and_export_must_match_run(self):
        source = self.prepare()
        args = self.gui_fixture(source)
        raw = Path(args.export)
        original = raw.read_text()
        rows = [[audit.parse_export(raw)["metadata"]["harnessJarSha256"], "0" * 64],
                [audit.parse_export(raw)["metadata"]["harnessManifestSha256"], "0" * 64]]
        for before, after in rows:
            raw.write_text(original.replace(before, after))
            with self.assertRaisesRegex(ValueError, "export harness identity"):
                audit.accept_baseline(args)
        raw.write_text(original)
        audit.accept_baseline(args)
        directory = source / "plugins/csc-keymap-audit"
        for path in [directory / audit.HARNESS_JAR, directory / "manifest.json"]:
            before = path.read_bytes()
            path.write_bytes(before + b" ")
            with self.assertRaises(ValueError):
                audit.validate_acceptance(source)
            path.write_bytes(before)
        audit.validate_acceptance(source)

    def test_changed_config_raw_gui_or_product_invalidates_acceptance(self):
        source = self.prepare()
        args = self.gui_fixture(source)
        audit.accept_baseline(args)
        paths = [source / "config/options/keymap.xml", Path(args.export), Path(args.gui_evidence[1]),
                 next((source / "plugins").rglob("*.jar"))]
        for path in paths:
            with self.subTest(path=path):
                original = path.read_bytes()
                path.write_bytes(original + b"changed")
                with self.assertRaises(ValueError):
                    audit.validate_acceptance(source)
                path.write_bytes(original)
        audit.validate_acceptance(source)

    def test_seed_contract_rejects_lost_mouse_unassigned_and_deleted_ide_values(self):
        for case, action in [("custom", audit.PREFIX + "Copy"), ("unassigned", audit.PREFIX + "Copy"),
                             ("removed-ide", "IntroduceConstant"), ("unrelated-only", "EditorToggleUseSoftWraps")]:
            with self.subTest(case=case):
                source = self.prepare(case)
                args = self.gui_fixture(source)
                data = audit.parse_export(Path(args.export))
                key = (args.observed_keymap, action)
                data["bindings"][key][3] = json.dumps([keyboard("meta alt pressed C")])
                with self.assertRaises(ValueError):
                    audit.validate_seed_bindings(audit.load_profile(source), data)

    def test_unassigned_and_unrelated_cases_keep_distinct_intent(self):
        empty = self.prepare("unassigned")
        entries = ET.parse(empty / "config/keymaps/audit.xml").getroot().findall("action")
        self.assertEqual(len(entries), 2)
        self.assertTrue(all(len(entry) == 0 for entry in entries))
        unrelated = self.prepare("unrelated-only")
        self.assertNotIn(audit.PREFIX, (unrelated / "config/keymaps/audit.xml").read_text())

    def test_malformed_and_incomplete_exports_fail_without_summary(self):
        complete = self.export_rows()
        cases = {
            "missing END": complete[:-1],
            "duplicate END": complete + [complete[-1]],
            "wrong counts": complete[:-1] + [["END", "keymaps", 99, "commands", 9, "bindings", 11, "occupancies", 3, "rows", len(complete)]],
            "missing command": recount([r for r in complete if not (r[0] == "COMMAND" and r[1].endswith("Copy"))]),
            "zero command": recount([r for r in complete if r[0] != "COMMAND"]),
            "duplicate command": recount(complete + [next(r for r in complete if r[0] == "COMMAND")]),
            "missing keymap": recount([r for r in complete if r[0] != "KEYMAP"]),
            "duplicate keymap": recount(complete + [next(r for r in complete if r[0] == "KEYMAP")]),
            "missing binding": recount([r for r in complete if not (r[0] == "BINDING" and r[2].endswith("Copy"))]),
            "missing watch": recount([r for r in complete if not (r[0] == "BINDING" and r[2] == "IntroduceConstant")]),
            "duplicate binding": recount(complete + [next(r for r in complete if r[0] == "BINDING")]),
            "missing occupancy": recount([r for r in complete if r[0] != "OCCUPANCY"]),
            "missing probe": recount([r for r in complete if not (r[0] == "PROBE" and r[1] == "meta alt C")]),
            "missing metadata": recount([r for r in complete if not (r[0] == "META" and r[1] == "runId")]),
            "unknown row": recount(complete + [["UNKNOWN", "test"]]),
        }
        for name, rows in cases.items():
            with self.subTest(name=name):
                source, target = self.root / "bad.tsv", self.root / "result.json"
                source.write_text(encode_rows(rows))
                with self.assertRaises(ValueError):
                    audit.summarize(source, target)
                self.assertFalse(target.exists())

    def test_external_single_and_other_chord_occupancies_are_kept(self):
        source = self.root / "complete.tsv"
        source.write_text(encode_rows(self.export_rows(extra={
            "External.single": [keyboard("ctrl alt shift pressed G")],
            "External.chord": [keyboard("ctrl alt shift pressed G", "pressed Q")]})))
        target = self.root / "summary.json"
        audit.summarize(source, target)
        result = json.loads(target.read_text())
        self.assertEqual({row[3] for row in result["externalPrefixOccupancy"]},
                         {"External.single", "External.chord"})
        self.assertEqual(len(result["oldCHOccupancy"]), 3)
        self.assertFalse(result["legacyInspectionOnly"])

    def test_legacy_requires_opt_in_and_still_checks_completeness(self):
        rows = self.export_rows()
        rows = [r for r in rows if r[0] not in {"PROBE", "END"}]
        for row in rows:
            if row[:2] == ["META", "schema"]:
                row[2] = "1"
        rows.append(["END", "keymaps", 1, "commands", 9])
        source = self.root / "legacy.tsv"
        source.write_text(encode_rows(rows))
        with self.assertRaisesRegex(ValueError, "legacy export"):
            audit.parse_export(source)
        self.assertTrue(audit.parse_export(source, allow_legacy=True)["legacyInspectionOnly"])
        rows[-1][-1] = 8
        source.write_text(encode_rows(rows))
        with self.assertRaises(ValueError):
            audit.parse_export(source, allow_legacy=True)

    def test_config_symlinks_are_rejected(self):
        source = self.prepare()
        (source / "config/linked").symlink_to(self.archive)
        with self.assertRaisesRegex(ValueError, "symlink"):
            audit.config_snapshot(source)

    def test_process_timeout_records_pid_and_cleans_up(self):
        source = self.prepare()
        run = source / "process-test"
        run.mkdir()
        with self.assertRaises(subprocess.TimeoutExpired):
            audit.run_process([sys.executable, "-c", "import time; time.sleep(30)"],
                              source, run, {"runId": "test"}, timeout=0.1)
        start = json.loads((run / "process-start.json").read_text())
        result = json.loads((run / "process-result.json").read_text())
        self.assertEqual(start["pid"], result["pid"])
        self.assertTrue(result["interrupted"])
        self.assertIsInstance(result["exitCode"], int)
        with self.assertRaises(ProcessLookupError):
            audit.os.kill(result["pid"], 0)

    def test_abnormal_exit_is_reported_with_original_exit_code(self):
        source = self.prepare()
        run = source / "exit-test"
        run.mkdir()
        with self.assertRaisesRegex(RuntimeError, "IDE exited 7"):
            audit.run_process([sys.executable, "-c", "raise SystemExit(7)"], source, run, {})
        result = json.loads((run / "process-result.json").read_text())
        self.assertEqual(result["exitCode"], 7)
        self.assertTrue(result["interrupted"])

    def test_keyboard_interrupt_records_cleanup_result(self):
        source = self.prepare()
        run = source / "interrupt-test"
        run.mkdir()
        wait = subprocess.Popen.wait
        first = True
        def interrupt_once(process, *args, **kwargs):
            nonlocal first
            if first:
                first = False
                raise KeyboardInterrupt()
            return wait(process, *args, **kwargs)
        with mock.patch.object(subprocess.Popen, "wait", interrupt_once):
            with self.assertRaises(KeyboardInterrupt):
                audit.run_process([sys.executable, "-c", "import time; time.sleep(30)"], source, run, {})
        result = json.loads((run / "process-result.json").read_text())
        self.assertTrue(result["interrupted"])
        self.assertIsInstance(result["exitCode"], int)
        with self.assertRaises(ProcessLookupError):
            audit.os.kill(result["pid"], 0)

    def test_archive_path_traversal_is_rejected(self):
        bad = self.root / "bad.zip"
        with zipfile.ZipFile(bad, "w") as archive:
            archive.writestr("../outside", "unexpected")
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            audit.unpack_product(bad, self.root / "plugins")
        self.assertFalse((self.root / "outside").exists())


if __name__ == "__main__":
    unittest.main()
