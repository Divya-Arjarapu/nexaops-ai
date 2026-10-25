import assert from 'node:assert/strict'
import test from 'node:test'
import {
  RagAnswerCoordinator,
  requestRagAnswer,
  type RagAnswerResponse,
} from './rag.ts'

const response: RagAnswerResponse = {
  query: 'Who approves database access?',
  answer: 'A system administrator approves access [S1].',
  hasContext: true,
  citations: [{
    sourceId: 'S1', documentId: 'doc-1', filename: 'policy.pdf', chunkId: 'chunk-1',
    chunkIndex: 0, pageNumber: 4, sectionTitle: 'Approvals', sourceLocator: 'Page 4',
    similarityScore: 0.84,
  }],
  sources: [{
    sourceId: 'S1', documentId: 'doc-1', filename: 'policy.pdf', chunkId: 'chunk-1',
    chunkIndex: 0, pageNumber: 4, sectionTitle: 'Approvals', sourceLocator: 'Page 4',
    similarityScore: 0.84, text: 'Production database access requires administrator approval.',
  }],
}

test('submits a question to the Java RAG endpoint and validates citations and sources', async () => {
  const fetcher: typeof fetch = async (input, init) => {
    assert.equal(input, '/api/rag/answer')
    assert.equal(init?.method, 'POST')
    assert.deepEqual(JSON.parse(String(init?.body)), { query: response.query, top_k: 5 })
    return Response.json(response)
  }
  assert.deepEqual(await requestRagAnswer({ query: ` ${response.query} `, topK: 5 }, fetcher), response)
})

test('coordinator reports loading, answer, and no-context states', async () => {
  const coordinator = new RagAnswerCoordinator(async () => response)
  const states: string[] = []
  await coordinator.submit({ query: response.query, topK: 5 }, (state) => states.push(state.status))
  assert.deepEqual(states, ['loading', 'success'])

  const noContext: RagAnswerResponse = {
    query: response.query, answer: 'The retrieved passage does not support this question.', hasContext: false,
    citations: [], sources: response.sources,
  }
  const emptyCoordinator = new RagAnswerCoordinator(async () => noContext)
  await emptyCoordinator.submit({ query: response.query, topK: 5 }, (state) => states.push(state.status))
  assert.equal(states.at(-1), 'empty')
})

test('reports backend errors and blocks duplicate submissions during generation', async () => {
  let release!: (value: RagAnswerResponse) => void
  let calls = 0
  const pending = new Promise<RagAnswerResponse>((resolve) => { release = resolve })
  const coordinator = new RagAnswerCoordinator(async () => { calls += 1; return pending })
  const states: string[] = []
  const first = coordinator.submit({ query: response.query, topK: 5 }, (state) => states.push(state.status))
  await coordinator.submit({ query: response.query, topK: 5 }, (state) => states.push(state.status))
  assert.equal(calls, 1)
  release(response)
  await first
  assert.deepEqual(states, ['loading', 'success'])

  const failing = new RagAnswerCoordinator(async () => { throw new Error('Ollama unavailable.') })
  await failing.submit({ query: response.query, topK: 5 }, (state) => states.push(state.status))
  assert.equal(states.at(-1), 'error')
})
