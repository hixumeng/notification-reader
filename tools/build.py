#!/usr/bin/env python3
"""Build this dependency-free Java Android app with an isolated Android toolchain."""
from pathlib import Path
import os
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TC = ROOT / '.toolchain'
BUILD = ROOT / 'build'
DIST = ROOT / 'dist'
JDK = TC / 'jdk-17.0.20.1+1/Contents/Home'
TOOLS = TC / 'android-15'
ANDROID = TC / 'android-35/android.jar'
ENV = dict(os.environ, JAVA_HOME=str(JDK))

def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, env=ENV, check=True)

def main():
    for path in [JDK / 'bin/java', TOOLS / 'aapt2', ANDROID]:
        if not path.is_file():
            raise SystemExit('Missing toolchain. Run python3 tools/bootstrap.py first.')
    if BUILD.exists():
        shutil.rmtree(BUILD)
    for path in [BUILD / 'generated', BUILD / 'classes', BUILD / 'dex', DIST, ROOT / '.signing']:
        path.mkdir(parents=True, exist_ok=True)
    # Gradle supplies the namespace; standalone AAPT2 needs it in its manifest input.
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
    manifest.getroot().set('package', 'dev.qianwen.notifyreader')
    manifest.write(BUILD / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
    run(TOOLS / 'aapt2', 'compile', '--dir', ROOT / 'app/src/main/res', '-o', BUILD / 'resources.zip')
    run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', BUILD / 'AndroidManifest.xml',
        '--java', BUILD / 'generated', '-o', BUILD / 'resources.apk', BUILD / 'resources.zip')
    sources = sorted((ROOT / 'app/src/main/java').rglob('*.java')) + sorted((BUILD / 'generated').rglob('*.java'))
    source_list = BUILD / 'sources.txt'
    source_list.write_text('\n'.join('"' + str(p) + '"' for p in sources))
    run(JDK / 'bin/javac', '--release', '8', '-encoding', 'UTF-8', '-cp', ANDROID,
        '-d', BUILD / 'classes', '@' + str(source_list))
    run(JDK / 'bin/jar', 'cf', BUILD / 'classes.jar', '-C', BUILD / 'classes', '.')
    run(JDK / 'bin/java', '-cp', TOOLS / 'lib/d8.jar', 'com.android.tools.r8.D8',
        '--min-api', '28', '--lib', ANDROID, '--output', BUILD / 'dex', BUILD / 'classes.jar')
    shutil.copyfile(BUILD / 'resources.apk', BUILD / 'unsigned.apk')
    with zipfile.ZipFile(BUILD / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
        for dex in (BUILD / 'dex').glob('*.dex'):
            archive.write(dex, dex.name)
    run(TOOLS / 'zipalign', '-f', '4', BUILD / 'unsigned.apk', BUILD / 'aligned.apk')
    key = ROOT / '.signing/personal-test.jks'
    if not key.exists():
        run(JDK / 'bin/keytool', '-genkeypair', '-keystore', key, '-alias', 'personal-test',
            '-storepass', 'android', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '2048',
            '-validity', '10000', '-dname', 'CN=Notification Reader Personal Test', '-noprompt')
    apk = DIST / 'notification-reader-v0.1.0.apk'
    run(JDK / 'bin/java', '-jar', TOOLS / 'lib/apksigner.jar', 'sign', '--ks', key,
        '--ks-key-alias', 'personal-test', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
        '--out', apk, BUILD / 'aligned.apk')
    run(JDK / 'bin/java', '-jar', TOOLS / 'lib/apksigner.jar', 'verify', '--verbose', apk)
    print(f'APK ready: {apk} ({apk.stat().st_size:,} bytes)')

if __name__ == '__main__':
    main()
