"""HTTP API adapter for the DarkGPT Android client.

Run from DarkGPT-dark-v.2.0 with:
    uvicorn android_backend.main:app --host 0.0.0.0 --port 8000
"""
import os
import sys
import json
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

from fastapi import Depends, FastAPI, Header, HTTPException, Query
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


def github_get_json(api_path: str) -> Any:
    """Read public GitHub repository data without storing a GitHub credential."""
    request = urllib.request.Request("https://api.github.com" + api_path, headers={
        "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "DarkGPT-Pro-Android"})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            raise HTTPException(status_code=404, detail="GitHub repository or path not found") from exc
        if exc.code == 403:
            raise HTTPException(status_code=429, detail="GitHub API rate limit reached; try again later") from exc
        raise HTTPException(status_code=502, detail=f"GitHub API error: HTTP {exc.code}") from exc
    except (urllib.error.URLError, TimeoutError) as exc:
        raise HTTPException(status_code=502, detail="Could not reach GitHub") from exc


@app.get("/api/github/repos/{owner}/{repo}", dependencies=[Depends(verify_api_key)])
async def github_repository(owner: str, repo: str) -> dict[str, Any]:
    data = await run_in_threadpool(github_get_json, f"/repos/{owner}/{repo}")
    return {"full_name": data.get("full_name", ""), "description": data.get("description") or "",
            "default_branch": data.get("default_branch", "main"), "private": data.get("private", False),
            "html_url": data.get("html_url", "")}


@app.get("/api/github/repos/{owner}/{repo}/contents", dependencies=[Depends(verify_api_key)])
async def github_contents(owner: str, repo: str, path: str = Query(default="", max_length=500),
                          ref: str | None = Query(default=None, max_length=100)) -> dict[str, Any]:
    safe_path = "/".join(part for part in path.split("/") if part not in ("", ".", ".."))
    endpoint = f"/repos/{owner}/{repo}/contents"
    if safe_path:
        endpoint += "/" + urllib.parse.quote(safe_path, safe="/")
    if ref:
        endpoint += "?ref=" + urllib.parse.quote(ref, safe="")
    data = await run_in_threadpool(github_get_json, endpoint)
    items = data if isinstance(data, list) else [data]
    return {"items": [{"name": item.get("name", ""), "path": item.get("path", ""),
                       "type": item.get("type", ""), "size": item.get("size", 0),
                       "html_url": item.get("html_url", "")} for item in items]}


@app.get("/api/github/repos/{owner}/{repo}/file", dependencies=[Depends(verify_api_key)])
async def github_file(owner: str, repo: str, path: str = Query(min_length=1, max_length=500),
                      ref: str | None = Query(default=None, max_length=100)) -> dict[str, str]:
    safe_path = "/".join(part for part in path.split("/") if part not in ("", ".", ".."))
    if not safe_path:
        raise HTTPException(status_code=400, detail="A file path is required")
    endpoint = f"/repos/{owner}/{repo}/contents/{urllib.parse.quote(safe_path, safe='/')}"
    if ref:
        endpoint += "?ref=" + urllib.parse.quote(ref, safe="")
    data = await run_in_threadpool(github_get_json, endpoint)
    if data.get("type") != "file" or not data.get("content"):
        raise HTTPException(status_code=400, detail="Selected path is not a readable text file")
    import base64
    try:
        content = base64.b64decode(data["content"]).decode("utf-8")
    except (ValueError, UnicodeDecodeError) as exc:
        raise HTTPException(status_code=415, detail="This file is not UTF-8 text") from exc
    return {"name": data.get("name", safe_path), "path": data.get("path", safe_path), "content": content[:100000]}
