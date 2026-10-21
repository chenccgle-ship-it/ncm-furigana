"""Check the installable artifact's module registration and compile-only API boundary."""
import hashlib
import struct
import zipfile
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).parent
version = ET.parse(root / 'module/AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName').split('-')[0]
apk = Path(sys.argv[1]) if len(sys.argv)>1 else root / ('outputs/ncm-furigana-' + version + '.apk')
with zipfile.ZipFile(apk) as z:
    assert len(z.namelist()) == len(set(z.namelist())), 'Duplicate ZIP entries'
    assert z.read('META-INF/xposed/java_init.list').decode().strip() == 'dev.local.ncmfurigana.ProbeModule'
    assert z.read('META-INF/xposed/scope.list').decode().strip() == 'com.netease.cloudmusic'
    props = z.read('META-INF/xposed/module.prop').decode()
    assert 'minApiVersion=102' in props and 'targetApiVersion=102' in props
    b = z.read('classes.dex')
    def uint(p): return struct.unpack_from('<I', b, p)[0]
    sn, so, tn, to = struct.unpack_from('<4I', b, 56)
    strings = []
    for i in range(sn):
        p = uint(so + i * 4)
        while b[p] & 128: p += 1
        p += 1
        strings.append(b[p:b.index(b'\0', p)].decode('utf8', errors='replace'))
    types = [strings[uint(to + i * 4)] for i in range(tn)]
    cn, co = struct.unpack_from('<2I', b, 96)
    defined = [types[uint(co + i * 32)] for i in range(cn)]
    for cls in ('ProbeModule', 'MainActivity', 'ReportProvider'):
        assert 'Ldev/local/ncmfurigana/' + cls + ';' in defined
    assert not any(n.startswith('Lio/github/libxposed/') for n in defined), 'API classes must be compile-only'
    assert 'Lcom/atilika/kuromoji/ipadic/Tokenizer;' in defined
    assert len([n for n in z.namelist() if n.endswith('.bin')]) == 8
    assert 'assets/licenses/kuromoji-ipadic/NOTICE.md' in z.namelist()
print('PASS: registration, scope, API 102 metadata, entry classes, no bundled framework API')
print('SHA-256:', hashlib.sha256(apk.read_bytes()).hexdigest())
