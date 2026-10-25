# nexaops-ai
NexaOps AI, an enterprise knowledge and service copilot.

## AI-Assisted Development with OpenAI Codex

OpenAI Codex was used extensively throughout the development of NexaOps AI as
an AI-powered coding assistant to accelerate implementation, explore technical
approaches, and improve development workflows.

### How Codex Supported Development

- **Project implementation:** Assisted with developing and refining components
  across the React + TypeScript frontend, Java + Spring Boot backend, and Python
  + FastAPI AI service.
- **RAG pipeline development:** Supported implementation of document ingestion,
  text extraction, chunking, embedding generation, vector-based semantic
  retrieval, and retrieval-augmented answer generation.
- **Service integration:** Assisted with connecting the frontend, backend, AI
  service, PostgreSQL with pgvector, and locally hosted Ollama models.
- **Database development:** Supported work on database schemas, Flyway
  migrations, document storage, vector embeddings, and retrieval queries.
- **Testing and debugging:** Helped investigate errors, improve test coverage,
  troubleshoot integration issues, and validate application behaviour.
- **Code review and quality checks:** Supported reviewing implementation
  changes, identifying potential issues, and checking code quality before
  integration.
- **Documentation:** Assisted with documenting the architecture, implementation
  decisions, API workflows, and development progress.

### Development Approach

Codex was used as a development collaborator rather than a replacement for
engineering judgement. Implementation decisions, generated changes, and test
results were reviewed as part of the development workflow.

The project demonstrates how AI-assisted software development can help
engineers move from architectural ideas to a working application while
maintaining an emphasis on testing, integration, and understanding the
underlying systems.

**Project:** NexaOps AI

**Focus:** Generative AI, Retrieval-Augmented Generation (RAG), full-stack
engineering, and AI-assisted development.

## License

The original NexaOps AI project code is proprietary. Copying, modifying,
distributing, sublicensing, publishing, or commercially exploiting it requires
written permission from the copyright owner. This notice applies to original
project code and does not replace the licences governing third-party components.
See [LICENSE](LICENSE).

## Local PostgreSQL with pgvector

The local database runs PostgreSQL 16 with the pgvector extension, using the
version-pinned `pgvector/pgvector:0.8.7-pg16-bookworm` image. Compose creates a
named volume (`postgres_data`) for persistent database files and reports
readiness through a PostgreSQL health check.

### Configure and start

From the repository root, create your local environment file:

```sh
cp .env.example .env
```

Edit `.env` if you want different local database credentials or host port. The
example values are placeholders for local development, not production secrets.
The root `.gitignore` excludes `.env` from Git.

Validate the Compose configuration and start PostgreSQL:

```sh
docker compose config -q
docker compose up -d --wait
```

Check service health:

```sh
docker compose ps
docker compose exec -T postgres pg_isready -U nexaops -d nexaops
```

If you changed the database name or user from the example defaults, substitute
those values in the readiness command. The initialization script at
`infra/postgres/001-enable-vector.sql` enables `vector` when PostgreSQL first
initializes its data directory. To verify or enable the extension in the
configured database, run:

```sh
docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "CREATE EXTENSION IF NOT EXISTS vector;"'
docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\\dx vector"'
```

### Connection details

- From your host: `localhost:${POSTGRES_PORT:-5432}`
- From another Compose service: `postgres:5432`
- Database, username, and password: the `POSTGRES_DB`, `POSTGRES_USER`, and
  `POSTGRES_PASSWORD` values in `.env`

### Stop PostgreSQL

```sh
docker compose down
```

This stops and removes the container while keeping the named database volume.
Do not add `--volumes` / `-v` if you want to preserve database data. Application
tables are created through versioned Flyway migrations described below, not
through the container initialization script.

## Java backend

The backend uses Java 21, Maven, Spring Boot 4.1.2, and the PostgreSQL settings
from the root `.env` file. Start PostgreSQL first using the instructions above.
If you do not already have a root `.env`, create one from the example:

```sh
cp .env.example .env
```

From the repository root, source those settings and run the backend:

```sh
cd backend
set -a
source ../.env
set +a
mvn spring-boot:run
```

For admin-only document management, set private `NEXAOPS_ADMIN_USERNAME` and
`NEXAOPS_ADMIN_PASSWORD` values in the ignored root `.env` before starting the
backend. The example file leaves them blank intentionally; login returns a
configuration error until both are set. Admin sessions are opaque bearer tokens
held in backend memory for up to eight hours. They are not stored in the browser
after a page reload. The Vite proxy keeps browser requests same-origin; direct
development origins are restricted to localhost and do not use cookie
credentials.

The API listens on port `8080`. Check its structured health response with:

```sh
curl http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP","service":"nexaops-backend"}
```

Run the backend tests from the repository root with:

```sh
cd backend
mvn test
```

The tests cover the health endpoint and verify datasource settings from the
`POSTGRES_*` environment variables without requiring a live database
connection.

## Python AI service

The AI service uses the existing Python 3.12 virtual environment and connects
to the local Ollama server. It does not download models. Diagnostics use the
`OLLAMA_BASE_URL`, `OLLAMA_GENERATION_MODEL`, and `OLLAMA_EMBEDDING_MODEL`
environment variables, which default to `http://localhost:11434`, `qwen3:4b`,
and `nomic-embed-text` respectively.

From the repository root, activate the virtual environment, install the
service dependencies, and run the API:

```sh
source .venv/bin/activate
cd ai-service
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

Verify the Python service and Ollama diagnostics in another terminal:

```sh
curl http://127.0.0.1:8001/health
curl -i http://127.0.0.1:8001/health/ollama
```

The diagnostic response reports the Ollama server and each configured model
separately. If a model is missing, it includes a manual `ollama pull <model>`
command; the service never pulls models automatically. To run the mocked tests,
activate `.venv`, change to `ai-service`, and run:

```sh
python -m pytest
```

## Java-to-Python health integration

Start the services in this order: PostgreSQL, Ollama, the Python AI service,
then the Java backend. Ollama and PostgreSQL should be ready before the
application services are checked. The Python and Java services can run in
separate terminals.

The Java backend calls the Python AI service at
`http://127.0.0.1:8001` by default. Override that address with
`AI_SERVICE_BASE_URL` when starting Java. It also accepts
`AI_SERVICE_CONNECT_TIMEOUT` and `AI_SERVICE_READ_TIMEOUT` (defaults `2s` and
`4s`). The Java integration endpoint reports each service and model status
separately, so an unavailable Python service or Ollama does not crash Java.

After both application services are running, verify the end-to-end diagnostics
from the repository root:

```sh
curl -i http://127.0.0.1:8080/api/ai/health
```

The Java tests mock Python HTTP responses and do not require Ollama inference.
Run the Java and Python test suites with:

```sh
cd backend && mvn test
cd ../ai-service && ../.venv/bin/python -m pytest
```

## Frontend and live system health

The React + TypeScript frontend runs with Vite on port `5173` and calls the
Java integration endpoint at `/api/ai/health`. For local development, Vite
proxies `/api` to `http://127.0.0.1:8080`, so the browser uses the same origin
and does not need a separate CORS configuration. Set `VITE_API_BASE_URL` in
`frontend/.env.local` to override the API base URL. Set `VITE_BACKEND_URL` in
that file to change the Vite proxy target while keeping the same-origin `/api`
path. Restart Vite after changing these values.

Start services in this order: PostgreSQL, Ollama, the Python AI service, and
the Java backend. Use the startup instructions above for the database and
application services. Then, in a separate terminal:

```sh
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The **System health**
control in the top bar checks Java, Python, Ollama, and both configured models
through the Java endpoint. Open it to see individual status and diagnostic
messages; use **Refresh** to check again. If the backend is unavailable, the
panel shows an error and the local chat interface remains usable. You can
verify the response directly with:

```sh
curl http://127.0.0.1:8080/api/ai/health
```

The API base defaults to `/api` for Vite proxying. For a directly reachable
backend in another environment, set `VITE_API_BASE_URL` to its `/api` URL.
Run the frontend checks from `frontend/`:

```sh
npm run lint
npm run test
npm run build
```

The frontend test command uses Node's built-in test runner and requires a Node
version with TypeScript type stripping support (Node 22.6 or newer).

## Semantic retrieval

The Knowledge Base view searches passages stored in
`nexaops.document_chunks`. It embeds the question with the existing local
`nomic-embed-text` Ollama client, then the Python service uses a parameterized
PostgreSQL query with pgvector cosine distance (`<=>`). The query orders by
increasing cosine distance, filters out chunks whose embedding is null, and
limits results to 5 by default (configurable from 1 to 10). Returned source
fields include document and chunk IDs, filename, chunk position, page and
section information, and the original excerpt. The score is `1 - cosine
distance`; it is a similarity score, not a calibrated probability.

The Python service uses the small `psycopg` 3 driver because it previously had
no database client; it connects directly to the existing PostgreSQL database
and does not create or change schema. Set `POSTGRES_HOST`, `POSTGRES_PORT`,
`POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` to the same values used
by Compose. For a local host process, load the root `.env` before starting the
service:

```sh
cd ai-service
set -a
source ../.env
set +a
../.venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

The Python endpoint is `POST /retrieval/search`, with JSON such as
`{"query":"Who approves production database access?","top_k":5}`. The Java
public endpoint is `POST /api/retrieval/search` with the same request shape;
it forwards the call to Python and returns a typed response containing `query`,
`results`, and `result_count`. The frontend Knowledge Base view uses this Java
endpoint. Start PostgreSQL and Ollama first, then Python, Java, and Vite. Check
the health endpoints and search endpoint with:

```sh
curl http://127.0.0.1:8001/health
curl http://127.0.0.1:8080/api/health
curl http://127.0.0.1:8080/api/ai/health
curl -X POST http://127.0.0.1:8080/api/retrieval/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"Who approves production database access?","top_k":5}'
```

No model is pulled automatically. Run `mvn test` in `backend/`,
`.venv/bin/python -m pytest` in `ai-service/`, and `npm run test`,
`npm run lint`, and `npm run build` in `frontend/` to verify the retrieval
integration. The existing partial HNSW index on non-null embeddings with
`vector_cosine_ops` is reused without migration or index changes.

## Grounded RAG answers

The Knowledge Base view offers **Answer with sources** and **Search passages**.
Answer mode calls Java's `POST /api/rag/answer`; Java forwards the typed
request to Python's `POST /rag/answer`. Python calls the existing semantic
retrieval service once, which embeds the question with the configured local
`nomic-embed-text` model and searches PostgreSQL. It sends the retrieved
passages and question to the configured local Ollama generation model (default
`qwen3:4b`). The prompt treats document contents as untrusted reference data
and directs the model to answer only from that context. Ollama structured JSON
output supplies an answer and source IDs; the service validates those IDs
against this request's retrieved chunks and displays them as `[S1]`, `[S2]`,
and so on. No cloud provider or model download is used.

Generation uses the existing `OLLAMA_BASE_URL` and `OLLAMA_GENERATION_MODEL`
settings. Its timeout defaults to 120 seconds and can be changed with
`OLLAMA_GENERATION_TIMEOUT_SECONDS`. The Java-to-Python call uses the retrieval
answer read timeout (300 seconds by default via `AI_SERVICE_RAG_READ_TIMEOUT`),
allowing time for query embedding and local generation. Model output is limited
to 256 generated tokens, with thinking output disabled for concise user-facing
answers. The configured model must already be installed in
Ollama.

The response contains `query`, `answer`, `hasContext`, `citations`, and
`sources`. `citations` lists only retrieved source labels explicitly
referenced by the model, with document/chunk metadata and page information
when available. `sources` separately lists all passages retrieved for the
request, including their text, so readers can inspect the evidence even when
the model did not cite a specific passage. Unknown source labels produced by
the model are removed and never become citations. If retrieval returns no
chunks, the service returns a clear no-context response without calling the
generation model. `hasContext` is true only when the model selected at least one
retrieved source as supporting evidence; a response can include retrieved
passages while setting it false when those passages do not support an answer.
No database migration is needed.

Example request (use `/api/rag/answer` on Java or `/rag/answer` on Python):

```json
{"query":"Who approves production database access?","top_k":5}
```

Verify the Java endpoint after starting PostgreSQL, Ollama, Python, and Java:

```sh
curl -X POST http://127.0.0.1:8080/api/rag/answer \
  -H 'Content-Type: application/json' \
  -d '{"query":"Who approves production database access?","top_k":5}'
```

The frontend displays the generated answer, explicitly cited sources, and
retrieved passages in separate sections. The **Search passages** mode continues
to use semantic retrieval without answer generation.

## Knowledge engine schema

The Java backend applies versioned Flyway migrations from
`backend/src/main/resources/db/migration/` when it starts. The migration history
and application tables live in a dedicated `nexaops` schema; the existing
`public.vector` extension and its objects remain untouched.

Migration `V1__create_knowledge_schema.sql` creates:

- `nexaops.documents` for document title, original filename, media type, source
  URI, optional SHA-256 checksum and file size, processing status, status
  message, and timestamps.
- `nexaops.document_chunks` for ordered chunk text and citation locations
  (page, section, source locator, and optional offsets). Each chunk has an
  optional `public.vector(768)` embedding and records its embedding model;
  embeddings may be null while a chunk is not yet searchable.
- Flyway's `nexaops.flyway_schema_history` table for applied migration versions
  and checksums.

The schema adds primary and foreign keys, status and source checks, lookup
indexes for ingestion and citation access, and a partial HNSW cosine index for
chunks with embeddings. The document foreign key cascades when a document is
explicitly deleted. The migration does not ingest files or create application
records. Do not edit an applied migration; add a new versioned migration for
later schema changes.

### Apply and verify migrations

Start PostgreSQL as described above, then start the Java backend with the
database settings from the root `.env` file. Flyway applies pending migrations
before the backend finishes starting:

```sh
cd backend
set -a
source ../.env
set +a
mvn spring-boot:run
```

In another terminal, verify the applied version, tables, vector dimension, and
indexes without changing data:

```sh
docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, description, success FROM nexaops.flyway_schema_history ORDER BY installed_rank;"'
docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\\dt nexaops.*"'
docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\\d nexaops.document_chunks"'
docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\\di nexaops.*"'
```

The first result should show migration version `1` as successful, and the
`document_chunks.embedding` type should be `vector(768)`. The existing
`public.vector` extension remains available and can be checked with the
`\dx vector` command shown above. Run the backend tests from `backend/` with
`mvn test`; they do not require a live database connection.

## Document upload and text extraction

The Knowledge Base provides admin-only upload and document management for PDF
and UTF-8 plain-text files (`.pdf`, `.txt`, `.text`). After configuring the
admin environment variables and restarting Java, sign in in the Knowledge Base.
Uploads go to `POST /api/documents` as
multipart form data, then Java forwards the content in memory to the Python
`POST /documents/extract` endpoint. The Python service uses pypdf for PDF text
extraction; scanned/image-only or encrypted PDFs are rejected because OCR is
not included. Files are not retained on disk.

The default maximum upload is 10 MiB (`DOCUMENT_MAX_UPLOAD_BYTES`); the Python
service also limits PDFs to 500 pages (`DOCUMENT_MAX_PDF_PAGES`). Java chunks
extracted text in page order with a 1,200-character target and 150-character
overlap (`DOCUMENT_CHUNK_SIZE`, `DOCUMENT_CHUNK_OVERLAP`). Page number and
source offsets are stored with each chunk. Document metadata and chunks are
inserted in one database transaction. New chunks begin with null embeddings and
can be embedded using the Phase 7C operation below.
Repeated filenames are accepted as separate documents; each upload receives
its own UUID and content SHA-256 checksum.

Start PostgreSQL, Ollama, Python, and Java using the earlier instructions, then
start the frontend. In **Knowledge base**, sign in as the configured admin,
select one PDF or text file, and choose **Upload document**. Repeat for more
files; a completed upload refreshes the persistent document list. The list shows
filename, upload time, processing status, chunk count, and embedded chunk
count. Remove asks for confirmation and deletes only that document; the
existing foreign key cascades to its chunks and embeddings. Retrieval and RAG
remain available without admin sign-in. To check an upload directly, include
the bearer token returned from login:

```sh
curl -i -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F 'file=@./sample.txt;type=text/plain' http://127.0.0.1:8080/api/documents
```

Admin endpoints are `POST /api/admin/login`, `GET /api/admin/documents`,
`DELETE /api/admin/documents/{documentId}`, and `POST /api/admin/logout`.
Send `Authorization: Bearer <token>` on upload, listing, deletion, embedding,
and logout requests. Login accepts JSON with `username` and `password` and
returns `token` and `expiresAt`. A missing or expired token receives HTTP 401;
login with unset backend credentials receives HTTP 503. The browser keeps the
token in memory and prompts for sign-in after expiration or a page reload.

The success response includes `documentId`, `filename`, `status`,
`characterCount`, `pageCount`, `chunkCount`, and `embeddingCount` (zero until
embeddings are implemented). Unsupported formats return 415, empty or invalid
files return 400 or 422, oversized uploads return 413, and unavailable Python
service responses are reported as 503. Do not use `.env.example`'s placeholder
password with an already initialized database unless it matches that database;
keep the existing local database password in the untracked `.env`.

## Generate local document embeddings

After uploading a document, request embeddings for its chunks with:

```sh
curl -i -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://127.0.0.1:8080/api/documents/<document-id>/embeddings
```

Java reads chunks that still have null embeddings in chunk order, requests
batches from Python's `POST /embeddings/generate` endpoint, and validates that
all batches match the configured model and contain exactly 768 finite numeric
values per chunk. Python calls the local Ollama `POST /api/embed` endpoint with
the configured `nomic-embed-text` model. Ollama accepts a list of input texts
for batched embedding generation; no model download or cloud service is used.
[Ollama API reference](https://github.com/ollama/ollama/blob/main/docs/api.md),
[embedding guide](https://github.com/ollama/ollama/blob/main/docs/capabilities/embeddings.mdx).

The success response reports `documentId`, `embeddingModel`,
`totalChunkCount`, `newlyEmbeddedChunkCount`, `alreadyEmbeddedChunkCount`,
`failedChunkCount`, and `status`. Repeating the request skips chunks that
already have vectors. If Ollama or any batch fails, Java does not write any of
the vectors from that attempt. Validated vectors are written with bound SQL
parameters in one transaction and the existing `embedding_model` field is set
to the model that generated them. Database failures roll the whole write back.

Embedding calls use the configured `OLLAMA_BASE_URL` and
`OLLAMA_EMBEDDING_MODEL`. Python's default timeout is 120 seconds per batch
(`OLLAMA_EMBEDDING_TIMEOUT_SECONDS`), maximum batch size is 32
(`OLLAMA_EMBEDDING_MAX_BATCH`), and text size is limited to 10,000 characters
per input (`OLLAMA_EMBEDDING_MAX_TEXT_CHARS`). Java sends batches of 32 by
default (`AI_SERVICE_EMBEDDING_BATCH_SIZE`) and allows 180 seconds for Python
(`AI_SERVICE_EMBEDDING_READ_TIMEOUT`). Keep Java and Python batch-size settings
aligned if you override them. A missing model returns a diagnostic with the
manual `ollama pull nomic-embed-text` command; the service never pulls it.

Run the endpoint only after starting PostgreSQL, Ollama, the Python service,
and the Java backend. The existing database schema already contains the
`vector(768)` column, so this phase adds no migration. Existing vectors are not
overwritten, document statuses are unchanged, and embeddings are not generated
automatically during upload.
