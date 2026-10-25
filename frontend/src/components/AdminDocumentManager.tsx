import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  AdminApiError,
  confirmDocumentRemoval,
  deleteAdminDocument,
  listAdminDocuments,
  loginAdmin,
  logoutAdmin,
  type ManagedDocument,
} from '../api/admin.ts'
import { DocumentUpload } from './DocumentUpload.tsx'

type Props = {
  token: string | null
  onTokenChange: (token: string | null) => void
}

export function AdminDocumentManager({ token, onTokenChange }: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loginBusy, setLoginBusy] = useState(false)
  const [loginError, setLoginError] = useState('')
  const [documents, setDocuments] = useState<ManagedDocument[]>([])
  const [listBusy, setListBusy] = useState(false)
  const [listError, setListError] = useState('')
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const refreshDocuments = useCallback(async (activeToken: string) => {
    setListBusy(true)
    setListError('')
    try {
      setDocuments(await listAdminDocuments(activeToken))
    } catch (error) {
      if (error instanceof AdminApiError && error.status === 401) {
        setDocuments([])
        onTokenChange(null)
        setLoginError('Your admin session expired. Sign in again to manage documents.')
      } else {
        setListError(error instanceof Error ? error.message : 'The document list could not be loaded.')
      }
    } finally {
      setListBusy(false)
    }
  }, [onTokenChange])

  useEffect(() => {
    if (!token) return
    const timeout = window.setTimeout(() => void refreshDocuments(token), 0)
    return () => window.clearTimeout(timeout)
  }, [token, refreshDocuments])

  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (loginBusy) return
    setLoginBusy(true)
    setLoginError('')
    try {
      const session = await loginAdmin(username, password)
      setPassword('')
      onTokenChange(session.token)
    } catch (error) {
      setLoginError(error instanceof Error ? error.message : 'Admin sign-in failed.')
    } finally {
      setLoginBusy(false)
    }
  }

  async function signOut() {
    if (token) {
      try { await logoutAdmin(token) } catch { /* Local sign-out still removes the browser token. */ }
    }
    onTokenChange(null)
    setDocuments([])
    setLoginError('')
  }

  async function removeDocument(document: ManagedDocument) {
    if (!token || deletingId) return
    const confirmed = confirmDocumentRemoval(document.filename, window.confirm.bind(window))
    if (!confirmed) return
    setDeletingId(document.documentId)
    setListError('')
    try {
      await deleteAdminDocument(token, document.documentId)
      setDocuments((current) => current.filter((item) => item.documentId !== document.documentId))
    } catch (error) {
      if (error instanceof AdminApiError && error.status === 401) {
        onTokenChange(null)
        setDocuments([])
        setLoginError('Your admin session expired. Sign in again to manage documents.')
      } else {
        setListError(error instanceof Error ? error.message : 'The document could not be removed.')
      }
    } finally {
      setDeletingId(null)
    }
  }

  return <section className="admin-document-manager" aria-labelledby="admin-documents-title">
    <div className="admin-manager-heading">
      <div>
        <p className="section-label">ADMIN WORKSPACE</p>
        <h2 id="admin-documents-title">Document management</h2>
        <p>Sign in to add and manage knowledge documents. Search and answers remain available.</p>
      </div>
      {token && <button className="admin-logout-button" type="button" onClick={() => void signOut()}>Sign out</button>}
    </div>

    {!token ? <form className="admin-login-form" onSubmit={(event) => void signIn(event)}>
      <label>Admin username<input autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} required disabled={loginBusy} /></label>
      <label>Admin password<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required disabled={loginBusy} /></label>
      <button className="send-button" type="submit" disabled={loginBusy || !username.trim() || !password}>{loginBusy ? 'Signing in…' : 'Sign in'}</button>
      {loginError && <p className="admin-manager-error" role="alert">{loginError}</p>}
    </form> : <>
      <section className="knowledge-upload" aria-labelledby="knowledge-upload-title">
        <div><h3 id="knowledge-upload-title">Add a document</h3><p>Upload one PDF or plain-text file at a time. You can repeat this for additional documents.</p></div>
        <DocumentUpload token={token} onUploaded={() => void refreshDocuments(token)} onSessionExpired={() => {
          setDocuments([])
          setLoginError('Your admin session expired. Sign in again to manage documents.')
          onTokenChange(null)
        }} />
      </section>

      <div className="admin-document-list-heading">
        <div><h3>Uploaded documents</h3><p>Files and indexed passages stored in your local knowledge base.</p></div>
        <button type="button" onClick={() => void refreshDocuments(token)} disabled={listBusy}>{listBusy ? 'Refreshing…' : 'Refresh list'}</button>
      </div>
      {listError && <p className="admin-manager-error" role="alert">{listError}</p>}
      {listBusy && documents.length === 0 ? <p className="admin-list-state" role="status">Loading documents…</p>
        : documents.length === 0 ? <p className="admin-list-state" role="status">No documents uploaded yet. Add a PDF or text file to get started.</p>
          : <div className="admin-document-list" aria-label="Uploaded documents">
            {documents.map((document) => <article className="admin-document-row" key={document.documentId}>
              <div className="admin-document-file"><span className="admin-document-icon" aria-hidden="true">▤</span><div>
                <strong>{document.filename}</strong><span>{new Date(document.uploadedAt).toLocaleString()} · {document.status}</span>
              </div></div>
              <div className="admin-document-counts"><span>{document.chunkCount} chunks</span><span>{document.embeddedChunkCount} embedded</span></div>
              <button type="button" className="admin-remove-button" onClick={() => void removeDocument(document)} disabled={deletingId !== null}>
                {deletingId === document.documentId ? 'Removing…' : 'Remove'}
              </button>
            </article>)}
          </div>}
    </>}
  </section>
}
