package telemetry

import (
	"context"
	"testing"
	"time"

	"go.opentelemetry.io/otel"
	"go.uber.org/zap"
)

func TestSetupDisabledInstallsOnlyThePropagator(t *testing.T) {
	for name, tc := range map[string]struct {
		endpoint string
		disabled bool
	}{
		"OTEL_SDK_DISABLED": {endpoint: "http://localhost:4317", disabled: true},
		"empty endpoint":    {endpoint: "", disabled: false},
	} {
		t.Run(name, func(t *testing.T) {
			before := otel.GetTracerProvider()
			shutdown, err := Setup(context.Background(), tc.endpoint, tc.disabled, zap.NewNop())
			if err != nil {
				t.Fatalf("Setup: %v", err)
			}
			if err := shutdown(context.Background()); err != nil {
				t.Errorf("shutdown: %v", err)
			}
			if otel.GetTracerProvider() != before {
				t.Error("tracer provider replaced while export is disabled")
			}
			if fields := otel.GetTextMapPropagator().Fields(); len(fields) == 0 {
				t.Error("no propagator installed; the backend would get no traceparent")
			}
		})
	}
}

// Exporters connect lazily, so Setup succeeds without a collector; this also guards the
// resource merge (a semconv schema URL clash with resource.Default() fails here).
func TestSetupWithEndpointInstallsProviders(t *testing.T) {
	before := otel.GetTracerProvider()
	shutdown, err := Setup(context.Background(), "http://127.0.0.1:4317", false, zap.NewNop())
	if err != nil {
		t.Fatalf("Setup: %v", err)
	}
	if otel.GetTracerProvider() == before {
		t.Error("tracer provider not installed")
	}

	// Nothing listens on the endpoint, so the final flush can't succeed: shutdown must give
	// up at its deadline (main allows 5 s) instead of hanging the bot's exit.
	const deadline = 500 * time.Millisecond
	ctx, cancel := context.WithTimeout(context.Background(), deadline)
	defer cancel()
	start := time.Now()
	_ = shutdown(ctx)
	if elapsed := time.Since(start); elapsed > deadline+time.Second {
		t.Errorf("shutdown took %v, want it to honour the %v deadline", elapsed, deadline)
	}
}
