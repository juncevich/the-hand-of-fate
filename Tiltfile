# -*- mode: Python -*-
# Hot-reload dev loop on the local minikube cluster. Start with `make k8s-dev`
# (creates/resumes the cluster via k8s-start.sh --cluster-only, then `tilt up`).
#
#   frontend  Vite dev server in the pod; frontend/ is synced live, HMR goes through the gateway
#   backend   Gradle bootJar on the host → thin image (backend/Dockerfile.dev) → pod restart
#   bot       go build on the host → thin image (bot/Dockerfile.dev); only when BOT_TOKEN is set
#
# Everything else (Postgres, RustFS, Mailpit, gateway, monitoring) comes from infra/k8s/overlays/tilt,
# i.e. the same manifests k8s-start.sh deploys.

PROFILE = 'hand-of-fate'

if k8s_context() != PROFILE:
    fail("Tilt must target the '%s' context — run `make k8s-dev` (it passes --context)." % PROFILE)
allow_k8s_contexts(PROFILE)

# ── Manifests ────────────────────────────────────────────────────────────────
# Host-built artifacts land in .tilt-build/ (gitignored); separate contexts keep the image
# builds small and out of reach of backend/.dockerignore (which excludes build/).
local('mkdir -p .tilt-build/backend .tilt-build/bot', quiet=True)

local('infra/k8s/overlays/local/write-secrets.sh', quiet=True)
# The gateway config minus CSP (see overlays/tilt/kustomization.yaml).
local("sed '/Content-Security-Policy/d' infra/nginx/nginx.conf > .tilt-build/nginx.conf", quiet=True)
watch_file('.env')
watch_file('infra/k8s')
watch_file('infra/nginx/nginx.conf')

secrets = str(read_file('infra/k8s/overlays/local/.secrets.env'))
has_bot = 'BOT_TOKEN=\n' not in secrets + '\n'

manifests = local(
    'kubectl kustomize --load-restrictor LoadRestrictionsNone infra/k8s/overlays/tilt',
    quiet=True,
)
if not has_bot:
    _, manifests = filter_yaml(manifests, kind='Deployment', name='^bot$')
    print('BOT_TOKEN not set in .env — the bot is not deployed.')
k8s_yaml(manifests)

# ── Backend ──────────────────────────────────────────────────────────────────
local_resource(
    'backend-jar',
    cmd='cd backend && ./gradlew bootJar -x test --console=plain -q'
        + ' && cp "$(ls build/libs/*.jar | grep -v -- -plain.jar)" ../.tilt-build/backend/app.jar',
    deps=['backend/src/main', 'backend/build.gradle.kts', 'backend/settings.gradle.kts', 'proto'],
    labels=['backend'],
)
docker_build('fate-backend', '.tilt-build/backend', dockerfile='backend/Dockerfile.dev')
k8s_resource(
    'backend',
    resource_deps=['backend-jar'],
    port_forwards=['18080:8080'],
    links=[link('http://localhost:18080/swagger-ui.html', 'Swagger')],
    labels=['backend'],
)

# ── Frontend ─────────────────────────────────────────────────────────────────
docker_build(
    'fate-frontend',
    'frontend',
    dockerfile='frontend/Dockerfile.dev',
    live_update=[
        # Dependencies or Vite config changed: rebuild the image (npm ci).
        fall_back_on([
            'frontend/package.json',
            'frontend/package-lock.json',
            'frontend/vite.config.ts',
            'frontend/Dockerfile.dev',
        ]),
        sync('frontend', '/app'),
    ],
)
k8s_resource('frontend', labels=['frontend'])
k8s_resource(
    'gateway',
    links=[link('http://localhost:30080', 'App (admin@admin.com / admin)')],
    labels=['frontend'],
)

# ── Bot ──────────────────────────────────────────────────────────────────────
if has_bot:
    goarch = str(local('go env GOARCH', quiet=True)).strip()   # = minikube node arch
    local_resource(
        'bot-binary',
        cmd='cd bot && CGO_ENABLED=0 GOOS=linux GOARCH=%s go build -o ../.tilt-build/bot/fate-bot ./cmd/bot'
            % goarch,
        deps=['bot/cmd', 'bot/internal', 'bot/gen', 'bot/go.mod', 'bot/go.sum'],
        labels=['bot'],
    )
    docker_build('fate-bot', '.tilt-build/bot', dockerfile='bot/Dockerfile.dev')
    k8s_resource('bot', resource_deps=['bot-binary'], labels=['bot'])

# ── Infrastructure ───────────────────────────────────────────────────────────
k8s_resource('postgres', labels=['infra'])
k8s_resource('mailpit', links=[link('http://localhost:30025', 'Mailpit')], labels=['infra'])
k8s_resource(
    'rustfs',
    links=[link('http://localhost:30901/rustfs/console/', 'RustFS console')],
    labels=['infra'],
)

# ── Monitoring (infra/k8s/components/observability) ──────────────────────────
k8s_resource('otel-collector', labels=['monitoring'])
k8s_resource('loki', labels=['monitoring'])
k8s_resource('mimir', labels=['monitoring'])
k8s_resource('tempo', labels=['monitoring'])
k8s_resource(
    'grafana',
    links=[link('http://localhost:30300', 'Grafana (admin / admin)')],
    labels=['monitoring'],
)
