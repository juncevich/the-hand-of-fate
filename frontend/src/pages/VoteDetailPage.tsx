import { useParams } from 'react-router-dom'
import { useVoteDetail } from '@/hooks/useVoteDetail'
import { VoteHeader } from '@/components/vote/VoteHeader'
import { VoteLastResult } from '@/components/vote/VoteLastResult'
import { VoteOptions } from '@/components/vote/VoteOptions'
import { VoteParticipants } from '@/components/vote/VoteParticipants'
import { VoteHistory } from '@/components/vote/VoteHistory'
import { ErrorState } from '@/components/ui/error-state'
import { extractErrorMessage } from '@/lib/errors'

export function VoteDetailPage() {
  const { id } = useParams<{ id: string }>()
  const {
    vote,
    isLoading,
    isError,
    error,
    refetch,
    draw,
    reopen,
    addParticipant,
    removeParticipant,
    addOption,
    removeOption,
    deleteVote,
  } = useVoteDetail(id!)

  if (isError) {
    return (
      <div className="max-w-2xl mx-auto">
        <ErrorState message={extractErrorMessage(error)} onRetry={() => refetch()} />
      </div>
    )
  }

  if (isLoading) {
    return <div className="glass p-8 animate-pulse h-64" />
  }

  if (!vote) return null

  return (
    <div className="max-w-2xl mx-auto">
      <VoteHeader
        vote={vote}
        onDraw={() => draw.mutate()}
        onReopen={() => reopen.mutate()}
        onDelete={() => deleteVote.mutate()}
        isDrawLoading={draw.isPending}
        isReopenLoading={reopen.isPending}
      />

      {vote.lastResult && <VoteLastResult result={vote.lastResult} />}

      <VoteOptions
        options={vote.options}
        isCreator={vote.isCreator}
        status={vote.status}
        onAdd={(title) => addOption.mutate(title)}
        onRemove={(optionId) => removeOption.mutate(optionId)}
        isAddLoading={addOption.isPending}
      />

      <VoteParticipants
        participants={vote.participants}
        isCreator={vote.isCreator}
        status={vote.status}
        onAdd={(email) => addParticipant.mutate(email)}
        onRemove={(email) => removeParticipant.mutate(email)}
        isAddLoading={addParticipant.isPending}
      />

      <VoteHistory voteId={id!} />
    </div>
  )
}
