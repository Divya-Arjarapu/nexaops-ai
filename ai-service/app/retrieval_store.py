import asyncio
import math
from typing import Any

import psycopg
from psycopg.rows import dict_row


RETRIEVAL_SQL = """
SELECT
    d.id::text AS document_id,
    d.original_filename AS filename,
    c.id::text AS chunk_id,
    c.chunk_index,
    c.page_number,
    c.section_title,
    c.source_locator,
    c.content AS text,
    1 - (c.embedding <=> %s::public.vector) AS similarity_score
FROM nexaops.document_chunks AS c
JOIN nexaops.documents AS d ON d.id = c.document_id
WHERE c.embedding IS NOT NULL
  AND c.embedding_model = %s
ORDER BY c.embedding <=> %s::public.vector ASC
LIMIT %s
"""


class RetrievalStoreError(RuntimeError):
    """Safe failure raised when PostgreSQL retrieval cannot complete."""


class PostgresVectorStore:
    def __init__(
        self,
        *,
        host: str,
        port: int,
        database: str,
        user: str,
        password: str,
        connect_timeout_seconds: float = 3.0,
        query_timeout_seconds: float = 10.0,
    ) -> None:
        self._host = host
        self._port = port
        self._database = database
        self._user = user
        self._password = password
        self._connect_timeout_seconds = connect_timeout_seconds
        self._query_timeout_seconds = query_timeout_seconds

    async def search(
        self, query_embedding: list[float], embedding_model: str, top_k: int
    ) -> list[dict[str, Any]]:
        if not self._password:
            raise RetrievalStoreError(
                "PostgreSQL credentials are not configured for semantic retrieval."
            )
        vector = "[" + ",".join(str(value) for value in query_embedding) + "]"
        timeout = min(self._connect_timeout_seconds, 60.0)
        try:
            async with asyncio.timeout(self._query_timeout_seconds):
                async with await psycopg.AsyncConnection.connect(
                    host=self._host,
                    port=self._port,
                    dbname=self._database,
                    user=self._user,
                    password=self._password,
                    connect_timeout=max(1, math.ceil(timeout)),
                ) as connection:
                    async with connection.cursor(row_factory=dict_row) as cursor:
                        await cursor.execute(
                            "SELECT set_config('statement_timeout', %s, true)",
                            (f"{math.ceil(self._query_timeout_seconds * 1000)}ms",),
                        )
                        await cursor.execute(
                            RETRIEVAL_SQL,
                            (vector, embedding_model, vector, top_k),
                        )
                        rows = await cursor.fetchall()
        except TimeoutError as exc:
            raise RetrievalStoreError("The PostgreSQL retrieval query timed out.") from exc
        except (psycopg.Error, OSError) as exc:
            raise RetrievalStoreError("The PostgreSQL retrieval service is unavailable.") from exc

        results: list[dict[str, Any]] = []
        for row in rows:
            try:
                score = float(row["similarity_score"])
            except (TypeError, ValueError, OverflowError) as exc:
                raise RetrievalStoreError(
                    "PostgreSQL returned an invalid similarity score."
                ) from exc
            if not math.isfinite(score):
                raise RetrievalStoreError("PostgreSQL returned an invalid similarity score.")
            results.append(
                {
                    "document_id": row["document_id"],
                    "filename": row["filename"],
                    "chunk_id": row["chunk_id"],
                    "chunk_index": row["chunk_index"],
                    "page_number": row["page_number"],
                    "section_title": row["section_title"],
                    "source_locator": row["source_locator"],
                    "text": row["text"],
                    "similarity_score": score,
                }
            )
        return results
