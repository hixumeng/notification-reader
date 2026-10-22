#!/usr/bin/env python3
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
jdk = root / '.toolchain/jdk-17.0.20.1+1/Contents/Home/bin'
out = root / 'build/core-tests'
out.mkdir(parents=True, exist_ok=True)
core = root / 'app/src/main/java/dev/qianwen/notifyreader'
subprocess.run([str(jdk/'javac'), '--release', '8', '-encoding', 'UTF-8', '-d', str(out),
                str(core/'ReadingRules.java'), str(core/'WavPadding.java'), str(root/'tests/CoreTests.java')], check=True)
subprocess.run([str(jdk/'java'), '-cp', str(out), 'CoreTests'], check=True)
tree = ET.parse(root / 'app/src/main/AndroidManifest.xml')
android = '{http://schemas.android.com/apk/res/android}'
permissions = {p.get(android+'name') for p in tree.findall('uses-permission')}
assert 'android.permission.INTERNET' not in permissions
assert 'android.permission.READ_EXTERNAL_STORAGE' not in permissions
assert 'android.permission.RECORD_AUDIO' not in permissions
assert tree.find('application').get(android+'allowBackup') == 'false'
for service in tree.findall('./application/service'):
    if service.get(android+'exported') == 'true':
        assert service.get(android+'permission') == 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'
print('Manifest checks passed: no network/microphone/storage permission, no backup, listener OS-protected.')
