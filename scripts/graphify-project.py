#!/usr/bin/env python3
"""Query a local, content-checked Graphify index of this checkout."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'graphify-out'
VERSION = '0.9.97'
EXTENSIONS = {'.kt', '.kts', '.java', '.c', '.cpp', '.h', '.hpp', '.json', '.py', '.sh', '.toml', '.yaml', '.yml', '.pro', '.gradle'}


def fingerprint():
    """Hash source contents, including new files and deleted paths, not just HEAD."""
    paths = subprocess.check_output(['git', 'ls-files', '-c', '-o', '--exclude-standard', '-z'], cwd=ROOT).split(b'\0')
    digest = hashlib.sha256(VERSION.encode())
    for raw in sorted(set(paths)):
        if not raw:
            continue
        path = ROOT / os.fsdecode(raw)
        relative = path.relative_to(ROOT)
        if path.suffix.lower() not in EXTENSIONS or any(p in {'graphify-out', 'build', '.gradle', '.kotlin', '.idea', '.ocr-work', 'test-data'} for p in relative.parts):
            continue
        if path.is_symlink() or not path.is_file():
            continue
        digest.update(raw + b'\0')
        digest.update(hashlib.sha256(path.read_bytes()).digest())
    for name in ('.graphifyignore', '.gitignore', 'scripts/graphify-project.py'):
        path = ROOT / name
        if path.exists():
            digest.update(name.encode() + path.read_bytes())
    return digest.hexdigest()


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in {'refresh', 'status', 'query', 'explain', 'path'}:
        raise SystemExit('Usage: python3 scripts/graphify-project.py {refresh|status|query|explain|path} [arguments]')
    action, args = sys.argv[1], sys.argv[2:]
    tool = shutil.which('graphify') or str(Path.home() / '.local/bin/graphify')
    if not Path(tool).is_file():
        raise SystemExit(f'Install first: uv tool install graphifyy=={VERSION}')
    version = subprocess.check_output([tool, '--version'], text=True).strip()
    if version.split()[-1] != VERSION:
        raise SystemExit(f'Expected Graphify {VERSION}, got {version}; use uv tool install graphifyy=={VERSION}')
    env = dict(os.environ, GRAPHIFY_OUT='graphify-out', GRAPHIFY_NO_TIPS='1')
    OUT.mkdir(exist_ok=True)
    with (OUT / '.project.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        stamp = OUT / '.project-source.json'
        try:
            previous = json.loads(stamp.read_text())
        except (OSError, ValueError):
            previous = {}
        current = fingerprint()
        fresh = (OUT / 'graph.json').is_file() and previous.get('sha256') == current
        if action == 'status':
            print('fresh' if fresh else 'stale or missing; next query refreshes automatically')
            return
        if action == 'refresh' or not fresh:
            for attempt in range(2):
                before = fingerprint()
                # Discard derived state: even a forced scan may reuse stat-based cache entries.
                for generated in OUT.iterdir():
                    if generated.name == '.project.lock':
                        continue
                    if generated.is_dir() and not generated.is_symlink():
                        shutil.rmtree(generated)
                    else:
                        generated.unlink()
                subprocess.run([tool, 'extract', str(ROOT), '--code-only', '--force', '--no-cluster', '--max-workers', '2', '--exclude', '*.md'], cwd=ROOT, env=env, check=True)
                after = fingerprint()
                if before == after:
                    temporary = stamp.with_suffix('.tmp')
                    temporary.write_text(json.dumps({'sha256': after, 'graphify': VERSION}))
                    temporary.replace(stamp)
                    break
            else:
                stamp.unlink(missing_ok=True)
                raise SystemExit('Source changed during both index builds; retry after edits finish.')
        if action != 'refresh':
            if not args:
                raise SystemExit(f'{action} requires a question/symbol (path requires two).')
            command = [tool, action, *args, '--graph', str(OUT / 'graph.json')]
            if action == 'query' and '--budget' not in args and not any(a.startswith('--budget=') for a in args):
                command += ['--budget', '2000']
            subprocess.run(command, cwd=ROOT, env=env, check=True)


if __name__ == '__main__':
    main()
