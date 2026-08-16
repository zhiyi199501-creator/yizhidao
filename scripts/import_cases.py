#!/usr/bin/env python3
"""读回用户编辑后的「案例编辑表.xlsx」，生成新 cases.json

- 用 zipfile 直接解 XML 读（绕开 WPS 保存后 openpyxl 的样式兼容问题）
- 根据「卦名」重新关联卦号 number
- 支持：改内容、改卦名/爻位、末尾加行（编号留空=新增）、删行=删除案例
"""
import zipfile, re, json
import xml.etree.ElementTree as ET

XLSX = "案例编辑表.xlsx"
HEXES = "Yizhidao/Resources/Hexagrams.json"
OUT = "cases.json"

NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
M = {"m": NS}

# 1. 读共享字符串
zf = zipfile.ZipFile(XLSX)
shared = []
if "xl/sharedStrings.xml" in zf.namelist():
    root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
    for si in root.iter(f"{{{NS}}}si"):
        txt = "".join(t.text or "" for t in si.iter(f"{{{NS}}}t"))
        shared.append(txt)

# 2. 找到名为「案例」的 sheet 文件
sheet_file = None
if "xl/workbook.xml" in zf.namelist():
    wb = ET.fromstring(zf.read("xl/workbook.xml"))
    for s in wb.iter(f"{{{NS}}}sheet"):
        if s.get("name") == "案例":
            rId = s.get(f"{{{NS}}}id")  # 实际是 r:id，在 relationships 里
            # 解析 rels
            rels = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
            rid_map = {r.get("Id"): r.get("Target") for r in rels.iter() if r.get("Id")}
            target = rid_map.get(rId, "")
            if not target.startswith("xl/"):
                target = "xl/" + target.lstrip("/")
            sheet_file = target
            break
if sheet_file is None:
    # 兜底：第一个 worksheet
    for n in zf.namelist():
        if re.match(r"xl/worksheets/sheet\d+\.xml", n):
            sheet_file = n
            break

# 3. 解析单元格
def cell_value(c):
    v = c.find("m:v", M)
    if v is None or v.text is None:
        return ""
    if c.get("t") == "s":
        return shared[int(v.text)] if int(v.text) < len(shared) else ""
    return v.text

rows = []
root = ET.fromstring(zf.read(sheet_file))
for row in root.iter(f"{{{NS}}}row"):
    cells = {}
    for c in row.iter(f"{{{NS}}}c"):
        ref = c.get("r") or ""
        m = re.match(r"([A-Z]+)(\d+)", ref)
        if not m:
            continue
        col = m.group(1)
        cells[col] = cell_value(c)
    if not cells:
        continue
    # 取 A-H 列
    def get(col):
        return cells.get(col, "").strip()
    rows.append([get("A"), get("B"), get("C"), get("D"), get("E"), get("F"), get("G"), get("H")])

# 4. 表头校验
if not rows or rows[0][0] != "编号":
    raise SystemExit("找不到表头「编号」，请确认表格结构未被改动")
rows = rows[1:]

# 5. 卦名 -> 卦号 映射
hexes = json.load(open(HEXES, encoding="utf-8"))
name2num = {h["name"]: h["number"] for h in hexes}
name2num.update({h["name"] + "卦": h["number"] for h in hexes})

def resolve_number(hexagram):
    g = hexagram.strip()
    if g in name2num:
        return name2num[g]
    g2 = g.rstrip("卦")
    if g2 in name2num:
        return name2num[g2]
    return None

# 6. 组装案例
cases = []
seen = set()
problems = []
auto_id = 1
for row in rows:
    file, hexagram, position, background, question, casting, explanation, verification = row
    # 跳过完全空行
    if not any(row):
        continue
    # 新增案例（编号留空）→ 自动编号
    if not file:
        file = f"自定义{auto_id}"
        auto_id += 1
    if file in seen:
        problems.append(f"编号重复：{file}")
        continue
    seen.add(file)

    number = resolve_number(hexagram)
    if number is None:
        problems.append(f"卦名无法识别：{hexagram}（{file}）")
        number = 0

    cases.append({
        "file": file,
        "hexagram": hexagram.strip(),
        "position": position.strip() or "卦辞",
        "background": background,
        "question": question,
        "casting": casting,
        "explanation": explanation,
        "verification": verification,
        "number": number,
    })

# 7. 排序（保持稳定）
cases.sort(key=lambda c: c["file"])

json.dump(cases, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"已写回 {OUT}，共 {len(cases)} 条案例")
if problems:
    print("注意：")
    for p in problems:
        print(f"  - {p}")
