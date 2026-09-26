package grpcclient

import (
	"context"
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

func TestSharedSecretCredentials(t *testing.T) {
	creds := sharedSecretCredentials{secret: "s3cret"}

	md, err := creds.GetRequestMetadata(context.Background())
	if err != nil {
		t.Fatalf("GetRequestMetadata() error = %v", err)
	}
	if got := md[sharedSecretHeader]; got != "s3cret" {
		t.Fatalf("metadata[%q] = %q, want %q", sharedSecretHeader, got, "s3cret")
	}
	if len(md) != 1 {
		t.Fatalf("metadata = %v, want only the shared secret", md)
	}
	if creds.RequireTransportSecurity() {
		t.Fatal("RequireTransportSecurity() = true, want false (plaintext channel)")
	}
}

func TestNewRejectsInvalidTarget(t *testing.T) {
	if _, _, err := New("http://%zz", "secret", zap.NewNop()); err == nil {
		t.Fatal("New() error = nil, want error for malformed target")
	}
}
