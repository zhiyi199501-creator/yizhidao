#!/usr/bin/env python3
"""从《易经证释(全册).doc》抽出简体阅读稿，写入 ios/Yizhidao/Resources/Zhengshi.json。"""
from __future__ import annotations

import json
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "ios" / "Yizhidao" / "Resources" / "Zhengshi.json"
HEX_PATH = ROOT / "ios" / "Yizhidao" / "Resources" / "Hexagrams.json"

HEADER_RE = re.compile(
    r"^(?:[上下]经[-~0-9]|易经证释第.+册完|上经-\d|下经-\d)"
)
VOLUME_RE = re.compile(r"^第[一二三四五六七八九十百零〇0-9]+部\s*[上下]经第.+册$")
PAGE_ONLY_RE = re.compile(r"^\d+$")
CORR_RE = re.compile(r"[【『\[]已校正[^】』\]]*[】』\]]")
TRAIL_PAGE_RE = re.compile(r"(?:p\d+|[Pp]\d+|\s+\d+)$")
CTRL_RE = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")

FRONT_TITLES = [
    "列圣名称",
    "图表细目",
    "例言",
    "序例",
    "序",
    "全易大旨及习易要例",
    "图象",
    "河图讲义",
    "洛书讲义",
    "河洛大旨",
    "太极图讲义",
    "图象 伏羲八卦讲义",
    "图象 伏羲六十四卦讲义",
    "图象 伏羲六十四卦讲述",
    "图象 文王八卦讲义",
    "图象 文王六十四卦讲义",
    "图象 文王六十四卦疏述",
    "先后天各易象数及图表大旨",
]

WING_TITLES = ["系辞传", "说卦传", "序卦传", "杂卦传"]

SECTION_TITLES = [
    "总释象例",
    "申释象辞",
    "释彖辞",
    "释象辞",
    "释爻辞",
    "释文言",
    "释彖",
    "彖辞",
    "文言",
    "讲述乾坤大旨",
    "讲述",
    "疏述",
]


def _ensure_imports():
    extra = ROOT / ".firecrawl" / "py"
    if extra.exists():
        sys.path.insert(0, str(extra))
    import olefile  # noqa: F401
    import zhconv  # noqa: F401


def to_simp(text: str) -> str:
    import zhconv

    # 乾、遯若走通用简繁表会收成「干」「遁」，易经卦名不能换。
    protected = text.replace("乾", "\uE000").replace("遯", "\uE001")
    return zhconv.convert(protected, "zh-cn").replace("\uE000", "乾").replace("\uE001", "遯")


def extract_paragraphs(doc_path: Path) -> list[str]:
    import olefile

    ole = olefile.OleFileIO(str(doc_path))
    wd = ole.openstream("WordDocument").read()
    ident = struct.unpack_from("<H", wd, 0)[0]
    if ident != 0xA5EC:
        raise SystemExit(f"not a Word 97 document: ident={ident:#x}")
    fc_min = struct.unpack_from("<I", wd, 0x18)[0]
    fc_mac = struct.unpack_from("<I", wd, 0x1C)[0]
    raw = wd[fc_min:fc_mac]
    if len(raw) % 2:
        raw = raw[:-1]
    text = raw.decode("utf-16le", errors="ignore")

    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "\x13":
            i += 1
            while i < n and text[i] not in "\x14\x15":
                i += 1
            if i < n and text[i] == "\x14":
                i += 1
            continue
        if ch == "\x15":
            i += 1
            continue
        out.append(ch)
        i += 1
    clean = "".join(out).replace("\x0b", "\n").replace("\x0c", "\n").replace("\x07", " ")

    paras: list[str] = []
    for part in re.split(r"[\r]+", clean):
        part = CTRL_RE.sub("", part)
        part = re.sub(r"[ \t]+", " ", part).strip()
        if not part:
            continue
        part = to_simp(part)
        paras.append(part)
    return paras


def tidy_title(raw: str) -> str:
    t = CORR_RE.sub("", raw)
    t = TRAIL_PAGE_RE.sub("", t)
    t = t.replace("***", "")
    t = re.sub(r"[ \t]+", " ", t).strip(" ·•、")
    return t


def is_noise(p: str) -> bool:
    if not p or PAGE_ONLY_RE.match(p):
        return True
    if HEADER_RE.match(p) or VOLUME_RE.match(p):
        return True
    if p.startswith("目录"):
        return True
    return False


def load_hexagrams() -> list[dict]:
    data = json.loads(HEX_PATH.read_text(encoding="utf-8"))
    return data["hexagrams"]


def hex_heading_re(names: list[str]) -> re.Pattern:
    alt = "|".join(sorted((re.escape(n) for n in names), key=len, reverse=True))
    gua_mark = re.compile(rf"^({alt})卦(?:\s|$)")
    gua_trigram = re.compile(
        rf"^({alt})\s+[乾坤震巽坎离艮兑][上下]"
    )
    return gua_mark, gua_trigram


def section_heading(p: str) -> str | None:
    t = tidy_title(p)
    if len(t) > 24:
        return None
    if t.endswith("卦总义") or t.endswith("卦總義"):
        return t
    if t.startswith("系辞讲义") or t.startswith("系辞講義"):
        return t.replace("講義", "讲义")
    for title in SECTION_TITLES:
        if t == title or t.startswith(title):
            return title if t.startswith(title) and len(t) <= len(title) + 8 else t
    return None


def front_heading(p: str) -> str | None:
    t = tidy_title(p)
    if len(t) > 28:
        return None
    for title in sorted(FRONT_TITLES, key=len, reverse=True):
        if t == title or t.startswith(title + " "):
            return title if t == title else t
    if t.startswith("图象"):
        return t
    return None


def wing_heading(p: str) -> str | None:
    t = tidy_title(p)
    for title in WING_TITLES:
        if t == title or t.startswith(title):
            return title
    if t == "系辞传经文":
        return "系辞传"
    return None


def slug(s: str) -> str:
    return re.sub(r"[^0-9a-zA-Z\u4e00-\u9fff]+", "-", s).strip("-") or "x"


def flush_section(sections: list[dict], title: str, paras: list[str], prefix: str):
    kept = [p for p in paras if not is_noise(p)]
    if not kept:
        return
    sections.append({
        "id": f"{prefix}-{len(sections)+1:02d}-{slug(title)[:24]}",
        "title": title,
        "paragraphs": kept,
    })


def main() -> None:
    _ensure_imports()
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
        "/Users/luozhihao/Library/Mobile Documents/com~apple~CloudDocs/经典/易经证释(全册).doc"
    )
    if not src.exists():
        raise SystemExit(f"missing source: {src}")

    hexagrams = load_hexagrams()
    by_name = {h["name"]: h for h in hexagrams}
    names = [h["name"] for h in hexagrams]
    gua_mark, gua_trigram = hex_heading_re(names)

    paras = extract_paragraphs(src)
    start = next((i for i, p in enumerate(paras) if tidy_title(p) == "列圣名称"), None)
    if start is None:
        raise SystemExit("could not find body start 列圣名称")
    body = paras[start:]

    parts = {
        "front": {"id": "front", "title": "卷首", "chapters": []},
        "upper": {"id": "upper", "title": "上经", "chapters": []},
        "lower": {"id": "lower", "title": "下经", "chapters": []},
        "wings": {"id": "wings", "title": "十翼", "chapters": []},
    }

    mode = "front"
    chapter: dict | None = None
    section_title = ""
    section_paras: list[str] = []
    seen_gua: set[str] = set()

    def close_section():
        nonlocal section_title, section_paras
        if chapter is not None:
            flush_section(chapter["sections"], section_title or chapter["title"], section_paras, chapter["id"])
        section_title = ""
        section_paras = []

    def open_chapter(part_id: str, ch: dict):
        nonlocal chapter, mode, section_title, section_paras
        close_section()
        if chapter is not None and chapter["sections"]:
            parts[mode]["chapters"].append(chapter)
        mode = part_id
        chapter = ch
        section_title = ""
        section_paras = []

    for p in body:
        raw = p
        title = tidy_title(p)
        if is_noise(title) or is_noise(raw):
            continue

        gua = gua_mark.match(title) or gua_trigram.match(title)
        if gua and len(title) <= 24:
            name = gua.group(1)
            if name not in seen_gua:
                meta = by_name[name]
                part_id = "upper" if meta["number"] <= 30 else "lower"
                open_chapter(part_id, {
                    "id": f"{meta['number']:02d}-{name}",
                    "title": f"{name}卦",
                    "subtitle": meta.get("figure") or meta.get("title") or "",
                    "number": meta["number"],
                    "symbol": meta["symbol"],
                    "sections": [],
                })
                seen_gua.add(name)
            continue

        wing = wing_heading(title)
        if wing and len(title) <= 16:
            if chapter is None or chapter.get("title") != wing:
                open_chapter("wings", {
                    "id": f"wing-{slug(wing)}",
                    "title": wing,
                    "subtitle": "",
                    "number": None,
                    "symbol": "",
                    "sections": [],
                })
            else:
                close_section()
            if title not in (wing,):
                section_title = title
            continue

        if mode == "front":
            front = front_heading(title)
            if front and len(title) <= 28:
                if chapter is None or chapter["title"] != front:
                    open_chapter("front", {
                        "id": f"front-{slug(front)}",
                        "title": front,
                        "subtitle": "",
                        "number": None,
                        "symbol": "",
                        "sections": [],
                    })
                continue

        sec = section_heading(title)
        if sec and chapter is not None and mode != "front":
            close_section()
            section_title = sec
            continue

        if chapter is None:
            open_chapter("front", {
                "id": "front-start",
                "title": "卷首",
                "subtitle": "",
                "number": None,
                "symbol": "",
                "sections": [],
            })
        section_paras.append(raw)

    close_section()
    if chapter is not None and chapter["sections"]:
        parts[mode]["chapters"].append(chapter)

    missing = [n for n in names if n not in seen_gua]
    if missing:
        raise SystemExit(f"missing hexagrams: {missing}")

    book = {
        "source": "《易经证释》全册",
        "note": "据《易经证释》全册整理为简体阅读稿；页眉、目录与校对标记已去掉。解卦用的卦爻辞仍以结果页底稿为准。",
        "parts": [parts["front"], parts["upper"], parts["lower"], parts["wings"]],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(book, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("wrote", OUT, "bytes", OUT.stat().st_size)
    for part in book["parts"]:
        print(part["title"], "chapters", len(part["chapters"]))
        for ch in part["chapters"][:8]:
            npar = sum(len(s["paragraphs"]) for s in ch["sections"])
            print(" ", ch["title"], "sections", len(ch["sections"]), "paras", npar)
        if len(part["chapters"]) > 8:
            print("  ...")


if __name__ == "__main__":
    main()
