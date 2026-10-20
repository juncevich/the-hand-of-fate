import type { Logger, Plugin } from 'vite'
import type { IncomingMessage, ServerResponse } from 'node:http'
import { randomBytes, randomUUID } from 'node:crypto'
import type { components } from './src/api/generated/schema'

// In-memory stand-in for the REST API (`npm run dev:mock`). Response bodies are typed with the
// schema generated from api/openapi.yaml, so `tsc -b` flags this file when the contract changes.
// There is no Keycloak here: POST /mock/sign-in hands out a token for the demo account
// (src/auth/mock.ts), and the API accepts it as `Bearer mock-access.<user id>`.

// ── Types ─────────────────────────────────────────────────────────────────────

type Schemas      = components['schemas']
type ErrorCode    = Schemas['ErrorCode']
type VoteMode     = Schemas['VoteMode']
type VoteStatus   = Schemas['VoteStatus']
type DrawTarget   = Schemas['DrawTarget']
type VoteOption   = Schemas['VoteOptionDto']
type HistoryEntry = Schemas['DrawHistoryDto'] & { optionId: string | null }

interface Account {
  id: string; email: string; displayName: string; avatarVersion: number | null
}
interface Vote {
  id: string; title: string; description: string | null
  mode: VoteMode; drawTarget: DrawTarget; status: VoteStatus; currentRound: number
  creatorId: string; createdAt: string
  participants: string[]          // emails; display names come from the accounts
  options: VoteOption[]           // in position order
  history: HistoryEntry[]         // oldest first
}

// ── Limits (VoteLimits on the backend) ────────────────────────────────────────

const MAX_TITLE = 255
const MAX_DESCRIPTION = 2000
const MAX_PARTICIPANTS = 100
const MAX_OPTIONS = 100
const MAX_PAGE_SIZE = 100

// ── Mock state ────────────────────────────────────────────────────────────────

const now = () => new Date().toISOString()
const daysAgo = (n: number) => new Date(Date.now() - n * 86_400_000).toISOString()
const hoursAgo = (n: number) => new Date(Date.now() - n * 3_600_000).toISOString()
const fixedId = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`

const accounts = new Map<string, Account>(
  [
    { id: fixedId(1), email: 'demo@example.com',  displayName: 'Demo User' },
    { id: fixedId(2), email: 'alice@example.com', displayName: 'Alice' },
    { id: fixedId(3), email: 'bob@example.com',   displayName: 'Bob' },
  ].map((a) => [a.id, { ...a, avatarVersion: null }]),
)
const [DEMO_ID, ALICE_ID] = [fixedId(1), fixedId(2)]

const suppressed = new Set<string>()   // unsubscribed addresses

const participantWin = (id: number, email: string, round: number, drawnAt: string): HistoryEntry =>
  ({ id: fixedId(id), winnerEmail: email, winnerDisplayName: displayNameOf(email), winnerOptionTitle: null, optionId: null, round, drawnAt })
const optionWin = (id: number, option: VoteOption, round: number, drawnAt: string): HistoryEntry =>
  ({ id: fixedId(id), winnerEmail: null, winnerDisplayName: null, winnerOptionTitle: option.title, optionId: option.id, round, drawnAt })

const topics: VoteOption[] = [
  { id: fixedId(301), title: 'Новый онбординг' },
  { id: fixedId(302), title: 'Ретро по релизу' },
  { id: fixedId(303), title: 'Тёмная тема' },
]

const votes = new Map<string, Vote>(([
  {
    id: fixedId(101), title: 'Выбор ресторана на пятницу', description: 'Куда идём после работы',
    mode: 'FAIR_ROTATION', drawTarget: 'PARTICIPANTS', status: 'PENDING', currentRound: 2,
    creatorId: DEMO_ID, createdAt: daysAgo(3),
    participants: ['demo@example.com', 'alice@example.com', 'bob@example.com', 'carol@example.com'],
    options: [],
    history: [
      participantWin(201, 'alice@example.com', 1, daysAgo(2)),
      participantWin(202, 'bob@example.com', 1, daysAgo(1)),
    ],
  },
  {
    id: fixedId(102), title: 'Дежурный по кухне', description: null,
    mode: 'FAIR_ROTATION', drawTarget: 'PARTICIPANTS', status: 'DRAWN', currentRound: 1,
    creatorId: DEMO_ID, createdAt: daysAgo(7),
    participants: ['demo@example.com', 'dave@example.com'],
    options: [],
    history: [participantWin(203, 'dave@example.com', 1, hoursAgo(5))],
  },
  {
    id: fixedId(103), title: 'Тема следующего митапа', description: 'Участники — наблюдатели, разыгрываются темы',
    mode: 'FAIR_ROTATION', drawTarget: 'OPTIONS', status: 'PENDING', currentRound: 1,
    creatorId: DEMO_ID, createdAt: daysAgo(5),
    participants: ['demo@example.com', 'alice@example.com'],
    options: topics,
    history: [optionWin(204, topics[1], 1, daysAgo(4))],
  },
  {
    id: fixedId(104), title: 'Случайный розыгрыш призов', description: 'Приз получит один счастливчик',
    mode: 'SIMPLE', drawTarget: 'PARTICIPANTS', status: 'CLOSED', currentRound: 1,
    creatorId: ALICE_ID, createdAt: daysAgo(14),
    participants: ['alice@example.com', 'demo@example.com', 'eve@example.com'],
    options: [],
    history: [participantWin(205, 'eve@example.com', 1, daysAgo(10))],
  },
] satisfies Vote[]).map((v) => [v.id, v]))

// ── Helpers ───────────────────────────────────────────────────────────────────

let logger: Logger | undefined

function accountByEmail(email: string) {
  for (const a of accounts.values()) if (a.email === email) return a
  return undefined
}
function displayNameOf(email: string) { return accountByEmail(email)?.displayName ?? null }

const normalizeEmail = (email: unknown) => String(email ?? '').trim().toLowerCase()
const isEmail = (email: string) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)

const avatarUrl = (a: Account) =>
  a.avatarVersion === null ? null : `/api/v1/users/${a.id}/avatar?v=${a.avatarVersion}`

const profile = (a: Account): Schemas['ProfileResponse'] => ({
  userId: a.id, email: a.email, displayName: a.displayName, avatarUrl: avatarUrl(a),
})

const toSummary = (v: Vote, user: Account): Schemas['VoteSummaryDto'] => ({
  id: v.id, title: v.title, mode: v.mode, status: v.status, currentRound: v.currentRound,
  participantCount: v.participants.length, isCreator: v.creatorId === user.id, createdAt: v.createdAt,
})

const toHistoryDto = (h: HistoryEntry): Schemas['DrawHistoryDto'] => ({
  id: h.id, round: h.round, drawnAt: h.drawnAt,
  winnerEmail: h.winnerEmail, winnerDisplayName: h.winnerDisplayName, winnerOptionTitle: h.winnerOptionTitle,
})

const toDetail = (v: Vote, user: Account): Schemas['VoteDetailDto'] => ({
  id: v.id, title: v.title, description: v.description,
  mode: v.mode, drawTarget: v.drawTarget, status: v.status, currentRound: v.currentRound,
  participants: v.participants.map((email) => ({ email, displayName: displayNameOf(email) })),
  options: v.options,
  lastResult: v.history.length ? toHistoryDto(v.history[v.history.length - 1]) : null,
  isCreator: v.creatorId === user.id, createdAt: v.createdAt,
})

interface Page<T> { content: T[]; number: number; size: number; totalElements: number; totalPages: number }

function paginate<T>(items: T[], query: URLSearchParams): Page<T> | null {
  const page = Number(query.get('page') ?? 0)
  const size = Number(query.get('size') ?? 20)
  if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > MAX_PAGE_SIZE) return null
  return {
    content: items.slice(page * size, (page + 1) * size),
    number: page, size, totalElements: items.length, totalPages: Math.ceil(items.length / size),
  }
}

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
const problem = (res: ServerResponse, status: number, code: ErrorCode, title: string, extra: Partial<Schemas['ProblemDetail']> = {}) => {
  const body: Schemas['ProblemDetail'] = { status, code, title, timestamp: now(), ...extra }
  res.writeHead(status, { 'Content-Type': 'application/problem+json' })
  res.end(JSON.stringify(body))
}
const invalidField = (res: ServerResponse, field: string, message: string, constraint: string) =>
  problem(res, 400, 'VALIDATION_FAILED', 'Validation failed', { errors: { [field]: message }, fieldErrors: { [field]: constraint } })

function currentUser(req: IncomingMessage) {
  const match = /^Bearer mock-access\.(.+)$/.exec(req.headers.authorization ?? '')
  return match ? accounts.get(match[1]) : undefined
}

// ── Account ───────────────────────────────────────────────────────────────────

function routeAccount(req: IncomingMessage, res: ServerResponse, action: string, user: Account) {
  switch (action) {
    case 'logout-all':
      return noContent(res)
    // Keycloak would ask for the password again after an old sign-in; the mock never does.
    case 'delete-account': {
      req.resume()
      for (const vote of [...votes.values()]) {
        if (vote.creatorId === user.id) { votes.delete(vote.id); continue }
        vote.participants = vote.participants.filter((email) => email !== user.email)
        for (const h of vote.history) {
          if (h.winnerEmail === user.email) { h.winnerEmail = null; h.winnerDisplayName = 'Deleted user' }
        }
      }
      accounts.delete(user.id)
      return noContent(res)
    }
  }
  return problem(res, 404, 'REQUEST_INVALID', 'Not found')
}

// ── Votes ─────────────────────────────────────────────────────────────────────

async function routeVotes(req: IncomingMessage, res: ServerResponse, parts: string[], query: URLSearchParams, user: Account) {
  const method = req.method ?? 'GET'
  const visible = (v: Vote) => v.creatorId === user.id || v.participants.includes(user.email)

  if (parts.length === 1 && method === 'GET') {
    const list = [...votes.values()].filter(visible)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map((v) => toSummary(v, user))
    const page = paginate(list, query)
    return page ? json(res, page satisfies Schemas['PageResponseVoteSummaryDto']) : problem(res, 400, 'PAGE_INVALID', 'Invalid page request')
  }

  if (parts.length === 1 && method === 'POST') {
    const body = await readBody(req)
    const title = String(body.title ?? '').trim()
    if (!title || title.length > MAX_TITLE) return problem(res, 400, 'TITLE_INVALID', `Title must be 1–${MAX_TITLE} characters`)
    const description = body.description ? String(body.description) : null
    if ((description?.length ?? 0) > MAX_DESCRIPTION) return problem(res, 400, 'DESCRIPTION_TOO_LONG', `Description must not exceed ${MAX_DESCRIPTION} characters`)
    const invited = ((body.participantEmails as unknown[] | undefined) ?? []).map(normalizeEmail)
    const bad = invited.find((e) => !isEmail(e))
    if (bad !== undefined) return problem(res, 400, 'INVALID_EMAIL', `Invalid email: ${bad}`)
    const participants = [...new Set([user.email, ...invited])]
    if (participants.length > MAX_PARTICIPANTS) return problem(res, 400, 'TOO_MANY_PARTICIPANTS', `At most ${MAX_PARTICIPANTS} participants`)
    const titles = [...new Set(((body.options as unknown[] | null | undefined) ?? []).map((t) => String(t).trim()))]
    if (titles.length > MAX_OPTIONS) return problem(res, 400, 'TOO_MANY_OPTIONS', `At most ${MAX_OPTIONS} options`)
    if (titles.some((t) => !t || t.length > MAX_TITLE)) return problem(res, 400, 'TITLE_INVALID', `Option titles must be 1–${MAX_TITLE} characters`)
    const drawTarget = (body.drawTarget as DrawTarget | null | undefined) ?? (titles.length ? 'OPTIONS' : 'PARTICIPANTS')
    if (drawTarget === 'PARTICIPANTS' && titles.length)
      return problem(res, 400, 'OPTIONS_NOT_ALLOWED', 'A vote that draws participants cannot have options')

    const vote: Vote = {
      id: randomUUID(), title, description, mode: (body.mode as VoteMode | undefined) ?? 'SIMPLE', drawTarget,
      status: 'PENDING', currentRound: 1, creatorId: user.id, createdAt: now(),
      participants, options: titles.map((t) => ({ id: randomUUID(), title: t })), history: [],
    }
    votes.set(vote.id, vote)
    return json(res, toDetail(vote, user), 201)
  }

  if (parts[1] === 'stats' && method === 'GET') {
    const createdCount = [...votes.values()].filter((v) => v.creatorId === user.id).length
    return json(res, { createdCount } satisfies Schemas['VoteStatsDto'])
  }

  const vote = votes.get(parts[1])
  if (!vote) return problem(res, 404, 'VOTE_NOT_FOUND', 'Vote not found')
  if (!visible(vote)) return problem(res, 403, 'VOTE_ACCESS_DENIED', 'Vote is not available for this user')
  const action = parts.slice(2).join('/')

  // Read-only for every participant
  if (method === 'GET') {
    if (action === '') return json(res, toDetail(vote, user))
    const newestFirst = [...vote.history].reverse().map(toHistoryDto)
    if (action === 'history') return json(res, newestFirst.slice(0, 100))
    if (action === 'history/page') {
      const page = paginate(newestFirst, query)
      return page ? json(res, page satisfies Schemas['PageResponseDrawHistoryDto']) : problem(res, 400, 'PAGE_INVALID', 'Invalid page request')
    }
  }

  // Everything else is the creator's
  if (vote.creatorId !== user.id) return problem(res, 403, 'NOT_VOTE_CREATOR', 'Only the creator can perform this action')
  const notPending = () => problem(res, 409, 'VOTE_NOT_PENDING', 'Cannot modify a non-pending vote')

  if (action === '' && method === 'DELETE') { votes.delete(vote.id); return noContent(res) }

  if (action === 'participants' && method === 'POST') {
    const email = normalizeEmail((await readBody(req)).email)
    if (!isEmail(email)) return invalidField(res, 'email', 'must be a well-formed email address', 'Email')
    if (vote.status !== 'PENDING') return notPending()
    if (vote.participants.includes(email)) return problem(res, 409, 'PARTICIPANT_EXISTS', 'Participant already exists')
    if (vote.participants.length >= MAX_PARTICIPANTS) return problem(res, 400, 'TOO_MANY_PARTICIPANTS', `At most ${MAX_PARTICIPANTS} participants`)
    vote.participants.push(email)
    return noContent(res)
  }
  if (parts[2] === 'participants' && parts[3] && method === 'DELETE') {
    if (vote.status !== 'PENDING') return notPending()
    const email = normalizeEmail(decodeURIComponent(parts[3]))
    vote.participants = vote.participants.filter((e) => e !== email)
    return noContent(res)
  }

  if (action === 'options' && method === 'POST') {
    const title = String((await readBody(req)).title ?? '').trim()
    if (vote.status !== 'PENDING') return notPending()
    if (vote.drawTarget !== 'OPTIONS') return problem(res, 409, 'OPTIONS_NOT_ALLOWED', 'A vote that draws participants cannot have options')
    if (vote.options.length >= MAX_OPTIONS) return problem(res, 400, 'TOO_MANY_OPTIONS', `At most ${MAX_OPTIONS} options`)
    if (!title || title.length > MAX_TITLE) return problem(res, 400, 'TITLE_INVALID', `Title must be 1–${MAX_TITLE} characters`)
    vote.options.push({ id: randomUUID(), title })
    return noContent(res)
  }
  if (action === 'options/order' && method === 'PUT') {
    const ids = ((await readBody(req)).optionIds as string[] | undefined) ?? []
    if (vote.status !== 'PENDING') return notPending()
    const byId = new Map(vote.options.map((o) => [o.id, o]))
    if (ids.length !== byId.size || new Set(ids).size !== ids.length || ids.some((id) => !byId.has(id)))
      return problem(res, 400, 'OPTION_ORDER_INVALID', 'The new order must list every option of the vote exactly once')
    vote.options = ids.map((id) => byId.get(id)!)
    return noContent(res)
  }
  if (parts[2] === 'options' && parts[3] && method === 'DELETE') {
    if (vote.status !== 'PENDING') return notPending()
    vote.options = vote.options.filter((o) => o.id !== parts[3])
    return noContent(res)
  }

  if (action === 'draw' && method === 'POST') {
    if (vote.status !== 'PENDING') return problem(res, 409, 'VOTE_NOT_PENDING', `Vote must be in PENDING status to draw. Current status: ${vote.status}`)
    const byOptions = vote.drawTarget === 'OPTIONS'
    if (byOptions && !vote.options.length) return problem(res, 409, 'NO_OPTIONS', 'Cannot draw: add at least one option first')
    if (!byOptions && !vote.participants.length) return problem(res, 409, 'NO_PARTICIPANTS', 'Cannot draw: vote has no participants')

    const keys = byOptions ? vote.options.map((o) => o.id) : vote.participants
    let eligible = keys
    let newRoundStarted = false
    if (vote.mode === 'FAIR_ROTATION') {
      const won = new Set(vote.history.filter((h) => h.round === vote.currentRound).map((h) => byOptions ? h.optionId : h.winnerEmail))
      eligible = keys.filter((k) => !won.has(k))
      if (!eligible.length) { vote.currentRound++; eligible = keys; newRoundStarted = true }
    }
    const winner = eligible[Math.floor(Math.random() * eligible.length)]
    const option = byOptions ? vote.options.find((o) => o.id === winner)! : null
    const entry: HistoryEntry = {
      id: randomUUID(), round: vote.currentRound, drawnAt: now(),
      winnerEmail: option ? null : winner, winnerDisplayName: option ? null : displayNameOf(winner),
      winnerOptionTitle: option?.title ?? null, optionId: option?.id ?? null,
    }
    vote.history.push(entry)
    vote.status = 'DRAWN'
    const result: Schemas['DrawResultResponse'] = {
      winnerEmail: entry.winnerEmail, winnerDisplayName: entry.winnerDisplayName,
      winnerOptionTitle: entry.winnerOptionTitle, round: entry.round, newRoundStarted,
    }
    return json(res, result)
  }
  if (action === 'reopen' && method === 'POST') {
    if (vote.status !== 'DRAWN') return problem(res, 409, 'VOTE_NOT_DRAWN', 'Only DRAWN votes can be reopened')
    vote.status = 'PENDING'
    return noContent(res)
  }
  if (action === 'close' && method === 'POST') {
    if (vote.status === 'CLOSED') return problem(res, 409, 'VOTE_ALREADY_CLOSED', 'Vote is already closed')
    vote.status = 'CLOSED'
    return noContent(res)
  }

  return problem(res, 404, 'REQUEST_INVALID', 'Not found')
}

// ── Router ────────────────────────────────────────────────────────────────────

async function route(req: IncomingMessage, res: ServerResponse, url: URL) {
  const method = req.method ?? 'GET'
  const parts  = url.pathname.replace(/^\//, '').split('/')

  // ── Public ────────────────────────────────────────────────────────────────
  // Stands in for signing in through Keycloak (src/auth/mock.ts): always the demo account.
  if (parts[0] === 'mock' && parts[1] === 'sign-in' && method === 'POST') {
    return json(res, { accessToken: `mock-access.${DEMO_ID}` })
  }

  if (parts[0] === 'notifications' && method === 'POST') {
    // Any non-empty signature passes: the mock cannot sign links it never mailed.
    const email = normalizeEmail(parts[2] === 'one-click' ? url.searchParams.get('email') : (await readBody(req)).email)
    if (!email) return problem(res, 400, 'INVALID_TOKEN', 'This unsubscribe link is invalid')
    suppressed.add(email)
    return noContent(res)
  }

  // Avatar image: any upload becomes a generated placeholder
  if (parts[0] === 'users' && parts[1] !== 'me' && parts[2] === 'avatar' && method === 'GET') {
    if (accounts.get(parts[1])?.avatarVersion == null) return problem(res, 404, 'AVATAR_NOT_FOUND', 'Avatar not found')
    res.writeHead(200, { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'no-cache' })
    return res.end('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" fill="#c9a227"/>'
      + '<circle cx="32" cy="25" r="11" fill="#1a1a2e"/><rect x="14" y="40" width="36" height="20" rx="10" fill="#1a1a2e"/></svg>')
  }

  // ── Authenticated ─────────────────────────────────────────────────────────
  const user = currentUser(req)
  if (!user) return problem(res, 401, 'UNAUTHORIZED', 'Authentication required')

  if (parts[0] === 'auth' && method === 'POST') return routeAccount(req, res, parts[1], user)

  if (parts[0] === 'votes') return routeVotes(req, res, parts, url.searchParams, user)

  if (parts[0] === 'users' && parts[1] === 'me') {
    if (parts.length === 2 && method === 'GET') return json(res, profile(user))
    if (parts[2] === 'avatar' && method === 'PUT') {
      req.resume()
      user.avatarVersion = Date.now()
      return json(res, { avatarUrl: avatarUrl(user)! } satisfies Schemas['AvatarResponse'])
    }
    if (parts[2] === 'avatar' && method === 'DELETE') { user.avatarVersion = null; return noContent(res) }
    if (parts[2] === 'email-preferences') {
      if (method === 'PUT') {
        const enabled = (await readBody(req)).notificationsEnabled !== false
        if (enabled) suppressed.delete(user.email)
        else suppressed.add(user.email)
      }
      return json(res, { notificationsEnabled: !suppressed.has(user.email) } satisfies Schemas['EmailPreferencesDto'])
    }
  }

  if (parts[0] === 'telegram') {
    if (parts[1] === 'link-token' && method === 'GET') {
      const token: Schemas['LinkTokenResponse'] = { token: `mock-link-${randomBytes(6).toString('hex')}`, expiresAt: new Date(Date.now() + 300_000).toISOString() }
      return json(res, token)
    }
    if (parts[1] === 'unlink' && method === 'DELETE') return noContent(res)
  }

  problem(res, 404, 'REQUEST_INVALID', 'Not found')
}

// ── Plugin export ─────────────────────────────────────────────────────────────

export function mockApiPlugin(): Plugin {
  return {
    name: 'mock-api',
    configureServer(server) {
      logger = server.config.logger
      logger.info('[mock api] "Войти" signs in as demo@example.com (no Keycloak in mock mode)')
      server.middlewares.use('/api/v1', async (req, res, next) => {
        const url = new URL(req.url ?? '/', 'http://localhost')
        try {
          await route(req, res, url)
        } catch (err) {
          next(err)
        }
      })
    },
  }
}
