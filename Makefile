.PHONY: proto proto-bot proto-backend install-proto-tools dev dev-local infra infra-down

proto: proto-bot proto-backend

install-proto-tools:
	go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
	go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

proto-bot: install-proto-tools
	buf generate

proto-backend:
	cd backend && ./gradlew generateProto --no-daemon -q

dev:
	docker compose up -d

# Run infrastructure in Docker + app processes natively (hot-reload)
dev-local:
	./dev.sh

infra:
	docker compose -f docker-compose.infra.yml up -d

infra-down:
	docker compose -f docker-compose.infra.yml down
