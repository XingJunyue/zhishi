#!/usr/bin/env python3
"""
生成 app/src/main/assets/schools.tsv：学校 → 教务网址 + 教务类型。

数据来源（都是 MIT，署名见 THIRD_PARTY_NOTICES.md）：
  - ShiGuangSchedule/shiguang_warehouse   index/root_index.yaml + resources/*/adapters.yaml（社区为具体学校写过适配脚本的入口，标 s）
  - baoozak/timetable                     utils/schools.json（学校列表，标 b；类型有不少是猜的，网址能看出类型时以网址为准）

用法：python3 tools/build_schools.py <shiguang_warehouse 目录> <baoozak timetable 目录>
每行：学校名 \t 类型 \t 网址 \t 来源(s/b) \t 入口说明
"""
import json, os, re, sys, yaml

wh, bz = sys.argv[1], sys.argv[2]
out = os.path.join(os.path.dirname(__file__), "..", "app/src/main/assets/schools.tsv")

BZ_TYPE = {"zhengfang_new": "zf", "qiangzhi": "qz", "qiangzhi_old": "qz", "kingosoft": "kingo", "kingosoft_new": "kingo",
           "wisedu": "wisedu", "chaoxing": "cx", "urp_new": "urpnew", "urp": "urp", "south_soft": "nr"}

def by_url(u):
    u = u.lower()
    if "/jwglxt" in u: return "zf"
    if "/jsxsd" in u: return "qz"
    if "/eams" in u or "/for-std/" in u: return "eams"
    if "chaoxing.com" in u or "/admin/login" in u: return "cx"
    if "/jwapp" in u: return "wisedu"
    if "xkaction" in u: return "urp"
    if "jwweb" in u or "/cas/login.action" in u: return "kingo"
    return None

def by_script(js):
    if re.search(r"kbgrid_table|jwglxt|xskbcx", js): return "zf"
    if "sdpkkbList" in js: return "cx"
    if re.search(r"jsxsd|kbcontent", js): return "qz"
    if "jwapp" in js: return "wisedu"
    if re.search(r"TaskActivity|courseTableForStd|/eams", js): return "eams"
    if re.search(r"ajaxStudentSchedule|thisSemesterCurriculum", js): return "urpnew"
    if "xkAction" in js: return "urp"
    if re.search(r"jwweb|KINGO", js, re.I): return "kingo"
    return "generic"

def clean(s): return re.sub(r"[\t\r\n]+", " ", s or "").strip()

def norm_url(u):
    u = clean(u).split()[0] if clean(u) else ""
    if u and not re.match(r"^https?://", u, re.I): u = "http://" + u
    return u

rows = {}
# 1) 拾光适配仓库：有人在真实教务上调过的入口
idx = yaml.safe_load(open(os.path.join(wh, "index/root_index.yaml"), encoding="utf-8"))
for sc in idx["schools"]:
    folder = os.path.join(wh, "resources", sc["resource_folder"])
    name = sc["name"]
    if "通用" in name or sc["id"] in ("NEU",): continue   # 通用工具不算学校；东大有内置入口
    try: ad = yaml.safe_load(open(os.path.join(folder, "adapters.yaml"), encoding="utf-8"))
    except Exception: continue
    for a in (ad or {}).get("adapters", []):
        url = norm_url(a.get("import_url"))
        if not url: continue
        try: js = open(os.path.join(folder, a.get("asset_js_path", "")), encoding="utf-8").read()
        except Exception: js = ""
        t = by_url(url) or by_script(js)
        rows.setdefault(name, []).append((t, url, "s", clean(a.get("adapter_name"))))

# 2) baoozak 列表：拾光里没有的学校才补
for x in json.load(open(os.path.join(bz, "utils/schools.json"), encoding="utf-8")):
    name = clean(x["name"])
    if name in rows or name == "东北大学": continue
    url = norm_url(x["url"])
    t = by_url(url) or BZ_TYPE.get(x.get("type"), "generic")
    rows[name] = [(t, url, "b", "")]

# 3) 手动核对过网址 / 类型的学校（来源 m）：覆盖上面两份列表
MANUAL = {
    # 教务部选课通知（jwb.cumt.edu.cn）给的入口；只能校园网或学校 VPN 客户端访问，用教务系统自己的密码
    "中国矿业大学": [("zf", "http://jwxt.cumt.edu.cn/jwglxt/", "m", "新正方；需校园网或学校 VPN（aTrust）；用教务系统密码")],
}
rows.update(MANUAL)

with open(out, "w", encoding="utf-8") as f:
    f.write("# 学校\t类型\t网址\t来源\t入口  —— 由 tools/build_schools.py 生成；来源 s=ShiGuangSchedule/shiguang_warehouse (MIT)，b=baoozak/timetable (MIT)，m=手动核对\n")
    n = 0
    for name in sorted(rows):
        for t, url, src, note in rows[name]:
            f.write(f"{name}\t{t}\t{url}\t{src}\t{note}\n"); n += 1
print("schools", len(rows), "entries", n)
