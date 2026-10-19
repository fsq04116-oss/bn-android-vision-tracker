"""Decode the whole movie and compare a frame to the original rotated upright."""
from pathlib import Path
import re
import subprocess
import sys
from PIL import Image, ImageChops, ImageStat

project = Path(__file__).resolve().parents[1]
ffmpeg, original = sys.argv[1:3]
movie = project / 'app/src/main/res/raw/launch_video.mp4'
result = subprocess.run([ffmpeg, '-hide_banner', '-i', str(movie), '-f', 'null', '-'], capture_output=True)
log = result.stderr.decode('utf-8', errors='replace')
assert result.returncode == 0, log
assert '1280x720 [SAR 1:1 DAR 16:9]' in log
assert 'Video: h264 (Main)' in log
assert not re.search(r'Audio:|displaymatrix:|rotation of', log)
assert re.search(r'frame=\s*699\s', log), log

def frame(path, filters):
    args = [ffmpeg, '-v', 'error', '-ss', '9', '-i', str(path), '-vf', filters,
            '-frames:v', '1', '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-']
    result = subprocess.run(args, capture_output=True)
    assert result.returncode == 0, result.stderr
    return Image.frombytes('RGB', (128, 72), result.stdout)

expected = frame(original, 'transpose=2,scale=1280:640,pad=1280:720:0:40:color=black,scale=128:72')
actual = frame(movie, 'scale=128:72')
error = sum(ImageStat.Stat(ImageChops.difference(actual, expected)).mean) / 3
assert error < 8, f'Rotation/padding differs from original: mean error {error}'
with Image.open(project / 'app/src/main/res/drawable-nodpi/launch_poster.jpg') as poster:
    assert poster.size == (1280, 720)
print('MEDIA PASS codec=h264 size=1280x720 ratio=16:9 frames=699 rotation=counterclockwise90 poster=matched')
