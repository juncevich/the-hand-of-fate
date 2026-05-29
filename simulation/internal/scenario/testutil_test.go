package scenario

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/juncevich/fate/simulation/internal/client"
	"go.uber.org/zap"
)

func testLogger(t *testing.T) *zap.Logger {
	t.Helper()
	log, _ := zap.NewDevelopment()
	t.Cleanup(func() { _ = log.Sync() })
	return log
}

func testClient(t *testing.T, srv *httptest.Server) *client.Client {
	t.Helper()
	c, err := client.New(srv.URL, testLogger(t))
	if err != nil {
		t.Fatalf("client.New: %v", err)
	}
	c.SetAccessToken("test-token")
	return c
}

func writeJSON(w http.ResponseWriter, v any) {
	b, _ := json.Marshal(v)
	w.Header().Set("Content-Type", "application/json")
	_, _ = w.Write(b)
}
