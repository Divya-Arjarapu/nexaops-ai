import httpx

from app.main import app
from app.ollama_client import OllamaClient
from app.routes.health import get_ollama_client
from tests.helpers import get_response


def _client_for(handler) -> OllamaClient:
    return OllamaClient(
        "http://ollama.test",
        transport=httpx.MockTransport(handler),
    )


def test_ollama_connectivity_failure_is_reported(monkeypatch) -> None:
    def unavailable(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused", request=request)

    monkeypatch.setenv("OLLAMA_BASE_URL", "http://ollama.test")
    app.dependency_overrides[get_ollama_client] = lambda: _client_for(unavailable)
    try:
        response = get_response(app, "/health/ollama")
    finally:
        app.dependency_overrides.clear()

    payload = response.json()
    assert response.status_code == 503
    assert payload["status"] == "degraded"
    assert payload["ollama"]["status"] == "error"
    assert payload["generation_model"]["available"] is None
    assert payload["embedding_model"]["available"] is None


def test_missing_model_reports_manual_install_command(monkeypatch) -> None:
    def models_without_embedding(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/api/version":
            return httpx.Response(200, json={"version": "0.12.1"})
        if request.url.path == "/api/tags":
            return httpx.Response(200, json={"models": [{"name": "qwen3:4b"}]})
        return httpx.Response(404)

    monkeypatch.setenv("OLLAMA_BASE_URL", "http://ollama.test")
    app.dependency_overrides[get_ollama_client] = lambda: _client_for(
        models_without_embedding
    )
    try:
        response = get_response(app, "/health/ollama")
    finally:
        app.dependency_overrides.clear()

    payload = response.json()
    assert response.status_code == 503
    assert payload["ollama"]["status"] == "ok"
    assert payload["generation_model"]["available"] is True
    assert payload["embedding_model"]["available"] is False
    assert "ollama pull nomic-embed-text" in payload["embedding_model"]["message"]


def test_available_models_include_implicit_latest_tag(monkeypatch) -> None:
    def installed_models(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/api/version":
            return httpx.Response(200, json={"version": "0.12.1"})
        if request.url.path == "/api/tags":
            return httpx.Response(
                200,
                json={
                    "models": [
                        {"name": "qwen3:4b"},
                        {"name": "nomic-embed-text:latest"},
                    ]
                },
            )
        return httpx.Response(404)

    monkeypatch.setenv("OLLAMA_BASE_URL", "http://ollama.test")
    app.dependency_overrides[get_ollama_client] = lambda: _client_for(installed_models)
    try:
        response = get_response(app, "/health/ollama")
    finally:
        app.dependency_overrides.clear()

    payload = response.json()
    assert response.status_code == 200
    assert payload["status"] == "ok"
    assert payload["generation_model"]["available"] is True
    assert payload["embedding_model"]["available"] is True
