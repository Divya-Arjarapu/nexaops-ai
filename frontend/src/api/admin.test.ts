import assert from 'node:assert/strict'
import test from 'node:test'
import {
  AdminApiError,
  confirmDocumentRemoval,
  deleteAdminDocument,
  listAdminDocuments,
  loginAdmin,
  uploadAdminDocument,
} from './admin.ts'

test('admin sign-in submits credentials and validates its expiring session response', async () => {
  const fetcher: typeof fetch = async (input, init) => {
    assert.equal(input, '/api/admin/login')
    assert.equal(init?.method, 'POST')
    assert.deepEqual(JSON.parse(String(init?.body)), { username: 'admin', password: 'secret' })
    return Response.json({ token: 'opaque-token', expiresAt: '2026-10-25T10:00:00Z' })
  }
  assert.deepEqual(await loginAdmin('admin', 'secret', fetcher), {
    token: 'opaque-token', expiresAt: '2026-10-25T10:00:00Z',
  })
})

test('sign-in distinguishes invalid credentials and missing backend configuration', async () => {
  const invalid: typeof fetch = async () => Response.json({ message: 'The username or password is incorrect.' }, { status: 401 })
  await assert.rejects(loginAdmin('admin', 'wrong', invalid), (error: unknown) =>
    error instanceof AdminApiError && error.status === 401 && /incorrect/.test(error.message))
  const unconfigured: typeof fetch = async () => Response.json({ message: 'Admin sign-in is not configured.' }, { status: 503 })
  await assert.rejects(loginAdmin('admin', 'secret', unconfigured), /not configured/)
})

test('authenticated list and delete calls carry the bearer token and metadata only', async () => {
  const fetcher: typeof fetch = async (input, init) => {
    assert.match(String(input), /^\/api\/admin\/documents/)
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer opaque-token')
    if (init?.method === 'DELETE') return new Response(null, { status: 204 })
    return Response.json([{
      documentId: 'doc-1', filename: 'policy.txt', uploadedAt: '2026-10-24T12:30:00Z',
      status: 'ready', chunkCount: 4, embeddedChunkCount: 3,
    }])
  }
  const rows = await listAdminDocuments('opaque-token', fetcher)
  assert.equal(rows[0]?.filename, 'policy.txt')
  assert.equal(rows[0]?.embeddedChunkCount, 3)
  await deleteAdminDocument('opaque-token', 'doc-1', fetcher)
})

test('repeated uploads remain separate single-file requests and send authorization', async () => {
  let calls = 0
  const fetcher: typeof fetch = async (input, init) => {
    calls += 1
    assert.equal(input, '/api/documents')
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer opaque-token')
    assert.ok(init?.body instanceof FormData)
    return Response.json({ documentId: `doc-${calls}`, filename: 'same.txt', status: 'ready',
      characterCount: 5, pageCount: null, chunkCount: 1, embeddingCount: 0, message: 'ok' }, { status: 201 })
  }
  const file = new File(['hello'], 'same.txt', { type: 'text/plain' })
  assert.equal((await uploadAdminDocument(file, 'opaque-token', fetcher)).documentId, 'doc-1')
  assert.equal((await uploadAdminDocument(file, 'opaque-token', fetcher)).documentId, 'doc-2')
  assert.equal(calls, 2)
})

test('expired authorization errors are preserved and removal requires confirmation', async () => {
  const expired: typeof fetch = async () => Response.json({ message: 'Admin sign-in has expired.' }, { status: 401 })
  await assert.rejects(listAdminDocuments('expired', expired), (error: unknown) =>
    error instanceof AdminApiError && error.status === 401)
  let prompt = ''
  assert.equal(confirmDocumentRemoval('policy.txt', (message) => { prompt = message; return false }), false)
  assert.match(prompt, /policy\.txt/)
  assert.match(prompt, /cannot be undone/)
})
