from fastapi import APIRouter, Depends, HTTPException

from app.config import Settings
from app.ollama_client import OllamaClient
from app.rag import RagAnswerError, RagAnswerService
from app.retrieval import RetrievalError, RetrievalService
from app.routes.health import get_settings
from app.routes.retrieval import get_retrieval_service
from app.schemas import RagAnswerRequest, RagAnswerResponse

router = APIRouter()


def get_generation_client(settings: Settings = Depends(get_settings)) -> OllamaClient:
    return OllamaClient(
        settings.ollama_base_url,
        timeout_seconds=settings.generation_timeout_seconds,
    )


def get_rag_answer_service(
    settings: Settings = Depends(get_settings),
    retrieval_service: RetrievalService = Depends(get_retrieval_service),
    generation_client: OllamaClient = Depends(get_generation_client),
) -> RagAnswerService:
    return RagAnswerService(
        retrieval_service,
        generation_client,
        generation_model=settings.generation_model,
    )


@router.post(
    "/rag/answer",
    response_model=RagAnswerResponse,
    responses={422: {}, 502: {}, 503: {}},
)
async def answer(
    request: RagAnswerRequest,
    service: RagAnswerService = Depends(get_rag_answer_service),
) -> RagAnswerResponse:
    try:
        return await service.answer(request.query, request.top_k)
    except (RagAnswerError, RetrievalError) as exc:
        raise HTTPException(exc.status_code, str(exc)) from exc
