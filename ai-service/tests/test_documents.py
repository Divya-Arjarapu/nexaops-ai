from io import BytesIO

import httpx
from pypdf import PdfWriter

from app.main import app
from app.routes import documents as document_routes
from tests.helpers import get_response


def _pdf_with_pages(*texts: str) -> bytes:
    page_numbers = [4 + index * 2 for index in range(len(texts))]
    stream_numbers = [page_number + 1 for page_number in page_numbers]
    kids = " ".join(f"{page_number} 0 R" for page_number in page_numbers)

    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        f"<< /Type /Pages /Kids [{kids}] /Count {len(texts)} >>".encode(),
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]

    for index, text in enumerate(texts):
        page_number = page_numbers[index]
        stream_number = stream_numbers[index]
        page = (
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] "
            f"/Resources << /Font << /F1 3 0 R >> >> "
            f"/Contents {stream_number} 0 R >>"
        ).encode()
        stream = f"BT /F1 12 Tf 40 200 Td ({text}) Tj ET".encode()
        objects.append(page)
        objects.append(f"<< /Length {len(stream)} >>\nstream\n".encode() + stream + b"\nendstream")

    content = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for number, body in enumerate(objects, start=1):
        offsets.append(len(content))
        content.extend(f"{number} 0 obj\n".encode())
        content.extend(body)
        content.extend(b"\nendobj\n")
    xref_offset = len(content)
    content.extend(f"xref\n0 {len(offsets)}\n".encode())
    content.extend(b"0000000000 65535 f \n")
    for offset in offsets[1:]:
        content.extend(f"{offset:010d} 00000 n \n".encode())
    content.extend(
        f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\n"
        f"startxref\n{xref_offset}\n%%EOF\n".encode()
    )
    return bytes(content)


def _encrypted_pdf() -> bytes:
    writer = PdfWriter()
    writer.add_blank_page(width=300, height=300)
    writer.encrypt("private-password")
    stream = BytesIO()
    writer.write(stream)
    return stream.getvalue()


def _upload(
    name: str,
    content: bytes,
    content_type: str,
) -> httpx.Response:
    async def send_request() -> httpx.Response:
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://testserver",
        ) as client:
            return await client.post(
                "/documents/extract",
                files={"file": (name, content, content_type)},
            )

    import asyncio

    return asyncio.run(send_request())


def test_extracts_plain_text_and_sanitizes_filename() -> None:
    response = _upload("../meeting notes.txt", b"First line\nSecond line", "text/plain")

    assert response.status_code == 200
    assert response.json() == {
        "filename": "meeting notes.txt",
        "media_type": "text/plain",
        "pages": [{"page_number": None, "text": "First line\nSecond line"}],
        "character_count": 22,
        "page_count": None,
    }


def test_extracts_pdf_text_with_page_numbers_in_order() -> None:
    response = _upload(
        "report.pdf",
        _pdf_with_pages("Page one text", "Page two text"),
        "application/pdf",
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["page_count"] == 2
    assert [(page["page_number"], page["text"].strip()) for page in payload["pages"]] == [
        (1, "Page one text"),
        (2, "Page two text"),
    ]


def test_rejects_invalid_pdf() -> None:
    response = _upload("broken.pdf", b"%PDF-1.4\nnot a valid PDF", "application/pdf")

    assert response.status_code == 422
    assert response.json()["detail"] == "The PDF could not be read or extracted."


def test_rejects_empty_file() -> None:
    response = _upload("empty.txt", b"", "text/plain")

    assert response.status_code == 400
    assert response.json()["detail"] == "The uploaded document is empty."


def test_rejects_unsupported_type_and_mismatched_content_type() -> None:
    unsupported = _upload("notes.docx", b"hello", "application/octet-stream")
    mismatch = _upload("notes.txt", b"hello", "application/pdf")

    assert unsupported.status_code == 415
    assert mismatch.status_code == 415


def test_rejects_pdf_without_extractable_text() -> None:
    blank = PdfWriter()
    blank.add_blank_page(width=300, height=300)
    stream = BytesIO()
    blank.write(stream)

    response = _upload("scan.pdf", stream.getvalue(), "application/pdf")

    assert response.status_code == 422
    assert "Scanned PDFs are not supported" in response.json()["detail"]


def test_rejects_encrypted_pdf() -> None:
    response = _upload("locked.pdf", _encrypted_pdf(), "application/pdf")

    assert response.status_code == 422
    assert response.json()["detail"] == "Encrypted PDF documents are not supported."


def test_enforces_upload_size_limit(monkeypatch) -> None:
    monkeypatch.setattr(document_routes, "MAX_UPLOAD_BYTES", 4)

    response = _upload("long.txt", b"12345", "text/plain")

    assert response.status_code == 413


def test_rejects_non_utf8_text() -> None:
    response = _upload("binary.txt", b"\xff\xfe", "text/plain")

    assert response.status_code == 422
