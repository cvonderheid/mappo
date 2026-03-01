FROM ghcr.io/astral-sh/uv:python3.11-bookworm-slim

WORKDIR /app

COPY backend/pyproject.toml backend/README.md ./
COPY backend/app ./app

RUN uv pip install --system --no-cache .

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
