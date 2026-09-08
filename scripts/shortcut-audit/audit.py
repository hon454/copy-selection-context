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
import signal
import subprocess
import uuid
from datetime import datetime, timezone
import xml.etree.ElementTree as ET
import zipfile

from evidence import COMMANDS, COMMAND_IDS, PREFIX, PRODUCT_ID, parse_export, require


HERE = Path(__file__).resolve().parent
CASES = ["pristine", "unrelated-only", "explicit-old", "custom", "unassigned", "removed-ide"]
HARNESS_ID = PRODUCT_ID + ".audit"
HARNESS_JAR = "lib/csc-keymap-audit.jar"


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
        "schema": 2, "profileId": str(uuid.uuid4()), "createdUtc": datetime.now(timezone.utc).isoformat(),
        "profile": str(root), "productZip": str(archive), "productSha256": digest(archive),
        "productVersion": version, "productFiles": tree_snapshot(root, "plugins"),
        "state": "prepared-not-launched", **extra,
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
                     nativeMac=args.native_mac, removedAction=args.removed_action,
                     oldCopy=args.old_copy, oldHistory=args.old_history)


def clone_upgrade(args):
    source, target = Path(args.source).resolve(), Path(args.output).resolve()
    require(source != target and source not in target.parents and target not in source.parents,
            "source and target must be disjoint canonical paths")
    validate_acceptance(source)
    archive = Path(args.zip).resolve()
    if digest(archive) != args.sha256:
        raise ValueError("Candidate ZIP digest mismatch")
    initialize(target)
    shutil.copytree(source / "config", target / "config", dirs_exist_ok=True)
    version = unpack_product(archive, target / "plugins")
    profile_metadata(target, archive, version, baseline=str(source),
                     baselineConfig=config_snapshot(source), candidateSha=args.commit)


def config_snapshot(root):
    return tree_snapshot(root, "config")


def tree_snapshot(root, directory):
    result = {}
    base = root / directory
    require(base.is_dir() and not base.is_symlink(), "invalid profile directory " + directory)
    for path in sorted(base.rglob("*")):
        require(not path.is_symlink(), "profile contains a symlink: " + str(path))
        if path.is_file():
            result[str(path.relative_to(root))] = digest(path)
    return result


def load_profile(root):
    metadata = json.loads((root / "profile.json").read_text())
    require(metadata.get("schema") == 2, "profile must be prepared by the current tool")
    require(metadata.get("profile") == str(root), "profile identity/path mismatch")
    uuid.UUID(metadata["profileId"])
    require(metadata.get("state") == "prepared-not-launched", "invalid immutable preparation record")
    files = metadata.get("productFiles")
    require(isinstance(files, dict) and bool(files), "missing installed product manifest")
    for name, expected in files.items():
        path = contained_file(root, name)
        require(name.startswith("plugins/") and digest(path) == expected, "installed product changed")
    return metadata


def contained_file(root, name):
    path = root / name
    resolved = path.resolve()
    require(not Path(name).is_absolute() and root in resolved.parents and path.is_file(), "evidence path escapes profile or is missing")
    for node in [path, *path.parents]:
        if node == root:
            break
        require(not node.is_symlink(), "symlink evidence is not accepted")
    return resolved


def exporter_sources():
    return {path.name: digest(path) for path in sorted([*HERE.glob("*.java"), HERE / "plugin.xml"])}


def validate_harness(directory):
    """Accept only the exact diagnostic artifact built from these exporter sources."""
    manifest_path = contained_file(directory, "manifest.json")
    manifest = json.loads(manifest_path.read_text())
    require(manifest.get("schema") == 1 and manifest.get("pluginId") == HARNESS_ID,
            "missing or wrong harness manifest identity")
    require(manifest.get("sources") == exporter_sources(), "stale harness exporter sources; rebuild the harness")
    jar = contained_file(directory, HARNESS_JAR)
    require(manifest.get("jarSha256") == digest(jar), "harness JAR changed")
    files = tree_snapshot(directory, ".")
    require(set(files) == {"manifest.json", HARNESS_JAR}, "unexpected harness files")
    with zipfile.ZipFile(jar) as archive:
        descriptor = archive.read("META-INF/plugin.xml")
        require(ET.fromstring(descriptor).findtext("id") == HARNESS_ID, "wrong harness plugin ID")
        require(hashlib.sha256(descriptor).hexdigest() == manifest["sources"]["plugin.xml"],
                "harness descriptor differs from reviewed source")
    return {"pluginId": HARNESS_ID, "directory": str(directory),
            "jarSha256": manifest["jarSha256"], "manifestSha256": digest(manifest_path),
            "sources": manifest["sources"], "sourceRevision": manifest.get("sourceRevision"),
            "sourceTreeDirty": manifest.get("sourceTreeDirty")}


def harness_properties(identity):
    return ["-Dcsc.audit.harnessJarSha256=" + identity["jarSha256"],
            "-Dcsc.audit.harnessManifestSha256=" + identity["manifestSha256"]]


def ensure_closed(root):
    lock = root / "config/.lock"
    require(not lock.exists() and not lock.is_symlink(), "stop the profile IDE first")
    for run in root.glob("gui-run-*"):
        require(run.is_dir() and not run.is_symlink(), "invalid GUI run directory")
        require(not (run / "process-start.json").exists() or (run / "process-result.json").exists(), "unfinished GUI launch record")


def run_process(command, root, directory, context, environment=None, timeout=None, log_name="gui-console.log"):
    """Own the launched process group and always record exit/cleanup evidence."""
    started = datetime.now(timezone.utc).isoformat()
    process, interrupted = None, False
    with open(directory / log_name, "x") as log:
        try:
            process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT,
                                       env=environment, start_new_session=True)
            write_json(directory / "process-start.json", {**context, "pid": process.pid, "startedUtc": started})
            print(f"Started test IDE pid={process.pid}, profile={root}, evidence={directory}", flush=True)
            process.wait(timeout=timeout)
            if process.returncode:
                raise RuntimeError(f"IDE exited {process.returncode}; see {directory / log_name}")
        except BaseException:
            interrupted = True
            if process is not None:
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    pass
                # A launcher can exit before one of its children. Clean the
                # owned group even when the direct process has already ended.
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=5)
            raise
        finally:
            if process is not None:
                write_json(directory / "process-result.json", {**context, "pid": process.pid,
                    "startedUtc": started, "finishedUtc": datetime.now(timezone.utc).isoformat(),
                    "exitCode": process.poll(), "interrupted": interrupted,
                    "configSnapshot": config_snapshot(root)})


def run_context(root, metadata, run_id, mode, build, project=None):
    return {"schema": 1, "runId": run_id, "profileId": metadata["profileId"], "profile": str(root),
            "profileSha256": digest(root / "profile.json"), "productSha256": metadata["productSha256"],
            "mode": mode, "ideBuild": build, "project": project,
            "harness": validate_harness(root / "plugins/csc-keymap-audit")}


def acceptance_proof(root, run_name, export_name, gui_names, observed_keymap):
    metadata = load_profile(root)
    require(metadata.get("productVersion") == "1.6.0" and metadata.get("case") in CASES, "not a released-version baseline case")
    ensure_closed(root)
    directory = root / run_name
    require(directory.resolve().parent == root and directory.name.startswith("gui-run-") and not directory.is_symlink(), "invalid GUI run directory")
    start_path = contained_file(root, run_name + "/process-start.json")
    end_path = contained_file(root, run_name + "/process-result.json")
    command_path = contained_file(root, run_name + "/gui-command.json")
    start, end, command = [json.loads(path.read_text()) for path in [start_path, end_path, command_path]]
    require(start.get("schema") == 1 and end.get("schema") == 1 and start.get("mode") == end.get("mode") == "gui", "not a GUI launch")
    require(type(start.get("pid")) is int and start["pid"] > 0 and start["pid"] == end.get("pid"), "PID evidence mismatch")
    require(type(end.get("exitCode")) is int and end["exitCode"] == 0 and end.get("interrupted") is False, "GUI did not exit cleanly")
    for key in ["runId", "profileId", "profile", "profileSha256", "productSha256", "ideBuild", "project", "harness"]:
        require(start.get(key) is not None and start.get(key) == end.get(key) == command.get(key), "launch identity mismatch: " + key)
    require(start["profileId"] == metadata["profileId"] and start["profile"] == str(root)
            and start["profileSha256"] == digest(root / "profile.json")
            and start["productSha256"] == metadata["productSha256"], "launch/profile mismatch")
    uuid.UUID(start["runId"])
    require(directory.name == "gui-run-" + start["runId"], "GUI run directory identity mismatch")
    harness = validate_harness(root / "plugins/csc-keymap-audit")
    require(start["harness"] == harness, "installed harness differs from GUI launch")
    export_path = contained_file(root, export_name)
    require(export_path.parent == directory.resolve(), "export belongs to another run")
    data = parse_export(export_path)
    meta = data["metadata"]
    require(meta["headless"] == "false" and meta["profileId"] == start["profileId"]
            and meta["runId"] == start["runId"] and int(meta["processId"]) == start["pid"], "export is not from this GUI process")
    require(meta["harnessJarSha256"] == harness["jarSha256"]
            and meta["harnessManifestSha256"] == harness["manifestSha256"], "export harness identity mismatch")
    require(HARNESS_ID in data["plugins"]
            and Path(data["plugins"][HARNESS_ID][4]).resolve() == Path(harness["directory"]),
            "GUI loaded the harness from another directory")
    require(meta["build"].split("-", 1)[-1] == start["ideBuild"], "IDE build mismatch")
    require(data["plugins"][PRODUCT_ID][2] == "1.6.0", "GUI did not load v1.6.0")
    for name in ["config", "system", "plugins", "log"]:
        require(Path(meta["idea." + name + ".path"]).resolve() == root / name, "GUI used other profile paths")
    require(start["project"] in json.loads(meta["projectRoots"]), "expected project was not open")
    stamp = lambda value: datetime.fromisoformat(value.replace("Z", "+00:00"))
    require(stamp(start["startedUtc"]) <= stamp(meta["timeUtc"]) <= stamp(end["finishedUtc"]), "export outside GUI process lifetime")
    expected_map = metadata["parent"] if metadata["case"] == "pristine" else "CSC Audit " + metadata["case"]
    require(observed_keymap == meta["activeKeymap"] == expected_map, "active keymap did not accept the seed")
    validate_seed_bindings(metadata, data)
    require(config_snapshot(root) == end.get("configSnapshot"), "baseline config changed after clean exit")
    require(bool(gui_names), "missing GUI observation evidence")
    gui = [contained_file(root, name) for name in gui_names]
    require(all(path.parent == directory.resolve() and path.stat().st_size > 0 for path in gui), "GUI evidence belongs to another run or is empty")
    def is_screenshot(path):
        with open(path, "rb") as stream:
            signature = stream.read(8)
        return (path.suffix.lower() == ".png" and signature == b"\x89PNG\r\n\x1a\n"
                or path.suffix.lower() in {".jpg", ".jpeg"} and signature.startswith(b"\xff\xd8\xff"))
    require(any(is_screenshot(path) for path in gui)
            and any(path.suffix == ".txt" for path in gui), "both screenshot and accessibility text are required")
    files = [start_path, end_path, command_path, export_path, *gui]
    return {"profileId": metadata["profileId"], "runId": start["runId"], "keymap": observed_keymap,
            "harness": harness,
            "files": {str(path.relative_to(root)): digest(path) for path in files},
            "configSnapshot": config_snapshot(root), "finishedUtc": end["finishedUtc"]}


def accept_baseline(args):
    root = Path(args.profile).resolve()
    require(args.confirm_observed and args.performer.strip() and args.notes.strip(), "explicit operator observation is required")
    run_name = str(Path(args.run_directory).resolve().relative_to(root))
    export_name = str(Path(args.export).resolve().relative_to(root))
    gui_names = [str(Path(path).resolve().relative_to(root)) for path in args.gui_evidence]
    proof = acceptance_proof(root, run_name, export_name, gui_names, args.observed_keymap)
    write_json(root / "acceptance.json", {"schema": 1, "state": "gui-accepted",
        "performer": args.performer, "notes": args.notes, "acceptedUtc": datetime.now(timezone.utc).isoformat(),
        "runDirectory": run_name, "export": export_name, "guiEvidence": gui_names,
        "observedKeymap": args.observed_keymap, "proof": proof})


def validate_acceptance(root):
    record = json.loads(contained_file(root, "acceptance.json").read_text())
    require(record.get("schema") == 1 and record.get("state") == "gui-accepted"
            and isinstance(record.get("performer"), str) and bool(record["performer"].strip())
            and isinstance(record.get("notes"), str) and bool(record["notes"].strip()), "missing explicit baseline acceptance")
    proof = acceptance_proof(root, record["runDirectory"], record["export"], record["guiEvidence"], record["observedKeymap"])
    require(record.get("proof") == proof, "acceptance evidence changed or marker is incomplete")


def validate_seed_bindings(metadata, data):
    keymap = data["metadata"]["activeKeymap"]
    values = lambda action, scheme=keymap: json.loads(data["bindings"][(scheme, action)][3])
    for action in COMMAND_IDS - {PREFIX + "Copy", PREFIX + "ShowHistory"}:
        require(values(action) == [], "v1.6.0 seed unexpectedly binds " + action)
    tokens = lambda stroke: sorted(stroke.replace("control", "ctrl").split()
                                   + ([] if any(word in stroke.split() for word in ["pressed", "released", "typed"]) else ["pressed"]))
    def single_keyboard(items, expected):
        return len(items) == 1 and items[0]["kind"] == "keyboard" and items[0]["second"] is None and tokens(items[0]["first"]) == tokens(expected)
    case = metadata["case"]
    for command, key in [("Copy", "C"), ("ShowHistory", "H")]:
        items = values(PREFIX + command)
        if case == "unassigned":
            require(items == [], "explicit unassigned binding was not accepted")
        elif case == "explicit-old":
            expected = metadata["oldCopy" if command == "Copy" else "oldHistory"]
            require(single_keyboard(items, expected), "explicit legacy binding was not accepted")
        elif case == "custom":
            keyboard = [item for item in items if item["kind"] == "keyboard"]
            modifier = "meta" if metadata["nativeMac"] else "control"
            require(single_keyboard(keyboard, modifier + " shift F" + ("11" if key == "C" else "12")), "custom keyboard binding was not accepted")
            other = [item for item in items if item["kind"] != "keyboard"]
            expected_mouse = (not other if command == "ShowHistory" else len(other) == 1
                              and other[0]["kind"] == "mouse" and other[0]["button"] == 2
                              and other[0]["clickCount"] == 1 and other[0]["modifiers"] in {2, 128, 130})
            require(expected_mouse, "custom mouse binding was not accepted")
        else:
            require(items == values(PREFIX + command, metadata["parent"]), "inherited binding changed in seed")
    if case == "unrelated-only":
        require(single_keyboard(values("EditorToggleUseSoftWraps"), "control alt shift F9"), "unrelated edit was not accepted")
    if case == "removed-ide":
        action = metadata["removedAction"]
        require(values(action) == [] and bool(values(action, metadata["parent"])), "IDE removal was not accepted or parent was already unassigned")


def build(args):
    home, output = Path(args.ide_home).resolve(), Path(args.output).resolve()
    sources = exporter_sources()
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
    revision = subprocess.run(["git", "-C", str(HERE), "rev-parse", "HEAD"],
                              capture_output=True, text=True)
    status = subprocess.run(["git", "-C", str(HERE), "status", "--porcelain", "--", "."],
                            capture_output=True, text=True)
    require(sources == exporter_sources(), "exporter sources changed while compiling")
    directory = jar.parent.parent
    write_json(directory / "manifest.json", {"schema": 1, "pluginId": HARNESS_ID,
        "createdUtc": datetime.now(timezone.utc).isoformat(), "jarSha256": digest(jar), "sources": sources,
        "sourceRevision": revision.stdout.strip() if revision.returncode == 0 else None,
        "sourceTreeDirty": bool(status.stdout.strip()) if status.returncode == 0 else None})
    validate_harness(directory)
    print(jar)


def audit(args):
    home, root = Path(args.ide_home).resolve(), Path(args.profile).resolve()
    metadata = load_profile(root)
    ensure_closed(root)
    require(not (root / "acceptance.json").exists(), "accepted baselines are immutable; use a clone")
    run_id = str(uuid.uuid4())
    probe = Path(args.harness).resolve()
    validate_harness(probe)
    shutil.copytree(probe, root / "plugins/csc-keymap-audit", dirs_exist_ok=False)
    harness = validate_harness(root / "plugins/csc-keymap-audit")
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
               f"-Dcsc.audit.profileId={metadata['profileId']}", f"-Dcsc.audit.runId={run_id}",
               f"-Didea.home.path={home}", f"-Didea.properties.file={root / 'idea.properties'}",
               f"-XX:ErrorFile={root / 'log/hs_err_%p.log'}", f"-XX:HeapDumpPath={root / 'log/heap.hprof'}"]
    command += [f"-Didea.{name}.path={root / name}" for name in ["config", "system", "plugins", "log"]]
    command += harness_properties(harness)
    if metadata.get("removedAction"):
        command.append("-Dcsc.audit.removedAction=" + metadata["removedAction"])
    command += ["-cp", classpath, "com.intellij.idea.Main", "csc-keymap-audit", str(root / "keymaps.tsv")]
    context = run_context(root, metadata, run_id, "headless", info["buildNumber"])
    write_json(root / "audit-command.json", {**context, "argv": command, "productInfo": info})
    run_process(command, root, root, context, timeout=args.timeout, log_name="audit-console.log")
    require(context["harness"] == validate_harness(root / "plugins/csc-keymap-audit"),
            "harness changed during audit")
    exported = parse_export(root / "keymaps.tsv")["metadata"]
    require(exported["harnessJarSha256"] == context["harness"]["jarSha256"]
            and exported["harnessManifestSha256"] == context["harness"]["manifestSha256"],
            "audit export harness identity mismatch")
    summarize(root / "keymaps.tsv", root / "summary.json")


def launch_gui(args):
    app, root = Path(args.app).resolve(), Path(args.profile).resolve()
    metadata = load_profile(root)
    ensure_closed(root)
    require(not (root / "acceptance.json").exists(), "accepted baselines are immutable; use a clone")
    harness = validate_harness(root / "plugins/csc-keymap-audit")
    run_id = str(uuid.uuid4())
    info_path = app / "Contents/Resources/product-info.json"
    info = json.loads(info_path.read_text())
    launch = next(item for item in info["launch"] if item["os"] == "macOS" and item["arch"] == "aarch64")
    launcher = (info_path.parent / launch["launcherPath"]).resolve()
    original_options = (info_path.parent / launch["vmOptionsFilePath"]).read_text()
    run_directory = root / ("gui-run-" + run_id)
    run_directory.mkdir()
    vmoptions = run_directory / "gui.vmoptions"
    with open(vmoptions, "x") as stream:
        stream.write(original_options + f"\n-XX:ErrorFile={root / 'log/hs_err_%p.log'}\n"
                     + f"-XX:HeapDumpPath={root / 'log/heap.hprof'}\n"
                     + f"-Dcsc.audit.output={run_directory}\n"
                     + f"-Dcsc.audit.profileId={metadata['profileId']}\n-Dcsc.audit.runId={run_id}\n"
                     + "\n".join(harness_properties(harness)) + "\n")
        if metadata.get("removedAction"):
            stream.write("-Dcsc.audit.removedAction=" + metadata["removedAction"] + "\n")
    environment = os.environ.copy()
    variable = info["envVarBaseName"]
    environment[variable + "_PROPERTIES"] = str(root / "idea.properties")
    environment[variable + "_VM_OPTIONS"] = str(vmoptions)
    command = [str(launcher), str(Path(args.project).resolve())]
    context = run_context(root, metadata, run_id, "gui", info["buildNumber"], str(Path(args.project).resolve()))
    write_json(run_directory / "gui-command.json", {**context, "argv": command, "productInfo": info,
                                           "properties": str(root / "idea.properties"), "vmOptions": str(vmoptions)})
    run_process(command, root, run_directory, context, environment)
    require(context["harness"] == validate_harness(root / "plugins/csc-keymap-audit"),
            "harness changed during GUI run")


def summarize(source, target, allow_legacy=False):
    data = parse_export(source, allow_legacy)
    commands, keymaps, occupancy = data["commands"], data["keymaps"], data["occupancies"]
    external = [row for row in occupancy if row[2].endswith("shift G") and row[3] not in COMMAND_IDS]
    write_json(target, {"sourceSha256": digest(source), "keymapCount": len(keymaps),
                       "commandCount": len(commands), "keymaps": list(keymaps.values()), "bindingCount": len(data["bindings"]),
                       "legacyInspectionOnly": data["legacyInspectionOnly"],
                       "externalPrefixOccupancy": external,
                       "oldCHOccupancy": [row for row in occupancy if not row[2].endswith("shift G")],
                       "note": "Data inspection only, not a pass verdict. Legacy exports require regeneration and cannot be accepted as baselines; no physical key, GUI, OS or IME verdict."})
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
    accept = commands.add_parser("accept-baseline")
    for option in ["profile", "run-directory", "export", "observed-keymap", "performer", "notes"]:
        accept.add_argument("--" + option, required=True)
    accept.add_argument("--gui-evidence", action="append", required=True)
    accept.add_argument("--confirm-observed", action="store_true")
    accept.set_defaults(run=accept_baseline)
    report = commands.add_parser("summarize")
    report.add_argument("source", type=Path)
    report.add_argument("output", type=Path)
    report.add_argument("--allow-legacy", action="store_true", help="Inspect old data without accepting it as complete evidence")
    report.set_defaults(run=lambda args: summarize(args.source, args.output, args.allow_legacy))
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
