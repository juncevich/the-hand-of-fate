package client

// The request/response models are generated from the backend's REST contract into
// models.gen.go; regenerate after changing api/openapi.yaml.
//go:generate go tool oapi-codegen -config oapi-codegen.yaml ../../../api/openapi.yaml

// Session is the auth response plus the refresh token, which the backend only sends as a cookie.
type Session struct {
	AuthResponse
	RefreshToken string `json:"-"`
}
