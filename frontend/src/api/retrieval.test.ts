import assert from 'node:assert/strict'
import test from 'node:test'
import {
  RetrievalSearchCoordinator,
  searchKnowledge,
  type RetrievalSearchResponse,
} from './retrieval.ts'

const response: RetrievalSearchResponse = {
  query: 'Who approves database access?',
  resultCount: 1,
  results: [{
    documentId: 'doc-1', filename: 'operations.txt', chunkId: 'chunk-1', chunkIndex: 0,
    pageNumber: null, sectionTitle: null, sourceLocator: null,
    text: 'An administrator approves database access.', similarityScore: 0.82,
  }],
}

test('submits a trimmed query to the Java retrieval API and validates source results', async () => {
  const fetcher: typeof fetch = async (input, init) => {
    assert.equal(input, '/api/retrieval/search')
    assert.equal(init?.method, 'POST')
    assert.deepEqual(JSON.parse(String(init?.body)), { query: 'Who approves database access?', top_k: 5 })
    return Response.json({
      query: response.query,
      resultCount: 1,
      results: response.results,
    })
  }
  assert.deepEqual(await searchKnowledge({ query: ` ${response.query} `, topK: 5 }, fetcher), response)
})

test('coordinator reports loading, success, and empty-result states', async () => {
  const states: string[] = []
  const coordinator = new RetrievalSearchCoordinator(async () => response)
  await coordinator.submit({ query: response.query, topK: 5 }, (state) => states.push(state.status))
  assert.deepEqual(states, ['loading', 'success'])

  const empty = new RetrievalSearchCoordinator(async () => ({ ...response, results: [], resultCount: 0 }))
  await empty.submit({ query: response.query, topK: 5 }, (state) => states.push(state.status))
  assert.equal(states.at(-1), 'empty')
})

test('coordinator reports API errors and prevents duplicate submissions', async () => {
  let release!: (value: RetrievalSearchResponse) => void
  let calls = 0
  const pending = new Promise<RetrievalSearchResponse>((resolve) => { release = resolve })
  const coordinator = new RetrievalSearchCoordinator(async () => { calls += 1; return pending })
  const states: string[] = []
  const first = coordinator.submit({ query: response.query, topK: 5 }, (state) => states.push(state.status))
  await coordinator.submit({ query: response.query, topK: 5 }, (state) => states.push(state.status))
  assert.equal(calls, 1)
  release(response)
  await first
  assert.deepEqual(states, ['loading', 'success'])

  const failing = new RetrievalSearchCoordinator(async () => { throw new Error('Backend unavailable.') })
  await failing.submit({ query: response.query, topK: 5 }, (state) => states.push(state.status))
  assert.equal(states.at(-1), 'error')
})
