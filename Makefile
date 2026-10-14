.PHONY: proto proto-bot proto-backend install-proto-tools openapi openapi-spec openapi-clients dev dev-local infra infra-down \
        k8s-up k8s-down k8s-delete k8s-status k8s-dev k8s-dev-down k8s-check

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

# Full stack in local minikube (profile hand-of-fate) — see k8s-start.sh / k8s-stop.sh
k8s-up:
	./k8s-start.sh

k8s-down:
	./k8s-stop.sh

k8s-delete:
	./k8s-stop.sh --delete

k8s-status:
	kubectl --context hand-of-fate -n hand-of-fate get pods,svc,pvc

# Hot-reload loop in the same cluster (Tiltfile); Ctrl+C leaves the workloads running
k8s-dev:
	./k8s-start.sh --cluster-only
	tilt up --context hand-of-fate

# Remove what Tilt deployed (the rustfs PVC is kept, Postgres' StatefulSet volume too)
k8s-dev-down:
	tilt down --context hand-of-fate

# Static checks of manifests, mounted configs and scripts (what .github/workflows/k8s.yml runs)
k8s-check:
	./infra/k8s/check.sh
