package grpcclient

import (
	"time"

	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.uber.org/zap"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/keepalive"
)

func New(addr string, log *zap.Logger) (fatev1.FateServiceClient, *grpc.ClientConn, error) {
	log.Info("connecting to backend gRPC", zap.String("addr", addr))

	conn, err := grpc.NewClient(
		addr,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
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
