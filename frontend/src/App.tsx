import { useEffect, useState, type FormEvent } from 'react'
import {
  fetchAiIntegrationHealth,
  type AiIntegrationHealth,
  type ComponentHealth,
  type HealthStatus,
  type ModelHealth,
} from './api/aiHealth.ts'
import { SemanticSearch } from './components/SemanticSearch.tsx'
import './App.css'

type Message = {
  id: number
  role: 'user' | 'assistant'
  content: string
}

type HealthRowCheck = ComponentHealth | ModelHealth

function isFullyHealthy(health: AiIntegrationHealth): boolean {
  return health.javaBackend.status === 'UP' &&
    health.pythonAiService.status === 'UP' &&
    health.ollama.status === 'UP' &&
    health.generationModel.status === 'UP' &&
    health.generationModel.available === true &&
    health.embeddingModel.status === 'UP' &&
    health.embeddingModel.available === true
}

function HealthRow({ label, check }: { label: string; check: HealthRowCheck }) {
  const modelName = 'model' in check ? check.model : ''
  const statusText: Record<HealthStatus, string> = {
    UP: 'Available',
    DOWN: 'Unavailable',
    UNKNOWN: 'Unknown',
  }

  return (
    <div className="health-service-row">
      <span className={`health-row-dot health-row-${check.status.toLowerCase()}`} aria-hidden="true" />
      <div className="health-service-copy">
        <strong>{label}</strong>
        <span>{modelName ? `${modelName} · ` : ''}{check.message}</span>
      </div>
      <span className={`health-service-status health-text-${check.status.toLowerCase()}`}>
        {statusText[check.status]}
      </span>
    </div>
  )
}

const suggestions = [
  {
    icon: '⌕',
    title: 'Search company knowledge',
    description: 'Find answers across your internal documents.',
    prompt: 'How can I search company knowledge effectively?',
  },
  {
    icon: '▤',
    title: 'Analyze a document',
    description: 'Extract key insights from business documents.',
    prompt: 'Help me analyze a business document and extract key insights.',
  },
  {
    icon: '⌘',
    title: 'Understand a process',
    description: 'Get clarity on workflows and procedures.',
    prompt: 'Help me understand a business process step by step.',
  },
  {
    icon: '✳',
    title: 'Explore an idea',
    description: 'Turn a complex question into actionable steps.',
    prompt: 'Help me turn a business problem into an actionable plan.',
  },
]

function App() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [activeConversation, setActiveConversation] = useState('New conversation')
  const [systemHealth, setSystemHealth] = useState<AiIntegrationHealth | null>(null)
  const [healthLoading, setHealthLoading] = useState(true)
  const [healthError, setHealthError] = useState('')
  const [adminToken, setAdminToken] = useState<string | null>(null)

  async function refreshHealth() {
    setHealthLoading(true)
    setHealthError('')
    try {
      const result = await fetchAiIntegrationHealth()
      setSystemHealth(result)
    } catch (error) {
      setSystemHealth(null)
      setHealthError(
        error instanceof Error ? error.message : 'Unable to reach the backend.',
      )
    } finally {
      setHealthLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    void fetchAiIntegrationHealth(fetch, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setSystemHealth(result)
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setSystemHealth(null)
        setHealthError(
          error instanceof Error ? error.message : 'Unable to reach the backend.',
        )
      })
      .finally(() => {
        if (!controller.signal.aborted) setHealthLoading(false)
      })
    return () => controller.abort()
  }, [])

  const overallHealth = healthError
    ? 'error'
    : healthLoading
      ? 'loading'
      : systemHealth && isFullyHealthy(systemHealth)
        ? 'success'
        : 'partial'
  const overallLabel = {
    loading: 'Checking systems',
    success: 'All systems operational',
    partial: 'Service issue detected',
    error: 'Backend unavailable',
  }[overallHealth]

  function startNewChat() {
    setMessages([])
    setInput('')
    setActiveConversation('New conversation')
    setSidebarOpen(false)
    setSearchOpen(false)
  }

  function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const content = input.trim()
    if (!content) return

    const userMessage: Message = {
      id: Date.now(),
      role: 'user',
      content,
    }

    setMessages((previous) => [...previous, userMessage])
    setInput('')
    setActiveConversation(
      content.length > 32 ? `${content.slice(0, 32)}…` : content,
    )

    // The real AI response will be connected to our backend in a later step.
  }

  return (
    <div className="app-shell">
      {sidebarOpen && (
        <button
          className="sidebar-backdrop"
          aria-label="Close navigation"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <aside className={`sidebar ${sidebarOpen ? 'sidebar-open' : ''}`}>
        <div className="sidebar-top">
          <a className="brand" href="#" onClick={startNewChat}>
            <span className="brand-mark">N</span>
            <span className="brand-copy">
              <strong>NexaOps</strong>
              <span>INTELLIGENCE PLATFORM</span>
            </span>
          </a>

          <button
            className="icon-button sidebar-close"
            onClick={() => setSidebarOpen(false)}
            aria-label="Close sidebar"
          >
            ×
          </button>
        </div>

        <button className="new-chat-button" onClick={startNewChat}>
          <span className="new-chat-icon">＋</span>
          <span>New conversation</span>
          <span className="shortcut">⌘ K</span>
        </button>

        <div className="sidebar-section">
          <p className="section-label">WORKSPACE</p>
          <button className={`nav-item ${!searchOpen ? 'nav-item-active' : ''}`} onClick={() => setSearchOpen(false)}>
            <span className="nav-icon">⌂</span>
            <span>AI Assistant</span>
            <span className="nav-indicator" />
          </button>
          <button className={`nav-item ${searchOpen ? 'nav-item-active' : ''}`} onClick={() => { setSearchOpen(true); setSidebarOpen(false) }}>
            <span className="nav-icon">▤</span>
            <span>Knowledge base</span>
            {searchOpen && <span className="nav-indicator" />}
          </button>
          <button className="nav-item" onClick={() => window.alert('Activity analytics will be available in a future milestone.')}>
            <span className="nav-icon">▥</span>
            <span>Activity & insights</span>
          </button>
        </div>

        <div className="sidebar-section history-section">
          <p className="section-label">RECENT CONVERSATIONS</p>
          {messages.length > 0 ? (
            <button className="history-item" title={activeConversation}>
              <span>◷</span>
              <span>{activeConversation}</span>
            </button>
          ) : (
            <p className="empty-history">Your conversations will appear here.</p>
          )}
        </div>

        <div className="sidebar-bottom">
          <div className="privacy-card">
            <div className="privacy-icon">⌑</div>
            <div>
              <strong>Your data stays yours</strong>
              <p>Designed for private, local AI workflows.</p>
            </div>
          </div>

          <div className="profile-row">
            <div className="avatar">N</div>
            <div className="profile-info">
              <strong>Workspace user</strong>
              <span>Personal workspace</span>
            </div>
            <button
              className="icon-button profile-menu"
              aria-label="Workspace options"
              onClick={() => window.alert('Workspace settings will be available in a future milestone.')}
            >
              ···
            </button>
          </div>
        </div>
      </aside>

      <main className="main-panel">
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="icon-button mobile-menu"
              aria-label="Open navigation"
              onClick={() => setSidebarOpen(true)}
            >
              ☰
            </button>
            <div className="breadcrumb">
              <span>Workspace</span>
              <span className="breadcrumb-divider">/</span>
              <strong>{searchOpen ? 'Knowledge base' : 'AI Assistant'}</strong>
            </div>
          </div>

          <div className="topbar-right">
            <details className="system-health">
              <summary className={`system-health-summary health-${overallHealth}`}>
                <span className="health-indicator-dot" aria-hidden="true" />
                <span className="system-health-title">System health</span>
                <span className="system-health-label">{overallLabel}</span>
              </summary>
              <div className="system-health-popover">
                <div className="system-health-heading">
                  <div>
                    <strong>Service status</strong>
                    <p>Live checks from the NexaOps backend</p>
                  </div>
                  <button
                    className="health-refresh-button"
                    type="button"
                    onClick={() => void refreshHealth()}
                    disabled={healthLoading}
                  >
                    {healthLoading ? 'Checking…' : 'Refresh'}
                  </button>
                </div>
                {healthError ? (
                  <p className="health-error-message" role="status">
                    {healthError} Start the Java backend, then refresh. You can
                    continue using the workspace while it is offline.
                  </p>
                ) : (
                  <div className="health-service-list" aria-live="polite">
                    {healthLoading && !systemHealth ? (
                      <p className="health-loading-message">Checking connected services…</p>
                    ) : systemHealth ? (
                      <>
                        <HealthRow label="Java backend" check={systemHealth.javaBackend} />
                        <HealthRow label="Python AI service" check={systemHealth.pythonAiService} />
                        <HealthRow label="Ollama" check={systemHealth.ollama} />
                        <HealthRow label="Generation model" check={systemHealth.generationModel} />
                        <HealthRow label="Embedding model" check={systemHealth.embeddingModel} />
                      </>
                    ) : null}
                  </div>
                )}
              </div>
            </details>
            <button
              className="help-button"
              onClick={() =>
                window.alert(
                  'NexaOps AI is being set up. Local AI and knowledge search will be connected in upcoming steps.',
                )
              }
            >
              ?
            </button>
          </div>
        </header>

        <section className="chat-scroll-area">
          {searchOpen ? <SemanticSearch onClose={() => setSearchOpen(false)} adminToken={adminToken} onAdminTokenChange={setAdminToken} /> : messages.length === 0 ? (
            <div className="welcome-container">
              <div className="welcome-eyebrow">
                <span className="sparkle">✳</span>
                YOUR ENTERPRISE AI WORKSPACE
              </div>

              <div className="welcome-mark">N</div>

              <h1>
                Work smarter.
                <br />
                <span>Know more.</span>
              </h1>

              <p className="welcome-description">
                Your intelligent workspace for exploring company knowledge,
                understanding documents, and solving complex business problems.
              </p>

              <div className="capability-pills">
                <span><i /> Enterprise focused</span>
                <span><i /> Privacy first</span>
                <span><i /> AI powered</span>
              </div>

              <div className="suggestions-header">
                <div>
                  <p className="section-label">GET STARTED</p>
                  <h2>What would you like to work on?</h2>
                </div>
                <span className="suggestions-count">04 STARTING POINTS</span>
              </div>

              <div className="suggestions-grid">
                {suggestions.map((suggestion, index) => (
                  <button
                    className="suggestion-card"
                    key={suggestion.title}
                    onClick={() => setInput(suggestion.prompt)}
                  >
                    <div className="suggestion-card-top">
                      <span className={`suggestion-icon suggestion-icon-${index}`}>
                        {suggestion.icon}
                      </span>
                      <span className="suggestion-arrow">↗</span>
                    </div>
                    <strong>{suggestion.title}</strong>
                    <span className="suggestion-description">
                      {suggestion.description}
                    </span>
                  </button>
                ))}
              </div>

              <p className="welcome-footnote">
                <span>✳</span> A smarter way to work with your knowledge.
              </p>
            </div>
          ) : (
            <div className="messages-container">
              <div className="conversation-heading">
                <span className="conversation-heading-icon">✳</span>
                <div>
                  <h2>Conversation</h2>
                  <p>Your workspace session</p>
                </div>
              </div>

              {messages.map((message) => (
                <article
                  className={`message message-${message.role}`}
                  key={message.id}
                >
                  <div className="message-avatar">
                    {message.role === 'user' ? 'Y' : 'N'}
                  </div>
                  <div className="message-content">
                    <span className="message-author">
                      {message.role === 'user' ? 'You' : 'NexaOps AI'}
                    </span>
                    <p>{message.content}</p>
                    {message.role === 'user' && (
                      <span className="message-note">
                        Message received. AI responses will be connected next.
                      </span>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>

        {!searchOpen && <footer className="composer-area">
          <form className="composer" onSubmit={sendMessage}>
            <textarea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  event.currentTarget.form?.requestSubmit()
                }
              }}
              placeholder="Ask a question or explore an idea..."
              rows={2}
              aria-label="Message NexaOps AI"
            />

            <div className="composer-toolbar">
              <div className="composer-tools">
                <span className="composer-mode">
                  <span className="mode-dot" />
                  Local AI
                </span>
              </div>

              <button
                className="send-button"
                type="submit"
                disabled={!input.trim()}
                aria-label="Send message"
              >
                <span>Send</span>
                <span>↑</span>
              </button>
            </div>
          </form>

          <p className="composer-disclaimer">
            Verify important information. AI responses can contain mistakes.
          </p>
        </footer>}
      </main>
    </div>
  )
}

export default App
