// REST types are generated from the backend contract (api/openapi.yaml → `npm run gen:api`);
// this module only gives them the names the app uses.
import type { components } from '@/api/generated/schema'

type Schemas = components['schemas']

export type VoteSummary = Schemas['VoteSummaryDto']
export type VoteDetail = Schemas['VoteDetailDto']
export type VoteOption = Schemas['VoteOptionDto']
export type Participant = Schemas['ParticipantDto']
export type DrawHistoryEntry = Schemas['DrawHistoryDto']
export type DrawResult = Schemas['DrawResultResponse']
export type CreateVoteRequest = Schemas['CreateVoteWebRequest']
export type VoteMode = Schemas['VoteMode']
export type VoteStatus = Schemas['VoteStatus']

// OpenAPI has no generics: every PageResponse* schema shares this shape, only `content` differs.
export type Page<T> = Omit<Schemas['PageResponseVoteSummaryDto'], 'content'> & { content: T[] }
