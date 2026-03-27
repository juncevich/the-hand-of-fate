export type VoteMode = 'SIMPLE' | 'FAIR_ROTATION'
export type VoteStatus = 'PENDING' | 'DRAWN' | 'CLOSED'

export interface VoteSummary {
  id: string
  title: string
  mode: VoteMode
  status: VoteStatus
  currentRound: number
  participantCount: number
  isCreator: boolean
  createdAt: string
}

export interface VoteDetail {
  id: string
  title: string
  description: string | null
  mode: VoteMode
  status: VoteStatus
  currentRound: number
  participants: Participant[]
  lastResult: DrawHistoryEntry | null
  isCreator: boolean
  createdAt: string
}

export interface Participant {
  email: string
  displayName: string | null
}

export interface DrawHistoryEntry {
  id: string
  winnerEmail: string
  winnerDisplayName: string | null
  round: number
  drawnAt: string
}

export interface DrawResult {
  winnerEmail: string
  winnerDisplayName: string | null
  round: number
  newRoundStarted: boolean
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
