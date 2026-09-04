"""Generate a deterministic non-sensitive above-warning collection sample."""
from pathlib import Path
import argparse

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("project", type=Path, help="Disposable sample project directory")
args = parser.parse_args()
source = args.project / "src/demo/Warning.txt"
source.parent.mkdir(parents=True, exist_ok=True)
source.write_bytes(b"x" * 262144)
expected = args.project / "expected-warning-output.txt"
expected.write_bytes(b"x" * 262144 + b"\n")
print("One item: 262144 raw UTF-8 bytes, 262145 output UTF-8 bytes.")
print("Use custom template {code} followed by one newline, with collection code enabled.")
