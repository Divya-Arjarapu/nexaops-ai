from fastapi import APIRouter, Depends, HTTPException

from app.config import Settings
from app.ollama_embeddings import OllamaEmbeddingClient
from app.retrieval import RetrievalError, RetrievalService
from app.retrieval_store import PostgresVectorStore
from app.routes.embeddings import get_embedding_client
from app.routes.health import get_settings
from app.schemas import RetrievalSearchRequest, RetrievalSearchResponse

router = APIRouter()


def get_vector_store(settings: Settings = Depends(get_settings)) -> PostgresVectorStore:
    return PostgresVectorStore(
        host=settings.postgres_host,
        port=settings.postgres_port,
        database=settings.postgres_database,
        user=settings.postgres_user,
        password=settings.postgres_password,
        connect_timeout_seconds=settings.retrieval_db_connect_timeout_seconds,
        query_timeout_seconds=settings.retrieval_db_query_timeout_seconds,
    )


def get_retrieval_service(
    settings: Settings = Depends(get_settings),
    embedding_client: OllamaEmbeddingClient = Depends(get_embedding_client),
    vector_store: PostgresVectorStore = Depends(get_vector_store),
) -> RetrievalService:
    return RetrievalService(
        embedding_client,
        vector_store,
        embedding_model=settings.embedding_model,
        max_query_chars=settings.retrieval_query_max_chars,
        default_top_k=settings.retrieval_default_top_k,
        max_top_k=settings.retrieval_max_top_k,
    )


@router.post(
    "/retrieval/search",
    response_model=RetrievalSearchResponse,
    responses={422: {}, 502: {}, 503: {}},
)
async def search(
    request: RetrievalSearchRequest,
    service: RetrievalService = Depends(get_retrieval_service),
) -> RetrievalSearchResponse:
    try:
        return await service.search(request.query, request.top_k)
    except RetrievalError as exc:
        raise HTTPException(exc.status_code, str(exc)) from exc
