package grpcclient

import (
	"context"
	"net"
	"sync/atomic"
	"testing"
	"time"

	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
)

// flakyOnceServer fails the first GetMyVotes call with UNAVAILABLE, then succeeds -
// simulating a backend restart mid-request, which the retry policy should hide
// transparently from the bot.
type flakyOnceServer struct {
	fatev1.UnimplementedFateServiceServer
	attempts int32
}

func (s *flakyOnceServer) GetMyVotes(context.Context, *fatev1.GetMyVotesRequest) (*fatev1.GetMyVotesResponse, error) {
	if atomic.AddInt32(&s.attempts, 1) == 1 {
		return nil, status.Error(codes.Unavailable, "backend restarting")
	}
	return &fatev1.GetMyVotesResponse{}, nil
}

func dialBufconn(t *testing.T, srv *flakyOnceServer) (fatev1.FateServiceClient, func()) {
	t.Helper()
	lis := bufconn.Listen(1024 * 1024)
	grpcServer := grpc.NewServer()
	fatev1.RegisterFateServiceServer(grpcServer, srv)
	go func() { _ = grpcServer.Serve(lis) }()

	conn, err := grpc.NewClient(
		"passthrough:///bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) {
			return lis.DialContext(ctx)
		}),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithDefaultServiceConfig(retryServiceConfig),
	)
	if err != nil {
		t.Fatalf("grpc.NewClient() error = %v", err)
	}

	cleanup := func() {
		conn.Close()
		grpcServer.Stop()
	}
	return fatev1.NewFateServiceClient(conn), cleanup
}

func TestRetryPolicyTransparentlyRetriesOnUnavailable(t *testing.T) {
	srv := &flakyOnceServer{}
	client, cleanup := dialBufconn(t, srv)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	_, err := client.GetMyVotes(ctx, &fatev1.GetMyVotesRequest{TelegramId: 1})
	if err != nil {
		t.Fatalf("GetMyVotes() error = %v, want the retry policy to hide the transient UNAVAILABLE", err)
	}
	if got := atomic.LoadInt32(&srv.attempts); got != 2 {
		t.Fatalf("server saw %d attempts, want 2 (one failure + one retry)", got)
	}
}
