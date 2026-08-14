#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

VENV="$ROOT/.venv"
PYTHON="${PYTHON:-python3}"
HOST="${HOST:-0.0.0.0}"
PORT="${PORT:-8080}"

if ! command -v "$PYTHON" >/dev/null 2>&1; then
  echo "未找到 python3，请先安装 Python 3.9+"
  exit 1
fi

if [[ ! -d "$VENV" ]]; then
  echo "→ 创建虚拟环境..."
  "$PYTHON" -m venv "$VENV"
fi

# shellcheck disable=SC1091
source "$VENV/bin/activate"

echo "→ 检查依赖..."
pip install -q -r requirements.txt

if [[ ! -f .env ]]; then
  echo "→ 复制 .env.example → .env"
  cp .env.example .env
fi

echo ""
echo "易知道后端启动中"
echo "  本地地址   http://127.0.0.1:${PORT}"
echo "  接口文档   http://127.0.0.1:${PORT}/docs"
echo "  开发验证码 见 .env 中 DEV_SMS_FIXED_CODE（默认 123456）"
echo "  停止服务   Ctrl+C"
echo ""

exec uvicorn app.main:app --reload --host "$HOST" --port "$PORT"
