import asyncio

import httpx
from fastapi import FastAPI


def get_response(app: FastAPI, path: str) -> httpx.Response:
    async def send_request() -> httpx.Response:
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://testserver",
        ) as client:
            return await client.get(path)

    return asyncio.run(send_request())
