from app.config import Settings
from app.main import app
from tests.helpers import get_response


def test_health_returns_structured_service_status() -> None:
    response = get_response(app, "/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "nexaops-ai-service"}


def test_ollama_settings_have_local_defaults(monkeypatch) -> None:
    monkeypatch.delenv("OLLAMA_BASE_URL", raising=False)
    monkeypatch.delenv("OLLAMA_GENERATION_MODEL", raising=False)
    monkeypatch.delenv("OLLAMA_EMBEDDING_MODEL", raising=False)

    assert Settings.from_environment() == Settings(
        ollama_base_url="http://localhost:11434",
        generation_model="qwen3:4b",
        embedding_model="nomic-embed-text",
    )


def test_ollama_settings_read_environment_overrides(monkeypatch) -> None:
    monkeypatch.setenv("OLLAMA_BASE_URL", "http://ollama.internal:11434/")
    monkeypatch.setenv("OLLAMA_GENERATION_MODEL", "qwen3:8b")
    monkeypatch.setenv("OLLAMA_EMBEDDING_MODEL", "custom-embed:latest")

    assert Settings.from_environment() == Settings(
        ollama_base_url="http://ollama.internal:11434",
        generation_model="qwen3:8b",
        embedding_model="custom-embed:latest",
    )
