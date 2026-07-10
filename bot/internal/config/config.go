package config

import (
	"errors"

	"github.com/spf13/viper"
)

// defaultGRPCSharedSecret is used only when GRPC_SHARED_SECRET is unset, to keep
// zero-config local dev working. It must never be relied upon outside local dev -
// Config.GRPCSharedSecretIsDefault flags when it's in effect so callers can warn loudly.
const defaultGRPCSharedSecret = "changeme-dev-grpc-shared-secret"

type Config struct {
	BotToken                  string
	GRPCServerAddr            string
	GRPCSharedSecret          string
	GRPCSharedSecretIsDefault bool
	OTELExporterEndpoint      string
	LogLevel                  string
}

func Load() (*Config, error) {
	viper.AutomaticEnv()

	viper.SetDefault("GRPC_SERVER_ADDR", "localhost:9090")
	viper.SetDefault("GRPC_SHARED_SECRET", defaultGRPCSharedSecret)
	viper.SetDefault("LOG_LEVEL", "info")
	viper.SetDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317")

	cfg := &Config{
		BotToken:             viper.GetString("BOT_TOKEN"),
		GRPCServerAddr:       viper.GetString("GRPC_SERVER_ADDR"),
		GRPCSharedSecret:     viper.GetString("GRPC_SHARED_SECRET"),
		OTELExporterEndpoint: viper.GetString("OTEL_EXPORTER_OTLP_ENDPOINT"),
		LogLevel:             viper.GetString("LOG_LEVEL"),
	}
	cfg.GRPCSharedSecretIsDefault = cfg.GRPCSharedSecret == defaultGRPCSharedSecret
	if cfg.BotToken == "" {
		return nil, errors.New("BOT_TOKEN is required but not set")
	}
	return cfg, nil
}
