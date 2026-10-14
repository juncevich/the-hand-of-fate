package grpcclient

import (
	"context"
	"net"
	"strings"
	"testing"

	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
	"go.opentelemetry.io/otel/trace"
	"go.uber.org/zap"
	"google.golang.org/grpc"
	"google.golang.org/grpc/metadata"
)

// metadataCapturingServer records the incoming metadata of GetMyVotes.
type metadataCapturingServer struct {
	fatev1.UnimplementedFateServiceServer
	got chan metadata.MD
}

func (s *metadataCapturingServer) GetMyVotes(ctx context.Context, _ *fatev1.GetMyVotesRequest) (*fatev1.GetMyVotesResponse, error) {
	md, _ := metadata.FromIncomingContext(ctx)
	s.got <- md
	return &fatev1.GetMyVotesResponse{}, nil
}

// A backend call made inside a command span carries that trace in `traceparent` (so the
// backend's spans join it), next to the shared secret, and is recorded as a client span.
func TestCallsPropagateTraceContext(t *testing.T) {
	recorder := tracetest.NewSpanRecorder()
	provider := sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(recorder))
	otel.SetTracerProvider(provider)
	otel.SetTextMapPropagator(propagation.TraceContext{})

	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	srv := grpc.NewServer()
	backend := &metadataCapturingServer{got: make(chan metadata.MD, 1)}
	fatev1.RegisterFateServiceServer(srv, backend)
	go func() { _ = srv.Serve(lis) }()
	defer srv.Stop()

	client, conn, err := New(lis.Addr().String(), "s3cret", zap.NewNop())
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	defer conn.Close()

	ctx, parent := provider.Tracer("test").Start(context.Background(), "telegram /votes")
	if _, err := client.GetMyVotes(ctx, &fatev1.GetMyVotesRequest{}); err != nil {
		t.Fatalf("GetMyVotes: %v", err)
	}
	parent.End()

	md := <-backend.got
	traceparent := strings.Join(md.Get("traceparent"), "")
	if !strings.Contains(traceparent, parent.SpanContext().TraceID().String()) {
		t.Errorf("traceparent = %q, want trace id %s", traceparent, parent.SpanContext().TraceID())
	}
	if got := md.Get(sharedSecretHeader); len(got) != 1 || got[0] != "s3cret" {
		t.Errorf("%s = %v, want [s3cret]", sharedSecretHeader, got)
	}

	var clientSpan sdktrace.ReadOnlySpan
	for _, s := range recorder.Ended() {
		if s.SpanKind() == trace.SpanKindClient {
			clientSpan = s
		}
	}
	if clientSpan == nil {
		t.Fatal("no client span recorded for the call")
	}
	if clientSpan.Parent().SpanID() != parent.SpanContext().SpanID() {
		t.Errorf("client span parent = %s, want the command span %s",
			clientSpan.Parent().SpanID(), parent.SpanContext().SpanID())
	}
	if !strings.Contains(clientSpan.Name(), "GetMyVotes") {
		t.Errorf("client span name = %q, want the RPC method", clientSpan.Name())
	}
}
