module github.com/juncevich/the-hand-of-fate/bot

go 1.24

require (
	github.com/go-telegram-bot-api/telegram-bot-api/v5 v5.5.1
	github.com/spf13/viper v1.20.1
	go.opentelemetry.io/otel v1.35.0
	go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc v1.35.0
	go.opentelemetry.io/otel/sdk v1.35.0
	go.uber.org/zap v1.27.0
	google.golang.org/grpc v1.71.0
	google.golang.org/protobuf v1.36.5
)
