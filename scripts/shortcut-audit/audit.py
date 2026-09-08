#!/usr/bin/env python3
"""Prepare isolated profiles and run a separate, read-only IntelliJ keymap probe.

No network, personal IDE profile discovery, IDE installation, or product build.
Every new profile/output must have a previously unused path.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from datetime import datetime, timezone
import xml.etree.ElementTree as ET
import zipfile


HERE = Path(__file__).resolve().parent
PRODUCT_ID = "com.github.hon454.copy-selection-context"
PREFIX = "CopySelectionContext."
COMMANDS = ["Copy", "ShowHistory", "AddToCollection", "ShowCollection", "CopyAllCollection",
            "CopyRelativePath", "CopyAbsolutePath", "CopyWithCodeContent", "CopyGitPermalink"]
CASES = ["pristine", "unrelated-only", "explicit-old", "custom", "unassigned", "removed-ide"]


def digest(path):
    checksum = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def write_json(path, data):
    with open(path, "x", encoding="utf-8") as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def unpack_product(archive, destination):
    with zipfile.ZipFile(archive) as source:
        entries = source.infolist()
        if any(Path(entry.filename).is_absolute() or ".." in Path(entry.filename).parts
               or (entry.external_attr >> 16) & 0o170000 == 0o120000 for entry in entries):
            raise ValueError("Unsafe ZIP member")
        if sum(entry.file_size for entry in entries) > 100 * 1024 * 1024:
            raise ValueError("Unexpectedly large product ZIP")
        descriptors = []
        import io
        for entry in entries:
            if entry.filename.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(source.read(entry))) as jar:
                    if "META-INF/plugin.xml" in jar.namelist():
                        descriptors.append(ET.fromstring(jar.read("META-INF/plugin.xml")))
        product = [item for item in descriptors if item.findtext("id") == PRODUCT_ID]
        if len(product) != 1:
            raise ValueError("ZIP must contain exactly one product descriptor")
        source.extractall(destination)
        return product[0].findtext("version")


def initialize(root):
    root.mkdir(parents=True, exist_ok=False)
    for name in ["config", "system", "plugins", "log"]:
        (root / name).mkdir()
    (root / "idea.properties").write_text("".join(
        f"idea.{name}.path={root / name}\n" for name in ["config", "system", "plugins", "log"]
    ), encoding="utf-8")


def profile_metadata(root, archive, version, **extra):
    write_json(root / "profile.json", {
        "schema": 1, "createdUtc": datetime.now(timezone.utc).isoformat(),
        "profile": str(root), "productZip": str(archive), "productSha256": digest(archive),
        "productVersion": version, "state": "prepared-not-launched", **extra,
    })


def write_xml(path, element):
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(element)
    ET.ElementTree(element).write(path, encoding="utf-8", xml_declaration=True)


def seed_keymap(root, case, parent, native_mac, removed_action, old_copy=None, old_history=None):
    name = parent if case == "pristine" else "CSC Audit " + case
    options = ET.Element("application")
    ET.SubElement(ET.SubElement(options, "component", name="KeymapManager"), "active_keymap", name=name)
    write_xml(root / "config/options/keymap.xml", options)
    if case == "pristine":
        return
    keymap = ET.Element("keymap", version="1", name=name, parent=parent)
    modifier = "meta" if native_mac else "control"
    if case == "unrelated-only":
        action = ET.SubElement(keymap, "action", id="EditorToggleUseSoftWraps")
        ET.SubElement(action, "keyboard-shortcut", {"first-keystroke": "control alt shift F9"})
    elif case in ["explicit-old", "custom", "unassigned"]:
        for command, key in [("Copy", "C"), ("ShowHistory", "H")]:
            action = ET.SubElement(keymap, "action", id=PREFIX + command)
            if case != "unassigned":
                # Use observed effective values: native Mac keymaps can transform
                # inherited descriptor shortcuts (including v1.6.0 History).
                shortcut = (old_copy if command == "Copy" else old_history) if case == "explicit-old" else f"{modifier} shift F{11 if key == 'C' else 12}"
                ET.SubElement(action, "keyboard-shortcut", {"first-keystroke": shortcut})
        if case == "custom":
            ET.SubElement(keymap.find("action"), "mouse-shortcut", keystroke="control button2")
    elif case == "removed-ide":
        ET.SubElement(keymap, "action", id=removed_action)
    write_xml(root / "config/keymaps/audit.xml", keymap)


def prepare(args):
    root, archive = Path(args.output).resolve(), Path(args.zip).resolve()
    if digest(archive) != args.sha256:
        raise ValueError("Product ZIP digest mismatch")
    initialize(root)
    version = unpack_product(archive, root / "plugins")
    if args.case and version != "1.6.0":
        raise ValueError("Upgrade baseline cases require the released v1.6.0 ZIP")
    if args.case:
        seed_keymap(root, args.case, args.parent, args.native_mac, args.removed_action,
                    args.old_copy, args.old_history)
    profile_metadata(root, archive, version, case=args.case, parent=args.parent,
                     removedAction=args.removed_action, oldCopy=args.old_copy, oldHistory=args.old_history)


def clone_upgrade(args):
    source, target = Path(args.source).resolve(), Path(args.output).resolve()
    original = json.loads((source / "profile.json").read_text())
    if original["productVersion"] != "1.6.0":
        raise ValueError("Source is not a prepared v1.6.0 baseline")
    if (source / "config/.lock").exists():
        raise ValueError("Stop the baseline IDE before cloning")
    archive = Path(args.zip).resolve()
    if digest(archive) != args.sha256:
        raise ValueError("Candidate ZIP digest mismatch")
    initialize(target)
    shutil.copytree(source / "config", target / "config", dirs_exist_ok=True)
    version = unpack_product(archive, target / "plugins")
    profile_metadata(target, archive, version, baseline=str(source),
                     baselineConfig=config_snapshot(source), candidateSha=args.commit)


def config_snapshot(root):
    return {str(path.relative_to(root)): digest(path)
            for path in sorted((root / "config").rglob("*")) if path.is_file()}


def build(args):
    home, output = Path(args.ide_home).resolve(), Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=False)
    classes = output / "classes"
    classes.mkdir()
    subprocess.run([str(Path(args.jdk) / "bin/javac"), "--release", "21", "-encoding", "UTF-8",
                    "-cp", str(home / "lib/*"), "-d", str(classes),
                    *[str(path) for path in sorted(HERE.glob("*.java"))]], check=True)
    jar = output / "csc-keymap-audit/lib/csc-keymap-audit.jar"
    jar.parent.mkdir(parents=True)
    with zipfile.ZipFile(jar, "x", zipfile.ZIP_DEFLATED) as archive:
        archive.write(HERE / "plugin.xml", "META-INF/plugin.xml")
        for path in classes.rglob("*.class"):
            archive.write(path, str(path.relative_to(classes)))
    print(jar)


def audit(args):
    home, root = Path(args.ide_home).resolve(), Path(args.profile).resolve()
    metadata = json.loads((root / "profile.json").read_text())
    if metadata["profile"] != str(root):
        raise ValueError("Profile path changed; create or clone a profile explicitly")
    probe = Path(args.harness).resolve()
    shutil.copytree(probe, root / "plugins/csc-keymap-audit", dirs_exist_ok=False)
    product_info_path = home / "Resources/product-info.json"
    if not product_info_path.exists():
        product_info_path = home / "product-info.json"
    info = json.loads(product_info_path.read_text())
    launch = next(item for item in info["launch"] if item["os"] == "macOS" and item["arch"] == "aarch64")
    # Resolve product-info-relative paths; do not guess native/JBR names.
    java = (product_info_path.parent / launch["javaExecutablePath"]).resolve()
    classpath = os.pathsep.join(str(home / "lib" / name) for name in launch["bootClassPathJarNames"])
    # Gradle's cached transform is the Contents directory itself, without an
    # enclosing .app. Resolve that form before the general bundle placeholder.
    jvm = [item.replace("$APP_PACKAGE/Contents", str(home))
           .replace("$APP_PACKAGE", str(home.parent)).replace("$IDE_HOME", str(home))
           for item in launch["additionalJvmArguments"]]
    command = [str(java), "-Xms128m", "-Xmx1536m", *jvm, "-Djava.awt.headless=true",
               f"-Didea.home.path={home}", f"-Didea.properties.file={root / 'idea.properties'}",
               f"-XX:ErrorFile={root / 'log/hs_err_%p.log'}", f"-XX:HeapDumpPath={root / 'log/heap.hprof'}"]
    command += [f"-Didea.{name}.path={root / name}" for name in ["config", "system", "plugins", "log"]]
    command += ["-cp", classpath, "com.intellij.idea.Main", "csc-keymap-audit", str(root / "keymaps.tsv")]
    write_json(root / "audit-command.json", {"argv": command, "productInfo": info, "profile": metadata})
    with open(root / "audit-console.log", "x") as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=args.timeout)
    if result.returncode:
        raise RuntimeError(f"IDE exited {result.returncode}; see {root / 'audit-console.log'}")
    summarize(root / "keymaps.tsv", root / "summary.json")


def launch_gui(args):
    app, root = Path(args.app).resolve(), Path(args.profile).resolve()
    metadata = json.loads((root / "profile.json").read_text())
    if metadata["profile"] != str(root):
        raise ValueError("Profile path changed; create or clone it explicitly")
    info_path = app / "Contents/Resources/product-info.json"
    info = json.loads(info_path.read_text())
    launch = next(item for item in info["launch"] if item["os"] == "macOS" and item["arch"] == "aarch64")
    launcher = (info_path.parent / launch["launcherPath"]).resolve()
    original_options = (info_path.parent / launch["vmOptionsFilePath"]).read_text()
    run_directory = root / ("gui-run-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    run_directory.mkdir()
    vmoptions = run_directory / "gui.vmoptions"
    with open(vmoptions, "x") as stream:
        stream.write(original_options + f"\n-XX:ErrorFile={root / 'log/hs_err_%p.log'}\n"
                     + f"-XX:HeapDumpPath={root / 'log/heap.hprof'}\n"
                     + f"-Dcsc.audit.output={root}\n")
    environment = os.environ.copy()
    variable = info["envVarBaseName"]
    environment[variable + "_PROPERTIES"] = str(root / "idea.properties")
    environment[variable + "_VM_OPTIONS"] = str(vmoptions)
    command = [str(launcher), str(Path(args.project).resolve())]
    write_json(run_directory / "gui-command.json", {"argv": command, "productInfo": info, "profile": metadata,
                                           "properties": str(root / "idea.properties"), "vmOptions": str(vmoptions)})
    with open(run_directory / "gui-console.log", "x") as log:
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, env=environment)
        print(f"Started test IDE pid={process.pid}, profile={root}", flush=True)
        result = process.wait()
    if result:
        raise RuntimeError(f"IDE exited {result}; see {run_directory / 'gui-console.log'}")


def summarize(source, target):
    with source.open(encoding="utf-8") as stream:
        rows = [line.rstrip("\n").split("\t") for line in stream]
    commands = [row[1] for row in rows if row[0] == "COMMAND"]
    keymaps = [row for row in rows if row[0] == "KEYMAP"]
    if not rows or rows[-1][0] != "END" or set(commands) != {PREFIX + name for name in COMMANDS} or not keymaps:
        raise ValueError("Incomplete runtime evidence (missing END, keymaps or product actions)")
    occupancy = [row for row in rows if row[0] == "OCCUPANCY"]
    external = [row for row in occupancy if row[2].endswith("shift G") and row[3] not in commands]
    write_json(target, {"sourceSha256": digest(source), "keymapCount": len(keymaps),
                       "commandCount": len(commands), "keymaps": keymaps,
                       "externalPrefixOccupancy": external,
                       "oldCHOccupancy": [row for row in occupancy if not row[2].endswith("shift G")],
                       "note": "Loaded effective keymap data only; no physical key, GUI, OS or IME verdict"})
    print(f"{len(keymaps)} keymaps, {len(commands)} product actions, {len(external)} external prefix occupancies")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    make = commands.add_parser("prepare")
    make.add_argument("--output", required=True)
    make.add_argument("--zip", required=True)
    make.add_argument("--sha256", required=True)
    make.add_argument("--case", choices=CASES)
    make.add_argument("--parent", default="Mac OS X 10.5+")
    make.add_argument("--native-mac", action="store_true")
    make.add_argument("--removed-action")
    make.add_argument("--old-copy", help="Observed effective v1.6.0 Copy keystroke")
    make.add_argument("--old-history", help="Observed effective v1.6.0 History keystroke")
    make.set_defaults(run=prepare)
    clone = commands.add_parser("clone-upgrade")
    for option in ["source", "output", "zip", "sha256", "commit"]:
        clone.add_argument("--" + option, required=True)
    clone.set_defaults(run=clone_upgrade)
    compile_command = commands.add_parser("build-harness")
    for option in ["ide-home", "jdk", "output"]:
        compile_command.add_argument("--" + option, required=True)
    compile_command.set_defaults(run=build)
    collect = commands.add_parser("audit")
    for option in ["ide-home", "profile", "harness"]:
        collect.add_argument("--" + option, required=True)
    collect.add_argument("--timeout", type=int, default=120)
    collect.set_defaults(run=audit)
    gui = commands.add_parser("launch-gui")
    for option in ["app", "profile", "project"]:
        gui.add_argument("--" + option, required=True)
    gui.set_defaults(run=launch_gui)
    report = commands.add_parser("summarize")
    report.add_argument("source", type=Path)
    report.add_argument("output", type=Path)
    report.set_defaults(run=lambda args: summarize(args.source, args.output))
    snap = commands.add_parser("snapshot")
    snap.add_argument("profile", type=Path)
    snap.add_argument("output", type=Path)
    snap.set_defaults(run=lambda args: write_json(args.output, config_snapshot(args.profile)))
    args = parser.parse_args()
    if args.command == "prepare" and args.case == "removed-ide" and not args.removed_action:
        parser.error("removed-ide requires --removed-action from an observed baseline keymap dump")
    if args.command == "prepare" and args.case == "explicit-old" and not (args.old_copy and args.old_history):
        parser.error("explicit-old requires --old-copy and --old-history from an observed baseline keymap dump")
    args.run(args)


if __name__ == "__main__":
    main()
