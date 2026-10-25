import { resolveApiBaseUrl } from './aiHealth.ts'

export type RetrievalResult = {
  documentId: string
  filename: string
  chunkId: string
  chunkIndex: number
  pageNumber: number | null
  sectionTitle: string | null
  sourceLocator: string | null
  text: string
  similarityScore: number
}

export type RetrievalSearchResponse = {
  query: string
  results: RetrievalResult[]
  resultCount: number
}

export type RetrievalSearchState =
  | { status: 'loading' }
  | { status: 'success' | 'empty'; response: RetrievalSearchResponse }
  | { status: 'error'; message: string }

export type RetrievalSearchInput = { query: string; topK: number }
type Searcher = (input: RetrievalSearchInput) => Promise<RetrievalSearchResponse>

export async function searchKnowledge(
  input: RetrievalSearchInput,
  fetcher: typeof fetch = fetch,
): Promise<RetrievalSearchResponse> {
  const response = await fetcher(`${resolveApiBaseUrl()}/retrieval/search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query: input.query.trim(), top_k: input.topK }),
  })
  const body: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const message = typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string'
      ? body.message
      : `Search failed (HTTP ${response.status}).`
    throw new Error(message)
  }
  if (!isRetrievalResponse(body)) throw new Error('The backend returned an unexpected search response.')
  return body
}

export class RetrievalSearchCoordinator {
  private inFlight = false
  private readonly searcher: Searcher

  constructor(searcher: Searcher = searchKnowledge) {
    this.searcher = searcher
  }

  async submit(
    input: RetrievalSearchInput,
    onState: (state: RetrievalSearchState) => void,
  ): Promise<void> {
    if (this.inFlight) return
    if (!input.query.trim()) {
      onState({ status: 'error', message: 'Enter a question to search company knowledge.' })
      return
    }
    if (!Number.isInteger(input.topK) || input.topK < 1 || input.topK > 10) {
      onState({ status: 'error', message: 'Choose between 1 and 10 results.' })
      return
    }
    this.inFlight = true
    onState({ status: 'loading' })
    try {
      const response = await this.searcher({ query: input.query.trim(), topK: input.topK })
      onState({ status: response.results.length ? 'success' : 'empty', response })
    } catch (error) {
      onState({
        status: 'error',
        message: error instanceof Error ? error.message : 'The search request could not be completed.',
      })
    } finally {
      this.inFlight = false
    }
  }
}

function isRetrievalResponse(value: unknown): value is RetrievalSearchResponse {
  if (typeof value !== 'object' || value === null) return false
  const response = value as Record<string, unknown>
  return typeof response.query === 'string' && Array.isArray(response.results) &&
    typeof response.resultCount === 'number' && response.resultCount === response.results.length &&
    response.results.every(isRetrievalResult)
}

function isRetrievalResult(value: unknown): value is RetrievalResult {
  if (typeof value !== 'object' || value === null) return false
  const result = value as Record<string, unknown>
  return typeof result.documentId === 'string' && typeof result.filename === 'string' &&
    typeof result.chunkId === 'string' && typeof result.chunkIndex === 'number' &&
    (typeof result.pageNumber === 'number' || result.pageNumber === null) &&
    (typeof result.sectionTitle === 'string' || result.sectionTitle === null) &&
    (typeof result.sourceLocator === 'string' || result.sourceLocator === null) &&
    typeof result.text === 'string' && typeof result.similarityScore === 'number' &&
    Number.isFinite(result.similarityScore)
}
