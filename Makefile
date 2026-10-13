.PHONY: proto proto-bot proto-backend install-proto-tools openapi openapi-spec openapi-clients dev dev-local infra infra-down

proto: proto-bot proto-backend

install-proto-tools:
	go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
	go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

proto-bot: install-proto-tools
	buf generate

proto-backend:
	cd backend && ./gradlew generateProto --no-daemon -q

# REST contract: regenerate api/openapi.yaml from the controllers (needs Docker for
# Testcontainers), then the frontend types and simulation models generated from it.
openapi: openapi-spec openapi-clients

openapi-spec:
	cd backend && ./gradlew openApiUpdate --no-daemon -q

openapi-clients:
	cd frontend && npm run gen:api
	cd simulation && go generate ./...

dev:
	docker compose up -d

# Run infrastructure in Docker + app processes natively (hot-reload)
dev-local:
	./dev-start.sh

infra:
	docker compose -f docker-compose.infra.yml up -d

infra-down:
	docker compose -f docker-compose.infra.yml down
