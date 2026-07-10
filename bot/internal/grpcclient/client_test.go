package grpcclient

import (
	"testing"

	"go.uber.org/zap"
)

func TestNewReturnsClientAndConn(t *testing.T) {
	log := zap.NewNop()

	client, conn, err := New("localhost:9090", "test-secret", log)
	if err != nil {
		t.Fatalf("New() returned unexpected error: %v", err)
	}
	if client == nil {
		t.Fatal("New() returned nil client")
	}
	if conn == nil {
		t.Fatal("New() returned nil conn")
	}
	defer conn.Close()

	if got := conn.Target(); got != "localhost:9090" {
		t.Errorf("conn.Target() = %q, want %q", got, "localhost:9090")
	}
}
