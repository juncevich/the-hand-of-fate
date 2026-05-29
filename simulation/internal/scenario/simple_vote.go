package scenario

import (
	"fmt"

	"github.com/juncevich/fate/simulation/internal/client"
	"go.uber.org/zap"
)

// SimpleVoteScenario simulates a user who:
//  1. Creates a vote with named options
//  2. Lists votes and fetches the vote detail
//  3. Performs a draw
//  4. Fetches draw history
//  5. Reopens the vote and draws again
//  6. Closes the vote
//  7. Deletes the vote
func SimpleVoteScenario(c *client.Client, log *zap.Logger) error {
	log.Info("=== SimpleVoteScenario start ===")

	// 1. Create vote with options
	opts := randomOptions(4)
	vote, err := c.CreateVote(client.CreateVoteRequest{
		Title:   randomVoteTitle(),
		Mode:    "SIMPLE",
		Options: opts,
	})
	if err != nil {
		return fmt.Errorf("create vote: %w", err)
	}
	log.Info("vote created", zap.String("id", vote.ID), zap.String("title", vote.Title))

	// 2. List votes
	page, err := c.ListVotes(0, 20)
	if err != nil {
		return fmt.Errorf("list votes: %w", err)
	}
	log.Info("listed votes", zap.Int("total", page.TotalElements))

	// Fetch detail
	detail, err := c.GetVote(vote.ID)
	if err != nil {
		return fmt.Errorf("get vote: %w", err)
	}
	log.Info("fetched vote detail", zap.Int("options", len(detail.Options)))

	// 3. Draw
	result, err := c.Draw(vote.ID)
	if err != nil {
		return fmt.Errorf("draw: %w", err)
	}
	winner := winnerLabel(result)
	log.Info("draw result", zap.String("winner", winner), zap.Int("round", result.Round))

	// 4. History
	history, err := c.GetHistory(vote.ID)
	if err != nil {
		return fmt.Errorf("get history: %w", err)
	}
	log.Info("draw history", zap.Int("entries", len(history)))

	// 5. Reopen + draw again
	if err = c.Reopen(vote.ID); err != nil {
		return fmt.Errorf("reopen: %w", err)
	}
	log.Info("vote reopened")

	result2, err := c.Draw(vote.ID)
	if err != nil {
		return fmt.Errorf("second draw: %w", err)
	}
	log.Info("second draw result", zap.String("winner", winnerLabel(result2)))

	// 6. Close
	if err = c.Close(vote.ID); err != nil {
		return fmt.Errorf("close: %w", err)
	}
	log.Info("vote closed")

	// 7. Delete
	if err = c.DeleteVote(vote.ID); err != nil {
		return fmt.Errorf("delete vote: %w", err)
	}
	log.Info("vote deleted")

	log.Info("=== SimpleVoteScenario done ===")
	return nil
}

func winnerLabel(r client.DrawResultResponse) string {
	if r.WinnerOptionTitle != nil {
		return *r.WinnerOptionTitle
	}
	if r.WinnerDisplayName != nil {
		return *r.WinnerDisplayName
	}
	if r.WinnerEmail != nil {
		return *r.WinnerEmail
	}
	return "<unknown>"
}
