package main

import (
	"flag"
	"fmt"
	"os"
	"strings"

	"github.com/juncevich/fate/simulation/internal/mailbox"
	"github.com/juncevich/fate/simulation/internal/scenario"
	"go.uber.org/zap"
)

func main() {
	base := flag.String("url", "http://localhost:8080", "Backend base URL")
	app := flag.String("app", "http://localhost:3000", "The SPA's origin, which serves Keycloak under /auth (dev server or gateway)")
	which := flag.String("scenario", "all", "Scenario to run: all | simple | fair | options | session")
	mailpit := flag.String("mailpit", "http://localhost:8025", "Mailpit base URL, to follow the verification links Keycloak mails")
	flag.Parse()
	mail := mailbox.NewMailpit(*mailpit)

	log, _ := zap.NewDevelopment()
	defer log.Sync()

	log.Info("simulation starting", zap.String("url", *base), zap.String("app", *app), zap.String("scenario", *which))

	run := func(name string, fn func() error) {
		if *which != "all" && *which != name {
			return
		}
		log.Info("running scenario", zap.String("name", name))
		if err := fn(); err != nil {
			log.Error("scenario failed", zap.String("name", name), zap.Error(err))
			fmt.Fprintf(os.Stderr, "FAIL %s: %v\n", name, err)
			os.Exit(1)
		}
		log.Info("scenario passed", zap.String("name", name))
		fmt.Printf("OK  %s\n", name)
	}

	run("session", func() error {
		return scenario.SessionLifecycleScenario(*app, *base, mail, log)
	})

	run("simple", func() error {
		c, err := scenario.SignedUpClient(*app, *base, mail, log)
		if err != nil {
			return err
		}
		return scenario.SimpleVoteScenario(c, log)
	})

	run("options", func() error {
		c, err := scenario.SignedUpClient(*app, *base, mail, log)
		if err != nil {
			return err
		}
		return scenario.OptionsVoteScenario(c, log)
	})

	run("fair", func() error {
		c, err := scenario.SignedUpClient(*app, *base, mail, log)
		if err != nil {
			return err
		}
		return scenario.FairRotationScenario(c, log)
	})

	fmt.Println(strings.Repeat("-", 40))
	fmt.Println("All selected scenarios passed.")
}
