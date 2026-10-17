package handler

import (
	"context"
	"sync"
	"testing"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/metric/metricdata"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
	"go.opentelemetry.io/otel/trace"
	"go.uber.org/zap/zaptest"
	"google.golang.org/grpc"
)

// The package-level tracer/meter delegate to the first global providers installed, so the
// test providers are installed once for the whole package.
var (
	installOnce  sync.Once
	spanRecorder *tracetest.SpanRecorder
	metricReader *sdkmetric.ManualReader
)

func installTestTelemetry() {
	installOnce.Do(func() {
		spanRecorder = tracetest.NewSpanRecorder()
		metricReader = sdkmetric.NewManualReader()
		otel.SetTracerProvider(sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(spanRecorder)))
		otel.SetMeterProvider(sdkmetric.NewMeterProvider(sdkmetric.WithReader(metricReader)))
	})
}

func endedSpan(t *testing.T, name string) sdktrace.ReadOnlySpan {
	t.Helper()
	for _, s := range spanRecorder.Ended() {
		if s.Name() == name {
			return s
		}
	}
	t.Fatalf("no ended span %q", name)
	return nil
}

// commandCount reads bot.commands for the given attributes (cumulative across the package).
func commandCount(t *testing.T, command, outcome string) int64 {
	t.Helper()
	var rm metricdata.ResourceMetrics
	if err := metricReader.Collect(context.Background(), &rm); err != nil {
		t.Fatalf("collect metrics: %v", err)
	}
	want := attribute.NewSet(attribute.String("command", command), attribute.String("outcome", outcome))
	for _, sm := range rm.ScopeMetrics {
		for _, m := range sm.Metrics {
			if m.Name != "bot.commands" {
				continue
			}
			for _, dp := range m.Data.(metricdata.Sum[int64]).DataPoints {
				if dp.Attributes.Equals(&want) {
					return dp.Value
				}
			}
		}
	}
	return 0
}

// spanCapturingClient records the span context the handler passes to the backend call.
type spanCapturingClient struct {
	fakeFateClient
	got trace.SpanContext
}

func (c *spanCapturingClient) GetMyVotes(ctx context.Context, req *fatev1.GetMyVotesRequest, opts ...grpc.CallOption) (*fatev1.GetMyVotesResponse, error) {
	c.got = trace.SpanContextFromContext(ctx)
	return c.fakeFateClient.GetMyVotes(ctx, req, opts...)
}

func TestCommandIsTracedAndCounted(t *testing.T) {
	installTestTelemetry()
	before := commandCount(t, "votes", "ok")
	client := &spanCapturingClient{}
	h := New(&fakeTelegram{}, client, zaptest.NewLogger(t))

	h.handleMessage(context.Background(), makeCommandMsg(1, "votes"))

	span := endedSpan(t, "telegram /votes")
	if span.SpanKind() != trace.SpanKindServer {
		t.Errorf("span kind = %v, want server", span.SpanKind())
	}
	if !client.got.IsValid() || client.got.SpanID() != span.SpanContext().SpanID() {
		t.Errorf("backend call got span %v, want the command span %v", client.got, span.SpanContext())
	}
	if got := commandCount(t, "votes", "ok") - before; got != 1 {
		t.Errorf("bot.commands{command=votes,outcome=ok} grew by %d, want 1", got)
	}
}

func TestUnknownCommandUsesBoundedLabel(t *testing.T) {
	installTestTelemetry()
	before := commandCount(t, "unknown", "ok")
	h := New(&fakeTelegram{}, &fakeFateClient{}, zaptest.NewLogger(t))

	h.handleMessage(context.Background(), makeCommandMsg(1, "definitely-not-a-command"))

	endedSpan(t, "telegram /unknown")
	if got := commandCount(t, "unknown", "ok") - before; got != 1 {
		t.Errorf("bot.commands{command=unknown} grew by %d, want 1", got)
	}
}

func TestPanicIsRecordedAsOutcome(t *testing.T) {
	installTestTelemetry()
	before := commandCount(t, "votes", "panic")
	// panickingClient (commands_test.go) blows up inside GetMyVotes; handleMessage recovers.
	h := New(&fakeTelegram{}, &panickingClient{&fakeFateClient{}}, zaptest.NewLogger(t))

	h.handleMessage(context.Background(), makeCommandMsg(1, "votes"))

	if got := commandCount(t, "votes", "panic") - before; got != 1 {
		t.Errorf("bot.commands{command=votes,outcome=panic} grew by %d, want 1", got)
	}
}

func TestPlainTextIsNotInstrumented(t *testing.T) {
	installTestTelemetry()
	spansBefore := len(spanRecorder.Ended())
	h := New(&fakeTelegram{}, &fakeFateClient{}, zaptest.NewLogger(t))

	h.handleMessage(context.Background(), &tgbotapi.Message{Chat: privateChat(1), From: &tgbotapi.User{ID: 1}, Text: "hello"})

	if got := len(spanRecorder.Ended()) - spansBefore; got != 0 {
		t.Errorf("plain text produced %d spans, want 0", got)
	}
}
