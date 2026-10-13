package com.juncevich.fate.vote.internal.web

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.vote.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/votes")
@Tag(name = "Votes", description = "Votes, their participants/options and draws")
class VoteController(
    private val voteService: VoteService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createVote", summary = "Create a vote")
    fun createVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: CreateVoteWebRequest,
    ): VoteDetailDto = voteService.createVote(user.id, request.toCommand())

    @GetMapping
    @Operation(operationId = "listVotes", summary = "List votes the user created or participates in")
    fun listVotes(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @ParameterObject @PageableDefault(size = 20) pageable: Pageable,
    ): PageResponse<VoteSummaryDto> = voteService.listVotes(user.id, user.email, pageable).toResponse()

    @GetMapping("/{id}")
    @Operation(operationId = "getVote", summary = "Get vote details")
    fun getVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): VoteDetailDto = voteService.getVote(id, user.id, user.email)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteVote", summary = "Delete a vote (creator only)")
    fun deleteVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ) {
        voteService.deleteVote(id, user.id)
    }

    @PostMapping("/{id}/participants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "addParticipant", summary = "Invite a participant by email (creator only)")
    fun addParticipant(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddParticipantRequest,
    ) {
        voteService.addParticipant(id, user.id, request.email)
    }

    @DeleteMapping("/{id}/participants/{email}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeParticipant", summary = "Remove a participant (creator only)")
    fun removeParticipant(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @PathVariable email: String,
    ) {
        voteService.removeParticipant(id, user.id, email)
    }

    @PostMapping("/{id}/options")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "addOption", summary = "Add a named option (creator only)")
    fun addOption(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddOptionRequest,
    ) {
        voteService.addOption(id, user.id, request.title)
    }

    @DeleteMapping("/{id}/options/{optionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeOption", summary = "Remove an option (creator only)")
    fun removeOption(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @PathVariable optionId: UUID,
    ) {
        voteService.removeOption(id, user.id, optionId)
    }

    @PostMapping("/{id}/draw")
    @Operation(
        operationId = "drawVote",
        summary = "Draw a winner (creator only)",
        description = "Draws from the options if the vote has any, otherwise from the participants."
    )
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "reopenVote", summary = "Reopen a drawn or closed vote (creator only)")
    fun reopen(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ) {
        voteService.reopen(id, user.id)
    }

    @PostMapping("/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "closeVote", summary = "Close a vote (creator only)")
    fun closeVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ) {
        voteService.closeVote(id, user.id)
    }

    @GetMapping("/{id}/history")
    @Operation(
        operationId = "getHistory",
        summary = "Latest draw history (at most 100 entries)",
        deprecated = true,
        description = "Use `/history/page` instead."
    )
    fun getHistory(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): List<DrawHistoryDto> = voteService.getHistory(id, user.id, user.email)

    @GetMapping("/{id}/history/page")
    @Operation(operationId = "getHistoryPage", summary = "Paginated draw history, newest first")
    fun getHistoryPage(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @ParameterObject pageable: Pageable,
    ): PageResponse<DrawHistoryDto> = voteService.getHistory(id, user.id, user.email, pageable).toResponse()
}
