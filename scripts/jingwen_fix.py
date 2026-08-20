#!/usr/bin/env python3
"""拼音感知（含南方口音音近归一化）的易经经文校正。

用《易经证释》经文库（Hexagrams.json）做标准答案，把转写稿里被
whisper 识别成同音/音近字的经文片段逐字纠正回标准经文。

用法：
    python jingwen_fix.py <源目录> [输出目录] [报告路径]
输出目录缺省时原地覆盖源目录。报告为 markdown。
"""
import json, re, os, sys, glob
from pathlib import Path
from pypinyin import lazy_pinyin

HEXES = Path(__file__).resolve().parents[1] / "ios/Yizhidao/Resources/Hexagrams.json"
PUNCT = set("，。、；：！？ ！？\"'（）()·—…～~")

def norm_pinyin(p):
    """拼音归一化，把南方口音易混的音归并为一类：
    n/l 不分、平翘舌不分、前后鼻音不分。"""
    if not p:
        return ""
    if p[0] in "nr":
        p = "l" + p[1:]
    if p.startswith("zh"): p = "z" + p[2:]
    elif p.startswith("ch"): p = "c" + p[2:]
    elif p.startswith("sh"): p = "s" + p[2:]
    if p.endswith("eng"): p = p[:-3] + "en"
    elif p.endswith("ing"): p = p[:-3] + "in"
    elif p.endswith("ang"): p = p[:-3] + "an"
    return p

def char_key(c):
    """字符 → 归一化拼音 key；标点/空白返回占位 '\\x00'（不与任何拼音匹配）。"""
    if c in PUNCT or c.isspace():
        return "\x00"
    p = lazy_pinyin(c)
    if not p:
        return c
    return norm_pinyin(p[0])

def load_jingwen(path=HEXES):
    """从经文库提取标准经文短句（去前缀、去标点、去重、≥4字）。"""
    d = json.load(open(path, encoding="utf-8"))
    hexes = d["hexagrams"] if isinstance(d, dict) else d
    items = []
    for h in hexes:
        gua = h.get("guaci", "")
        if "，" in gua:
            gua = gua.split("，", 1)[1]
        items.append(gua)
        for y in h.get("yaoci", []):
            if "，" in y:
                items.append(y.split("，", 1)[1])
        for seg in re.split(r"[。！？]", h.get("tuanci", "")):
            seg = seg.strip()
            if seg:
                items.append(seg)
        items.append(h.get("daxiang", ""))
        for x in h.get("xiaoxiang", []):
            for seg in re.split(r"[。！？]", x):
                seg = seg.strip()
                if seg:
                    items.append(seg)
    seen = set()
    out = []
    for s in items:
        s = re.sub(r"[，。！？、；：\s\"'（）()]", "", s)
        if len(s) >= 4 and s not in seen:
            seen.add(s)
            out.append(s)
    out.sort(key=len, reverse=True)
    return out

def py_lcs(a, b):
    n, m = len(a), len(b)
    if n == 0 or m == 0:
        return 0
    prev = [0] * (m + 1)
    for i in range(1, n + 1):
        cur = [0] * (m + 1)
        ai = a[i - 1]
        for j in range(1, m + 1):
            if ai == b[j - 1]:
                cur[j] = prev[j - 1] + 1
            else:
                cur[j] = prev[j] if prev[j] > cur[j - 1] else cur[j - 1]
        prev = cur
    return prev[m]

def align(jw, jw_py, win, win_py):
    """Needleman-Wunsch 对齐，返回 (替换列表[(win_pos, std_char)], 连续替换run数)。"""
    n, m = len(jw), len(win)
    dp = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        pi = jw_py[i - 1]
        for j in range(1, m + 1):
            if pi == win_py[j - 1]:
                dp[i][j] = dp[i - 1][j - 1] + 1
            else:
                dp[i][j] = dp[i - 1][j] if dp[i - 1][j] > dp[i][j - 1] else dp[i][j - 1]
    i, j = n, m
    repl = []
    while i > 0 and j > 0:
        if jw_py[i - 1] == win_py[j - 1]:
            if win[j - 1] != jw[i - 1]:
                repl.append((j - 1, jw[i - 1]))
            i -= 1
            j -= 1
        elif dp[i - 1][j] >= dp[i][j - 1]:
            i -= 1
        else:
            j -= 1
    run = 0
    if repl:
        pos = sorted({p for p, _ in repl})
        cur = max_run = 1
        for k in range(1, len(pos)):
            cur = cur + 1 if pos[k] == pos[k - 1] + 1 else 1
            if cur > max_run:
                max_run = cur
        run = max_run
    return repl, run

# 人工复核确认的口语撞经文误伤（窗口原文命中则跳过）
BLACKLIST = {
    "，但是实际上也", "是实际上也", "但是实际上也",
    "遇，那就是几", "那就是几", "，于门，就是集", "犹豫就是机",
    "意义上对以", "小叙就是积", "小职务，你就",
    "喻，就是司机", "种意味，就是", "一，不宜上宜", "不宜上宜", "，于门就是集",
    # 本轮发现的误伤：相似爻辞混淆 + 口语撞短经文
    "飞龙在天力见大人", "在天力见大人", "富裕的人家", "就是你一", "女双兼宾制",
}

def dens_threshold(L):
    """短经文容易撞口语，要求更严；长经文连续音近极少误伤，可放宽。"""
    if L <= 5:
        return 0.90
    if L <= 7:
        return 0.85
    return 0.78

def fix_text(text, jw_list, jw_py_list, jw_set_list, jw_len_list):
    chars = list(text)
    n = len(chars)
    text_keys = [char_key(c) for c in chars]
    used = [False] * n
    fixed = []
    for idx, jw in enumerate(jw_list):
        L = jw_len_list[idx]
        jpy = jw_py_list[idx]
        jset = jw_set_list[idx]
        lo = max(2, L - 1)
        hi = L + 4
        i = 0
        while i <= n - lo:
            if used[i]:
                i += 1
                continue
            # 粗筛：窗口音近 key 集合与标准句交集
            wset = set(text_keys[i:i + min(hi, n - i)])
            if len(wset & jset) / max(len(jset), 1) < 0.5:
                i += 1
                continue
            best_dens, best_wlen, best_repl, best_run = 0.0, -1, [], 0
            for wlen in range(lo, hi + 1):
                if i + wlen > n:
                    break
                if any(used[i:i + wlen]):
                    continue
                win_py = text_keys[i:i + wlen]
                lcs = py_lcs(jpy, win_py)
                dens = lcs / L
                if dens > best_dens:
                    win = "".join(chars[i:i + wlen])
                    repl, run = align(jw, jpy, win, win_py)
                    best_dens, best_wlen, best_repl, best_run = dens, wlen, repl, run
            win = "".join(chars[i:i + best_wlen]) if best_wlen > 0 else ""
            if best_dens >= dens_threshold(L) and best_run >= 2 and best_repl and not any(b in win for b in BLACKLIST):
                for pos, std in best_repl:
                    if chars[i + pos] != std:
                        chars[i + pos] = std
                for k in range(i, i + best_wlen):
                    used[k] = True
                fixed.append((i, i + best_wlen, jw, win))
                i += best_wlen
            else:
                i += 1
    return "".join(chars), fixed

def main():
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else src
    report = sys.argv[3] if len(sys.argv) > 3 else "易经_经文校正报告.md"

    jw = load_jingwen()
    jw_py = [[char_key(c) for c in s] for s in jw]
    jw_set = [set(p) for p in jw_py]
    jw_len = [len(s) for s in jw]
    print(f"经文库 {len(jw)} 条")

    os.makedirs(dst, exist_ok=True)
    files = sorted(glob.glob(os.path.join(src, "*.txt")))
    all_fixed = []
    total = 0
    for fp in files:
        name = os.path.basename(fp)
        text = open(fp, encoding="utf-8").read()
        out, fixed = fix_text(text, jw, jw_py, jw_set, jw_len)
        if fixed:
            open(os.path.join(dst, name), "w", encoding="utf-8").write(out)
            total += len(fixed)
            for s, e, jw_s, win_s in fixed:
                all_fixed.append((name, win_s, jw_s))
        elif dst != src:
            # 无修改也复制，保持目录完整
            open(os.path.join(dst, name), "w", encoding="utf-8").write(text)

    print(f"校正 {total} 处，涉及 {len({f for f, _, _ in all_fixed})} 个文件")

    with open(report, "w", encoding="utf-8") as f:
        f.write("# 《张庆祥讲易经案例》经文校正报告\n\n")
        f.write(f"- 校正总数：**{total} 处**\n")
        f.write(f"- 涉及文件：{len({x[0] for x in all_fixed})} 个\n\n")
        f.write("## 校正明细\n\n")
        f.write("| 文件 | 原文（错字） | 校正为（标准经文） |\n|---|---|---|\n")
        for name, win, jw_s in all_fixed:
            f.write(f"| {name} | {win} | {jw_s} |\n")

if __name__ == "__main__":
    main()
