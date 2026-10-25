import { resolveApiBaseUrl } from './aiHealth.ts'
import { uploadDocument, type DocumentUploadResult } from './documents.ts'

export type AdminLoginResult = { token: string; expiresAt: string }
export type ManagedDocument = {
  documentId: string
  filename: string
  uploadedAt: string
  status: string
  chunkCount: number
  embeddedChunkCount: number
}

export class AdminApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'AdminApiError'
    this.status = status
  }
}

export async function loginAdmin(
  username: string,
  password: string,
  fetcher: typeof fetch = fetch,
): Promise<AdminLoginResult> {
  const response = await fetcher(`${resolveApiBaseUrl()}/admin/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const body: unknown = await response.json().catch(() => null)
  if (!response.ok) throw responseError(response.status, body, 'Admin sign-in failed.')
  if (!isLoginResult(body)) throw new Error('The backend returned an unexpected sign-in response.')
  return body
}

export async function listAdminDocuments(token: string, fetcher: typeof fetch = fetch): Promise<ManagedDocument[]> {
  const response = await fetcher(`${resolveApiBaseUrl()}/admin/documents`, {
    headers: authHeaders(token), cache: 'no-store',
  })
  const body: unknown = await response.json().catch(() => null)
  if (!response.ok) throw responseError(response.status, body, 'The document list could not be loaded.')
  if (!Array.isArray(body) || body.some((item) => !isManagedDocument(item))) {
    throw new Error('The backend returned an unexpected document list.')
  }
  return body
}

export async function uploadAdminDocument(
  file: File,
  token: string,
  fetcher: typeof fetch = fetch,
): Promise<DocumentUploadResult> {
  return uploadDocument(file, fetcher, token)
}

export async function deleteAdminDocument(token: string, documentId: string, fetcher: typeof fetch = fetch): Promise<void> {
  const response = await fetcher(`${resolveApiBaseUrl()}/admin/documents/${encodeURIComponent(documentId)}`, {
    method: 'DELETE', headers: authHeaders(token),
  })
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null)
    throw responseError(response.status, body, 'The document could not be removed.')
  }
}

export async function logoutAdmin(token: string, fetcher: typeof fetch = fetch): Promise<void> {
  const response = await fetcher(`${resolveApiBaseUrl()}/admin/logout`, {
    method: 'POST', headers: authHeaders(token),
  })
  if (!response.ok && response.status !== 401) {
    const body: unknown = await response.json().catch(() => null)
    throw responseError(response.status, body, 'Sign-out could not be completed.')
  }
}

export function confirmDocumentRemoval(filename: string, confirmAction: (message: string) => boolean): boolean {
  return confirmAction(`Remove “${filename}” and its stored passages? This cannot be undone.`)
}

function authHeaders(token: string): HeadersInit {
  return { Authorization: `Bearer ${token}`, Accept: 'application/json' }
}

function responseError(status: number, body: unknown, fallback: string): AdminApiError {
  const message = typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string'
    ? body.message : `${fallback} (HTTP ${status}).`
  return new AdminApiError(status, message)
}

function isLoginResult(value: unknown): value is AdminLoginResult {
  return typeof value === 'object' && value !== null &&
    typeof (value as Record<string, unknown>).token === 'string' &&
    typeof (value as Record<string, unknown>).expiresAt === 'string'
}

function isManagedDocument(value: unknown): value is ManagedDocument {
  if (typeof value !== 'object' || value === null) return false
  const row = value as Record<string, unknown>
  return typeof row.documentId === 'string' && typeof row.filename === 'string' &&
    typeof row.uploadedAt === 'string' && typeof row.status === 'string' &&
    typeof row.chunkCount === 'number' && typeof row.embeddedChunkCount === 'number'
}
