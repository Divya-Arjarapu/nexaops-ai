import os
from io import BytesIO

from fastapi import APIRouter, File, HTTPException, UploadFile
from pypdf import PdfReader
from pypdf.errors import PdfReadError, PdfStreamError

from app.schemas import DocumentExtractionResponse, ExtractedPage

router = APIRouter()
MAX_UPLOAD_BYTES = int(os.getenv("DOCUMENT_MAX_UPLOAD_BYTES", "10485760"))
MAX_PDF_PAGES = int(os.getenv("DOCUMENT_MAX_PDF_PAGES", "500"))
MAX_EXTRACTED_CHARACTERS = 10_000_000
ALLOWED_TEXT_MIME_TYPES = {"text/plain", "application/octet-stream"}
ALLOWED_PDF_MIME_TYPES = {"application/pdf", "application/octet-stream"}


@router.post(
    "/documents/extract",
    response_model=DocumentExtractionResponse,
    responses={400: {}, 413: {}, 415: {}, 422: {}},
)
async def extract_document(file: UploadFile = File(...)) -> DocumentExtractionResponse:
    filename = _safe_filename(file.filename)
    extension = filename.rsplit(".", 1)[-1].casefold() if "." in filename else ""
    media_type = (file.content_type or "").split(";", 1)[0].strip().casefold()

    if extension == "pdf":
        if media_type not in ALLOWED_PDF_MIME_TYPES:
            raise HTTPException(415, "PDF uploads must use application/pdf content type.")
        expected_media_type = "application/pdf"
    elif extension in {"txt", "text"}:
        if media_type not in ALLOWED_TEXT_MIME_TYPES:
            raise HTTPException(415, "Text uploads must use text/plain content type.")
        expected_media_type = "text/plain"
    else:
        raise HTTPException(415, "Only PDF and plain-text documents are supported.")

    try:
        if file.size is not None and file.size > MAX_UPLOAD_BYTES:
            raise HTTPException(413, "The document exceeds the upload size limit.")
        content = await file.read(MAX_UPLOAD_BYTES + 1)
    finally:
        await file.close()

    if not content:
        raise HTTPException(400, "The uploaded document is empty.")
    if len(content) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, "The document exceeds the upload size limit.")

    if expected_media_type == "application/pdf":
        pages, page_count = _extract_pdf(content)
    else:
        pages, page_count = _extract_text(content)

    character_count = sum(len(page.text) for page in pages)
    if character_count > MAX_EXTRACTED_CHARACTERS:
        raise HTTPException(413, "The extracted document text exceeds the processing limit.")

    return DocumentExtractionResponse(
        filename=filename,
        media_type=expected_media_type,
        pages=pages,
        character_count=character_count,
        page_count=page_count,
    )


def _safe_filename(filename: str | None) -> str:
    normalized = (filename or "").replace("\\", "/").rsplit("/", 1)[-1].strip()
    if not normalized or normalized in {".", ".."}:
        raise HTTPException(400, "A valid filename is required.")
    if any(ord(character) < 32 or ord(character) == 127 for character in normalized):
        raise HTTPException(400, "The filename contains invalid characters.")
    if len(normalized) > 255:
        raise HTTPException(400, "The filename is too long.")
    return normalized


def _extract_text(content: bytes) -> tuple[list[ExtractedPage], None]:
    try:
        text = content.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise HTTPException(422, "The text document must be valid UTF-8.") from exc

    if "\x00" in text or any(
        ord(character) < 32 and character not in "\n\r\t\f" for character in text
    ):
        raise HTTPException(415, "The uploaded file is not plain text.")
    if not text.strip():
        raise HTTPException(422, "The text document contains no extractable text.")
    return [ExtractedPage(page_number=None, text=text)], None


def _extract_pdf(content: bytes) -> tuple[list[ExtractedPage], int]:
    if b"%PDF-" not in content[:1024]:
        raise HTTPException(422, "The uploaded file is not a valid PDF document.")

    try:
        reader = PdfReader(BytesIO(content), strict=True)
        if reader.is_encrypted:
            raise HTTPException(422, "Encrypted PDF documents are not supported.")
        page_count = len(reader.pages)
        if page_count > MAX_PDF_PAGES:
            raise HTTPException(413, "The PDF exceeds the page limit.")
        pages = [
            ExtractedPage(page_number=page_number, text=page.extract_text() or "")
            for page_number, page in enumerate(reader.pages, start=1)
        ]
    except HTTPException:
        raise
    except (PdfReadError, PdfStreamError, ValueError, TypeError, OSError) as exc:
        raise HTTPException(422, "The PDF could not be read or extracted.") from exc
    except Exception as exc:
        # pypdf may surface malformed object graphs as implementation-specific errors.
        raise HTTPException(422, "The PDF could not be read or extracted.") from exc

    if not any(page.text.strip() for page in pages):
        raise HTTPException(
            422,
            "The PDF contains no extractable text. Scanned PDFs are not supported in this phase.",
        )
    return pages, page_count
