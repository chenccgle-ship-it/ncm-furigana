"""Fetch pinned build inputs; run from any directory with Python 3.10+."""
from pathlib import Path
import hashlib, urllib.request, zipfile, shutil, io

root = Path(__file__).resolve().parents[1]
cache = root / '.build-cache'
tools = root / 'tools'
cache.mkdir(exist_ok=True)
tools.mkdir(exist_ok=True)

def fetch(name, url, digest):
    path = cache / name
    if not path.exists():
        print('Downloading', name, flush=True)
        with urllib.request.urlopen(url, timeout=120) as response, path.open('wb') as output:
            shutil.copyfileobj(response, output)
    if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        raise RuntimeError('Checksum mismatch: ' + str(path) + '; remove this cache file and retry')
    print('Verified', name, flush=True)
    return path

def extract(path, target):
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            resolved = (target / entry.filename).resolve()
            if not resolved.is_relative_to(target.resolve()):
                raise RuntimeError('Unsafe archive path')
        archive.extractall(target)

android = 'https://dl.google.com/android/repository/'
extract(fetch('build-tools.zip', android + 'build-tools_r35_windows.zip',
    '5753c679a1b90bcf6fbc9945a2ce39dfb9e74f1df0831a1e1866ae5b594326f0'), tools/'build-tools')
extract(fetch('platform.zip', android + 'platform-35_r02.zip',
    '0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0'), tools/'platform')
maven = 'https://repo.maven.apache.org/maven2/'
aar = fetch('libxposed-api-102.0.0.aar', maven + 'io/github/libxposed/api/102.0.0/api-102.0.0.aar',
    '423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0')
(tools/'libxposed-api-102').mkdir(exist_ok=True)
with zipfile.ZipFile(aar) as archive:
    (tools/'libxposed-api-102/classes.jar').write_bytes(archive.read('classes.jar'))
dictionary_dir = tools/'kuromoji'
dictionary_dir.mkdir(exist_ok=True)
jars = []
for name, digest in [('core','9dec317cab2879474d39bffea4f3f481288d87952c9b5e19d6897b0d5e0d3613'),
                     ('ipadic','24909fd751c0b439f7af5131b080eb65bc85062f0c4977ca4a71c76abe74e0b6')]:
    filename = f'kuromoji-{name}-0.9.0.jar'
    path = fetch(filename, maven + f'com/atilika/kuromoji/kuromoji-{name}/0.9.0/{filename}', digest)
    shutil.copyfile(path, dictionary_dir/filename)
    jars.append(path)
with zipfile.ZipFile(dictionary_dir/'kuromoji-classes-only.jar', 'w', zipfile.ZIP_DEFLATED) as classes:
    for path in jars:
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if name.endswith('.class'):
                    classes.writestr(name, archive.read(name))
                elif name.startswith('com/atilika/') and name.endswith('.bin'):
                    output = root/'module/resources'/name
                    output.parent.mkdir(parents=True, exist_ok=True)
                    output.write_bytes(archive.read(name))
node = fetch('libnode.so', 'https://raw.githubusercontent.com/nining377/dolby_beta/258ab9b95671ecfb13a03636842890d9ca36fe75/app/src/main/jniLibs/arm64-v8a/libnode.so',
    'eda5e07a48f9a4ffe4c463379d6bca6d382398693c508b262df848e045f2f788')
target = root/'module/resources/lib/arm64-v8a/libnode.so'
target.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(node, target)
print('Dependencies ready. Install runtime packages and run pack-runtime.py next.')
