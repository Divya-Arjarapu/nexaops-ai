import { resolveApiBaseUrl } from './aiHealth.ts'

export type RagReference = {
  sourceId: string
  documentId: string
  filename: string
  chunkId: string
  chunkIndex: number
  pageNumber: number | null
  sectionTitle: string | null
  sourceLocator: string | null
  similarityScore: number
}

export type RagCitation = RagReference
export type RagSource = RagReference & { text: string }

export type RagAnswerResponse = {
  query: string
  answer: string
  hasContext: boolean
  citations: RagCitation[]
  sources: RagSource[]
}

export type RagAnswerInput = { query: string; topK: number }
export type RagAnswerState =
  | { status: 'loading' }
  | { status: 'success' | 'empty'; response: RagAnswerResponse }
  | { status: 'error'; message: string }

type Answerer = (input: RagAnswerInput) => Promise<RagAnswerResponse>

export async function requestRagAnswer(
  input: RagAnswerInput,
  fetcher: typeof fetch = fetch,
): Promise<RagAnswerResponse> {
  const response = await fetcher(`${resolveApiBaseUrl()}/rag/answer`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query: input.query.trim(), top_k: input.topK }),
  })
  const body: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const message = typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string'
      ? body.message
      : `Answer request failed (HTTP ${response.status}).`
    throw new Error(message)
  }
  if (!isRagAnswerResponse(body)) throw new Error('The backend returned an unexpected answer response.')
  return body
}

export class RagAnswerCoordinator {
  private inFlight = false
  private readonly answerer: Answerer

  constructor(answerer: Answerer = requestRagAnswer) {
    this.answerer = answerer
  }

  async submit(input: RagAnswerInput, onState: (state: RagAnswerState) => void): Promise<void> {
    if (this.inFlight) return
    if (!input.query.trim()) {
      onState({ status: 'error', message: 'Enter a question to get a source-grounded answer.' })
      return
    }
    if (!Number.isInteger(input.topK) || input.topK < 1 || input.topK > 10) {
      onState({ status: 'error', message: 'Choose between 1 and 10 source passages.' })
      return
    }
    this.inFlight = true
    onState({ status: 'loading' })
    try {
      const response = await this.answerer({ query: input.query.trim(), topK: input.topK })
      onState({ status: response.hasContext ? 'success' : 'empty', response })
    } catch (error) {
      onState({
        status: 'error',
        message: error instanceof Error ? error.message : 'The answer request could not be completed.',
      })
    } finally {
      this.inFlight = false
    }
  }
}

function isRagAnswerResponse(value: unknown): value is RagAnswerResponse {
  if (typeof value !== 'object' || value === null) return false
  const response = value as Record<string, unknown>
  if (typeof response.query !== 'string' || typeof response.answer !== 'string' ||
      typeof response.hasContext !== 'boolean' || !Array.isArray(response.citations) ||
      !Array.isArray(response.sources) || response.sources.some((source) => !isRagSource(source)) ||
      response.citations.some((citation) => !isRagCitation(citation))) return false
  const sourceIds = new Set((response.sources as RagSource[]).map((source) => source.sourceId))
  return response.citations.every((citation) => sourceIds.has((citation as RagCitation).sourceId)) &&
    (!response.hasContext || response.sources.length > 0) &&
    (response.hasContext || response.citations.length === 0)
}

function isRagCitation(value: unknown): value is RagCitation {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Record<string, unknown>
  return typeof item.sourceId === 'string' && typeof item.documentId === 'string' &&
    typeof item.filename === 'string' && typeof item.chunkId === 'string' &&
    typeof item.chunkIndex === 'number' && Number.isInteger(item.chunkIndex) &&
    (typeof item.pageNumber === 'number' || item.pageNumber === null) &&
    (typeof item.sectionTitle === 'string' || item.sectionTitle === null) &&
    (typeof item.sourceLocator === 'string' || item.sourceLocator === null) &&
    typeof item.similarityScore === 'number' && Number.isFinite(item.similarityScore)
}

function isRagSource(value: unknown): value is RagSource {
  return isRagCitation(value) && typeof (value as Record<string, unknown>).text === 'string'
}
