"""Explicit, source-pinned mirror of the product's keymap modifier selection.

Updating this pin requires reviewing the Kotlin rule and its Python mirror.
It deliberately rejects even unrelated source changes rather than silently
applying an obsolete policy to a different product revision.
"""

import hashlib
import json
import re
from pathlib import Path

from evidence import require

SUPPORTED_SOURCE_SHA256 = "08bc83b6d6252f2f8ae7eebf90b9e5b46956911efb92d6a8921c0d39fd09c2a5"


def load_policy(source):
    source = Path(source)
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    require(digest == SUPPORTED_SOURCE_SHA256,
            "unsupported product defaults source; review the modifier rule before updating its pin")
    text = source.read_text(encoding="utf-8")
    # The verified source contains parentheses in one ID; use the closing line.
    block = text.split("val macKeymapIds: Set<String> = linkedSetOf(\n", 1)[1].split("\n    )", 1)[0]
    return {"id": "CopySelectionShortcuts.usesMacKeymap/source-pinned-v1",
            "sourcePath": str(source.resolve()), "sourceSha256": digest,
            "macKeymapIds": re.findall(r'"([^"\n]+)"', block),
            "defaultKeymapId": "$default", "macModifier": "meta", "otherModifier": "control",
            "rule": "Walk self then parents; first exact Mac ID selects Meta, first $default selects Ctrl; otherwise use recorded host OS."}


def select_modifier(chain, os_name, policy):
    for ancestor in chain:
        if ancestor in policy["macKeymapIds"]:
            return {"modifier": "meta", "decisiveAncestor": ancestor, "reason": "exact-mac-ancestor"}
        if ancestor == policy["defaultKeymapId"]:
            return {"modifier": "control", "decisiveAncestor": ancestor, "reason": "default-ancestor"}
    # These are the supported captured host families. Unknown hosts cannot
    # silently become a non-Mac fallback when the lineage gives no answer.
    require(os_name == "Mac OS X" or os_name == "Linux" or os_name.startswith("Windows"),
            "unknown host OS for product modifier fallback")
    return {"modifier": "meta" if os_name == "Mac OS X" else "control",
            "decisiveAncestor": None, "reason": "recorded-host-fallback", "hostOs": os_name}


def decisions(inventory, policy):
    return {name: {"chain": json.loads(row[3]), **select_modifier(
        json.loads(row[3]), inventory["audit"]["metadata"]["os"][0], policy)}
        for name, row in inventory["audit"]["keymaps"].items()}
