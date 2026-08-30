"""IMA 讲解清洗：与 iOS / Android ImaAnswerFormatter 对齐，供提示词使用。"""

from __future__ import annotations

from typing import List, Union

TextBlock = str
TableBlock = List[List[str]]
Block = Union[TextBlock, TableBlock]


def clean_catalog_answers(entries: dict) -> int:
    """就地清洗 entries[*].answer，返回改动条数。"""
    changed = 0
    for entry in entries.values():
        if not isinstance(entry, dict):
            continue
        old = str(entry.get("answer") or "")
        new = stripped(old)
        if new != old:
            entry["answer"] = new
            changed += 1
    return changed


def stripped(text: str) -> str:
    """去掉整行「思考过程」和出处脚注数字。"""
    without_thinking = "\n".join(
        line for line in text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        if line.strip() != "思考过程"
    ).strip()
    chars = list(without_thinking)
    n = len(chars)
    i = 0
    body_start = 0
    result: List[str] = []

    def flush(end: int) -> None:
        nonlocal body_start
        if end > body_start:
            result.extend(chars[body_start:end])
        body_start = end

    def is_digit(ch: str) -> bool:
        return "0" <= ch <= "9"

    def is_sentence_punct(ch: str) -> bool:
        return ch in "。！？"

    while i < n:
        ch = chars[i]
        if "1" <= ch <= "9":
            j = i
            while j < n and j - i < 5 and is_digit(chars[j]):
                j += 1
            more_digits = j < n and is_digit(chars[j])
            list_marker = j < n and chars[j] in (".", "、", "．")
            if not more_digits and not list_marker:
                k = j
                while k < n and chars[k] in (" ", "\t"):
                    k += 1
                at_end = k == n or chars[k] == "\n"
                before_punct = j < n and is_sentence_punct(chars[j])
                if at_end or before_punct:
                    p = i - 1
                    space_start = i
                    while p >= 0 and chars[p] in (" ", "\t"):
                        space_start = p
                        p -= 1
                    not_line_start = p >= 0 and chars[p] != "\n"
                    if not_line_start:
                        after_punct = is_sentence_punct(chars[p])
                        after_word = not is_digit(chars[p])
                        if after_punct or after_word:
                            flush(space_start)
                            body_start = j
                            i = j
                            continue
        i += 1
    flush(n)
    return "".join(result)


def _is_pipe_row(line: str) -> bool:
    trimmed = line.strip()
    return trimmed.startswith("|") and trimmed.endswith("|") and trimmed.count("|") >= 2


def _is_markdown_separator(line: str) -> bool:
    trimmed = line.strip()
    if "-" not in trimmed:
        return False
    return all(ch in "|-: " for ch in trimmed)


def _parse_pipe_row(line: str) -> List[str]:
    trimmed = line.strip()
    if trimmed.startswith("|"):
        trimmed = trimmed[1:]
    if trimmed.endswith("|"):
        trimmed = trimmed[:-1]
    return [cell.strip() for cell in trimmed.split("|")]


def _padded(rows: List[List[str]]) -> List[List[str]]:
    width = max((len(row) for row in rows), default=0)
    if width == 0:
        return rows
    out: List[List[str]] = []
    for row in rows:
        if len(row) >= width:
            out.append(row[:width])
        else:
            out.append(row + [""] * (width - len(row)))
    return out


def blocks(raw: str) -> List[Block]:
    lines = raw.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    result: List[Block] = []
    buffer: List[str] = []
    i = 0

    def flush() -> None:
        text = stripped("\n".join(buffer).strip())
        if text:
            result.append(text)
        buffer.clear()

    while i < len(lines):
        line = lines[i]
        if line.strip() == "表格":
            rows: List[List[str]] = []
            j = i + 1
            while j < len(lines) and "\t" in lines[j]:
                rows.append([stripped(cell.strip()) for cell in lines[j].split("\t")])
                j += 1
            if rows:
                flush()
                result.append(_padded(rows))
                i = j
                continue
        if _is_pipe_row(line) and i + 1 < len(lines) and _is_markdown_separator(lines[i + 1]):
            rows = [[stripped(cell) for cell in _parse_pipe_row(line)]]
            j = i + 2
            while j < len(lines) and _is_pipe_row(lines[j]):
                if _is_markdown_separator(lines[j]):
                    j += 1
                    continue
                rows.append([stripped(cell) for cell in _parse_pipe_row(lines[j])])
                j += 1
            if len(rows) >= 2:
                flush()
                result.append(_padded(rows))
                i = j
                continue
        buffer.append(line)
        i += 1
    flush()
    return result


def prompt_text(raw: str) -> str:
    parts: List[str] = []
    for block in blocks(raw):
        if isinstance(block, list):
            parts.append("\n".join(" | ".join(row) for row in block))
        elif block.strip():
            parts.append(block)
    return "\n\n".join(parts).strip()
