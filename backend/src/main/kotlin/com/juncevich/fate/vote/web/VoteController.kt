package com.juncevich.fate.vote.web

import com.juncevich.fate.account.AuthenticatedUser
import com.juncevich.fate.vote.VoteService
import com.juncevich.fate.vote.VoteStatsService
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
    private val voteStatsService: VoteStatsService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createVote",
        summary = "Create a vote",
        description =
            "Without drawTarget the vote draws from options when any are given, otherwise from participants. " +
                "Options are rejected when drawTarget is PARTICIPANTS."
    )
    fun createVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: CreateVoteWebRequest,
    ): VoteDetailDto = voteService.createVote(user.id, request.toCommand()).toDto()

    @GetMapping("/stats")
    @Operation(operationId = "voteStats", summary = "Counts of the user's votes")
    fun stats(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): VoteStatsDto = VoteStatsDto(voteStatsService.countCreatedBy(user.id))

    @GetMapping
    @Operation(operationId = "listVotes", summary = "List votes the user created or participates in")
    fun listVotes(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @ParameterObject @PageableDefault(size = 20) pageable: Pageable,
    ): PageResponse<VoteSummaryDto> =
        voteService.listVotes(user.id, user.email, pageable).map { it.toDto() }.toResponse()

    @GetMapping("/{id}")
    @Operation(operationId = "getVote", summary = "Get vote details")
    fun getVote(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): VoteDetailDto = voteService.getVote(id, user.id, user.email).toDto()

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

    @PutMapping("/{id}/options/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "reorderOptions",
        summary = "Reorder the options (creator only)",
        description = "optionIds must list every option of the vote exactly once, in the new order."
    )
    fun reorderOptions(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReorderOptionsRequest,
    ) {
        voteService.reorderOptions(id, user.id, request.optionIds)
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
        description = "Draws from the vote's drawTarget: its options, or its participants."
    )
    fun draw(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
    ): DrawResultResponse = voteService.draw(id, user.id).toResponse()

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
    ): List<DrawHistoryDto> = voteService.getHistory(id, user.id, user.email).map { it.toDto() }

    @GetMapping("/{id}/history/page")
    @Operation(operationId = "getHistoryPage", summary = "Paginated draw history, newest first")
    fun getHistoryPage(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: UUID,
        @ParameterObject pageable: Pageable,
    ): PageResponse<DrawHistoryDto> =
        voteService
            .getHistory(id, user.id, user.email, pageable)
            .map {
                it.toDto()
            }.toResponse()
}
