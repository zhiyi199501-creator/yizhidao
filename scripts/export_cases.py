#!/usr/bin/env python3
"""把 cases.json 导出成可编辑的 Excel 表格（案例编辑表.xlsx）"""
import json
import openpyxl
from openpyxl.styles import Font, Alignment, PatternFill
from openpyxl.utils import get_column_letter

CASES = "cases.json"
OUT = "案例编辑表.xlsx"

cases = json.load(open(CASES, encoding="utf-8"))

headers = ["编号", "卦名", "爻位", "背景", "所问何事", "起卦结果", "讲师解读", "验证结果"]
fields = ["file", "hexagram", "position", "background", "question", "casting", "explanation", "verification"]

wb = openpyxl.Workbook()

# —— 说明 sheet ——
ws_info = wb.active
ws_info.title = "说明"
info_lines = [
    ["《张庆祥讲易经案例》编辑说明"],
    [""],
    ["1. 在「案例」表里直接改内容即可，改完保存后告诉 AI，AI 会同步回 App。"],
    ["2. 「编号」列是内部标识（灰色底），请勿修改；改了会被当作新案例。"],
    ["3. 「卦名」可改（写「屯卦」或「屯」都行），保存后会自动重新关联对应卦号。"],
    ["4. 想新增案例：在表格末尾加一行，「编号」留空即可。"],
    ["5. 想删除案例：把整行删掉即可。"],
    ["6. 「爻位」填：初爻 / 二爻 / 三爻 / 四爻 / 五爻 / 上爻 / 卦辞。"],
]
for line in info_lines:
    ws_info.append(line)
ws_info.column_dimensions["A"].width = 90

# —— 数据 sheet ——
ws = wb.create_sheet("案例")
ws.append(headers)
for c in cases:
    ws.append([c[f] for f in fields])

# 表头样式
for cell in ws[1]:
    cell.font = Font(bold=True)
    cell.alignment = Alignment(vertical="center", horizontal="left")

# 编号列灰色填充（提示勿改）
id_fill = PatternFill(start_color="F2F2F2", end_color="F2F2F2", fill_type="solid")
for row in ws.iter_rows(min_row=2, min_col=1, max_col=1):
    for cell in row:
        cell.fill = id_fill

# 列宽
widths = [32, 12, 10, 50, 40, 30, 60, 40]
for i, w in enumerate(widths, start=1):
    ws.column_dimensions[get_column_letter(i)].width = w

# 内容单元格自动换行 + 顶端对齐
for row in ws.iter_rows(min_row=2):
    for cell in row:
        cell.alignment = Alignment(wrap_text=True, vertical="top")

# 冻结首行
ws.freeze_panes = "A2"

wb.save(OUT)
print(f"已导出 {len(cases)} 条案例 -> {OUT}")
