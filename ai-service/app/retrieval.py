import math

from app.ollama_embeddings import OllamaEmbeddingClient, OllamaEmbeddingError
from app.retrieval_store import PostgresVectorStore, RetrievalStoreError
from app.schemas import RetrievalResult, RetrievalSearchResponse


class RetrievalError(RuntimeError):
    def __init__(self, message: str, status_code: int = 503) -> None:
        super().__init__(message)
        self.status_code = status_code


class RetrievalService:
    def __init__(
        self,
        embedding_client: OllamaEmbeddingClient,
        vector_store: PostgresVectorStore,
        *,
        embedding_model: str,
        max_query_chars: int = 2000,
        default_top_k: int = 5,
        max_top_k: int = 10,
    ) -> None:
        self._embedding_client = embedding_client
        self._vector_store = vector_store
        self._embedding_model = embedding_model
        self._max_query_chars = max_query_chars
        self._default_top_k = default_top_k
        self._max_top_k = min(max_top_k, 10)

    async def search(self, query: str, top_k: int | None) -> RetrievalSearchResponse:
        normalized_query = query.strip() if isinstance(query, str) else ""
        if not normalized_query:
            raise RetrievalError("The search query must not be empty.", 422)
        if len(normalized_query) > self._max_query_chars:
            raise RetrievalError(
                f"The search query must be no longer than {self._max_query_chars} characters.",
                422,
            )
        limit = self._default_top_k if top_k is None else top_k
        if limit < 1 or limit > self._max_top_k:
            raise RetrievalError(
                f"top_k must be between 1 and {self._max_top_k}.", 422
            )

        try:
            embedded = await self._embedding_client.embed(
                self._embedding_model, [normalized_query]
            )
        except OllamaEmbeddingError as exc:
            raise RetrievalError(str(exc), exc.status_code) from exc

        if (
            embedded.model != self._embedding_model
            or len(embedded.embeddings) != 1
            or len(embedded.embeddings[0]) != 768
            or any(not math.isfinite(value) for value in embedded.embeddings[0])
        ):
            raise RetrievalError("Ollama returned an invalid query embedding.", 502)

        try:
            rows = await self._vector_store.search(
                embedded.embeddings[0], self._embedding_model, limit
            )
        except RetrievalStoreError as exc:
            raise RetrievalError(str(exc), 503) from exc

        try:
            results = [RetrievalResult.model_validate(row) for row in rows]
        except (TypeError, ValueError) as exc:
            raise RetrievalError("The retrieval database returned invalid source data.", 502) from exc
        return RetrievalSearchResponse(
            query=normalized_query, results=results, result_count=len(results)
        )
