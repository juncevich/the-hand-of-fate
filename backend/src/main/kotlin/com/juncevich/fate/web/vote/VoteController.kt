package com.juncevich.fate.web.vote

import com.juncevich.fate.security.AuthenticatedUser
import com.juncevich.fate.service.DrawResult
import com.juncevich.fate.service.VoteService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/votes")
class VoteController(private val voteService: VoteService) {

    @PostMapping
    fun createVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: CreateVoteRequest,
    ): ResponseEntity<VoteDetailDto> =
        ResponseEntity.status(201).body(voteService.createVote(user.id, request))

    @GetMapping
    fun listVotes(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<VoteSummaryDto> =
        voteService.listVotes(user.id, user.email, pageable)

    @GetMapping("/{id}")
    fun getVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): VoteDetailDto = voteService.getVote(id, user.id)

    @DeleteMapping("/{id}")
    fun deleteVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        voteService.deleteVote(id, user.id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/participants")
    fun addParticipant(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddParticipantRequest,
    ): ResponseEntity<Void> {
        voteService.addParticipant(id, user.id, request.email)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/participants/{email}")
    fun removeParticipant(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @PathVariable email: String,
    ): ResponseEntity<Void> {
        voteService.removeParticipant(id, user.id, email)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/options")
    fun addOption(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddOptionRequest,
    ): ResponseEntity<Void> {
        voteService.addOption(id, user.id, request.title)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/options/{optionId}")
    fun removeOption(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @PathVariable optionId: UUID,
    ): ResponseEntity<Void> {
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
            newRoundStarted = result.newRoundStarted,
        )
    }

    @PostMapping("/{id}/reopen")
    fun reopen(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        voteService.reopen(id, user.id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/close")
    fun closeVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        voteService.closeVote(id, user.id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/history")
    fun getHistory(@PathVariable id: UUID): List<DrawHistoryDto> =
        voteService.getHistory(id).map { it.toDto() }
}
