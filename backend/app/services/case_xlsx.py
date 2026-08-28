"""Parse 案例编辑表.xlsx into the same dicts as scripts/import_cases.py."""

from __future__ import annotations

import io
import re
import zipfile
import xml.etree.ElementTree as ET
from typing import Any, Dict, List, Tuple

from app.errors import AppError
from app.services.hexagram_store import hexagram_name_to_number

NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
M = {"m": NS}


def _cell_value(cell, shared: list[str]) -> str:
    if cell.get("t") == "inlineStr":
        return "".join(t.text or "" for t in cell.iter(f"{{{NS}}}t"))
    is_el = cell.find("m:is", M)
    if is_el is not None:
        return "".join(t.text or "" for t in is_el.iter(f"{{{NS}}}t"))
    value = cell.find("m:v", M)
    if value is None or value.text is None:
        return ""
    if cell.get("t") == "s":
        index = int(value.text)
        return shared[index] if index < len(shared) else ""
    return value.text


def parse_cases_xlsx(raw: bytes) -> Tuple[List[Dict[str, Any]], List[str]]:
    try:
        archive = zipfile.ZipFile(io.BytesIO(raw))
    except zipfile.BadZipFile as exc:
        raise AppError("不是有效的 xlsx 文件", code=4001, status_code=400) from exc

    shared: list[str] = []
    if "xl/sharedStrings.xml" in archive.namelist():
        root = ET.fromstring(archive.read("xl/sharedStrings.xml"))
        for si in root.iter(f"{{{NS}}}si"):
            shared.append("".join(t.text or "" for t in si.iter(f"{{{NS}}}t")))

    sheet_file = None
    if "xl/workbook.xml" in archive.namelist():
        workbook = ET.fromstring(archive.read("xl/workbook.xml"))
        rels = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        rid_map = {node.get("Id"): node.get("Target") for node in rels.iter() if node.get("Id")}
        for sheet in workbook.iter(f"{{{NS}}}sheet"):
            if sheet.get("name") != "案例":
                continue
            r_id = sheet.get(f"{{{REL_NS}}}id") or sheet.get("r:id") or sheet.get("id")
            target = rid_map.get(r_id or "", "")
            if not target:
                break
            target = target.lstrip("/")
            if not target.startswith("xl/"):
                target = "xl/" + target
            sheet_file = target
            break
    if sheet_file is None:
        names = sorted(n for n in archive.namelist() if re.match(r"xl/worksheets/sheet\d+\.xml", n))
        sheet_file = names[1] if len(names) >= 2 else (names[0] if names else None)
    if not sheet_file:
        raise AppError("找不到「案例」工作表", code=4001, status_code=400)

    rows: list[list[str]] = []
    root = ET.fromstring(archive.read(sheet_file))
    for row in root.iter(f"{{{NS}}}row"):
        cells: dict[str, str] = {}
        for cell in row.iter(f"{{{NS}}}c"):
            ref = cell.get("r") or ""
            match = re.match(r"([A-Z]+)(\d+)", ref)
            if not match:
                continue
            cells[match.group(1)] = _cell_value(cell, shared).strip()
        if cells:
            rows.append([cells.get(col, "") for col in ("A", "B", "C", "D", "E", "F", "G", "H")])

    if not rows or rows[0][0] != "编号":
        raise AppError("找不到表头「编号」", code=4001, status_code=400)

    items: List[Dict[str, Any]] = []
    problems: List[str] = []
    seen: set[str] = set()
    auto_id = 1
    for row in rows[1:]:
        if not any(row):
            continue
        file, hexagram, position, background, question, casting, explanation, verification = row
        if not file:
            file = f"自定义{auto_id}"
            auto_id += 1
        if file in seen:
            problems.append(f"编号重复：{file}")
            continue
        seen.add(file)
        number = hexagram_name_to_number(hexagram) or 0
        if number == 0 and hexagram.strip():
            problems.append(f"卦名无法识别：{hexagram}（{file}）")
        items.append(
            {
                "file": file,
                "hexagram": hexagram.strip(),
                "position": position.strip() or "卦辞",
                "background": background,
                "question": question,
                "casting": casting,
                "explanation": explanation,
                "verification": verification,
                "number": number,
            }
        )
    items.sort(key=lambda item: (item.get("number") or 99, item["file"]))
    return items, problems
