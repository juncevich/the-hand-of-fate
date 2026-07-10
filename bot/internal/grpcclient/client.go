package grpcclient

import (
	"context"
	"time"

	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.uber.org/zap"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/keepalive"
)

const sharedSecretHeader = "x-grpc-shared-secret"

// retryServiceConfig retries only on UNAVAILABLE (the backend never received/processed
// the request), so mutating RPCs like CreateVote/DrawVote can't be double-executed.
const retryServiceConfig = `{
	"methodConfig": [{
		"name": [{"service": "fate.v1.FateService"}],
		"retryPolicy": {
			"maxAttempts": 3,
			"initialBackoff": "0.2s",
			"maxBackoff": "2s",
			"backoffMultiplier": 2.0,
			"retryableStatusCodes": ["UNAVAILABLE"]
		}
	}]
}`

// sharedSecretCredentials attaches a static shared-secret header to every
// outgoing gRPC call so the backend can authenticate this client.
type sharedSecretCredentials struct {
	secret string
}

func (c sharedSecretCredentials) GetRequestMetadata(_ context.Context, _ ...string) (map[string]string, error) {
	return map[string]string{sharedSecretHeader: c.secret}, nil
}

func (c sharedSecretCredentials) RequireTransportSecurity() bool {
	return false
}

func New(addr string, sharedSecret string, log *zap.Logger) (fatev1.FateServiceClient, *grpc.ClientConn, error) {
	log.Info("connecting to backend gRPC", zap.String("addr", addr))

	conn, err := grpc.NewClient(
		addr,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithPerRPCCredentials(sharedSecretCredentials{secret: sharedSecret}),
		grpc.WithDefaultServiceConfig(retryServiceConfig),
		grpc.WithKeepaliveParams(keepalive.ClientParameters{
			Time:                30 * time.Second,
			Timeout:             10 * time.Second,
			PermitWithoutStream: true,
		}),
	)
	if err != nil {
		return nil, nil, err
	}

	return fatev1.NewFateServiceClient(conn), conn, nil
}

var _ credentials.PerRPCCredentials = sharedSecretCredentials{}
