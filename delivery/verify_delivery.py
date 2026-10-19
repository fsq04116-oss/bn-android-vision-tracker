"""Validate the shipped APKs and the separately restored source copy."""
from pathlib import Path
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_HOME', r'E:\114514\android-sdk'))
BUILD_TOOLS = SDK / 'build-tools' / '36.0.0'
BASELINE_HASH = 'b2a56e080997cb6d76131dc5a13b6c36089de39d9d184373872c0f808391d815'
CERT = 'ce23b923a2b69bb2a8c52582e557e5761251700d54dc30a29941068e6d5a6aad'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def run(args):
    result = subprocess.run([str(a) for a in args], capture_output=True, encoding='utf-8', errors='replace')
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout

def apk(mode):
    path = PROJECT.parent / ('白柠视觉辅助-v1.1.0-debug.apk' if mode == 'BASELINE' else '白柠视觉辅助-v1.2.0-debug.apk')
    signature = run([BUILD_TOOLS / 'apksigner.bat', 'verify', '--print-certs', path])
    assert CERT in signature, 'Signing certificate differs from the installed baseline'
    # This Windows aapt build rejects Unicode filenames; inspect an identical
    # temporary copy with an ASCII name and keep the distributed name Chinese.
    with tempfile.TemporaryDirectory(prefix='vision-apk-') as tmp:
        staged = Path(tmp) / 'application.apk'
        shutil.copyfile(path, staged)
        badging = run([BUILD_TOOLS / 'aapt.exe', 'dump', 'badging', staged])
    assert "name='com.visionoverlay.app'" in badging
    assert "sdkVersion:'28'" in badging
    permissions = set(re.findall(r"uses-permission: name='([^']+)'", badging))
    assert permissions == {'android.permission.CAMERA', 'com.visionoverlay.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}, permissions
    label = '白柠视觉辅助'
    version = '1.1.0' if mode == 'BASELINE' else '1.2.0'
    assert f"application-label:'{label}'" in badging
    assert f"versionName='{version}'" in badging
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None
        names = set(z.namelist())
        for abi in ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']:
            assert f'lib/{abi}/libvisionoverlay.so' in names, abi
        for model in ['yolov8n', 'yolov8n_pose']:
            for ext in ['bin', 'param']:
                assert len(z.read(f'assets/{model}.ncnn.{ext}')) > 100
        if mode == 'BASELINE':
            assert sha(path.read_bytes()) == BASELINE_HASH
            assert 'res/raw/launch_video.mp4' in names
            assert "launchable-activity: name='com.visionoverlay.app.LaunchActivity'" in badging
        else:
            assert "launchable-activity: name='com.visionoverlay.app.LaunchActivity'" in badging
            assert "versionCode='3'" in badging
            # UI/media changes must preserve the shipped native inference and models.
            with zipfile.ZipFile(PROJECT.parent / '白柠视觉辅助-v1.1.0-debug.apk') as baseline:
                for name in names:
                    if name.startswith(('lib/', 'assets/')):
                        assert z.read(name) == baseline.read(name), name
            assert sha(z.read('res/raw/launch_video.mp4')) == sha((PROJECT / 'app/src/main/res/raw/launch_video.mp4').read_bytes())
            assert z.read('res/drawable-nodpi-v4/launch_poster.jpg') == (PROJECT / 'app/src/main/res/drawable-nodpi/launch_poster.jpg').read_bytes()
            from PIL import Image
            sizes = []
            for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
                icon = f'res/mipmap-{density}-v4/ic_launcher.png'
                packaged = z.read(icon)
                assert packaged == (PROJECT / f'app/src/main/res/mipmap-{density}/ic_launcher.png').read_bytes()
                with Image.open(io.BytesIO(packaged)) as image:
                    assert image.size == (size, size)
                sizes.append(size)
            lint = ET.parse(PROJECT / 'app/build/reports/lint-results-debug.xml').getroot()
            assert not any(i.attrib.get('severity') in ('Error','Fatal') for i in lint.findall('issue'))
            dex = b''.join(z.read(n) for n in names if re.fullmatch(r'classes\d*\.dex', n))
            for value in ['jujuenb','https://t.me/BNSTR','https://github.com/fsq04116-oss/bn-android-vision-tracker']:
                assert value.encode() in dex, value
    print(f'{mode} PASS')

def rollback(target):
    target = Path(target).resolve()
    changes = json.loads((PROJECT / 'delivery/changes.json').read_text(encoding='utf-8'))
    with zipfile.ZipFile(PROJECT / 'delivery/baseline-source.zip') as z:
        for name in z.namelist():
            assert (target / name).read_bytes() == z.read(name), name
    for name in changes['added']:
        assert not (target / name).exists(), name
    # The working project must stay modified while the independent copy is restored.
    for name, expected in changes['modified_hashes'].items():
        assert sha((PROJECT / name).read_bytes()) == expected, name
    build = (target / 'app/build.gradle.kts').read_text(encoding='utf-8-sig')
    assert 'versionName = "1.1.0"' in build
    ui = (target / 'app/src/main/java/com/visionoverlay/app/AppUi.kt').read_text(encoding='utf-8-sig')
    assert 'Color.rgb(8, 15, 24)' in ui
    print('ROLLBACK PASS')

if __name__ == '__main__':
    mode = sys.argv[1]
    if mode in ('BASELINE', 'MODIFIED'):
        apk(mode)
    elif mode == 'ROLLBACK':
        rollback(sys.argv[2])
    else:
        raise ValueError(mode)
