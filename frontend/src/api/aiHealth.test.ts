import assert from 'node:assert/strict'
import test from 'node:test'
import {
  fetchAiIntegrationHealth,
  isAiIntegrationHealth,
  resolveApiBaseUrl,
} from './aiHealth.ts'

const healthyResponse = {
  javaBackend: { status: 'UP', message: 'Java backend is responding.' },
  pythonAiService: { status: 'UP', message: 'Python AI service is reachable.' },
  ollama: { status: 'UP', message: 'Ollama server is reachable.' },
  generationModel: {
    model: 'qwen3:4b',
    status: 'UP',
    available: true,
    message: 'Model is installed.',
  },
  embeddingModel: {
    model: 'nomic-embed-text',
    status: 'UP',
    available: true,
    message: 'Model is installed.',
  },
}

test('uses the same-origin API path by default and trims configured URLs', () => {
  assert.equal(resolveApiBaseUrl(), '/api')
  assert.equal(resolveApiBaseUrl(' https://api.example.test/api/ '), 'https://api.example.test/api')
})

test('accepts the structured Java integration health response', () => {
  assert.equal(isAiIntegrationHealth(healthyResponse), true)
  assert.equal(isAiIntegrationHealth({ ...healthyResponse, ollama: { status: 'BROKEN' } }), false)
})

test('preserves partial service and missing-model diagnostics', async () => {
  const partialResponse = {
    ...healthyResponse,
    ollama: { status: 'DOWN', message: 'Ollama is not reachable.' },
    generationModel: {
      model: 'qwen3:4b',
      status: 'DOWN',
      available: false,
      message: 'Model is missing.',
    },
  }
  const fetcher: typeof fetch = async () => Response.json(partialResponse)

  const result = await fetchAiIntegrationHealth(fetcher)
  assert.equal(result.ollama.status, 'DOWN')
  assert.equal(result.generationModel.available, false)
  assert.equal(result.embeddingModel.available, true)
})

test('fetches and validates the backend health response', async () => {
  const fetcher: typeof fetch = async (input) => {
    assert.equal(input, '/api/ai/health')
    return Response.json(healthyResponse)
  }

  const result = await fetchAiIntegrationHealth(fetcher)
  assert.equal(result.generationModel.model, 'qwen3:4b')
  assert.equal(result.embeddingModel.available, true)
})

test('reports unsuccessful HTTP responses and invalid payloads', async () => {
  const unavailable: typeof fetch = async () => new Response(null, { status: 503 })
  await assert.rejects(fetchAiIntegrationHealth(unavailable), /HTTP 503/)

  const invalid: typeof fetch = async () => Response.json({ status: 'UP' })
  await assert.rejects(fetchAiIntegrationHealth(invalid), /unexpected health response/)
})
