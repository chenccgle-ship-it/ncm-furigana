"""Bundle runtime sources and production dependencies, with reproducible path order."""
from pathlib import Path
import zipfile
root = Path(__file__).parent
source = root / 'runtime'
out = root / 'module/resources/assets/resolver.zip'
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    for path in sorted(source.rglob('*')):
        rel = path.relative_to(source)
        if path.is_file() and not any(x.startswith('.') for x in rel.parts) and path.suffix not in ('.cmd', '.ps1'):
            z.write(path, rel.as_posix())
print(out, out.stat().st_size)
