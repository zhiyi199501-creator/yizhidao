# 在仓库根目录构建：docker build -f backend/Dockerfile .
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY backend/requirements.txt /app/requirements.txt
RUN pip install -r /app/requirements.txt

COPY backend/app /app/app
COPY ios/Yizhidao/Resources/Hexagrams.json /app/data/Hexagrams.json
COPY ios/Yizhidao/Resources/cases.json /app/app/data/cases.json

RUN mkdir -p /app/data

ENV HEXAGRAMS_PATH=/app/data/Hexagrams.json \
    DATABASE_URL=sqlite:////app/data/yizhidao.db \
    APP_ENV=production

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=3)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080", "--workers", "2"]
