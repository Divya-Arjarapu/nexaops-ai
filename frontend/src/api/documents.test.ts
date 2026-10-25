import assert from 'node:assert/strict'
import test from 'node:test'
import { DocumentUploadCoordinator, uploadDocument, validateDocumentFile, type DocumentUploadResult } from './documents.ts'

test('validates supported document types and local upload limit', () => {
  assert.match(validateDocumentFile(new File(['x'], 'notes.docx')) ?? '', /PDF or plain-text/)
  assert.match(validateDocumentFile(new File([], 'empty.txt')) ?? '', /empty/)
  assert.match(validateDocumentFile(new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'big.txt')) ?? '', /10 MiB/)
  assert.equal(validateDocumentFile(new File(['hello'], 'notes.txt')), null)
})

test('sends multipart file and accepts structured success response', async () => {
  const file = new File(['hello'], 'notes.txt', { type: 'text/plain' })
  const fetcher: typeof fetch = async (input, init) => {
    assert.equal(input, '/api/documents')
    assert.equal(init?.method, 'POST')
    const body = init?.body
    assert.ok(body instanceof FormData)
    assert.equal(body.get('file'), file)
    return Response.json({ documentId: 'id-1', filename: 'notes.txt', status: 'ready', characterCount: 5, pageCount: null, chunkCount: 1, embeddingCount: 0, message: 'ok' }, { status: 201 })
  }
  assert.equal((await uploadDocument(file, fetcher)).documentId, 'id-1')
})

test('surfaces safe server errors and rejects malformed success payloads', async () => {
  const file = new File(['hello'], 'notes.txt')
  const rejected: typeof fetch = async () => Response.json({ message: 'Only PDF and plain-text documents are supported.' }, { status: 415 })
  await assert.rejects(uploadDocument(file, rejected), /Only PDF and plain-text/)
  const invalid: typeof fetch = async () => Response.json({ ok: true })
  await assert.rejects(uploadDocument(file, invalid), /unexpected upload response/)
})

test('reports loading and success while refusing duplicate submissions', async () => {
  let release!: (result: DocumentUploadResult) => void
  let calls = 0
  const result: DocumentUploadResult = { documentId: 'id-2', filename: 'notes.txt', status: 'ready', characterCount: 5, pageCount: null, chunkCount: 1, embeddingCount: 0, message: 'ok' }
  const coordinator = new DocumentUploadCoordinator(async () => {
    calls += 1
    return new Promise((resolve) => { release = resolve })
  })
  const states: string[] = []
  const file = new File(['hello'], 'notes.txt')
  const first = coordinator.submit(file, (state) => states.push(state.status))
  await coordinator.submit(file, (state) => states.push(state.status))
  assert.equal(calls, 1)
  assert.deepEqual(states, ['loading'])
  release(result)
  await first
  assert.deepEqual(states, ['loading', 'success'])
})

test('reports validation and server failures as upload states', async () => {
  const invalidStates: string[] = []
  await new DocumentUploadCoordinator().submit(new File(['x'], 'brief.docx'), (state) => invalidStates.push(`${state.status}:${state.message}`))
  assert.match(invalidStates[0] ?? '', /^error:Choose a PDF/)

  const failedStates: string[] = []
  await new DocumentUploadCoordinator(async () => { throw new Error('Python service unavailable.') })
    .submit(new File(['hello'], 'notes.txt'), (state) => failedStates.push(`${state.status}:${state.message}`))
  assert.deepEqual(failedStates, ['loading:Uploading document…', 'error:Python service unavailable.'])
})
