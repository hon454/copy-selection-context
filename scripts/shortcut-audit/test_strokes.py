"""Synthetic complete-inventory integrity and prefix-comparison tests; no real IDE verdict."""

import json
import unittest
from pathlib import Path

from evidence import COMMAND_IDS, PREFIX, parse_export
from strokes import DEFAULT_KEYS, PUNCTUATION_KEYS, compare_prefixes, parse_inventory, sha256
from lineage import load_policy, select_modifier
import test_audit
from test_audit import encode_rows, keyboard

POLICY_SOURCE = Path(__file__).with_name("fixtures") / "CopySelectionShortcuts.kt"


class ProductModifierPolicyTest(unittest.TestCase):
    def test_exact_ancestor_rule_and_default_stop_precede_host_fallback(self):
        policy = load_policy(POLICY_SOURCE)
        examples = [
            (["Eclipse (Mac OS X)", "Mac OS X 10.5+", "$default"], "Linux", "meta", "Mac OS X 10.5+"),
            (["Visual Studio 2022", "Visual Studio", "$default"], "Mac OS X", "control", "$default"),
            (["My Mac OS X child", "$default", "Mac OS X"], "Mac OS X", "control", "$default"),
            (["Custom", "ReSharper OSX", "$default"], "Windows 11", "meta", "ReSharper OSX"),
            (["Custom", "Sublime Text (Mac OS X)", "$default"], "Linux", "meta", "Sublime Text (Mac OS X)"),
        ]
        for chain, host, modifier, ancestor in examples:
            with self.subTest(chain=chain):
                result = select_modifier(chain, host, policy)
                self.assertEqual(result["modifier"], modifier)
                self.assertEqual(result["decisiveAncestor"], ancestor)

    def test_unknown_lineage_uses_recorded_host_and_unknown_host_fails(self):
        policy = load_policy(POLICY_SOURCE)
        for host, modifier in [("Mac OS X", "meta"), ("Windows 11", "control"), ("Linux", "control")]:
            result = select_modifier(["Unlisted OSX"], host, policy)
            self.assertEqual(result["modifier"], modifier)
            self.assertEqual(result["reason"], "recorded-host-fallback")
        with self.assertRaisesRegex(ValueError, "unknown host"):
            select_modifier(["Unlisted"], "unknown", policy)


def recount_inventory(rows):
    rows = [row for row in rows if row[0] not in {"END", "MAP"}]
    maps = sorted({row[1] for row in rows if row[0] == "SOURCE"})
    for name in maps:
        actions = [row for row in rows if row[0] == "ACTION" and row[1] == name]
        items = [item for row in actions for item in json.loads(row[3])]
        rows.append(["MAP", name, len(actions), len(items), sum(item["kind"] == "keyboard" for item in items)])
    rows.append(["END", "keymaps", len(maps), "registered", sum(row[0] == "REGISTERED" for row in rows),
                 "sources", sum(row[0] == "SOURCE" for row in rows),
                 "actions", sum(row[0] == "ACTION" for row in rows),
                 "shortcuts", sum(len(json.loads(row[3])) for row in rows if row[0] == "ACTION"),
                 "rows", len(rows) + 1])
    return rows


class StrokeInventoryTest(unittest.TestCase):
    def setUp(self):
        fixture = test_audit.AuditToolTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        self.root = fixture.root
        self.audit_path = self.root / "keymaps.tsv"
        self.path = self.root / "keymaps.tsv.strokes.tsv"
        extras = {
            "ExternalSingle": [keyboard("shift ctrl alt pressed J")],
            "ExternalChord": [keyboard("alt shift ctrl pressed J", "pressed X")],
            "DormantChord": [keyboard("shift meta alt pressed F13", "pressed B")],
            PREFIX + "NotOneOfNine": [keyboard("shift ctrl alt pressed Q")],
            "ExternalG": [keyboard("ctrl alt shift pressed G", "pressed Z")],
            "ReleasedOnly": [keyboard("shift ctrl alt released Z")],
            "MouseOnly": [{"kind": "mouse", "button": 2, "modifiers": 128, "clickCount": 1}],
        }
        audit_rows = fixture.export_rows(extra=extras)
        # Candidate Q on an exact product ID must be listed separately; a merely
        # prefix-matching external ID above must still block Q.
        for row in audit_rows:
            if row[:3] == ["BINDING", "$default", PREFIX + "AddToCollection"]:
                row[3] = json.dumps([keyboard("shift ctrl alt pressed Q")])
        registered = COMMAND_IDS | {"IntroduceConstant", "EditorToggleUseSoftWraps"} | (set(extras) - {"DormantChord"})
        for row in audit_rows:
            if row[:2] == ["META", "registeredActionCount"]:
                row[2] = str(len(registered))
        audit_rows.insert(0, ["META", "strokeInventory", "complete-v1"])
        audit_rows[-1][-1] += 1
        self.audit_path.write_text(encode_rows(audit_rows))
        self.audit_rows = audit_rows
        data = parse_export(self.audit_path)
        values = {key[1]: json.loads(row[3]) for key, row in data["bindings"].items()}
        values.update(extras)
        rows = [["META", "schema", "1"], ["META", "auditFile", self.audit_path.name],
                ["META", "auditSha256", sha256(self.audit_path)]]
        rows += [["REGISTERED", action] for action in sorted(registered)]
        rows += [["SOURCE", "$default", "$default", json.dumps(["DormantChord"])]]
        for action, items in sorted(values.items()):
            rows.append(["ACTION", "$default", action, json.dumps(items), str(action in registered).lower(),
                         "unknown" if action == "DormantChord" else
                         "com.github.hon454.copy-selection-context" if action in COMMAND_IDS else "com.intellij",
                         "unknown" if action == "DormantChord" else "false"])
        self.rows = recount_inventory(rows)
        self.write(self.rows)

    def write(self, rows):
        self.path.write_text(encode_rows(rows))

    def test_changed_product_source_is_rejected_before_policy_claim(self):
        changed = self.root / "CopySelectionShortcuts.kt"
        changed.write_text(POLICY_SOURCE.read_text().replace("return false", "return true"))
        with self.assertRaisesRegex(ValueError, "unsupported product defaults source"):
            compare_prefixes([self.audit_path], ["J"], changed)

    def test_product_rule_comparison_preserves_opposite_modifier_and_full_raw_view(self):
        result = compare_prefixes([self.audit_path], ["J", "F13", "Q"], POLICY_SOURCE)
        candidates = {row["key"]: row for row in result["candidates"]}
        self.assertEqual(result["productModifierPolicy"]["sourceSha256"], sha256(POLICY_SOURCE))
        decision = result["inputs"][0]["productModifierDecisions"]["$default"]
        self.assertEqual(decision["modifier"], "control")  # Exported host is Mac; $default still wins.
        self.assertEqual(candidates["J"]["productRuleComparison"]["externalOccupancyCount"], 2)
        f13 = candidates["F13"]
        self.assertEqual(f13["externalOccupancyCount"], 1)
        self.assertEqual(f13["verdict"], "OCCUPIED")
        self.assertEqual(f13["productRuleComparison"]["externalOccupancyCount"], 0)
        self.assertEqual(f13["productRuleComparison"]["oppositeModifierOccupancy"], f13["externalOccupancy"])
        self.assertFalse(f13["externalOccupancy"][0]["selectedByProductRule"])
        self.assertEqual(candidates["Q"]["productRuleComparison"]["externalOccupancyCount"], 1)
        self.assertTrue(candidates["Q"]["productOccupancy"][0]["selectedByProductRule"])
        self.assertEqual(set(result["comparisonSourceSha256"]), {"strokes.py", "lineage.py"})

    def test_complete_inventory_keeps_single_chord_dormant_and_exact_product_exclusion(self):
        result = compare_prefixes([self.audit_path], ["J", "F13", "Q", "Z"])
        candidates = {row["key"]: row for row in result["candidates"]}
        self.assertEqual(candidates["J"]["externalOccupancyCount"], 2)
        self.assertEqual({row["second"] for row in candidates["J"]["externalOccupancy"]}, {None, "pressed X"})
        self.assertFalse(candidates["F13"]["externalOccupancy"][0]["registered"])
        self.assertEqual(candidates["Q"]["externalActionIds"], [PREFIX + "NotOneOfNine"])
        self.assertEqual(candidates["Q"]["productOccupancy"][0]["action"], PREFIX + "AddToCollection")
        self.assertEqual(candidates["Z"]["verdict"], "NO_OCCUPANCY_IN_RECORDED_KEYMAPS")
        self.assertEqual(result["inputs"][0]["inventorySha256"], sha256(self.path))

    def test_default_candidates_cover_all_letters_and_f1_through_f24(self):
        result = compare_prefixes([self.audit_path])
        self.assertEqual([row["key"] for row in result["candidates"]], DEFAULT_KEYS)
        self.assertEqual(len(DEFAULT_KEYS), 61)
        self.assertEqual(len(result["candidates"][0]["probes"]), 2)

    def test_punctuation_matches_key_codes_and_any_second_stroke_without_typed_aliases(self):
        rows = [row.copy() for row in self.rows]
        next(row for row in rows if row[:3] == ["ACTION", "$default", "ExternalSingle"])[3] = json.dumps([
            keyboard("shift ctrl alt pressed SEMICOLON"), keyboard("shift meta alt pressed OPEN_BRACKET", "pressed A"),
            keyboard("ctrl alt shift typed ,")])
        self.write(recount_inventory(rows))
        result = compare_prefixes([self.audit_path], PUNCTUATION_KEYS)
        candidates = {row["key"]: row for row in result["candidates"]}
        self.assertEqual(candidates["SEMICOLON"]["externalOccupancyCount"], 1)
        self.assertEqual(candidates["OPEN_BRACKET"]["externalOccupancy"][0]["second"], "pressed A")
        self.assertEqual(candidates["COMMA"]["externalOccupancyCount"], 0)
        for literal in [";", ",", ".", "/", "[", "]"]:
            with self.subTest(literal=literal), self.assertRaises(ValueError):
                compare_prefixes([self.audit_path], [literal])

    def test_platform_duplicate_shortcuts_are_preserved_without_double_counting_occupancy(self):
        rows = [row.copy() for row in self.rows]
        row = next(row for row in rows if row[:3] == ["ACTION", "$default", "ExternalSingle"])
        row[3] = json.dumps(json.loads(row[3]) * 2)
        self.write(recount_inventory(rows))
        candidate = compare_prefixes([self.audit_path], ["J"])["candidates"][0]
        self.assertEqual(candidate["externalOccupancyCount"], 2)
        single = next(row for row in candidate["externalOccupancy"] if row["action"] == "ExternalSingle")
        self.assertEqual(single["occurrencesInApiList"], 2)
        self.assertEqual(len(single["allShortcuts"]), 2)

    def test_inherited_dormant_mapping_is_required_in_child_even_without_local_id(self):
        rows = [row.copy() for row in self.audit_rows]
        rows += [["KEYMAP", "Child", "true", json.dumps(["Child", "$default"])]]
        for row in self.audit_rows:
            if row[0] in {"BINDING", "OCCUPANCY"}:
                copied = row.copy()
                copied[1] = "Child"
                rows.append(copied)
        self.audit_path.write_text(encode_rows(test_audit.recount(rows)))
        inventory = [row.copy() for row in self.rows]
        next(row for row in inventory if row[:2] == ["META", "auditSha256"])[2] = sha256(self.audit_path)
        inventory += [["SOURCE", "Child", "Child", "[]"],
                      ["SOURCE", "Child", "$default", json.dumps(["DormantChord"])]]
        for row in self.rows:
            if row[0] == "ACTION":
                copied = row.copy()
                copied[1] = "Child"
                inventory.append(copied)
        self.write(recount_inventory(inventory))
        candidate = compare_prefixes([self.audit_path], ["F13"])["candidates"][0]
        self.assertEqual(candidate["occupiedKeymaps"], 2)
        self.write(recount_inventory([row for row in inventory if row[:3] != ["ACTION", "Child", "DormantChord"]]))
        with self.assertRaisesRegex(ValueError, "missing/extra effective action"):
            parse_inventory(self.audit_path)

    def test_export_without_inventory_opt_in_is_rejected_even_with_companion(self):
        rows = [row for row in self.audit_rows if row[:2] != ["META", "strokeInventory"]]
        self.audit_path.write_text(encode_rows(test_audit.recount(rows)))
        with self.assertRaisesRegex(ValueError, "no complete stroke inventory"):
            parse_inventory(self.audit_path)

    def test_old_six_probe_export_cannot_claim_new_candidate_coverage(self):
        self.path.unlink()
        with self.assertRaisesRegex(ValueError, "missing/invalid"):
            parse_inventory(self.audit_path)

    def test_missing_action_fails_even_after_map_and_end_counts_are_repaired(self):
        for action in ["ExternalChord", "DormantChord", PREFIX + "Copy"]:
            self.write(recount_inventory([row for row in self.rows if row[:3] != ["ACTION", "$default", action]]))
            with self.subTest(action=action), self.assertRaisesRegex(ValueError, "missing/extra effective action"):
                parse_inventory(self.audit_path)

    def test_missing_registered_and_ancestor_source_rows_fail(self):
        for kind in ["REGISTERED", "SOURCE"]:
            changed = list(self.rows)
            changed.pop(next(index for index, row in enumerate(changed) if row[0] == kind))
            self.write(recount_inventory(changed))
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                parse_inventory(self.audit_path)

    def test_hash_mismatch_and_cross_export_pairing_fail(self):
        for key, value in [("auditFile", "other.tsv"), ("auditSha256", "0" * 64)]:
            rows = [row.copy() for row in self.rows]
            next(row for row in rows if row[:2] == ["META", key])[2] = value
            self.write(rows)
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "different audit export"):
                parse_inventory(self.audit_path)

    def test_corrupt_counts_duplicate_rows_unknown_rows_and_missing_end_fail(self):
        variants = [self.rows[:-1], self.rows + [self.rows[-1]],
                    recount_inventory(self.rows[:-1] + [self.rows[3]]),
                    self.rows[:-1] + [["UNEXPECTED", "x"], self.rows[-1]]]
        bad_count = [row.copy() for row in self.rows]
        next(row for row in bad_count if row[0] == "MAP")[2] = 999
        variants.append(bad_count)
        for rows in variants:
            self.write(rows)
            with self.subTest(rows=len(rows)), self.assertRaises(ValueError):
                parse_inventory(self.audit_path)

    def test_changed_original_binding_and_missing_original_probe_occupancy_fail(self):
        for action in [PREFIX + "Copy", "ExternalG"]:
            rows = [row.copy() for row in self.rows]
            next(row for row in rows if row[:3] == ["ACTION", "$default", action])[3] = "[]"
            self.write(recount_inventory(rows))
            with self.subTest(action=action), self.assertRaisesRegex(ValueError, "inventory/schema-2"):
                parse_inventory(self.audit_path)

    def test_ownership_disagreement_with_original_probe_fails(self):
        rows = [row.copy() for row in self.rows]
        next(row for row in rows if row[:3] == ["ACTION", "$default", "ExternalG"])[5] = "wrong.owner"
        self.write(rows)
        with self.assertRaisesRegex(ValueError, "owner/list mismatch"):
            parse_inventory(self.audit_path)

    def test_duplicate_or_missing_input_and_invalid_candidate_keys_fail(self):
        for paths, keys in [([], ["J"]), ([self.audit_path, self.audit_path], ["J"]),
                            ([self.audit_path], []), ([self.audit_path], ["J", "J"]),
                            ([self.audit_path], ["F25"]), ([self.audit_path], ["j"])]:
            with self.subTest(paths=paths, keys=keys), self.assertRaises(ValueError):
                compare_prefixes(paths, keys)


if __name__ == "__main__":
    unittest.main()
