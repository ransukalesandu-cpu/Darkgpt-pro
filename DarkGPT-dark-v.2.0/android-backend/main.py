"""HTTP API adapter for the DarkGPT Android client.

Run from DarkGPT-dark-v.2.0 with:
    uvicorn android_backend.main:app --host 0.0.0.0 --port 8000
"""
import os
import sys
from pathlib import Path
from typing import Any

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from utils.get_response import (  # noqa: E402
    display_model_mapping,
    get_bot_response,
    get_model,
    get_provider,
)

app = FastAPI(title="DarkGPT Android API", version="1.0.0")

allowed_origins = os.getenv("DARKGPT_CORS_ORIGINS", "*").split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[origin.strip() for origin in allowed_origins],
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "X-API-Key"],
)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=20000)
    model: str = Field(min_length=1, max_length=100)


class ChatResponse(BaseModel):
    response: str
    model: str


def verify_api_key(x_api_key: str | None = Header(default=None)) -> None:
    """If a server token is configured, require it for every API request."""
    expected = os.getenv("DARKGPT_API_TOKEN")
    if expected and x_api_key != expected:
        raise HTTPException(status_code=401, detail="Invalid or missing API key")


@app.get("/api/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "DarkGPT Android API"}


@app.get("/api/models", dependencies=[Depends(verify_api_key)])
async def models() -> dict[str, list[dict[str, str]]]:
    return {
        "models": [
            {"label": label, "id": model}
            for label, model in display_model_mapping.items()
        ]
    }


@app.post("/api/chat", response_model=ChatResponse, dependencies=[Depends(verify_api_key)])
async def chat(payload: ChatRequest) -> Any:
    internal_model = get_model(payload.model)
    if not internal_model:
        raise HTTPException(status_code=400, detail="Unknown model")

    provider_name = get_provider(internal_model)
    try:
        answer = await run_in_threadpool(
            get_bot_response, payload.message, internal_model, provider_name
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Model provider error: {exc}") from exc

    if not answer:
        raise HTTPException(status_code=502, detail="The model returned an empty response")
    return ChatResponse(response=answer, model=payload.model)
