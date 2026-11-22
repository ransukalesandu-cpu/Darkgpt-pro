# DarkGPT Android app

This is a native Android client built with Kotlin and Jetpack Compose. It connects to the existing Python/G4F implementation through the small FastAPI adapter in `../android-backend`.

## Open and build

1. Install Android Studio and its Android SDK (API 35).
2. Open this `android` directory in Android Studio.
3. Allow Gradle sync to finish, then run the `app` configuration on an emulator or Android phone.
4. The project uses Java 17 and Android Gradle Plugin 8.7.3.

## Start the Python backend

From the `DarkGPT-dark-v.2.0` directory:

```bash
python -m pip install -r requirements.txt
python -m pip install -r android-backend/requirements.txt
uvicorn android_backend.main:app --host 0.0.0.0 --port 8000
```

For the Android emulator, the default API address `http://10.0.2.2:8000` reaches the host computer.

For a physical phone, connect the phone and computer to the same Wi-Fi network. In the app's Settings, set Backend URL to `http://YOUR-COMPUTER-LAN-IP:8000` (for example, `http://192.168.1.20:8000`). Allow the backend port through the computer firewall.

## Public hosting

For an internet-accessible backend, use HTTPS and set `DARKGPT_API_TOKEN` on the server. Enter that token in the app's Settings. Never commit the token to this repository.

## Included in this first Android client

- Existing G4F model list fetched from the Python backend
- Model selection
- Chat messages and new-chat clearing
- Backend URL and optional API token settings

The app does not bundle Python or G4F inside the APK. The Python backend must be running and reachable for AI responses. This keeps the phone lightweight and preserves the existing model integration.
