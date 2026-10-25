from typing import Literal

from pydantic import BaseModel, Field


class ServiceHealthResponse(BaseModel):
    status: Literal["ok"]
    service: str


class OllamaServerCheck(BaseModel):
    status: Literal["ok", "error"]
    message: str
    version: str | None = None


class ModelCheck(BaseModel):
    status: Literal["ok", "error"]
    model: str
    available: bool | None
    message: str


class OllamaDiagnosticResponse(BaseModel):
    status: Literal["ok", "degraded"]
    ollama: OllamaServerCheck
    generation_model: ModelCheck
    embedding_model: ModelCheck


class ExtractedPage(BaseModel):
    page_number: int | None
    text: str


class DocumentExtractionResponse(BaseModel):
    filename: str
    media_type: Literal["application/pdf", "text/plain"]
    pages: list[ExtractedPage]
    character_count: int
    page_count: int | None


class EmbeddingRequest(BaseModel):
    texts: list[str] = Field(min_length=1)


class EmbeddingResponse(BaseModel):
    model: str
    embeddings: list[list[float]]


class RetrievalSearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=2000)
    top_k: int | None = Field(default=None, ge=1, le=10)


class RetrievalResult(BaseModel):
    document_id: str
    filename: str
    chunk_id: str
    chunk_index: int
    page_number: int | None
    section_title: str | None
    source_locator: str | None
    text: str
    similarity_score: float


class RetrievalSearchResponse(BaseModel):
    query: str
    results: list[RetrievalResult]
    result_count: int


class RagAnswerRequest(BaseModel):
    query: str = Field(min_length=1, max_length=2000)
    top_k: int | None = Field(default=None, ge=1, le=10)


class RagCitation(BaseModel):
    source_id: str
    document_id: str
    filename: str
    chunk_id: str
    chunk_index: int
    page_number: int | None
    section_title: str | None
    source_locator: str | None
    similarity_score: float


class RagSource(RagCitation):
    text: str


class RagAnswerResponse(BaseModel):
    query: str
    answer: str
    has_context: bool
    citations: list[RagCitation]
    sources: list[RagSource]
