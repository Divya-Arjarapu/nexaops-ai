import asyncio

import httpx

from app.config import Settings
from app.main import app
from app.ollama_embeddings import EmbeddingBatch, OllamaEmbeddingError
from app.retrieval import RetrievalError, RetrievalService
from app.retrieval_store import PostgresVectorStore, RetrievalStoreError
from app.routes.retrieval import get_retrieval_service


def _post(payload: dict) -> httpx.Response:
    async def send_request() -> httpx.Response:
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://testserver"
        ) as client:
            return await client.post("/retrieval/search", json=payload)

    return asyncio.run(send_request())


class FakeEmbeddingClient:
    def __init__(self, result=None, error=None):
        self.result = result or EmbeddingBatch("nomic-embed-text", [[0.1] * 768])
        self.error = error
        self.calls = []

    async def embed(self, model, texts):
        self.calls.append((model, texts))
        if self.error:
            raise self.error
        return self.result


class FakeVectorStore:
    def __init__(self, rows=None, error=None):
        self.rows = rows or []
        self.error = error
        self.calls = []

    async def search(self, vector, model, top_k):
        self.calls.append((vector, model, top_k))
        if self.error:
            raise self.error
        return self.rows[:top_k]


def _row(chunk_id="18", chunk_index=0, page_number=2, score=0.87):
    return {
        "document_id": "6c1c8f17-bb31-47a5-b90c-1761d17030bc",
        "filename": "operations.pdf",
        "chunk_id": chunk_id,
        "chunk_index": chunk_index,
        "page_number": page_number,
        "section_title": "Database access",
        "source_locator": "Page 2",
        "text": "Production database access requires administrator approval.",
        "similarity_score": score,
    }


def _service(embedding=None, store=None, **settings):
    return RetrievalService(
        embedding or FakeEmbeddingClient(),
        store or FakeVectorStore(),
        embedding_model="nomic-embed-text",
        **settings,
    )


def _request(payload, service):
    app.dependency_overrides[get_retrieval_service] = lambda: service
    try:
        return _post(payload)
    finally:
        app.dependency_overrides.clear()


def test_search_embeds_query_maps_source_metadata_and_preserves_distance_order():
    embedding = FakeEmbeddingClient()
    store = FakeVectorStore(rows=[_row("18", 0, 2, 0.91), _row("21", 1, 3, 0.72)])
    response = _request(
        {"query": "Who authorizes production database access?", "top_k": 2},
        _service(embedding, store),
    )

    assert response.status_code == 200
    payload = response.json()
    assert embedding.calls == [("nomic-embed-text", ["Who authorizes production database access?"])]
    assert store.calls[0][1:] == ("nomic-embed-text", 2)
    assert payload["result_count"] == 2
    assert [result["chunk_id"] for result in payload["results"]] == ["18", "21"]
    assert payload["results"][0]["document_id"] == "6c1c8f17-bb31-47a5-b90c-1761d17030bc"
    assert payload["results"][0]["filename"] == "operations.pdf"
    assert payload["results"][0]["chunk_index"] == 0
    assert payload["results"][0]["page_number"] == 2
    assert payload["results"][0]["section_title"] == "Database access"
    assert payload["results"][0]["similarity_score"] == 0.91


def test_empty_results_and_null_source_metadata_are_returned_cleanly():
    empty = _request({"query": "A query with no matches"}, _service())
    assert empty.status_code == 200
    assert empty.json()["results"] == []
    assert empty.json()["result_count"] == 0

    row = _row(page_number=None)
    row["section_title"] = None
    response = _request({"query": "A query"}, _service(store=FakeVectorStore([row])))
    assert response.status_code == 200
    assert response.json()["results"][0]["page_number"] is None
    assert response.json()["results"][0]["section_title"] is None


def test_rejects_empty_long_and_invalid_top_k_queries():
    service = _service(max_query_chars=10, default_top_k=5, max_top_k=10)
    assert _request({"query": "   "}, service).status_code == 422
    assert _request({"query": "this query is too long"}, service).status_code == 422
    assert _request({"query": "valid", "top_k": 0}, service).status_code == 422
    assert _request({"query": "valid", "top_k": 11}, service).status_code == 422


def test_rejects_malformed_query_embedding_and_reports_ollama_failure():
    malformed = FakeEmbeddingClient(result=EmbeddingBatch("nomic-embed-text", [[0.0] * 767]))
    response = _request({"query": "valid"}, _service(malformed))
    assert response.status_code == 502

    unavailable = FakeEmbeddingClient(error=OllamaEmbeddingError("Ollama is unavailable.", 503))
    response = _request({"query": "valid"}, _service(unavailable))
    assert response.status_code == 503


def test_database_failure_is_returned_as_a_safe_service_error():
    store = FakeVectorStore(error=RetrievalStoreError("The PostgreSQL retrieval service is unavailable."))
    response = _request({"query": "valid"}, _service(store=store))
    assert response.status_code == 503
    assert "PostgreSQL retrieval service is unavailable" in response.json()["detail"]


def test_postgres_store_uses_parameterized_cosine_search_and_maps_rows(monkeypatch):
    row = _row()
    observed = {}

    class FakeCursor:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def execute(self, query, params=()):
            if "set_config" in query:
                observed["timeout"] = params
                return
            observed["query"] = query
            observed["params"] = params

        async def fetchall(self):
            return [row]

    class FakeConnection:
        def cursor(self, row_factory=None):
            observed["row_factory"] = row_factory
            return FakeCursor()

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

    async def connect(**kwargs):
        observed["connect"] = kwargs
        return FakeConnection()

    monkeypatch.setattr("app.retrieval_store.psycopg.AsyncConnection.connect", connect)
    store = PostgresVectorStore(
        host="localhost", port=5432, database="nexaops", user="nexaops", password="safe-test"
    )
    results = asyncio.run(store.search([0.0] * 768, "nomic-embed-text", 5))

    assert results[0]["filename"] == "operations.pdf"
    assert results[0]["similarity_score"] == 0.87
    assert "c.embedding <=> %s::public.vector" in observed["query"]
    assert "ORDER BY c.embedding <=> %s::public.vector ASC" in observed["query"]
    assert "c.embedding IS NOT NULL" in observed["query"]
    assert "c.embedding_model = %s" in observed["query"]
    assert "nexaops.document_chunks" in observed["query"]
    assert observed["params"][0].startswith("[0.0,0.0,")
    assert observed["params"][1:] == ("nomic-embed-text", observed["params"][0], 5)
    assert observed["timeout"] == ("10000ms",)

    row["similarity_score"] = None
    try:
        asyncio.run(store.search([0.0] * 768, "nomic-embed-text", 5))
        assert False, "expected invalid similarity score to be rejected"
    except RetrievalStoreError as exc:
        assert str(exc) == "PostgreSQL returned an invalid similarity score."


def test_postgres_store_returns_empty_rows_and_handles_database_errors(monkeypatch):
    class EmptyCursor:
        async def __aenter__(self): return self
        async def __aexit__(self, *args): return None
        async def execute(self, query, params=()): return None
        async def fetchall(self): return []

    class EmptyConnection:
        def cursor(self, row_factory=None): return EmptyCursor()
        async def __aenter__(self): return self
        async def __aexit__(self, *args): return None

    async def connect(**kwargs): return EmptyConnection()

    monkeypatch.setattr("app.retrieval_store.psycopg.AsyncConnection.connect", connect)
    store = PostgresVectorStore(
        host="localhost", port=5432, database="nexaops", user="nexaops", password="safe-test"
    )
    assert asyncio.run(store.search([0.0] * 768, "nomic-embed-text", 5)) == []

    async def unavailable(**kwargs):
        raise OSError("synthetic connection error")

    monkeypatch.setattr("app.retrieval_store.psycopg.AsyncConnection.connect", unavailable)
    try:
        asyncio.run(store.search([0.0] * 768, "nomic-embed-text", 5))
        assert False, "expected safe database failure"
    except RetrievalStoreError as exc:
        assert str(exc) == "The PostgreSQL retrieval service is unavailable."


def test_database_credentials_are_not_inferred_from_the_example_password():
    settings = Settings(postgres_password="")
    store = PostgresVectorStore(
        host=settings.postgres_host,
        port=settings.postgres_port,
        database=settings.postgres_database,
        user=settings.postgres_user,
        password=settings.postgres_password,
    )
    try:
        asyncio.run(store.search([0.0] * 768, "nomic-embed-text", 5))
        assert False, "expected missing database configuration"
    except RetrievalStoreError as exc:
        assert "credentials are not configured" in str(exc)
