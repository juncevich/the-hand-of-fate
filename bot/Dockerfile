FROM golang:1.24-alpine AS build

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o bot ./cmd/bot

# ─────────────────────────────────────────────────────────────────────────────

FROM alpine:3.21

RUN addgroup -S bot && adduser -S bot -G bot
USER bot

COPY --from=build /app/bot /bot

EXPOSE 8081

ENTRYPOINT ["/bot"]
