import type { Plugin } from 'vite'
import type { IncomingMessage, ServerResponse } from 'node:http'

// ── Types ─────────────────────────────────────────────────────────────────────

type VoteMode   = 'SIMPLE' | 'FAIR_ROTATION'
type VoteStatus = 'PENDING' | 'DRAWN' | 'CLOSED'

interface Participant       { email: string; displayName: string | null }
interface DrawHistoryEntry  { id: string; winnerEmail: string; winnerDisplayName: string | null; round: number; drawnAt: string }
interface Vote {
  id: string; title: string; description: string | null
  mode: VoteMode; status: VoteStatus; currentRound: number
  isCreator: boolean; createdAt: string
  participants: Participant[]
  history: DrawHistoryEntry[]
}

// ── Mock state ────────────────────────────────────────────────────────────────

const MOCK_USER = {
  accessToken:  'mock-access-token',
  refreshToken: 'mock-refresh-token',
  userId:       'user-1',
  email:        'demo@example.com',
  displayName:  'Demo User',
}

let seq = 100
const uid = () => String(++seq)
const now = () => new Date().toISOString()
const daysAgo = (n: number) => new Date(Date.now() - n * 86_400_000).toISOString()
const hoursAgo = (n: number) => new Date(Date.now() - n * 3_600_000).toISOString()

const db = new Map<string, Vote>([
  ['vote-1', {
    id: 'vote-1', title: 'Выбор ресторана на пятницу',
    description: 'Куда идём после работы', mode: 'FAIR_ROTATION',
    status: 'PENDING', currentRound: 2, isCreator: true, createdAt: daysAgo(3),
    participants: [
      { email: 'alice@example.com', displayName: 'Alice' },
      { email: 'bob@example.com',   displayName: 'Bob' },
      { email: 'carol@example.com', displayName: 'Carol' },
      { email: 'demo@example.com',  displayName: 'Demo User' },
    ],
    history: [
      { id: 'h-1', winnerEmail: 'alice@example.com', winnerDisplayName: 'Alice', round: 1, drawnAt: daysAgo(2) },
      { id: 'h-2', winnerEmail: 'bob@example.com',   winnerDisplayName: 'Bob',   round: 1, drawnAt: daysAgo(1) },
    ],
  }],
  ['vote-2', {
    id: 'vote-2', title: 'Дежурный по кухне', description: null,
    mode: 'FAIR_ROTATION', status: 'DRAWN', currentRound: 1,
    isCreator: true, createdAt: daysAgo(7),
    participants: [
      { email: 'demo@example.com', displayName: 'Demo User' },
      { email: 'dave@example.com', displayName: 'Dave' },
    ],
    history: [
      { id: 'h-3', winnerEmail: 'dave@example.com', winnerDisplayName: 'Dave', round: 1, drawnAt: hoursAgo(5) },
    ],
  }],
  ['vote-3', {
    id: 'vote-3', title: 'Случайный розыгрыш призов',
    description: 'Приз получит один счастливчик', mode: 'SIMPLE',
    status: 'CLOSED', currentRound: 1, isCreator: false, createdAt: daysAgo(14),
    participants: [
      { email: 'demo@example.com',  displayName: 'Demo User' },
      { email: 'alice@example.com', displayName: 'Alice' },
      { email: 'eve@example.com',   displayName: 'Eve' },
    ],
    history: [
      { id: 'h-4', winnerEmail: 'eve@example.com', winnerDisplayName: 'Eve', round: 1, drawnAt: daysAgo(10) },
    ],
  }],
])

// ── Helpers ───────────────────────────────────────────────────────────────────

const toSummary = (v: Vote) => ({
  id: v.id, title: v.title, mode: v.mode, status: v.status,
  currentRound: v.currentRound, participantCount: v.participants.length,
  isCreator: v.isCreator, createdAt: v.createdAt,
})

const toDetail = (v: Vote) => ({
  id: v.id, title: v.title, description: v.description,
  mode: v.mode, status: v.status, currentRound: v.currentRound,
  participants: v.participants,
  lastResult: v.history.at(-1) ?? null,
  isCreator: v.isCreator, createdAt: v.createdAt,
})

function readBody(req: IncomingMessage): Promise<Record<string, unknown>> {
  return new Promise((resolve) => {
    let raw = ''
    req.on('data', (chunk) => { raw += chunk })
    req.on('end', () => {
      try { resolve(JSON.parse(raw || '{}')) } catch { resolve({}) }
    })
  })
}

const json = (res: ServerResponse, data: unknown, status = 200) => {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(data))
}
const noContent = (res: ServerResponse) => { res.writeHead(204); res.end() }
const notFound  = (res: ServerResponse) => json(res, { message: 'Not found' }, 404)

// ── Router ────────────────────────────────────────────────────────────────────

async function route(req: IncomingMessage, res: ServerResponse, pathname: string) {
  const method = req.method ?? 'GET'
  const parts  = pathname.replace(/^\//, '').split('/')

  // ── Auth ──────────────────────────────────────────────────────────────────
  if (parts[0] === 'auth') {
    if (method === 'POST') return json(res, MOCK_USER)          // login / register / refresh
    if (method === 'DELETE') return noContent(res)              // logout (DELETE variant)
    return noContent(res)
  }

  // ── Votes list / create ───────────────────────────────────────────────────
  if (parts[0] === 'votes' && parts.length === 1) {
    if (method === 'GET') {
      const list = [...db.values()].map(toSummary)
      return json(res, { content: list, totalElements: list.length, totalPages: 1, number: 0, size: 20 })
    }
    if (method === 'POST') {
      const body = await readBody(req)
      const id = `vote-${uid()}`
      const vote: Vote = {
        id, title: String(body.title ?? 'Новое голосование'),
        description: body.description ? String(body.description) : null,
        mode: (body.mode as VoteMode) ?? 'SIMPLE',
        status: 'PENDING', currentRound: 1, isCreator: true, createdAt: now(),
        participants: [
          { email: MOCK_USER.email, displayName: MOCK_USER.displayName },
          ...((body.participantEmails as string[] | undefined) ?? []).map((email) => ({ email, displayName: null })),
        ],
        history: [],
      }
      db.set(id, vote)
      return json(res, toDetail(vote), 201)
    }
  }

  // ── Single vote ───────────────────────────────────────────────────────────
  if (parts[0] === 'votes' && parts[1]) {
    const vote = db.get(parts[1])

    if (parts.length === 2) {
      if (method === 'GET')    { if (!vote) return notFound(res); return json(res, toDetail(vote)) }
      if (method === 'DELETE') { if (!vote) return notFound(res); db.delete(parts[1]); return noContent(res) }
    }

    if (parts[2] === 'history' && method === 'GET') {
      if (!vote) return notFound(res)
      return json(res, vote.history)
    }

    if (parts[2] === 'participants') {
      if (!vote) return notFound(res)
      if (method === 'POST') {
        const body = await readBody(req)
        const email = String(body.email ?? '')
        if (!vote.participants.find((p) => p.email === email))
          vote.participants.push({ email, displayName: null })
        return noContent(res)
      }
      if (method === 'DELETE' && parts[3]) {
        const email = decodeURIComponent(parts[3])
        vote.participants = vote.participants.filter((p) => p.email !== email)
        return noContent(res)
      }
    }

    if (parts[2] === 'draw' && method === 'POST') {
      if (!vote) return notFound(res)
      let eligible = vote.participants
      let newRoundStarted = false

      if (vote.mode === 'FAIR_ROTATION') {
        const wonThisRound = new Set(
          vote.history.filter((h) => h.round === vote.currentRound).map((h) => h.winnerEmail)
        )
        eligible = vote.participants.filter((p) => !wonThisRound.has(p.email))
        if (eligible.length === 0) {
          vote.currentRound++
          eligible = vote.participants
          newRoundStarted = true
        }
      }

      if (eligible.length === 0) return json(res, { message: 'No eligible participants' }, 400)

      const winner = eligible[Math.floor(Math.random() * eligible.length)]
      vote.history.push({ id: `h-${uid()}`, winnerEmail: winner.email, winnerDisplayName: winner.displayName, round: vote.currentRound, drawnAt: now() })
      vote.status = 'DRAWN'
      return json(res, { winnerEmail: winner.email, winnerDisplayName: winner.displayName, round: vote.currentRound, newRoundStarted })
    }

    if (parts[2] === 'reopen' && method === 'POST') {
      if (!vote) return notFound(res)
      vote.status = 'PENDING'
      return noContent(res)
    }

    if (parts[2] === 'close' && method === 'POST') {
      if (!vote) return notFound(res)
      vote.status = 'CLOSED'
      return noContent(res)
    }
  }

  // ── Telegram ──────────────────────────────────────────────────────────────
  if (parts[0] === 'telegram') {
    if (method === 'GET')    return json(res, { token: 'mock-link-token-abc123', expiresAt: new Date(Date.now() + 300_000).toISOString() })
    if (method === 'DELETE') return noContent(res)
  }

  notFound(res)
}

// ── Plugin export ─────────────────────────────────────────────────────────────

export function mockApiPlugin(): Plugin {
  return {
    name: 'mock-api',
    configureServer(server) {
      server.middlewares.use('/api/v1', async (req, res, next) => {
        const pathname = new URL(req.url ?? '/', 'http://localhost').pathname
        try {
          await route(req, res, pathname)
        } catch (err) {
          next(err)
        }
      })
    },
  }
}
