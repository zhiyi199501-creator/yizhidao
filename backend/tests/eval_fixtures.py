"""抽检用的固定卦例。不调模型；跑真实模型见 scripts/eval_ai_reading.py。"""

from typing import Any, Dict, List, Optional, TypedDict


class Sample(TypedDict):
    id: str
    title: str
    question: str
    method: str
    primary: int
    moving: List[int]
    expect_resulting: Optional[int]
    expect_ima_ids: List[str]
    expect_ima_absent_prefixes: List[str]
    expect_case_count_max: int
    expect_cases_from: Optional[int]  # hexagram number cases should come from; None if skip


SAMPLES: List[Sample] = [
    {
        "id": "zero",
        "title": "0动·兑·主看卦辞",
        "question": "今年还要不要换岗",
        "method": "coin",
        "primary": 58,
        "moving": [],
        "expect_resulting": None,
        "expect_ima_ids": ["58-guaci", "58-tuanci", "58-daxiang"],
        "expect_ima_absent_prefixes": ["58-yao-"],
        "expect_case_count_max": 0,
        "expect_cases_from": None,
    },
    {
        "id": "one",
        "title": "1动·乾初·之姤",
        "question": "现在该不该出来做事",
        "method": "coin",
        "primary": 1,
        "moving": [1],
        "expect_resulting": 44,
        "expect_ima_ids": ["01-guaci", "01-daxiang", "01-yao-0"],
        "expect_ima_absent_prefixes": ["01-tuanci", "01-wenyan"],
        "expect_case_count_max": 3,
        "expect_cases_from": 1,
    },
    {
        "id": "two",
        "title": "2动·乾二上·之革",
        "question": "这段关系是守还是走",
        "method": "coin",
        "primary": 1,
        "moving": [2, 6],
        "expect_resulting": 49,
        "expect_ima_ids": ["01-guaci", "01-daxiang", "01-yao-1", "01-yao-5"],
        "expect_ima_absent_prefixes": ["01-tuanci"],
        "expect_case_count_max": 3,
        "expect_cases_from": 1,
    },
    {
        "id": "three",
        "title": "3动·乾初二三·之否",
        "question": "这桩合伙还要不要继续",
        "method": "digitalManual",
        "primary": 1,
        "moving": [1, 2, 3],
        "expect_resulting": 12,
        "expect_ima_ids": ["01-guaci", "01-tuanci", "01-daxiang", "12-guaci"],
        "expect_ima_absent_prefixes": ["12-tuanci", "01-yao-"],
        "expect_case_count_max": 0,
        "expect_cases_from": None,
    },
    {
        "id": "four",
        "title": "4动·复三至上·之同人",
        "question": "要不要加入这个圈子",
        "method": "coin",
        "primary": 24,
        "moving": [3, 4, 5, 6],
        "expect_resulting": 13,
        "expect_ima_ids": ["24-guaci", "24-daxiang", "13-yao-0", "13-yao-1"],
        "expect_ima_absent_prefixes": ["24-tuanci", "24-yao-"],
        "expect_case_count_max": 3,
        "expect_cases_from": 13,
    },
    {
        "id": "six",
        "title": "6动·乾之坤",
        "question": "这条路走到头了该如何收",
        "method": "coin",
        "primary": 1,
        "moving": [1, 2, 3, 4, 5, 6],
        "expect_resulting": 2,
        "expect_ima_ids": ["01-guaci", "01-daxiang", "02-guaci", "02-tuanci", "02-daxiang"],
        "expect_ima_absent_prefixes": ["01-tuanci"],
        "expect_case_count_max": 0,
        "expect_cases_from": None,
    },
    {
        "id": "dui-upper",
        "title": "1动·兑上（案例多）",
        "question": "这次谈判该硬还是该让",
        "method": "coin",
        "primary": 58,
        "moving": [6],
        "expect_resulting": 10,
        "expect_ima_ids": ["58-guaci", "58-daxiang", "58-yao-5"],
        "expect_ima_absent_prefixes": ["58-tuanci"],
        "expect_case_count_max": 3,
        "expect_cases_from": 58,
    },
    {
        "id": "career",
        "title": "1动·革五·所问具体",
        "question": "该不该签这份跳槽 offer",
        "method": "coin",
        "primary": 49,
        "moving": [5],
        "expect_resulting": 55,
        "expect_ima_ids": ["49-guaci", "49-daxiang", "49-yao-4"],
        "expect_ima_absent_prefixes": ["49-tuanci"],
        "expect_case_count_max": 3,
        "expect_cases_from": 49,
    },
]


FOLLOWUP: Dict[str, Any] = {
    "after_id": "career",
    "message": "如果对方反对、或原公司挽留呢",
}
