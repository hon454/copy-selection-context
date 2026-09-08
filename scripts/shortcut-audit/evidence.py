"""Strict parser for complete keymap export evidence, independent of IDE APIs."""

import json
from datetime import datetime

PRODUCT_ID = "com.github.hon454.copy-selection-context"
PREFIX = "CopySelectionContext."
COMMANDS = ["Copy", "ShowHistory", "AddToCollection", "ShowCollection", "CopyAllCollection",
            "CopyRelativePath", "CopyAbsolutePath", "CopyWithCodeContent", "CopyGitPermalink"]
COMMAND_IDS = {PREFIX + name for name in COMMANDS}
WATCHED_IDS = {"IntroduceConstant", "EditorToggleUseSoftWraps"}
PROBES = {"control alt shift G", "meta alt shift G", "control alt C", "meta alt C", "control alt H", "meta alt H"}


def require(condition, message):
    if not condition:
        raise ValueError("Invalid audit evidence: " + message)


def unescape(cell):
    output, index = [], 0
    escapes = {"\\": "\\", "t": "\t", "r": "\r", "n": "\n"}
    while index < len(cell):
        if cell[index] == "\\":
            index += 1
            require(index < len(cell) and cell[index] in escapes, "invalid TSV escape")
            output.append(escapes[cell[index]])
        else:
            output.append(cell[index])
        index += 1
    return "".join(output)


def shortcuts(value):
    items = json.loads(value)
    require(isinstance(items, list), "shortcuts must be a JSON array")
    for item in items:
        require(isinstance(item, dict), "shortcut must be an object")
        if item.get("kind") == "keyboard":
            require(set(item) == {"kind", "first", "second"}, "keyboard shortcut fields")
            require(isinstance(item["first"], str) and bool(item["first"]), "empty first stroke")
            require(item["second"] is None or isinstance(item["second"], str) and bool(item["second"]), "second stroke")
        elif item.get("kind") == "mouse":
            require(set(item) == {"kind", "button", "modifiers", "clickCount"}
                    and all(type(item[key]) is int for key in ["button", "modifiers", "clickCount"]), "mouse shortcut fields")
        else:
            require(set(item) == {"kind", "display"} and item["kind"] == "other"
                    and isinstance(item["display"], str) and bool(item["display"]), "non-keyboard shortcut fields")
    require(len({json.dumps(item, sort_keys=True) for item in items}) == len(items), "duplicate shortcut")
    return items


def parse_export(source, allow_legacy=False):
    require(source.stat().st_size <= 100 * 1024 * 1024, "export exceeds 100 MiB")
    rows = [[unescape(cell) for cell in line.split("\t")]
            for line in source.read_text(encoding="utf-8").splitlines()]
    require(bool(rows) and all(row[0] for row in rows), "empty export/row")
    require(sum(row[0] == "END" for row in rows) == 1 and rows[-1][0] == "END", "missing/duplicate/non-final END")
    meta, commands, maps, bindings, plugins, probes = {}, {}, {}, {}, {}, {}
    occupancy = []
    sizes = {"COMMAND": 3, "KEYMAP": 4, "BINDING": 4, "PLUGIN": 5, "PROBE": 3, "OCCUPANCY": 10}
    for row in rows[:-1]:
        kind = row[0]
        if kind == "META":
            require(len(row) >= 3, "META column count")
            require(len(row) == (5 if row[1] == "os" else 3), "META column count")
            require(row[1] not in meta, "duplicate META " + row[1])
            meta[row[1]] = row[2:] if row[1] == "os" else row[2]
            continue
        require(kind in sizes and len(row) == sizes[kind], "unknown row or column count: " + kind)
        if kind == "OCCUPANCY":
            occupancy.append(row)
            continue
        target = {"COMMAND": commands, "KEYMAP": maps, "BINDING": bindings, "PLUGIN": plugins, "PROBE": probes}[kind]
        key = tuple(row[1:3]) if kind == "BINDING" else row[1]
        require(key not in target, "duplicate " + kind + " " + str(key))
        require(bool(row[1]), "empty " + kind + " identity")
        target[key] = row
    required_meta = {"schema", "timeUtc", "ide", "build", "os", "activeKeymap", "headless", "registeredActionCount",
                     "idea.config.path", "idea.system.path", "idea.plugins.path", "idea.log.path"}
    require(required_meta <= set(meta), "missing metadata")
    require(meta["schema"] in {"1", "2"}, "unsupported schema")
    legacy = meta["schema"] == "1"
    require(not legacy or allow_legacy, "legacy export requires explicit inspection; regenerate for acceptance")
    require(meta["headless"] in {"true", "false"}, "invalid headless value")
    require(int(meta["registeredActionCount"]) >= len(COMMAND_IDS), "registered action count")
    require(datetime.fromisoformat(meta["timeUtc"].replace("Z", "+00:00")).tzinfo is not None, "timestamp needs timezone")
    require(set(commands) == COMMAND_IDS and all(row[2] for row in commands.values()), "missing/extra product commands")
    require(bool(maps) and meta["activeKeymap"] in maps, "zero keymaps or missing active scheme")
    require(PRODUCT_ID in plugins and bool(plugins[PRODUCT_ID][2]), "product plugin not loaded")
    require(all(row[3] in {"true", "false"} for row in plugins.values()), "plugin bundled value")
    for name, row in maps.items():
        require(row[2] in {"true", "false"}, "keymap mutable value")
        if not legacy:
            chain = json.loads(row[3])
            require(isinstance(chain, list) and chain and all(isinstance(item, str) and item for item in chain)
                    and chain[0] == name and len(set(chain)) == len(chain), "invalid/cyclic parent chain")
    required_ids = COMMAND_IDS | WATCHED_IDS
    if legacy:
        # Old files may be structurally inspected, but cannot certify parent
        # initialization or be used as GUI acceptance/candidate evidence.
        observed_ids = {key[1] for key in bindings}
        require(observed_ids in (COMMAND_IDS, required_ids), "legacy binding ID set")
        required_ids = observed_ids
    else:
        require("watchedActions" in meta, "missing watched action IDs")
        watched = json.loads(meta["watchedActions"])
        require(isinstance(watched, list) and all(isinstance(item, str) and item for item in watched)
                and len(set(watched)) == len(watched) and WATCHED_IDS <= set(watched), "watched action IDs")
        required_ids = COMMAND_IDS | set(watched)
    require(set(bindings) == {(name, action) for name in maps for action in required_ids},
            "missing/extra per-keymap bindings")
    if legacy:
        require(len(rows[-1]) == 5 and rows[-1][1::2] == ["keymaps", "commands"], "legacy END fields")
        expected_counts = [len(maps), len(commands)]
    else:
        require({"profileId", "runId", "processId", "projectRoots"} <= set(meta), "missing run identity")
        require(all(isinstance(meta.get(key), str) and len(meta[key]) == 64
                    and all(char in "0123456789abcdef" for char in meta[key])
                    for key in ["harnessJarSha256", "harnessManifestSha256"]), "missing/invalid harness identity")
        require(all(meta[key] not in {"", "null"} for key in ["profileId", "runId", "processId"]), "empty run identity")
        require(int(meta["processId"]) > 0, "process ID")
        roots = json.loads(meta["projectRoots"])
        require(isinstance(roots, list) and all(item is None or isinstance(item, str) for item in roots), "project roots")
        require(set(probes) == PROBES, "missing/extra probes")
        for name, row in probes.items():
            require(sorted(row[2].split()) == sorted(name.replace("control", "ctrl").split() + ["pressed"]), "probe stroke mismatch")
        require(len(rows[-1]) == 11 and rows[-1][1::2] == ["keymaps", "commands", "bindings", "occupancies", "rows"], "END fields")
        expected_counts = [len(maps), len(commands), len(bindings), len(occupancy), len(rows)]
    require([int(value) for value in rows[-1][2::2]] == expected_counts, "END counts do not match actual rows")
    seen, groups = set(), {}
    for row in occupancy:
        _, keymap, probe, action, first, second, all_shortcuts, registered, owner, bundled = row
        require(keymap in maps and probe in PROBES and bool(action), "occupancy identity")
        require(registered in {"true", "false"} and bundled in {"true", "false", "unknown"} and bool(owner), "occupancy ownership")
        key = (keymap, probe, action, first, second)
        require(key not in seen, "duplicate occupancy")
        seen.add(key)
        if legacy:
            continue
        require(first == probes[probe][2], "occupancy first stroke mismatch")
        items = shortcuts(all_shortcuts)
        require({"kind": "keyboard", "first": first, "second": None if second == "null" else second} in items,
                "occupancy does not match its shortcut list")
        group = (keymap, action)
        require(group not in groups or groups[group] == items, "inconsistent occupancy shortcut list")
        groups[group] = items
    if not legacy:
        for key, row in bindings.items():
            items = shortcuts(row[3])
            require(key not in groups or groups[key] == items, "binding/occupancy mismatch")
            groups[key] = items
        for (keymap, action), items in groups.items():
            for item in items:
                if item["kind"] != "keyboard":
                    continue
                for probe, row in probes.items():
                    if item["first"] == row[2]:
                        require((keymap, probe, action, item["first"], item["second"] or "null") in seen,
                                "missing occupancy for a recorded shortcut")
    return {"schema": int(meta["schema"]), "metadata": meta, "commands": commands, "keymaps": maps,
            "bindings": bindings, "plugins": plugins, "occupancies": occupancy, "rowCount": len(rows),
            "legacyInspectionOnly": legacy}
