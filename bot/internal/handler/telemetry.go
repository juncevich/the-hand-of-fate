package handler

import (
	"context"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	otelcodes "go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/trace"
)

// The global providers (installed by the telemetry package, no-ops otherwise) are resolved
// lazily, so these package-level instruments work regardless of initialisation order.
const instrumentationName = "github.com/juncevich/the-hand-of-fate/bot/internal/handler"

var (
	tracer = otel.Tracer(instrumentationName)
	meter  = otel.Meter(instrumentationName)

	// In Mimir: bot_commands_total{command, outcome}, bot_command_duration_seconds_*.
	commandsCounter, _ = meter.Int64Counter("bot.commands",
		metric.WithDescription("Telegram commands handled"), metric.WithUnit("{command}"))
	commandDuration, _ = meter.Float64Histogram("bot.command.duration",
		metric.WithDescription("Time to handle a Telegram command, backend calls and replies included"),
		metric.WithUnit("s"))
)

// knownCommands bounds the `command` label; anything else is reported as "unknown".
var knownCommands = map[string]bool{
	"start": true, "help": true, "link": true, "votes": true, "newvote": true,
	"vote": true, "draw": true, "result": true, "history": true, "unlink": true,
}

func commandLabel(cmd string) string {
	if knownCommands[cmd] {
		return cmd
	}
	return "unknown"
}

// commandTelemetry spans one command: the returned context carries the span (so the gRPC
// calls made while handling it become its children), and finish records the metrics.
type commandTelemetry struct {
	span    trace.Span
	command string
	start   time.Time
}

func startCommand(ctx context.Context, cmd string) (context.Context, *commandTelemetry) {
	label := commandLabel(cmd)
	ctx, span := tracer.Start(ctx, "telegram /"+label,
		trace.WithSpanKind(trace.SpanKindServer),
		trace.WithAttributes(attribute.String("bot.command", label)))
	return ctx, &commandTelemetry{span: span, command: label, start: time.Now()}
}

// finish ends the span; panicked marks the command as failed.
func (t *commandTelemetry) finish(ctx context.Context, panicked bool) {
	outcome := "ok"
	if panicked {
		outcome = "panic"
		t.span.SetStatus(otelcodes.Error, "panic while handling the command")
	}
	commandsCounter.Add(ctx, 1, metric.WithAttributes(
		attribute.String("command", t.command), attribute.String("outcome", outcome)))
	commandDuration.Record(ctx, time.Since(t.start).Seconds(),
		metric.WithAttributes(attribute.String("command", t.command)))
	t.span.End()
}
