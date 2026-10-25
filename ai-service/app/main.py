from fastapi import FastAPI

from app.routes.documents import router as documents_router
from app.routes.embeddings import router as embeddings_router
from app.routes.health import router as health_router
from app.routes.retrieval import router as retrieval_router
from app.routes.rag import router as rag_router

app = FastAPI(title="NexaOps AI Service", version="0.1.0")
app.include_router(health_router)
app.include_router(documents_router)
app.include_router(embeddings_router)
app.include_router(retrieval_router)
app.include_router(rag_router)
