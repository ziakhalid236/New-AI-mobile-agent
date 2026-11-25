# Repository Guide

Artemis is a Python 3.12+ AI mobile-agent runtime that drives Android devices,
plus an MCP server, a FastAPI admin console, an Angular showcase UI, and a small
Java accessibility-helper Android app.

## Environment

```bash
uv sync --dev                     # install all runtime + dev dependencies
cp .env.example .env              # then add at least one LLM provider key
```

## Tests

Deterministic suites (no device or credentials needed):

```bash
GOOGLE_API_KEY=dummy GEMINI_API_KEY=dummy OPENAI_API_KEY=dummy \
  uv run pytest tests/unit tests/tools packages/artemis-client/tests -q
```

The unit tests instantiate real LangChain chat models, so a placeholder API key
must be present in the environment; without one ~80 tests fail at model
construction. Device, cloud and end-to-end suites live in `tests/` and require
hardware or credentials.

## Android helper APK

```bash
cd packages/artemis-accessibility-helper
ANDROID_HOME=/path/to/android-sdk JAVA_HOME=/path/to/jdk17 bash build_apk.sh
```

The script uses `aapt2`, `d8`, `zipalign` and `apksigner` directly (no Gradle),
writes `ArtemisAccessibilityHelper.apk` and regenerates `helper_manifest.json`,
which the runtime reads to decide between install and upgrade. Version numbers
come from `app/build.gradle.kts`; bump them there, never in the script.
`.github/workflows/android-apk.yml` builds the APK on every relevant push and
publishes it as a release asset tagged `helper-v<version>`.

## Conventions

- Ruff for formatting and linting (`uv run ruff format .`, `uv run ruff check .`).
- Pyright for the protected core modules (`uv run pyright --project pyright-core.json`).
- `scripts/check_dependency_sources.py` rejects non-PyPI dependency sources; it
  runs before installs in CI.
- No third-party analytics or crash-reporting SDKs. Telemetry in
  `artemis/telemetry/` writes local JSONL span files and never leaves the host.
