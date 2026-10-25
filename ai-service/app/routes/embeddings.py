from fastapi import APIRouter, Depends, HTTPException

from app.config import Settings
from app.ollama_embeddings import OllamaEmbeddingClient, OllamaEmbeddingError
from app.routes.health import get_settings
from app.schemas import EmbeddingRequest, EmbeddingResponse

router = APIRouter()


def get_embedding_client(settings: Settings = Depends(get_settings)) -> OllamaEmbeddingClient:
    return OllamaEmbeddingClient(
        settings.ollama_base_url,
        timeout_seconds=settings.embedding_timeout_seconds,
        max_batch=settings.embedding_max_batch,
        dimensions=768,
    )


@router.post(
    "/embeddings/generate",
    response_model=EmbeddingResponse,
    responses={400: {}, 502: {}, 503: {}},
)
async def generate_embeddings(
    request: EmbeddingRequest,
    settings: Settings = Depends(get_settings),
    client: OllamaEmbeddingClient = Depends(get_embedding_client),
) -> EmbeddingResponse:
    if settings.embedding_max_batch < 1:
        raise HTTPException(503, "Embedding batch configuration is invalid.")
    if len(request.texts) > settings.embedding_max_batch:
        raise HTTPException(
            413,
            f"This service accepts at most {settings.embedding_max_batch} texts per embedding request.",
        )
    if any(not text.strip() for text in request.texts):
        raise HTTPException(400, "Embedding input texts must not be empty.")
    if any(len(text) > settings.embedding_max_text_chars for text in request.texts):
        raise HTTPException(
            413,
            f"Embedding input texts must be no longer than {settings.embedding_max_text_chars} characters.",
        )
    try:
        result = await client.embed(settings.embedding_model, request.texts)
    except OllamaEmbeddingError as exc:
        raise HTTPException(exc.status_code, str(exc)) from exc
    return EmbeddingResponse(model=result.model, embeddings=result.embeddings)
