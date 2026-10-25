import { resolveApiBaseUrl } from './aiHealth.ts'

export type DocumentUploadResult = {
  documentId: string
  filename: string
  status: string
  characterCount: number
  pageCount: number | null
  chunkCount: number
  embeddingCount: number
  message: string
}

export type DocumentUploadState = { status: 'loading' | 'success' | 'error'; message: string; result?: DocumentUploadResult }
type DocumentUploader = (file: File) => Promise<DocumentUploadResult>

export class DocumentUploadCoordinator {
  private inFlight = false
  private readonly uploader: DocumentUploader

  constructor(uploader: DocumentUploader = uploadDocument) {
    this.uploader = uploader
  }

  async submit(file: File, onState: (state: DocumentUploadState) => void): Promise<void> {
    if (this.inFlight) return
    const validationError = validateDocumentFile(file)
    if (validationError) {
      onState({ status: 'error', message: validationError })
      return
    }
    this.inFlight = true
    onState({ status: 'loading', message: 'Uploading document…' })
    try {
      const result = await this.uploader(file)
      onState({ status: 'success', message: 'Document uploaded and text extracted.', result })
    } catch (error) {
      onState({ status: 'error', message: error instanceof Error ? error.message : 'The upload could not be completed.' })
    } finally {
      this.inFlight = false
    }
  }
}

export async function uploadDocument(file: File, fetcher: typeof fetch = fetch, token?: string): Promise<DocumentUploadResult> {
  const form = new FormData()
  form.append('file', file)
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined
  const response = await fetcher(`${resolveApiBaseUrl()}/documents`, { method: 'POST', body: form, headers })
  const body: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const message = typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string'
      ? body.message
      : `Upload failed (HTTP ${response.status}).`
    throw new Error(message)
  }
  if (!isDocumentUploadResult(body)) throw new Error('The backend returned an unexpected upload response.')
  return body
}

function isDocumentUploadResult(value: unknown): value is DocumentUploadResult {
  if (typeof value !== 'object' || value === null) return false
  const result = value as Record<string, unknown>
  return typeof result.documentId === 'string' && typeof result.filename === 'string' &&
    typeof result.status === 'string' && typeof result.characterCount === 'number' &&
    (typeof result.pageCount === 'number' || result.pageCount === null) &&
    typeof result.chunkCount === 'number' && typeof result.embeddingCount === 'number' &&
    typeof result.message === 'string'
}

export function validateDocumentFile(file: File): string | null {
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['pdf', 'txt', 'text'].includes(extension ?? '')) return 'Choose a PDF or plain-text file.'
  if (file.size === 0) return 'The selected file is empty.'
  if (file.size > 10 * 1024 * 1024) return 'Files must be 10 MiB or smaller.'
  return null
}
