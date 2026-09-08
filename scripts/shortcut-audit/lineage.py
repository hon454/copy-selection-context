"""Explicit, source-pinned mirror of the product's keymap modifier selection.

Updating this pin requires reviewing the Kotlin rule and its Python mirror.
It deliberately rejects even unrelated source changes rather than silently
applying an obsolete policy to a different product revision.
"""

import hashlib
import json
import os
import re
import stat
from pathlib import Path

from evidence import require

SUPPORTED_SOURCE_SHA256 = "08bc83b6d6252f2f8ae7eebf90b9e5b46956911efb92d6a8921c0d39fd09c2a5"
MAX_SOURCE_BYTES = 16 * 1024


def read_policy_snapshot(source):
    # The absolute lookup path is a label, not a post-read resolution claim.
    # Parent aliases are allowed; the final entry must not be a symlink. The
    # opened descriptor's device/inode identifies the bytes actually inspected.
    source = Path(source).absolute()
    entry = source.lstat()
    require(stat.S_ISREG(entry.st_mode), "policy source must be a regular file, not a symlink or special file")
    require(0 < entry.st_size <= MAX_SOURCE_BYTES, "policy source exceeds size limit or is empty")
    require(hasattr(os, "O_NOFOLLOW") and hasattr(os, "O_NONBLOCK"),
            "policy snapshot requires no-follow and nonblocking file-open support")
    descriptor = os.open(source, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        opened = os.fstat(descriptor)
        require(stat.S_ISREG(opened.st_mode) and (opened.st_dev, opened.st_ino) == (entry.st_dev, entry.st_ino),
                "policy source identity changed before opening")
        require(0 < opened.st_size <= MAX_SOURCE_BYTES, "opened policy source exceeds size limit or is empty")
        data = os.read(descriptor, MAX_SOURCE_BYTES + 1)
        require(0 < len(data) <= MAX_SOURCE_BYTES and len(data) == opened.st_size,
                "policy source snapshot exceeds size limit or changed size")
        after = os.fstat(descriptor)
        require((opened.st_size, opened.st_mtime_ns, opened.st_ctime_ns) ==
                (after.st_size, after.st_mtime_ns, after.st_ctime_ns), "policy source changed during read")
        return data, {"lookupPath": str(source), "device": opened.st_dev, "inode": opened.st_ino,
                      "sizeBytes": len(data), "mtimeNs": opened.st_mtime_ns,
                      "symlinkPolicy": "reject-final-entry; parent aliases allowed; descriptor identity recorded"}
    finally:
        os.close(descriptor)


def load_policy(source):
    data, identity = read_policy_snapshot(source)
    digest = hashlib.sha256(data).hexdigest()
    require(digest == SUPPORTED_SOURCE_SHA256,
            "unsupported product defaults source; review the modifier rule before updating its pin")
    text = data.decode("utf-8")
    # The verified source contains parentheses in one ID; use the closing line.
    block = text.split("val macKeymapIds: Set<String> = linkedSetOf(\n", 1)[1].split("\n    )", 1)[0]
    return {"id": "CopySelectionShortcuts.usesMacKeymap/source-pinned-v1",
            "sourcePath": identity["lookupPath"], "sourceIdentity": identity, "sourceSha256": digest,
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
