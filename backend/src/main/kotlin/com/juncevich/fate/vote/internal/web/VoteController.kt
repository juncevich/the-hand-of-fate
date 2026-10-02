package com.juncevich.fate.vote.internal.web

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.vote.*
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/votes")
class VoteController(
    private val voteService: VoteService,
) {
    @PostMapping
    fun createVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: CreateVoteWebRequest,
    ): ResponseEntity<VoteDetailDto> =
        ResponseEntity.status(201).body(voteService.createVote(user.id, request.toCommand()))

    @GetMapping
    fun listVotes(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PageableDefault(size = 20) pageable: Pageable,
    ): PageResponse<VoteSummaryDto> = voteService.listVotes(user.id, user.email, pageable).toResponse()

    @GetMapping("/{id}")
    fun getVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): VoteDetailDto = voteService.getVote(id, user.id, user.email)

    @DeleteMapping("/{id}")
    fun deleteVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): ResponseEntity<Unit> {
        voteService.deleteVote(id, user.id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/participants")
    fun addParticipant(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddParticipantRequest,
    ): ResponseEntity<Unit> {
        voteService.addParticipant(id, user.id, request.email)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/participants/{email}")
    fun removeParticipant(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @PathVariable email: String,
    ): ResponseEntity<Unit> {
        voteService.removeParticipant(id, user.id, email)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/options")
    fun addOption(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddOptionRequest,
    ): ResponseEntity<Unit> {
        voteService.addOption(id, user.id, request.title)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/options/{optionId}")
    fun removeOption(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @PathVariable optionId: UUID,
    ): ResponseEntity<Unit> {
        voteService.removeOption(id, user.id, optionId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/draw")
    fun draw(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): DrawResultResponse {
        val result: DrawResult = voteService.draw(id, user.id)
        return DrawResultResponse(
            winnerEmail = result.winnerEmail,
            winnerDisplayName = result.winnerDisplayName,
            winnerOptionTitle = result.winnerOptionTitle,
            round = result.round,
            newRoundStarted = result.newRoundStarted
        )
    }

    @PostMapping("/{id}/reopen")
    fun reopen(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): ResponseEntity<Unit> {
        voteService.reopen(id, user.id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/close")
    fun closeVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): ResponseEntity<Unit> {
        voteService.closeVote(id, user.id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/history")
    fun getHistory(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): List<DrawHistoryDto> = voteService.getHistory(id, user.id, user.email)

    @GetMapping("/{id}/history/page")
    fun getHistoryPage(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        pageable: Pageable,
    ): PageResponse<DrawHistoryDto> = voteService.getHistory(id, user.id, user.email, pageable).toResponse()
}
