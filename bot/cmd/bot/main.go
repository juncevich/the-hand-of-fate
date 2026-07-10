package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	"go.uber.org/zap"

	"github.com/juncevich/the-hand-of-fate/bot/internal/config"
	"github.com/juncevich/the-hand-of-fate/bot/internal/grpcclient"
	"github.com/juncevich/the-hand-of-fate/bot/internal/handler"
)

// shutdownGracePeriod bounds how long we wait for in-flight message handlers
// to drain before exiting anyway, so a stuck Telegram/gRPC call can't block
// the process from ever terminating on SIGTERM.
const shutdownGracePeriod = 15 * time.Second

func main() {
	// ── Logger ────────────────────────────────────────────────────────────────
	log, _ := zap.NewProduction()
	defer log.Sync() //nolint:errcheck

	// ── Config ────────────────────────────────────────────────────────────────
	cfg, err := config.Load()
	if err != nil {
		log.Fatal("failed to load config", zap.Error(err))
	}
	if cfg.GRPCSharedSecretIsDefault {
		log.Warn("GRPC_SHARED_SECRET is not set, using the well-known dev default - do not use this in any shared or public environment")
	}

	// ── gRPC client ───────────────────────────────────────────────────────────
	fateClient, conn, err := grpcclient.New(cfg.GRPCServerAddr, cfg.GRPCSharedSecret, log)
	if err != nil {
		log.Fatal("failed to connect to backend gRPC", zap.Error(err))
	}
	defer conn.Close()

	// ── Telegram bot ──────────────────────────────────────────────────────────
	bot, err := tgbotapi.NewBotAPI(cfg.BotToken)
	if err != nil {
		log.Fatal("failed to create telegram bot", zap.Error(err))
	}
	log.Info("Telegram bot authorised", zap.String("username", bot.Self.UserName))

	// ── Handler ───────────────────────────────────────────────────────────────
	h := handler.New(bot, fateClient, log)
	if err := h.RegisterCommands(); err != nil {
		log.Fatal("failed to register Telegram menu", zap.Error(err))
	}

	// ── Graceful shutdown ─────────────────────────────────────────────────────
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	runDone := make(chan struct{})
	go func() {
		h.Run(ctx)
		close(runDone)
	}()

	<-ctx.Done()
	log.Info("shutting down")

	select {
	case <-runDone:
	case <-time.After(shutdownGracePeriod):
		log.Warn("shutdown grace period elapsed, exiting with handlers still in flight")
	}
}
