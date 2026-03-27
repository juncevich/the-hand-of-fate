package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	"go.uber.org/zap"

	"github.com/juncevich/the-hand-of-fate/bot/internal/config"
	"github.com/juncevich/the-hand-of-fate/bot/internal/grpcclient"
	"github.com/juncevich/the-hand-of-fate/bot/internal/handler"
)

func main() {
	// ── Logger ────────────────────────────────────────────────────────────────
	log, _ := zap.NewProduction()
	defer log.Sync() //nolint:errcheck

	// ── Config ────────────────────────────────────────────────────────────────
	cfg, err := config.Load()
	if err != nil {
		log.Fatal("failed to load config", zap.Error(err))
	}

	// ── gRPC client ───────────────────────────────────────────────────────────
	fateClient, conn, err := grpcclient.New(cfg.GRPCServerAddr, log)
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

	// ── Graceful shutdown ─────────────────────────────────────────────────────
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go h.Run(ctx)

	<-ctx.Done()
	log.Info("shutting down")
}
