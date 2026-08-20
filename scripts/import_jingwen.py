#!/usr/bin/env python3
"""读回用户编辑后的「易经正文编辑表.xlsx」，写回 Hexagrams.json

- 用 zipfile 直接解 XML 读（绕开 WPS 保存后 openpyxl 的样式兼容问题）
- 按卦号更新；爻辞按初→上；文言 / 四传按段序排序
- 不改 symbol、binary 等表里没有的字段
"""
import zipfile, re, json
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

XLSX = Path(__file__).resolve().parents[1] / "易经正文编辑表.xlsx"
HEXES = Path(__file__).resolve().parents[1] / "ios/Yizhidao/Resources/Hexagrams.json"

NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
M = {"m": NS}

YAO_LABELS = ["初爻", "二爻", "三爻", "四爻", "五爻", "上爻"]
YAO_INDEX = {name: i for i, name in enumerate(YAO_LABELS)}
WING_ORDER = ["系辞传", "说卦传", "序卦传", "杂卦传"]

zf = zipfile.ZipFile(XLSX)
shared = []
if "xl/sharedStrings.xml" in zf.namelist():
    root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
    for si in root.iter(f"{{{NS}}}si"):
        txt = "".join(t.text or "" for t in si.iter(f"{{{NS}}}t"))
        shared.append(txt)

wb = ET.fromstring(zf.read("xl/workbook.xml"))
rels = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
rid_map = {r.get("Id"): r.get("Target") for r in rels.iter() if r.get("Id")}
sheet_by_name = {}
for s in wb.iter(f"{{{NS}}}sheet"):
    name = s.get("name") or ""
    rId = s.get(f"{{{REL_NS}}}id") or s.get("r:id") or s.get("id")
    target = rid_map.get(rId or "", "")
    if not target:
        continue
    target = target.lstrip("/")
    if not target.startswith("xl/"):
        target = "xl/" + target
    sheet_by_name[name] = target


def cell_value(c):
    if c.get("t") == "inlineStr":
        return "".join(t.text or "" for t in c.iter(f"{{{NS}}}t"))
    is_el = c.find("m:is", M)
    if is_el is not None:
        return "".join(t.text or "" for t in is_el.iter(f"{{{NS}}}t"))
    v = c.find("m:v", M)
    if v is None or v.text is None:
        return ""
    if c.get("t") == "s":
        return shared[int(v.text)] if int(v.text) < len(shared) else ""
    return v.text


def col_index(col):
    n = 0
    for ch in col:
        n = n * 26 + (ord(ch) - 64)
    return n


def read_sheet(name):
    path = sheet_by_name.get(name)
    if not path:
        raise SystemExit(f"找不到工作表「{name}」")
    rows = []
    root = ET.fromstring(zf.read(path))
    for row in root.iter(f"{{{NS}}}row"):
        cells = {}
        for c in row.iter(f"{{{NS}}}c"):
            ref = c.get("r") or ""
            m = re.match(r"([A-Z]+)(\d+)", ref)
            if not m:
                continue
            cells[m.group(1)] = cell_value(c)
        if not cells:
            continue
        max_i = max(col_index(k) for k in cells)
        vals = []
        col = ""
        for i in range(1, max_i + 1):
            n = i
            letters = []
            while n:
                n, r = divmod(n - 1, 26)
                letters.append(chr(65 + r))
            col = "".join(reversed(letters))
            vals.append((cells.get(col, "") or "").strip())
        if any(vals):
            rows.append(vals)
    if not rows:
        raise SystemExit(f"工作表「{name}」是空的")
    header = rows[0]
    body = []
    for row in rows[1:]:
        padded = row + [""] * (len(header) - len(row))
        body.append({header[i]: padded[i] for i in range(len(header))})
    return body


def parse_int(text, label):
    raw = str(text).strip()
    if not raw:
        return None
    if raw.endswith(".0"):
        raw = raw[:-2]
    try:
        return int(raw)
    except ValueError:
        problems.append(f"{label}不是数字：{text}")
        return None


problems = []
gua_rows = read_sheet("六十四卦")
yao_rows = read_sheet("爻辞")
wenyan_rows = read_sheet("文言")
wing_rows = read_sheet("四传")

book = json.loads(HEXES.read_text(encoding="utf-8"))
if not isinstance(book, dict) or "hexagrams" not in book:
    raise SystemExit("Hexagrams.json 格式不对，需要 {hexagrams, wings}")

by_number = {h["number"]: h for h in book["hexagrams"]}
name2num = {h["name"]: h["number"] for h in book["hexagrams"]}
name2num.update({h["name"] + "卦": h["number"] for h in book["hexagrams"]})


def resolve_number(row, sheet):
    n = parse_int(row.get("卦号", ""), f"{sheet}卦号")
    if n:
        return n
    name = (row.get("卦名") or "").strip()
    if name in name2num:
        return name2num[name]
    g2 = name.rstrip("卦")
    if g2 in name2num:
        return name2num[g2]
    problems.append(f"{sheet}无法识别卦：卦号={row.get('卦号')} 卦名={name}")
    return None


seen_gua = set()
for row in gua_rows:
    n = resolve_number(row, "六十四卦")
    if n is None:
        continue
    if n in seen_gua:
        problems.append(f"六十四卦卦号重复：{n}")
        continue
    seen_gua.add(n)
    h = by_number.get(n)
    if h is None:
        problems.append(f"六十四卦没有第{n}卦，已跳过")
        continue
    if row.get("卦名"):
        h["name"] = row["卦名"]
    if row.get("上下经"):
        h["part"] = row["上下经"]
    if row.get("目录标题"):
        h["title"] = row["目录标题"]
    h["figure"] = row.get("卦象题", "")
    h["guaci"] = row.get("卦辞", "")
    h["tuanci"] = row.get("彖", "")
    h["daxiang"] = row.get("大象", "")
    ci = row.get("用辞", "")
    xiang = row.get("用象", "")
    h["yong"] = {"ci": ci, "xiang": xiang} if ci or xiang else None

if len(seen_gua) != 64:
    problems.append(f"六十四卦表写回 {len(seen_gua)} 卦，期望 64")

yao_map = defaultdict(dict)
for row in yao_rows:
    n = resolve_number(row, "爻辞")
    if n is None:
        continue
    pos = row.get("爻位", "")
    idx = YAO_INDEX.get(pos)
    if idx is None:
        problems.append(f"爻辞爻位无法识别：{pos}（卦{n}）")
        continue
    if idx in yao_map[n]:
        problems.append(f"爻辞重复：卦{n} {pos}")
    yao_map[n][idx] = (row.get("爻辞", ""), row.get("小象", ""))

for n, slots in yao_map.items():
    h = by_number.get(n)
    if h is None:
        problems.append(f"爻辞没有第{n}卦，已跳过")
        continue
    yaoci, xiaoxiang = [], []
    for i, label in enumerate(YAO_LABELS):
        if i not in slots:
            problems.append(f"爻辞缺 {label}（卦{n}）")
            yaoci.append(h.get("yaoci", [""] * 6)[i] if i < len(h.get("yaoci") or []) else "")
            xiaoxiang.append(h.get("xiaoxiang", [""] * 6)[i] if i < len(h.get("xiaoxiang") or []) else "")
        else:
            yaoci.append(slots[i][0])
            xiaoxiang.append(slots[i][1])
    h["yaoci"] = yaoci
    h["xiaoxiang"] = xiaoxiang

wenyan_map = defaultdict(list)
for row in wenyan_rows:
    n = resolve_number(row, "文言")
    if n is None:
        continue
    seq = parse_int(row.get("段序", ""), f"文言段序（卦{n}）") or 10**9
    text = row.get("正文", "")
    if not text:
        continue
    wenyan_map[n].append((seq, text))

for h in book["hexagrams"]:
    items = sorted(wenyan_map.get(h["number"], []), key=lambda x: x[0])
    h["wenyan"] = [t for _, t in items]

id_by_title = {w.get("title"): w.get("id", "") for w in book.get("wings") or []}
grouped = defaultdict(lambda: defaultdict(list))
for row in wing_rows:
    wing = row.get("传", "")
    chap = row.get("章", "")
    if not wing:
        problems.append("四传缺「传」名，已跳过一行")
        continue
    seq = parse_int(row.get("段序", ""), f"四传段序（{wing}/{chap}）") or 10**9
    text = row.get("正文", "")
    if not text:
        continue
    grouped[wing][chap].append((seq, text))

wings = []
seen_wings = set()
for title in WING_ORDER + [t for t in grouped if t not in WING_ORDER]:
    if title not in grouped:
        continue
    seen_wings.add(title)
    chapters = []
    for chap_title, paras in grouped[title].items():
        paras = sorted(paras, key=lambda x: x[0])
        chapters.append({"title": chap_title, "paragraphs": [t for _, t in paras]})
    wings.append({
        "id": id_by_title.get(title) or re.sub(r"传$", "", title),
        "title": title,
        "chapters": chapters,
    })
for title in grouped:
    if title not in seen_wings:
        problems.append(f"四传出现未知传名：{title}")
if len(wings) != 4:
    problems.append(f"四传写回 {len(wings)} 篇，期望 4")
book["wings"] = wings
book["hexagrams"] = sorted(book["hexagrams"], key=lambda h: h["number"])

HEXES.write_text(json.dumps(book, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
print(f"已写回 {HEXES}，{len(book['hexagrams'])} 卦，四传 {len(wings)} 篇")
if problems:
    print("注意：")
    for p in problems:
        print(f"  - {p}")
