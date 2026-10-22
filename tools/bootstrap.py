#!/usr/bin/env python3
"""Download pinned, checksum-verified build tools into this project (macOS arm64)."""
from pathlib import Path
import hashlib
import platform
import subprocess
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TC = ROOT / '.toolchain'
DOWNLOADS = TC / 'downloads'
PACKAGES = [
    ('jdk.tar.gz', 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.20.1_1.tar.gz',
     '196d13ba5f10414bef7f6a05a9b3f00edacb18ebacef2b99485db9e2ee18f0e8'),
    ('platform.zip', 'https://dl.google.com/android/repository/platform-35_r02.zip',
     '0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0'),
    ('build-tools.zip', 'https://dl.google.com/android/repository/build-tools_r35_macosx.zip',
     '530cdbd1ec315e1477624d7ed2f0f2962108d69f36eddba5894cef9ea2cedb48'),
]

def main():
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise SystemExit('This isolated bootstrap targets Apple Silicon Macs. On other hosts use Android Studio with the included Gradle project.')
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    for name, url, checksum in PACKAGES:
        archive = DOWNLOADS / name
        if not archive.exists():
            subprocess.run(['curl', '-fL', '--retry', '2', '--max-time', '600', url, '-o', str(archive)], check=True)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != checksum:
            raise SystemExit(f'Checksum mismatch: {name}. Nothing was executed from this archive.')
        if name.endswith('.tar.gz'):
            with tarfile.open(archive) as source:
                source.extractall(TC, filter='data')
        else:
            with zipfile.ZipFile(archive) as source:
                for entry in source.infolist():
                    destination = (TC / entry.filename).resolve()
                    if not destination.is_relative_to(TC.resolve()):
                        raise SystemExit('Unsafe ZIP path')
                source.extractall(TC)
                for entry in source.infolist():
                    destination = TC / entry.filename
                    mode = entry.external_attr >> 16
                    if destination.is_file() and mode:
                        destination.chmod(mode & 0o777)
        print(f'{name}: SHA-256 verified, extracted')

if __name__ == '__main__':
    main()
