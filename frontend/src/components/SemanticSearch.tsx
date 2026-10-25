import { useRef, useState, type FormEvent } from 'react'
import {
  RetrievalSearchCoordinator,
  type RetrievalSearchResponse,
  type RetrievalSearchState,
} from '../api/retrieval.ts'
import {
  RagAnswerCoordinator,
  type RagAnswerResponse,
  type RagAnswerState,
} from '../api/rag.ts'
import { AdminDocumentManager } from './AdminDocumentManager.tsx'

type SearchMode = 'answer' | 'passages'

export function SemanticSearch({ onClose, adminToken, onAdminTokenChange }: {
  onClose: () => void
  adminToken: string | null
  onAdminTokenChange: (token: string | null) => void
}) {
  const [mode, setMode] = useState<SearchMode>('answer')
  const [query, setQuery] = useState('')
  const [topK, setTopK] = useState(5)
  const [searchState, setSearchState] = useState<RetrievalSearchState | null>(null)
  const [answerState, setAnswerState] = useState<RagAnswerState | null>(null)
  const searchCoordinator = useRef(new RetrievalSearchCoordinator())
  const answerCoordinator = useRef(new RagAnswerCoordinator())
  const isLoading = searchState?.status === 'loading' || answerState?.status === 'loading'

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (mode === 'answer') {
      await answerCoordinator.current.submit({ query, topK }, setAnswerState)
    } else {
      await searchCoordinator.current.submit({ query, topK }, setSearchState)
    }
  }

  return <section className="semantic-search" aria-labelledby="semantic-search-title">
    <div className="semantic-search-heading">
      <div>
        <p className="section-label">KNOWLEDGE BASE</p>
        <h1 id="semantic-search-title">Ask your company knowledge</h1>
        <p>Get a grounded response or inspect the matching document passages.</p>
      </div>
      <button className="icon-button" type="button" aria-label="Return to assistant" onClick={onClose}>×</button>
    </div>

    <AdminDocumentManager token={adminToken} onTokenChange={onAdminTokenChange} />

    <div className="knowledge-mode-tabs" role="group" aria-label="Knowledge request type">
      <button type="button" className={mode === 'answer' ? 'knowledge-mode-active' : ''}
        aria-pressed={mode === 'answer'} disabled={isLoading} onClick={() => setMode('answer')}>
        Answer with sources
      </button>
      <button type="button" className={mode === 'passages' ? 'knowledge-mode-active' : ''}
        aria-pressed={mode === 'passages'} disabled={isLoading} onClick={() => setMode('passages')}>
        Search passages
      </button>
    </div>

    <form className="semantic-search-form" onSubmit={(event) => void submit(event)}>
      <label htmlFor="semantic-search-query">Your question</label>
      <textarea id="semantic-search-query" value={query} maxLength={2000} rows={3}
        placeholder="For example: Who approves production database access?"
        onChange={(event) => setQuery(event.target.value)} disabled={isLoading} />
      <div className="semantic-search-actions">
        <label htmlFor="semantic-search-top-k">Sources
          <select id="semantic-search-top-k" value={topK} onChange={(event) => setTopK(Number(event.target.value))} disabled={isLoading}>
            <option value={3}>3 sources</option><option value={5}>5 sources</option><option value={10}>10 sources</option>
          </select>
        </label>
        <button className="send-button semantic-search-submit" type="submit" disabled={isLoading || !query.trim()}>
          {isLoading ? (mode === 'answer' ? 'Preparing answer…' : 'Searching…') : (mode === 'answer' ? 'Ask' : 'Search')}
        </button>
      </div>
    </form>

    <div className="semantic-search-output" aria-live="polite">
      {mode === 'answer' ? <RagAnswerOutput state={answerState} /> : <RetrievalOutput state={searchState} />}
    </div>
  </section>
}

function RetrievalOutput({ state }: { state: RetrievalSearchState | null }) {
  if (!state) return null
  if (state.status === 'loading') return <p className="search-loading" role="status">Searching your knowledge base…</p>
  if (state.status === 'error') return <p className="search-error" role="alert">{state.message}</p>
  if (state.status === 'empty') return <p className="search-empty" role="status">No matching chunks with embeddings were found. Upload a document and generate its embeddings before searching.</p>
  return <SearchResults response={state.response} />
}

function SearchResults({ response }: { response: RetrievalSearchResponse }) {
  return <div className="retrieved-sources">
    <div className="retrieved-sources-heading">
      <div><h2>Retrieved source excerpts</h2><p>{response.resultCount} relevant {response.resultCount === 1 ? 'passage' : 'passages'} · “{response.query}”</p></div>
      <span className="retrieval-label">SOURCE MATERIAL</span>
    </div>
    <p className="retrieval-note">These are passages from your documents, not an AI-generated answer. Similarity scores are not probabilities.</p>
    <div className="retrieval-results">
      {response.results.map((result) => <article className="retrieval-result" key={result.chunkId}>
        <div className="retrieval-result-meta">
          <strong>{result.filename}</strong>
          {result.pageNumber !== null && <span>Page {result.pageNumber}</span>}
          {result.sectionTitle && <span>{result.sectionTitle}</span>}
          <span className="retrieval-score">Cosine similarity · {result.similarityScore.toFixed(3)}</span>
        </div>
        <blockquote>{result.text}</blockquote>
        {result.sourceLocator && <p className="retrieval-locator">{result.sourceLocator}</p>}
      </article>)}
    </div>
  </div>
}

function RagAnswerOutput({ state }: { state: RagAnswerState | null }) {
  if (!state) return null
  if (state.status === 'loading') return <p className="search-loading" role="status">Retrieving sources and preparing an answer…</p>
  if (state.status === 'error') return <p className="search-error" role="alert">{state.message}</p>
  return <RagAnswerResults response={state.response} noContext={state.status === 'empty'} />
}

function RagAnswerResults({ response, noContext }: { response: RagAnswerResponse; noContext: boolean }) {
  return <div className="rag-answer-results">
    <section className={`rag-answer-card${noContext ? ' rag-answer-no-context' : ''}`} aria-labelledby="rag-answer-title">
      <div className="retrieved-sources-heading">
        <div><h2 id="rag-answer-title">Answer</h2><p>{noContext ? 'No supporting passages were found' : `Grounded in ${response.sources.length} retrieved ${response.sources.length === 1 ? 'passage' : 'passages'}`}</p></div>
        <span className="retrieval-label">LOCAL AI</span>
      </div>
      <p className="rag-answer-text">{response.answer}</p>
    </section>

    {response.citations.length > 0 && <section className="rag-citations" aria-labelledby="rag-citations-title">
      <h3 id="rag-citations-title">Sources cited in the answer</h3>
      <ul>
        {response.citations.map((citation) => <li key={citation.sourceId}>
          <span className="rag-source-id">[{citation.sourceId}]</span>
          <strong>{citation.filename}</strong>
          {citation.pageNumber !== null && <span>Page {citation.pageNumber}</span>}
          {citation.sectionTitle && <span>{citation.sectionTitle}</span>}
        </li>)}
      </ul>
    </section>}

    {response.sources.length > 0 && <details className="rag-source-passages" open>
      <summary>Retrieved passages ({response.sources.length})</summary>
      <div className="retrieval-results">
        {response.sources.map((source) => <article className="retrieval-result" key={source.sourceId}>
          <div className="retrieval-result-meta">
            <span className="rag-source-id">[{source.sourceId}]</span>
            <strong>{source.filename}</strong>
            {source.pageNumber !== null && <span>Page {source.pageNumber}</span>}
            {source.sectionTitle && <span>{source.sectionTitle}</span>}
            <span className="retrieval-score">Cosine similarity · {source.similarityScore.toFixed(3)}</span>
          </div>
          <blockquote>{source.text}</blockquote>
          {source.sourceLocator && <p className="retrieval-locator">{source.sourceLocator}</p>}
        </article>)}
      </div>
      {response.citations.length === 0 && <p className="retrieval-note">The model did not explicitly cite a passage. These retrieved excerpts are shown for verification.</p>}
    </details>}
  </div>
}
