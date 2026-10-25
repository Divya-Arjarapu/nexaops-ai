import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    ollama_base_url: str = "http://localhost:11434"
    generation_model: str = "qwen3:4b"
    embedding_model: str = "nomic-embed-text"
    embedding_timeout_seconds: float = 120.0
    generation_timeout_seconds: float = 120.0
    embedding_max_batch: int = 32
    embedding_max_text_chars: int = 10000
    postgres_host: str = "127.0.0.1"
    postgres_port: int = 5432
    postgres_database: str = "nexaops"
    postgres_user: str = "nexaops"
    postgres_password: str = ""
    retrieval_db_connect_timeout_seconds: float = 3.0
    retrieval_db_query_timeout_seconds: float = 10.0
    retrieval_query_max_chars: int = 2000
    retrieval_default_top_k: int = 5
    retrieval_max_top_k: int = 10

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            ollama_base_url=os.getenv("OLLAMA_BASE_URL", cls.ollama_base_url).rstrip("/"),
            generation_model=os.getenv(
                "OLLAMA_GENERATION_MODEL", cls.generation_model
            ),
            embedding_model=os.getenv("OLLAMA_EMBEDDING_MODEL", cls.embedding_model),
            embedding_timeout_seconds=float(
                os.getenv("OLLAMA_EMBEDDING_TIMEOUT_SECONDS", "120")
            ),
            generation_timeout_seconds=float(
                os.getenv("OLLAMA_GENERATION_TIMEOUT_SECONDS", "120")
            ),
            embedding_max_batch=int(os.getenv("OLLAMA_EMBEDDING_MAX_BATCH", "32")),
            embedding_max_text_chars=int(
                os.getenv("OLLAMA_EMBEDDING_MAX_TEXT_CHARS", "10000")
            ),
            postgres_host=os.getenv("POSTGRES_HOST", cls.postgres_host),
            postgres_port=int(os.getenv("POSTGRES_PORT", str(cls.postgres_port))),
            postgres_database=os.getenv("POSTGRES_DB", cls.postgres_database),
            postgres_user=os.getenv("POSTGRES_USER", cls.postgres_user),
            postgres_password=os.getenv("POSTGRES_PASSWORD", ""),
            retrieval_db_connect_timeout_seconds=float(
                os.getenv("RETRIEVAL_DB_CONNECT_TIMEOUT_SECONDS", "3")
            ),
            retrieval_db_query_timeout_seconds=float(
                os.getenv("RETRIEVAL_DB_QUERY_TIMEOUT_SECONDS", "10")
            ),
            retrieval_query_max_chars=int(
                os.getenv("RETRIEVAL_QUERY_MAX_CHARS", "2000")
            ),
            retrieval_default_top_k=int(os.getenv("RETRIEVAL_DEFAULT_TOP_K", "5")),
            retrieval_max_top_k=min(int(os.getenv("RETRIEVAL_MAX_TOP_K", "10")), 10),
        )
