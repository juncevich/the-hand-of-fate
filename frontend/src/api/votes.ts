import { apiClient } from './client'
import type {
  DrawResult,
  DrawHistoryEntry,
  Page,
  VoteDetail,
  VoteMode,
  VoteSummary,
} from '@/types/vote'

export const votesApi = {
  list: (page = 0, size = 20) =>
    apiClient
      .get<Page<VoteSummary>>('/votes', { params: { page, size } })
      .then((r) => r.data),

  get: (id: string) =>
    apiClient.get<VoteDetail>(`/votes/${id}`).then((r) => r.data),

  create: (data: {
    title: string
    description?: string
    mode: VoteMode
    participantEmails: string[]
    options: string[]
  }) => apiClient.post<VoteDetail>('/votes', data).then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/votes/${id}`),

  addParticipant: (id: string, email: string) =>
    apiClient.post(`/votes/${id}/participants`, { email }),

  removeParticipant: (id: string, email: string) =>
    apiClient.delete(`/votes/${id}/participants/${email}`),

  addOption: (id: string, title: string) =>
    apiClient.post(`/votes/${id}/options`, { title }),

  removeOption: (id: string, optionId: string) =>
    apiClient.delete(`/votes/${id}/options/${optionId}`),

  draw: (id: string) =>
    apiClient.post<DrawResult>(`/votes/${id}/draw`).then((r) => r.data),

  reopen: (id: string) => apiClient.post(`/votes/${id}/reopen`),

  close: (id: string) => apiClient.post(`/votes/${id}/close`),

  getHistory: (id: string) =>
    apiClient.get<DrawHistoryEntry[]>(`/votes/${id}/history`).then((r) => r.data),
}
