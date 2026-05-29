package scenario

import (
	"fmt"
	"math/rand/v2"

	"github.com/google/uuid"
)

var firstNames = []string{"Alice", "Bob", "Carol", "Dave", "Eve", "Frank", "Grace", "Henry"}
var lastNames = []string{"Smith", "Jones", "Lee", "Brown", "Taylor", "White", "Harris", "Clark"}

func randomName() string {
	return firstNames[rand.IntN(len(firstNames))] + " " + lastNames[rand.IntN(len(lastNames))]
}

func randomEmail() string {
	return fmt.Sprintf("sim_%s@example.com", uuid.New().String()[:8])
}

func randomVoteTitle() string {
	topics := []string{
		"Team lunch venue",
		"Next sprint theme",
		"Office playlist",
		"Code review focus",
		"Release name",
		"Meeting facilitator",
		"Retrospective format",
		"On-call rotation",
	}
	return topics[rand.IntN(len(topics))]
}

func randomOptions(n int) []string {
	pool := []string{
		"Option Alpha", "Option Beta", "Option Gamma", "Option Delta",
		"Option Epsilon", "Option Zeta", "Option Eta", "Option Theta",
	}
	rand.Shuffle(len(pool), func(i, j int) { pool[i], pool[j] = pool[j], pool[i] })
	if n > len(pool) {
		n = len(pool)
	}
	return pool[:n]
}

func ptr[T any](v T) *T { return &v }
