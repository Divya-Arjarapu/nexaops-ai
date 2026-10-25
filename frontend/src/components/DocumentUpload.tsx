import { useRef, useState } from 'react'
import { AdminApiError, uploadAdminDocument } from '../api/admin.ts'
import { validateDocumentFile, type DocumentUploadResult } from '../api/documents.ts'

export function DocumentUpload({ token, onUploaded, onSessionExpired }: {
  token: string
  onUploaded?: (result: DocumentUploadResult) => void
  onSessionExpired?: () => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [result, setResult] = useState<DocumentUploadResult | null>(null)
  const inFlight = useRef(false)

  async function submit() {
    if (!file || inFlight.current) return
    const validationError = validateDocumentFile(file)
    if (validationError) { setMessage(validationError); return }
    inFlight.current = true
    setBusy(true)
    setMessage('')
    setResult(null)
    try {
      const uploaded = await uploadAdminDocument(file, token)
      setResult(uploaded)
      setMessage('Document uploaded and text extracted.')
      setFile(null)
      if (inputRef.current) inputRef.current.value = ''
      onUploaded?.(uploaded)
    } catch (error) {
      if (error instanceof AdminApiError && error.status === 401) {
        setMessage('Your admin session expired. Sign in again to upload documents.')
        onSessionExpired?.()
      } else {
        setMessage(error instanceof Error ? error.message : 'The upload could not be completed.')
      }
    } finally {
      inFlight.current = false
      setBusy(false)
    }
  }

  return <section className="document-upload" aria-label="Upload a document">
    <label className="document-upload-picker">
      <span>Knowledge documents</span>
      <input ref={inputRef} type="file" accept=".pdf,.txt,.text,application/pdf,text/plain" disabled={busy}
        onChange={(event) => { const next = event.target.files?.[0] ?? null; setFile(next); setResult(null); setMessage(next ? validateDocumentFile(next) ?? '' : '') }} />
    </label>
    {file && <div className="document-upload-selected"><span>{file.name} · {(file.size / 1024).toFixed(1)} KB</span>
      <button type="button" onClick={() => setFile(null)} disabled={busy}>Remove</button></div>}
    <button className="document-upload-button" type="button" onClick={() => void submit()} disabled={!file || busy || Boolean(validateDocumentFile(file))}>
      {busy ? 'Uploading…' : 'Upload document'}
    </button>
    {message && <p className={`document-upload-message ${result ? 'is-success' : ''}`} role="status">{message}</p>}
    {result && <dl className="document-upload-result">
      <div><dt>Document ID</dt><dd>{result.documentId}</dd></div>
      <div><dt>Extracted</dt><dd>{result.characterCount.toLocaleString()} characters · {result.chunkCount} chunks{result.pageCount === null ? '' : ` · ${result.pageCount} pages`}</dd></div>
    </dl>}
  </section>
}
