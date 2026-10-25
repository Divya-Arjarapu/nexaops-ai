from typing import Any

import json
import re
from dataclasses import dataclass

import httpx


class OllamaClientError(RuntimeError):
    """Raised when Ollama cannot return a valid diagnostic response."""

    def __init__(self, message: str, status_code: int = 502) -> None:
        super().__init__(message)
        self.status_code = status_code


@dataclass(frozen=True)
class GeneratedAnswer:
    answer: str
    source_ids: list[str]


class OllamaClient:
    """Small HTTP client for Ollama diagnostics, isolated from API routes."""

    def __init__(
        self,
        base_url: str,
        timeout_seconds: float = 3.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout_seconds = timeout_seconds
        self._transport = transport

    async def get_version(self) -> str:
        payload = await self._get_json("/api/version")
        version = payload.get("version")
        if not isinstance(version, str) or not version:
            raise OllamaClientError("Ollama returned no server version.")
        return version

    async def list_models(self) -> list[str]:
        payload = await self._get_json("/api/tags")
        models = payload.get("models")
        if not isinstance(models, list):
            raise OllamaClientError("Ollama returned an invalid model list.")

        names: list[str] = []
        for entry in models:
            if not isinstance(entry, dict):
                raise OllamaClientError("Ollama returned an invalid model entry.")
            name = entry.get("name") or entry.get("model")
            if not isinstance(name, str) or not name:
                raise OllamaClientError("Ollama returned a model without a name.")
            names.append(name)
        return names

    async def generate(self, model: str, prompt: str, system_prompt: str) -> GeneratedAnswer:
        """Generate a non-streaming response using Ollama's local API."""
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=self._timeout_seconds,
                transport=self._transport,
            ) as client:
                response = await client.post(
                    "/api/generate",
                    json={
                        "model": model,
                        "prompt": prompt,
                        "system": system_prompt,
                        "format": {
                            "type": "object",
                            "properties": {
                                "answer": {"type": "string"},
                                "source_ids": {
                                    "type": "array",
                                    "items": {"type": "string", "pattern": "^S[1-9][0-9]*$"},
                                },
                            },
                            "required": ["answer", "source_ids"],
                            "additionalProperties": False,
                        },
                        "stream": False,
                        "think": False,
                        "options": {"num_predict": 256},
                    },
                )
                response.raise_for_status()
                payload = response.json()
        except httpx.TimeoutException as exc:
            raise OllamaClientError("Ollama generation timed out.", 503) from exc
        except httpx.ConnectError as exc:
            raise OllamaClientError("Ollama generation service is unavailable.", 503) from exc
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code == 404:
                raise OllamaClientError(
                    "The configured Ollama generation model is unavailable. Check that it is installed locally.",
                    503,
                ) from exc
            if exc.response.status_code >= 500:
                raise OllamaClientError("Ollama could not generate an answer.", 503) from exc
            raise OllamaClientError("Ollama rejected the generation request.", 502) from exc
        except (httpx.HTTPError, ValueError) as exc:
            raise OllamaClientError("Ollama returned an invalid generation response.", 502) from exc

        if not isinstance(payload, dict):
            raise OllamaClientError("Ollama returned an invalid generation response.", 502)
        returned_model = payload.get("model")
        answer = payload.get("response")
        if (
            not isinstance(returned_model, str)
            or canonical_model_name(returned_model) != canonical_model_name(model)
            or not isinstance(answer, str)
            or not answer.strip()
        ):
            raise OllamaClientError("Ollama returned an empty or malformed answer.", 502)
        try:
            structured = json.loads(answer)
        except (TypeError, ValueError) as exc:
            raise OllamaClientError("Ollama returned a malformed structured answer.", 502) from exc
        if (
            not isinstance(structured, dict)
            or set(structured) != {"answer", "source_ids"}
            or not isinstance(structured["answer"], str)
            or not structured["answer"].strip()
            or not isinstance(structured["source_ids"], list)
            or any(
                not isinstance(source_id, str)
                or re.fullmatch(r"S[1-9][0-9]*", source_id) is None
                for source_id in structured["source_ids"]
            )
        ):
            raise OllamaClientError("Ollama returned a malformed structured answer.", 502)
        return GeneratedAnswer(
            answer=structured["answer"].strip(),
            source_ids=list(dict.fromkeys(structured["source_ids"])),
        )

    async def _get_json(self, path: str) -> dict[str, Any]:
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=self._timeout_seconds,
                transport=self._transport,
            ) as client:
                response = await client.get(path)
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise OllamaClientError(
                f"Could not read {path} from Ollama at {self._base_url}: {exc}"
            ) from exc

        if not isinstance(payload, dict):
            raise OllamaClientError(f"Ollama returned an invalid response for {path}.")
        return payload


def canonical_model_name(name: str) -> str:
    """Treat an omitted tag as Ollama's default `latest` tag."""
    normalized = name.strip().casefold()
    return normalized if ":" in normalized else f"{normalized}:latest"
