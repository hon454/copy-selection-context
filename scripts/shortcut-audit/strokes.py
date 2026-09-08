"""Validate complete companion inventories and compare possible chord prefixes.

This is runtime data inspection, never a physical keyboard/OS/IME pass verdict.
The original schema-2 export and its six probes remain independently readable.
"""

import hashlib
import json
import string
from pathlib import Path

from evidence import COMMAND_IDS, PROBES, parse_export, require, shortcuts, unescape

DEFAULT_KEYS = list(string.ascii_uppercase) + ["F" + str(number) for number in range(1, 25)]


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def identities(value):
    items = json.loads(value)
    require(isinstance(items, list) and all(isinstance(item, str) and item for item in items)
            and len(set(items)) == len(items), "inventory source action IDs")
    return set(items)


def parse_inventory(audit_path):
    audit_path = Path(audit_path)
    data = parse_export(audit_path)
    require(data["schema"] == 2 and data["metadata"].get("strokeInventory") == "complete-v1",
            "export has no complete stroke inventory; collect a new run")
    source = audit_path.with_name(audit_path.name + ".strokes.tsv")
    require(source.is_file() and not source.is_symlink() and source.stat().st_size <= 100 * 1024 * 1024,
            "missing/invalid/oversized stroke inventory")
    rows = [[unescape(cell) for cell in line.split("\t")]
            for line in source.read_text(encoding="utf-8").splitlines()]
    require(bool(rows) and sum(row[0] == "END" for row in rows) == 1 and rows[-1][0] == "END",
            "inventory missing/duplicate/non-final END")
    meta, registered, maps, origins, actions = {}, {}, {}, {}, {}
    sizes = {"META": 3, "REGISTERED": 2, "MAP": 5, "SOURCE": 4, "ACTION": 7}
    for row in rows[:-1]:
        kind = row[0]
        require(kind in sizes and len(row) == sizes[kind], "inventory row/column count")
        require(bool(row[1]), "empty inventory identity")
        target = {"META": meta, "REGISTERED": registered, "MAP": maps,
                  "SOURCE": origins, "ACTION": actions}[kind]
        key = tuple(row[1:3]) if kind in {"SOURCE", "ACTION"} else row[1]
        require(key not in target, "duplicate inventory " + kind)
        target[key] = row
    require(set(meta) == {"schema", "auditFile", "auditSha256"} and meta["schema"][2] == "1",
            "inventory metadata/schema")
    require(meta["auditFile"][2] == audit_path.name and meta["auditSha256"][2] == sha256(audit_path),
            "inventory belongs to a different audit export")
    require(len(registered) == int(data["metadata"]["registeredActionCount"])
            and COMMAND_IDS <= set(registered), "inventory registered action completeness")
    require(set(maps) == set(data["keymaps"]), "inventory keymap completeness")
    chains = {name: json.loads(row[3]) for name, row in data["keymaps"].items()}
    require(set(origins) == {(name, ancestor) for name, chain in chains.items() for ancestor in chain},
            "inventory ancestor source completeness")
    expected = set()
    watched = set(json.loads(data["metadata"]["watchedActions"]))
    for name, chain in chains.items():
        ids = set(registered) | COMMAND_IDS | watched
        for ancestor in chain:
            ids |= identities(origins[(name, ancestor)][3])
        expected |= {(name, action) for action in ids}
    require(set(actions) == expected, "inventory missing/extra effective action rows")
    counts = {name: [0, 0, 0] for name in maps}
    values = {}
    for (name, action), row in actions.items():
        require(row[4] in {"true", "false"} and row[6] in {"true", "false", "unknown"}
                and bool(row[5]), "inventory ownership fields")
        require(action not in registered or row[4] == "true", "registered inventory action missing at capture")
        # Some platform actions return identical entries (for example IC 243's
        # CommentByBlockComment on macOS). Keep the full API list and its counts.
        items = shortcuts(row[3], allow_duplicates=True)
        values[(name, action)] = items
        counts[name][0] += 1
        counts[name][1] += len(items)
        counts[name][2] += sum(item["kind"] == "keyboard" for item in items)
    for name, row in maps.items():
        require([int(value) for value in row[2:]] == counts[name], "inventory per-keymap counts")
    require(rows[-1][1::2] == ["keymaps", "registered", "sources", "actions", "shortcuts", "rows"]
            and len(rows[-1]) == 13, "inventory END fields")
    require([int(value) for value in rows[-1][2::2]] == [len(maps), len(registered), len(origins),
            len(actions), sum(value[1] for value in counts.values()), len(rows)], "inventory END counts")
    # Cross-check the complete inventory with the independent original six-probe
    # representation, including dormant owners and every recorded second stroke.
    for key, row in data["bindings"].items():
        require(values[key] == shortcuts(row[3]), "inventory/schema-2 binding mismatch")
    probes = {probe: normalize_stroke(probe.rsplit(" ", 1)[0] + " pressed " + probe.rsplit(" ", 1)[1])
              for probe in PROBES}
    expected_occupancy = set()
    for (name, action), items in values.items():
        for item in items:
            if item["kind"] != "keyboard":
                continue
            for probe, stroke in probes.items():
                if normalize_stroke(item["first"]) == stroke:
                    expected_occupancy.add((name, probe, action, item["first"], item["second"] or "null"))
    observed = {tuple(row[1:6]) for row in data["occupancies"]}
    require(expected_occupancy == observed, "inventory/schema-2 occupancy completeness mismatch")
    for row in data["occupancies"]:
        key = (row[1], row[3])
        require(shortcuts(row[6]) == values[key] and row[7:10] == actions[key][4:7],
                "inventory/schema-2 occupancy owner/list mismatch")
    return {"auditPath": str(audit_path.resolve()), "inventoryPath": str(source.resolve()),
            "auditSha256": sha256(audit_path), "inventorySha256": sha256(source),
            "audit": data, "actions": actions, "values": values}


def normalize_stroke(stroke):
    # Swing can print modifier tokens in a different order; pressed/released and
    # typed remain distinct. Candidate keys are deliberately ASCII letters/F1-24.
    return tuple(sorted("ctrl" if token == "control" else token for token in stroke.split()))


def compare_prefixes(exports, keys=None):
    keys = DEFAULT_KEYS if keys is None else keys
    require(bool(keys) and len(set(keys)) == len(keys) and set(keys) <= set(DEFAULT_KEYS),
            "candidate keys must be unique A-Z or F1-F24")
    inventories = [parse_inventory(path) for path in exports]
    require(bool(inventories) and len({item["auditSha256"] for item in inventories}) == len(inventories),
            "missing or duplicate audit inputs")
    reports = []
    for key in keys:
        probes = {modifier + " alt shift " + key:
                  normalize_stroke(modifier + " alt shift pressed " + key) for modifier in ["control", "meta"]}
        external, product = [], []
        for index, inventory in enumerate(inventories):
            for (name, action), items in inventory["values"].items():
                row = inventory["actions"][(name, action)]
                seen = set()
                for item in items:
                    if item["kind"] != "keyboard":
                        continue
                    identity = (item["first"], item["second"])
                    if identity in seen:
                        continue
                    seen.add(identity)
                    for probe, stroke in probes.items():
                        if normalize_stroke(item["first"]) != stroke:
                            continue
                        match = {"input": index, "keymap": name, "probe": probe, "action": action,
                                 "first": item["first"], "second": item["second"],
                                 "registered": row[4] == "true", "owner": row[5], "bundled": row[6],
                                 "allShortcuts": items, "occurrencesInApiList": items.count(item)}
                        (product if action in COMMAND_IDS else external).append(match)
        reports.append({"key": key, "probes": list(probes), "externalOccupancyCount": len(external),
                        "occupiedKeymaps": len({(row["input"], row["keymap"]) for row in external}),
                        "externalActionIds": sorted({row["action"] for row in external}),
                        "externalOccupancy": external, "productOccupancy": product,
                        "verdict": "OCCUPIED" if external else "NO_OCCUPANCY_IN_RECORDED_KEYMAPS"})
    return {"schema": 1, "inputs": [{key: item[key] for key in
            ["auditPath", "inventoryPath", "auditSha256", "inventorySha256"]} | {
                "metadata": item["audit"]["metadata"], "keymaps": list(item["audit"]["keymaps"].values()),
                "plugins": list(item["audit"]["plugins"].values())} for item in inventories],
            "candidates": reports,
            "note": "Only exact nine product IDs are excluded. Single strokes, other chords, dormant mappings and both Ctrl/Meta variants count. Zero is limited to these recorded IDEs/keymaps/plugins; no OS, IME, physical-key, candidate-product or unobserved-family verdict."}
