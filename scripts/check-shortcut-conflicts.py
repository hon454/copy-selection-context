#!/usr/bin/env python3
"""Report actions that already use the plugin's default shortcut in IDE distributions.

usage: python3 -I scripts/check-shortcut-conflicts.py <ide-dir> [<ide-dir> ...]

The shortcut is the `$default` keystroke of CopySelectionContext.Copy in plugin.xml; macOS keymaps
are checked with Ctrl replaced by Cmd. Each <ide-dir> is an unpacked IDE, for example the IntelliJ
IDEA, Rider or IntelliJ IDEA Community distributions Gradle downloads under
~/.gradle/caches/*/transforms/*/transformed/. Bundled keymaps and the <keyboard-shortcut>
declarations of bundled plugins are read from the IDE's jars, with keymap inheritance resolved.

A stroke counts as taken when any action uses it alone or as the first stroke of a chord.
Exits with status 1 when any keymap of any IDE has a conflict.
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

COPY_ACTION_ID = "CopySelectionContext.Copy"
PLUGIN_XML = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "META-INF", "plugin.xml")
MODIFIERS = {"control": "ctrl", "ctrl": "ctrl", "alt": "alt", "shift": "shift", "meta": "meta"}
MAC_ROOTS = {"Mac OS X", "Mac OS X 10.5+"}


def normalize(keystroke):
    parts = keystroke.replace("pressed", "").split()
    modifiers = sorted({MODIFIERS[p] for p in parts if p in MODIFIERS})
    keys = [p.upper() for p in parts if p not in MODIFIERS]
    return "+".join(modifiers + keys)


def swap_ctrl_meta(stroke):
    parts = stroke.split("+")
    modifiers = {"meta" if p == "ctrl" else "ctrl" if p == "meta" else p for p in parts[:-1]}
    return "+".join(sorted(modifiers) + [parts[-1]])


def plugin_default_stroke():
    root = ET.parse(PLUGIN_XML).getroot()
    for action in root.iter("action"):
        if action.get("id") == COPY_ACTION_ID:
            for shortcut in action.iter("keyboard-shortcut"):
                if shortcut.get("keymap") == "$default":
                    return normalize(shortcut.get("first-keystroke"))
    sys.exit(f"{COPY_ACTION_ID} has no $default keyboard-shortcut in plugin.xml")


def load_ide(ide_dir):
    keymaps, plugin_shortcuts = {}, []
    for jar in glob.glob(os.path.join(ide_dir, "**", "*.jar"), recursive=True):
        try:
            archive = zipfile.ZipFile(jar)
        except (zipfile.BadZipFile, OSError):
            continue
        for name in archive.namelist():
            if not name.endswith(".xml"):
                continue
            data = archive.read(name)
            if name.startswith("keymaps/") and name.count("/") == 1:
                root = ET.fromstring(data)
                keymaps[root.get("name")] = root
                continue
            text = data.decode("utf-8", "ignore")
            if "keyboard-shortcut" not in text:
                continue
            for action in re.finditer(r"<action\b([^>]*)>(.*?)</action>", text, re.S):
                action_id = re.search(r'\bid="([^"]+)"', action.group(1))
                if not action_id:
                    continue
                for shortcut in re.finditer(r"<keyboard-shortcut\b([^>]*)/?>", action.group(2)):
                    attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', shortcut.group(1)))
                    if "first-keystroke" in attrs and attrs.get("remove") != "true":
                        keymap = attrs.get("keymap", "$default")
                        plugin_shortcuts.append((keymap, normalize(attrs["first-keystroke"]), action_id.group(1)))
    return keymaps, plugin_shortcuts


def effective_keymaps(keymaps, plugin_shortcuts):
    cache = {}

    def resolve(name):
        if name in cache:
            return cache[name]
        root = keymaps[name]
        parent = root.get("parent")
        strokes = {k: set(v) for k, v in resolve(parent).items()} if parent in keymaps else {}
        if parent == "$default" and name in MAC_ROOTS:
            strokes = {swap_ctrl_meta(k): v for k, v in strokes.items()}
        for keymap, stroke, action_id in plugin_shortcuts:
            if keymap == name:
                strokes.setdefault(stroke, set()).add(action_id)
        for action in root.iter("action"):
            action_id = action.get("id")
            for actions in strokes.values():
                actions.discard(action_id)
            for shortcut in action.iter("keyboard-shortcut"):
                strokes.setdefault(normalize(shortcut.get("first-keystroke")), set()).add(action_id)
        cache[name] = strokes
        return strokes

    def is_mac(name):
        while name in keymaps:
            if name in MAC_ROOTS:
                return True
            name = keymaps[name].get("parent")
        return False

    return {name: (resolve(name), is_mac(name)) for name in keymaps}


def main():
    ide_dirs = sys.argv[1:]
    if not ide_dirs:
        sys.exit(__doc__)
    stroke = plugin_default_stroke()
    conflicts = 0
    for ide_dir in ide_dirs:
        keymaps, plugin_shortcuts = load_ide(ide_dir)
        if not keymaps:
            sys.exit(f"{ide_dir}: no bundled keymaps found")
        label = os.path.basename(os.path.normpath(ide_dir))
        print(f"{label}: {len(keymaps)} keymaps, {len(plugin_shortcuts)} plugin shortcuts")
        for name, (strokes, mac) in sorted(effective_keymaps(keymaps, plugin_shortcuts).items()):
            target = swap_ctrl_meta(stroke) if mac else stroke
            taken = strokes.get(target, set()) - {COPY_ACTION_ID}
            if taken:
                conflicts += 1
                print(f"  CONFLICT {name} {target}: {', '.join(sorted(taken))}")
    print(f"{stroke}: {conflicts} conflicting keymaps" if conflicts else f"{stroke}: free in every keymap")
    sys.exit(1 if conflicts else 0)


if __name__ == "__main__":
    main()
