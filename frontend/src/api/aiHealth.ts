export type HealthStatus = 'UP' | 'DOWN' | 'UNKNOWN'

export interface ComponentHealth {
  status: HealthStatus
  message: string
}

export interface ModelHealth extends ComponentHealth {
  model: string
  available: boolean | null
}

export interface AiIntegrationHealth {
  javaBackend: ComponentHealth
  pythonAiService: ComponentHealth
  ollama: ComponentHealth
  generationModel: ModelHealth
  embeddingModel: ModelHealth
}

export function resolveApiBaseUrl(configuredUrl?: string): string {
  return (configuredUrl?.trim() || '/api').replace(/\/$/, '')
}

export const apiBaseUrl = resolveApiBaseUrl(import.meta.env?.VITE_API_BASE_URL)

function isHealthStatus(value: unknown): value is HealthStatus {
  return value === 'UP' || value === 'DOWN' || value === 'UNKNOWN'
}

function isComponentHealth(value: unknown): value is ComponentHealth {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as unknown as Record<string, unknown>
  return isHealthStatus(candidate.status) && typeof candidate.message === 'string'
}

function isModelHealth(value: unknown): value is ModelHealth {
  if (!isComponentHealth(value)) return false
  const candidate = value as unknown as Record<string, unknown>
  return typeof candidate.model === 'string' &&
    (typeof candidate.available === 'boolean' || candidate.available === null)
}

export function isAiIntegrationHealth(value: unknown): value is AiIntegrationHealth {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return isComponentHealth(candidate.javaBackend) &&
    isComponentHealth(candidate.pythonAiService) &&
    isComponentHealth(candidate.ollama) &&
    isModelHealth(candidate.generationModel) &&
    isModelHealth(candidate.embeddingModel)
}

export async function fetchAiIntegrationHealth(
  fetcher: typeof fetch = fetch,
  signal?: AbortSignal,
): Promise<AiIntegrationHealth> {
  const response = await fetcher(`${apiBaseUrl}/ai/health`, {
    headers: { Accept: 'application/json' },
    cache: 'no-store',
    signal,
  })

  if (!response.ok) {
    throw new Error(`Health check failed with HTTP ${response.status}.`)
  }

  const payload: unknown = await response.json()
  if (!isAiIntegrationHealth(payload)) {
    throw new Error('The backend returned an unexpected health response.')
  }

  return payload
}
