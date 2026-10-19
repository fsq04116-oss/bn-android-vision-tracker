"""Restore only this update's source paths after verifying their current hashes."""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

DATA = Path(__file__).resolve().parent
target = Path(sys.argv[1]).resolve()
changes = json.loads((DATA / 'changes.json').read_text(encoding='utf-8'))

def destination(name):
    path = (target / name).resolve()
    if not path.is_relative_to(target):
        raise ValueError('Path escapes target project')
    return path

# Check all paths before changing any file. No recursive deletion is performed.
for name, expected in changes['modified_hashes'].items():
    path = destination(name)
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise SystemExit('Target differs from this update: ' + name)

with zipfile.ZipFile(DATA / 'baseline-source.zip') as archive:
    for name in changes['changed']:
        path = destination(name)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(archive.read(name))
for name in changes['added']:
    destination(name).unlink()
print('RESTORE PASS source=baseline added_resources=removed')
