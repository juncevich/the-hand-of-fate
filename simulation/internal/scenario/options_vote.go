package scenario

import (
	"fmt"

	"github.com/juncevich/fate/simulation/internal/client"
	"go.uber.org/zap"
)

// OptionsVoteScenario simulates managing a vote that uses named options:
//  1. Create a vote without options
//  2. Add options one by one via the API
//  3. Draw (picks from options)
//  4. Remove one option
//  5. Draw again
//  6. Delete vote
func OptionsVoteScenario(c *client.Client, log *zap.Logger) error {
	log.Info("=== OptionsVoteScenario start ===")

	vote, err := c.CreateVote(client.CreateVoteRequest{
		Title: randomVoteTitle() + " (options)",
		Mode:  "SIMPLE",
	})
	if err != nil {
		return fmt.Errorf("create vote: %w", err)
	}
	log.Info("vote created (no options yet)", zap.String("id", vote.ID))

	// Add options dynamically
	options := randomOptions(3)
	for _, o := range options {
		if err = c.AddOption(vote.ID, o); err != nil {
			return fmt.Errorf("add option %q: %w", o, err)
		}
		log.Info("option added", zap.String("title", o))
	}

	// Fetch detail to confirm options landed
	detail, err := c.GetVote(vote.ID)
	if err != nil {
		return fmt.Errorf("get vote: %w", err)
	}
	log.Info("vote detail fetched", zap.Int("options", len(detail.Options)))

	// Draw — should pick an option
	result, err := c.Draw(vote.ID)
	if err != nil {
		return fmt.Errorf("draw: %w", err)
	}
	log.Info("draw result", zap.String("winner", winnerLabel(result)))

	// Remove one option
	if len(detail.Options) > 0 {
		optID := detail.Options[0].ID
		if err = c.RemoveOption(vote.ID, optID); err != nil {
			return fmt.Errorf("remove option: %w", err)
		}
		log.Info("option removed", zap.String("id", optID))
	}

	// Reopen + draw again
	if err = c.Reopen(vote.ID); err != nil {
		return fmt.Errorf("reopen: %w", err)
	}
	result2, err := c.Draw(vote.ID)
	if err != nil {
		return fmt.Errorf("second draw: %w", err)
	}
	log.Info("second draw result", zap.String("winner", winnerLabel(result2)))

	if err = c.DeleteVote(vote.ID); err != nil {
		return fmt.Errorf("delete vote: %w", err)
	}
	log.Info("vote deleted")

	log.Info("=== OptionsVoteScenario done ===")
	return nil
}
