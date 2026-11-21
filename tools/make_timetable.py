# 生成像课表应用截图的图片，给模拟器里的 OCR 测试用
# 用法：make_timetable.py out.png [zh|zh_dense|en|ja|ko|de]
import sys, glob
from PIL import Image, ImageDraw, ImageFont

font_path = (glob.glob('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc') + glob.glob('/usr/share/fonts/**/NotoSansCJK*.ttc', recursive=True))[0]
v = sys.argv[2] if len(sys.argv) > 2 else 'zh'
f = lambda s: ImageFont.truetype(font_path, s)
V = {
    'zh': dict(W=1080, H=2000, left=90, top=170, colw=141, rowh=140, fs=30, wrap=4, days=['周一', '周二', '周三', '周四', '周五', '周六', '周日'], sec='{}',
               courses=[(1, 1, 2, '高等数学', '@教1-201'), (1, 5, 2, '大学物理', '@理A305'), (2, 3, 2, '大学英语', '@外语楼302'),
                        (3, 1, 2, '线性代数', '@教2-108'), (4, 7, 2, '程序设计', '@机房C3'), (5, 9, 3, '形势与政策', '@大礼堂')]),
    # 窄屏、小字、课名两字一行，像真实课表应用
    'zh_dense': dict(W=720, H=1400, left=56, top=110, colw=95, rowh=96, fs=20, wrap=2, days=['一', '二', '三', '四', '五', '六', '日'], sec='{}',
               courses=[(1, 1, 2, '概率统计', '@A201'), (2, 3, 2, '数据结构', '@B105'), (3, 5, 2, '马克思主义原理', '@C301'),
                        (6, 1, 2, '日语', '@D2'), (7, 3, 2, '体育', '@操场')]),
    'en': dict(W=1080, H=2000, left=110, top=170, colw=138, rowh=140, fs=26, wrap=0, days=['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'], sec='{}',
               courses=[(1, 1, 2, 'Linear Algebra', 'Room 204'), (2, 3, 2, 'Physics', 'Hall B'), (4, 5, 2, 'History', 'Room 112')]),
    'de': dict(W=1080, H=2000, left=110, top=170, colw=190, rowh=140, fs=26, wrap=0, days=['Mo', 'Di', 'Mi', 'Do', 'Fr'], sec='{}.',
               courses=[(1, 1, 2, 'Mathematik', 'Raum 204'), (3, 3, 2, 'Chemie', 'Raum 101'), (5, 5, 2, 'Englisch', 'Raum 012')]),
    'ja': dict(W=1080, H=2000, left=110, top=170, colw=190, rowh=140, fs=30, wrap=4, days=['月', '火', '水', '木', '金'], sec='{}限',
               courses=[(1, 1, 2, '線形代数', '講義室3'), (2, 3, 2, '英語', '201号室'), (4, 5, 2, '物理学', '講義室1')]),
    'ko': dict(W=1080, H=2000, left=110, top=170, colw=190, rowh=140, fs=30, wrap=4, days=['월', '화', '수', '목', '금'], sec='{}교시',
               courses=[(1, 1, 2, '자료구조', '강의실 301'), (3, 3, 2, '영어회화', '교실 12'), (5, 5, 2, '물리학', '강의실 101')]),
}[v]
W, H, left, top, colw, rowh, fs = V['W'], V['H'], V['left'], V['top'], V['colw'], V['rowh'], V['fs']
img = Image.new('RGB', (W, H), (250, 250, 250))
d = ImageDraw.Draw(img)
for i, t in enumerate(V['days']):
    x = left + i * colw + colw // 2
    d.text((x, top - 70 * fs // 30), t, font=f(fs + 2), fill=(60, 60, 60), anchor='mm')
    d.text((x, top - 30 * fs // 30), f'11/{16 + i}', font=f(fs * 2 // 3), fill=(140, 140, 140), anchor='mm')
for n in range(1, 13):
    y = top + (n - 1) * rowh + rowh // 2
    d.text((left // 2, y), V['sec'].format(n), font=f(fs), fill=(90, 90, 90), anchor='mm')
colors = [(255, 214, 196), (198, 230, 206), (210, 220, 245), (250, 232, 180), (230, 210, 240), (200, 235, 235)]
for k, (day, s, n, name, room) in enumerate(V['courses']):
    x0 = left + (day - 1) * colw + 4; y0 = top + (s - 1) * rowh + 4
    d.rounded_rectangle([x0, y0, x0 + colw - 8, y0 + n * rowh - 8], 14, fill=colors[k % len(colors)])
    w = V['wrap']
    lines = name.split(' ') if w == 0 else [name[i:i + w] for i in range(0, len(name), w)]
    yy = y0 + 12
    for ln in lines:
        d.text((x0 + 8, yy), ln, font=f(fs), fill=(40, 40, 40)); yy += fs * 4 // 3
    d.text((x0 + 8, yy + 6), room, font=f(fs * 3 // 4), fill=(80, 80, 80))
img.save(sys.argv[1])
print('saved', sys.argv[1], v)
