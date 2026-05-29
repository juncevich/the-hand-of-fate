package scenario

import (
	"fmt"

	"github.com/juncevich/fate/simulation/internal/client"
	"go.uber.org/zap"
)

// FairRotationScenario simulates a user who:
//  1. Creates a FAIR_ROTATION vote with participant emails
//  2. Draws until the round ends (all participants win once)
//  3. Verifies a new round started
//  4. Adds a new participant mid-vote
//  5. Draws again
//  6. Removes a participant
//  7. Cleans up (delete)
func FairRotationScenario(c *client.Client, log *zap.Logger) error {
	log.Info("=== FairRotationScenario start ===")

	participants := []string{
		randomEmail(),
		randomEmail(),
		randomEmail(),
	}

	vote, err := c.CreateVote(client.CreateVoteRequest{
		Title:             randomVoteTitle() + " (fair)",
		Mode:              "FAIR_ROTATION",
		ParticipantEmails: participants,
	})
	if err != nil {
		return fmt.Errorf("create vote: %w", err)
	}
	log.Info("vote created", zap.String("id", vote.ID), zap.Int("participants", len(participants)))

	// Draw until the round flips (or max 10 draws to be safe)
	roundStarted := false
	for i := 0; i < 10 && !roundStarted; i++ {
		result, err := c.Draw(vote.ID)
		if err != nil {
			return fmt.Errorf("draw %d: %w", i+1, err)
		}
		log.Info("draw", zap.Int("attempt", i+1), zap.String("winner", winnerLabel(result)),
			zap.Int("round", result.Round), zap.Bool("newRound", result.NewRoundStarted))
		roundStarted = result.NewRoundStarted

		if !roundStarted {
			// Reopen for next draw (vote status becomes DRAWN after each draw)
			if err = c.Reopen(vote.ID); err != nil {
				return fmt.Errorf("reopen after draw %d: %w", i+1, err)
			}
		}
	}

	// Add a new participant after round ends
	newParticipant := randomEmail()
	if err = c.AddParticipant(vote.ID, newParticipant); err != nil {
		return fmt.Errorf("add participant: %w", err)
	}
	log.Info("added participant", zap.String("email", newParticipant))

	// Draw with expanded pool
	if err = c.Reopen(vote.ID); err != nil {
		return fmt.Errorf("reopen before extra draw: %w", err)
	}
	result, err := c.Draw(vote.ID)
	if err != nil {
		return fmt.Errorf("extra draw: %w", err)
	}
	log.Info("extra draw winner", zap.String("winner", winnerLabel(result)))

	// Remove a participant
	if err = c.RemoveParticipant(vote.ID, participants[0]); err != nil {
		return fmt.Errorf("remove participant: %w", err)
	}
	log.Info("removed participant", zap.String("email", participants[0]))

	// Fetch full history
	history, err := c.GetHistory(vote.ID)
	if err != nil {
		return fmt.Errorf("get history: %w", err)
	}
	log.Info("full draw history", zap.Int("entries", len(history)))

	// Delete
	if err = c.DeleteVote(vote.ID); err != nil {
		return fmt.Errorf("delete vote: %w", err)
	}
	log.Info("vote deleted")

	log.Info("=== FairRotationScenario done ===")
	return nil
}
