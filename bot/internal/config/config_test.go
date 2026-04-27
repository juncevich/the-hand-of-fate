package config

import (
	"strings"
	"testing"

	"github.com/spf13/viper"
)

func TestLoad(t *testing.T) {
	tests := []struct {
		name    string
		env     map[string]string
		wantErr string
		check   func(t *testing.T, cfg *Config)
	}{
		{
			name:    "missing BOT_TOKEN returns error",
			env:     map[string]string{},
			wantErr: "BOT_TOKEN",
		},
		{
			name:    "empty BOT_TOKEN returns error",
			env:     map[string]string{"BOT_TOKEN": ""},
			wantErr: "BOT_TOKEN",
		},
		{
			name: "valid BOT_TOKEN returns config",
			env:  map[string]string{"BOT_TOKEN": "tok"},
			check: func(t *testing.T, cfg *Config) {
				if cfg.BotToken != "tok" {
					t.Fatalf("BotToken = %q, want %q", cfg.BotToken, "tok")
				}
			},
		},
		{
			name: "default GRPCServerAddr",
			env:  map[string]string{"BOT_TOKEN": "tok"},
			check: func(t *testing.T, cfg *Config) {
				if cfg.GRPCServerAddr != "localhost:9090" {
					t.Fatalf("GRPCServerAddr = %q, want %q", cfg.GRPCServerAddr, "localhost:9090")
				}
			},
		},
		{
			name: "custom GRPCServerAddr",
			env:  map[string]string{"BOT_TOKEN": "tok", "GRPC_SERVER_ADDR": "host:1234"},
			check: func(t *testing.T, cfg *Config) {
				if cfg.GRPCServerAddr != "host:1234" {
					t.Fatalf("GRPCServerAddr = %q, want %q", cfg.GRPCServerAddr, "host:1234")
				}
			},
		},
		{
			name: "default LogLevel is info",
			env:  map[string]string{"BOT_TOKEN": "tok"},
			check: func(t *testing.T, cfg *Config) {
				if cfg.LogLevel != "info" {
					t.Fatalf("LogLevel = %q, want %q", cfg.LogLevel, "info")
				}
			},
		},
		{
			name: "custom LogLevel",
			env:  map[string]string{"BOT_TOKEN": "tok", "LOG_LEVEL": "debug"},
			check: func(t *testing.T, cfg *Config) {
				if cfg.LogLevel != "debug" {
					t.Fatalf("LogLevel = %q, want %q", cfg.LogLevel, "debug")
				}
			},
		},
		{
			name: "default OTELExporterEndpoint",
			env:  map[string]string{"BOT_TOKEN": "tok"},
			check: func(t *testing.T, cfg *Config) {
				want := "http://localhost:4317"
				if cfg.OTELExporterEndpoint != want {
					t.Fatalf("OTELExporterEndpoint = %q, want %q", cfg.OTELExporterEndpoint, want)
				}
			},
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			viper.Reset()
			t.Cleanup(viper.Reset)

			for k, v := range tc.env {
				t.Setenv(k, v)
			}

			cfg, err := Load()

			if tc.wantErr != "" {
				if err == nil {
					t.Fatalf("Load() error = nil, want error containing %q", tc.wantErr)
				}
				if !strings.Contains(err.Error(), tc.wantErr) {
					t.Fatalf("Load() error = %q, want containing %q", err.Error(), tc.wantErr)
				}
				return
			}

			if err != nil {
				t.Fatalf("Load() unexpected error: %v", err)
			}
			if tc.check != nil {
				tc.check(t, cfg)
			}
		})
	}
}
