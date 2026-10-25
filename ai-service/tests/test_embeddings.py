import asyncio

import httpx

from app.config import Settings
from app.main import app
from app.ollama_embeddings import OllamaEmbeddingClient
from app.routes.embeddings import get_embedding_client
from app.routes.health import get_settings


def _post(handler, payload: dict) -> httpx.Response:
    async def send_request() -> httpx.Response:
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://testserver"
        ) as client:
            return await client.post("/embeddings/generate", json=payload)

    return asyncio.run(send_request())


def _settings(**overrides) -> Settings:
    values = {
        "ollama_base_url": "http://ollama.test",
        "embedding_model": "nomic-embed-text",
        "embedding_max_batch": 3,
    }
    values.update(overrides)
    return Settings(**values)


def _mock_client(handler, *, max_batch: int = 3) -> OllamaEmbeddingClient:
    return OllamaEmbeddingClient(
        "http://ollama.test",
        max_batch=max_batch,
        transport=httpx.MockTransport(handler),
    )


def _run(payload: dict, handler, *, settings: Settings | None = None, max_batch: int = 3):
    app.dependency_overrides[get_settings] = lambda: settings or _settings()
    app.dependency_overrides[get_embedding_client] = lambda: _mock_client(
        handler, max_batch=max_batch
    )
    try:
        return _post(handler, payload)
    finally:
        app.dependency_overrides.clear()


def test_generates_ordered_batch_and_uses_configured_model_and_dimensions() -> None:
    def embed(request: httpx.Request) -> httpx.Response:
        import json

        body = json.loads(request.content)
        assert request.url.path == "/api/embed"
        assert body["model"] == "nomic-embed-text"
        assert body["input"] == ["first chunk", "second chunk"]
        assert body["dimensions"] == 768
        assert body["truncate"] is False
        return httpx.Response(
            200,
            json={
                "model": "nomic-embed-text",
                "embeddings": [[float(index), 0.0] + [0.0] * 766 for index in (1, 2)],
            },
        )

    response = _run({"texts": ["first chunk", "second chunk"]}, embed)

    assert response.status_code == 200
    payload = response.json()
    assert payload["model"] == "nomic-embed-text"
    assert len(payload["embeddings"]) == 2
    assert payload["embeddings"][0][0] == 1
    assert payload["embeddings"][1][0] == 2
    assert all(len(vector) == 768 for vector in payload["embeddings"])


def test_rejects_missing_dimension_malformed_or_non_finite_vectors() -> None:
    def with_vectors(vectors):
        return lambda request: httpx.Response(
            200, json={"model": "nomic-embed-text", "embeddings": vectors}
        )

    for vectors in (
        [[0.0] * 767],
        [[0.0] * 768, [0.0] * 768],
        [[float("nan")] + [0.0] * 767],
        [["bad"] + [0.0] * 767],
        [[10**1000] + [0.0] * 767],
    ):
        response = _run({"texts": ["one"]}, with_vectors(vectors))
        assert response.status_code == 502
        assert "embedding" in response.json()["detail"].lower() or "vector" in response.json()["detail"].lower()


def test_reports_model_missing_and_ollama_unavailable_without_pulling() -> None:
    missing = _run(
        {"texts": ["one"]},
        lambda request: httpx.Response(404, json={"error": "model not found"}),
    )
    assert missing.status_code == 503
    assert "ollama pull nomic-embed-text" in missing.json()["detail"]

    def unavailable(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused", request=request)

    down = _run({"texts": ["one"]}, unavailable)
    assert down.status_code == 503
    assert down.json()["detail"] == "Ollama embedding service is unavailable."


def test_reports_timeout_and_ollama_server_errors() -> None:
    def timeout(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("timed out", request=request)

    assert _run({"texts": ["one"]}, timeout).status_code == 503
    server_error = _run(
        {"texts": ["one"]}, lambda request: httpx.Response(500)
    )
    assert server_error.status_code == 503


def test_enforces_batch_and_text_size_limits() -> None:
    settings = _settings(embedding_max_batch=1, embedding_max_text_chars=8)
    never_called = lambda request: httpx.Response(500)
    too_many = _run(
        {"texts": ["one", "two"]}, never_called, settings=settings, max_batch=1
    )
    too_long = _run(
        {"texts": ["123456789"]}, never_called, settings=settings, max_batch=1
    )
    assert too_many.status_code == 413
    assert too_long.status_code == 413


def test_rejects_blank_texts() -> None:
    response = _run(
        {"texts": ["   "]}, lambda request: httpx.Response(500)
    )
    assert response.status_code == 400
