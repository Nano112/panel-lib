#!/usr/bin/env python3
"""Check all six release jars, metadata, bundled native bindings and licenses."""
import hashlib
import json
from pathlib import Path
import re
import zipfile

root = Path(__file__).resolve().parents[1]
pins = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
version = '.'.join(pins['version' + part] for part in ['Major', 'Minor', 'Patch'])
targets = re.findall(r'"([\d.]+)"', re.search(r'versions\(([^)]+)\)', (root / 'settings.gradle.kts').read_text())[1])
artifacts = root / 'build/libs' / version
expected = {f'panel-lib-mc{mc}-{version}.jar' for mc in targets}
assert {p.name for p in artifacts.glob('*.jar')} == expected, 'Release artifact set differs from supported targets'
checksums = []
for mc in targets:
    path = artifacts / f'panel-lib-mc{mc}-{version}.jar'
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None
        assert 'LICENSE' in jar.namelist()
        meta = json.loads(jar.read('fabric.mod.json'))
        assert meta['id'] == 'panellib' and meta['version'] == version
        assert meta['environment'] == 'client'
        assert meta['depends']['minecraft'] == mc
        assert meta['depends']['fabric-language-kotlin'] == '>=' + pins['deps.flk_min']
        assert meta['contact']['sources'] == 'https://github.com/Nano112/panel-lib'
        assert '${' not in json.dumps(meta)
        nested = [e['file'] for e in meta['jars']]
        for binding in ['binding', 'lwjgl3', 'natives-windows', 'natives-linux', 'natives-macos']:
            assert any('imgui-java-' + binding + '-' in name for name in nested), binding
        assert all(name in jar.namelist() for name in nested)
    checksums.append(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n')
(artifacts / 'SHA256SUMS').write_text(''.join(checksums))
print(f'Checked {len(targets)} panel-lib {version} jars; checksums written to {artifacts}/SHA256SUMS')
