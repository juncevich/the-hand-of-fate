package handler

import (
	"strings"
	"testing"

	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.uber.org/zap/zaptest"
	"google.golang.org/genproto/googleapis/rpc/errdetails"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func TestEveryErrorCodeHasAText(t *testing.T) {
	for value, name := range fatev1.ErrorCode_name {
		code := fatev1.ErrorCode(value)
		if code == fatev1.ErrorCode_ERROR_CODE_UNSPECIFIED {
			continue
		}
		if strings.TrimSpace(errorTexts[code]) == "" {
			t.Errorf("no Russian text for %s", name)
		}
	}
}

func statusWithReason(c codes.Code, reason string) error {
	st, _ := status.New(c, "english fallback").WithDetails(&errdetails.ErrorInfo{Domain: errorDomain, Reason: reason})
	return st.Err()
}

func TestGrpcErrMsgTranslatesTheReason(t *testing.T) {
	h := New(&fakeTelegram{}, &fakeFateClient{}, zaptest.NewLogger(t))

	if got := h.grpcErrMsg(statusWithReason(codes.FailedPrecondition, "NO_OPTIONS")); got != "❌ Добавьте хотя бы один вариант." {
		t.Errorf("grpcErrMsg = %q", got)
	}
	if got := h.grpcErrMsg(statusWithReason(codes.NotFound, "TELEGRAM_NOT_LINKED")); !strings.Contains(got, "не привязан") {
		t.Errorf("grpcErrMsg = %q", got)
	}
	// An unknown reason (newer backend) falls back to the status.
	if got := h.grpcErrMsg(statusWithReason(codes.PermissionDenied, "SOMETHING_NEW")); !strings.Contains(got, "нет прав") {
		t.Errorf("grpcErrMsg = %q", got)
	}
}

func TestFailureTextPrefersTheTranslation(t *testing.T) {
	if got := failureText(fatev1.ErrorCode_ERROR_CODE_EMAIL_LIMIT_REACHED, "Email limit reached"); !strings.Contains(got, "лимит писем") {
		t.Errorf("failureText = %q", got)
	}
	if got := failureText(fatev1.ErrorCode_ERROR_CODE_UNSPECIFIED, "Old backend message"); got != "❌ Old backend message" {
		t.Errorf("failureText = %q", got)
	}
}
