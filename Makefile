.PHONY: proto proto-bot proto-backend install-proto-tools dev

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
