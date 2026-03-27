FROM golang:1.24-alpine AS build

WORKDIR /repo

# Install buf
RUN apk add --no-cache curl && \
    curl -sSL https://github.com/bufbuild/buf/releases/download/v1.50.0/buf-Linux-x86_64 \
      -o /usr/local/bin/buf && chmod +x /usr/local/bin/buf

# Install Go proto plugins
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@latest && \
    go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

# Generate proto stubs
COPY proto/ proto/
COPY buf.yaml buf.gen.yaml ./
RUN buf generate

# Build Go binary
COPY bot/go.mod bot/go.sum bot/
WORKDIR /repo/bot
RUN go mod download
COPY bot/ .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o fate-bot ./cmd/bot

FROM alpine:3.21
RUN addgroup -S bot && adduser -S bot -G bot
USER bot
COPY --from=build /repo/bot/fate-bot /bot
EXPOSE 8081
ENTRYPOINT ["/bot"]
