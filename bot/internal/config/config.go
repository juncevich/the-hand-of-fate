package config

import (
	"errors"

	"github.com/spf13/viper"
)

type Config struct {
	BotToken             string
	GRPCServerAddr       string
	OTELExporterEndpoint string
	LogLevel             string
}

func Load() (*Config, error) {
	viper.AutomaticEnv()

	viper.SetDefault("GRPC_SERVER_ADDR", "localhost:9090")
	viper.SetDefault("LOG_LEVEL", "info")
	viper.SetDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317")

	cfg := &Config{
		BotToken:             viper.GetString("BOT_TOKEN"),
		GRPCServerAddr:       viper.GetString("GRPC_SERVER_ADDR"),
		OTELExporterEndpoint: viper.GetString("OTEL_EXPORTER_OTLP_ENDPOINT"),
		LogLevel:             viper.GetString("LOG_LEVEL"),
	}
	if cfg.BotToken == "" {
		return nil, errors.New("BOT_TOKEN is required but not set")
	}
	return cfg, nil
}
