from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse

from app.config import Settings
from app.ollama_client import OllamaClient, OllamaClientError, canonical_model_name
from app.schemas import (
    ModelCheck,
    OllamaDiagnosticResponse,
    OllamaServerCheck,
    ServiceHealthResponse,
)

router = APIRouter()


def get_settings() -> Settings:
    return Settings.from_environment()


def get_ollama_client(settings: Settings = Depends(get_settings)) -> OllamaClient:
    return OllamaClient(settings.ollama_base_url)


@router.get("/health", response_model=ServiceHealthResponse)
async def health() -> ServiceHealthResponse:
    return ServiceHealthResponse(status="ok", service="nexaops-ai-service")


@router.get(
    "/health/ollama",
    response_model=OllamaDiagnosticResponse,
    responses={503: {"model": OllamaDiagnosticResponse}},
)
async def ollama_health(
    settings: Settings = Depends(get_settings),
    client: OllamaClient = Depends(get_ollama_client),
) -> OllamaDiagnosticResponse | JSONResponse:
    try:
        version = await client.get_version()
    except OllamaClientError as exc:
        result = OllamaDiagnosticResponse(
            status="degraded",
            ollama=OllamaServerCheck(status="error", message=str(exc)),
            generation_model=_not_checked_model(
                settings.generation_model, "Ollama is unreachable."
            ),
            embedding_model=_not_checked_model(
                settings.embedding_model, "Ollama is unreachable."
            ),
        )
        return _diagnostic_response(result)

    server_check = OllamaServerCheck(
        status="ok", message="Ollama server is reachable.", version=version
    )
    try:
        installed_models = await client.list_models()
    except OllamaClientError as exc:
        result = OllamaDiagnosticResponse(
            status="degraded",
            ollama=server_check,
            generation_model=_not_checked_model(
                settings.generation_model, f"Could not inspect Ollama models: {exc}"
            ),
            embedding_model=_not_checked_model(
                settings.embedding_model, f"Could not inspect Ollama models: {exc}"
            ),
        )
        return _diagnostic_response(result)

    installed = {canonical_model_name(name) for name in installed_models}
    generation_check = _model_check(settings.generation_model, installed)
    embedding_check = _model_check(settings.embedding_model, installed)
    all_checks_passed = (
        generation_check.available is True and embedding_check.available is True
    )
    result = OllamaDiagnosticResponse(
        status="ok" if all_checks_passed else "degraded",
        ollama=server_check,
        generation_model=generation_check,
        embedding_model=embedding_check,
    )
    return _diagnostic_response(result)


def _not_checked_model(model: str, reason: str) -> ModelCheck:
    return ModelCheck(
        status="error",
        model=model,
        available=None,
        message=f"Model availability was not checked because {reason}",
    )


def _model_check(model: str, installed: set[str]) -> ModelCheck:
    available = canonical_model_name(model) in installed
    if available:
        return ModelCheck(
            status="ok",
            model=model,
            available=True,
            message="Model is installed.",
        )
    return ModelCheck(
        status="error",
        model=model,
        available=False,
        message=(
            f"Model '{model}' is not installed. Install it manually with "
            f"`ollama pull {model}`."
        ),
    )


def _diagnostic_response(result: OllamaDiagnosticResponse) -> JSONResponse:
    status_code = 200 if result.status == "ok" else 503
    return JSONResponse(status_code=status_code, content=result.model_dump())
