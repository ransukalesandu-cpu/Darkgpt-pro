# DarkGPT Android API

This small FastAPI adapter reuses the existing `utils.get_response` G4F implementation. It does not replace or modify the Streamlit app.

## Run locally

From the `DarkGPT-dark-v.2.0` directory:

```bash
python -m pip install -r requirements.txt
python -m pip install -r android-backend/requirements.txt
uvicorn android_backend.main:app --host 0.0.0.0 --port 8000
```

Check `http://127.0.0.1:8000/api/health`.

## Protect a public server

Set `DARKGPT_API_TOKEN` to a long random secret before exposing the API to the internet. The Android app sends it as `X-API-Key`. Do not put provider credentials or server secrets in the Android source code.

Optional CORS setting: `DARKGPT_CORS_ORIGINS` as a comma-separated list. CORS does not replace API authentication.

The API uses the model list already defined in `utils/get_response.py`. It does not claim that a model is available when the existing G4F provider cannot serve it.
