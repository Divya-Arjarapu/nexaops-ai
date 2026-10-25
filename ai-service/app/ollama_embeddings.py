import math
from dataclasses import dataclass
from typing import Any

import httpx


class OllamaEmbeddingError(RuntimeError):
    def __init__(self, message: str, status_code: int = 502) -> None:
        super().__init__(message)
        self.status_code = status_code


@dataclass(frozen=True)
class EmbeddingBatch:
    model: str
    embeddings: list[list[float]]


class OllamaEmbeddingClient:
    """Small local Ollama embedding client with strict output validation."""

    def __init__(
        self,
        base_url: str,
        timeout_seconds: float = 120.0,
        max_batch: int = 32,
        dimensions: int = 768,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        if timeout_seconds <= 0 or max_batch < 1 or dimensions < 1:
            raise ValueError("Embedding timeout, batch size, and dimensions must be positive.")
        self._base_url = base_url.rstrip("/")
        self._timeout_seconds = timeout_seconds
        self._max_batch = max_batch
        self._dimensions = dimensions
        self._transport = transport

    async def embed(self, model: str, texts: list[str]) -> EmbeddingBatch:
        if not texts or len(texts) > self._max_batch:
            raise OllamaEmbeddingError(
                f"Embedding batches must contain 1 to {self._max_batch} texts.", 400
            )
        if any(not isinstance(text, str) or not text.strip() for text in texts):
            raise OllamaEmbeddingError("Embedding input texts must not be empty.", 400)

        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=self._timeout_seconds,
                transport=self._transport,
            ) as client:
                response = await client.post(
                    "/api/embed",
                    json={
                        "model": model,
                        "input": texts,
                        "dimensions": self._dimensions,
                        "truncate": False,
                    },
                )
                response.raise_for_status()
                payload: Any = response.json()
        except httpx.TimeoutException as exc:
            raise OllamaEmbeddingError("Ollama embedding request timed out.", 503) from exc
        except httpx.ConnectError as exc:
            raise OllamaEmbeddingError("Ollama embedding service is unavailable.", 503) from exc
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code == 404:
                raise OllamaEmbeddingError(
                    f"Embedding model '{model}' is unavailable. Install it manually with `ollama pull {model}`.",
                    503,
                ) from exc
            if exc.response.status_code >= 500:
                raise OllamaEmbeddingError("Ollama could not generate embeddings.", 503) from exc
            raise OllamaEmbeddingError("Ollama rejected the embedding request.", 502) from exc
        except httpx.HTTPError as exc:
            raise OllamaEmbeddingError("Ollama embedding service is unavailable.", 503) from exc
        except ValueError as exc:
            raise OllamaEmbeddingError("Ollama returned malformed embedding data.", 502) from exc

        if not isinstance(payload, dict):
            raise OllamaEmbeddingError("Ollama returned malformed embedding data.")
        returned_model = payload.get("model")
        vectors = payload.get("embeddings")
        if returned_model != model or not isinstance(vectors, list) or len(vectors) != len(texts):
            raise OllamaEmbeddingError("Ollama returned an unexpected embedding response.")

        validated: list[list[float]] = []
        for vector in vectors:
            if not isinstance(vector, list) or len(vector) != self._dimensions:
                raise OllamaEmbeddingError(
                    f"Ollama returned a vector with an invalid dimension; expected {self._dimensions}."
                )
            numeric_vector: list[float] = []
            for value in vector:
                if isinstance(value, bool) or not isinstance(value, (int, float)):
                    raise OllamaEmbeddingError(
                        "Ollama returned a vector with non-finite or non-numeric values."
                    )
                try:
                    numeric_value = float(value)
                except (OverflowError, ValueError) as exc:
                    raise OllamaEmbeddingError(
                        "Ollama returned a vector with non-finite or non-numeric values."
                    ) from exc
                if not math.isfinite(numeric_value):
                    raise OllamaEmbeddingError(
                        "Ollama returned a vector with non-finite or non-numeric values."
                    )
                numeric_vector.append(numeric_value)
            validated.append(numeric_vector)

        return EmbeddingBatch(model=returned_model, embeddings=validated)
