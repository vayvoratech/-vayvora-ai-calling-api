# Vayvora AI Calling Platform — API

Spring Boot 3.1 / Java 17 microservices behind a Spring Cloud Gateway.

| Module | Port | Routes (via gateway) |
|---|---|---|
| `api-gateway` | 8000 | entry point |
| `auth-service` | 8001 | `/api/v1/auth/**` (public) |
| `agent-service` | 8003 | `/api/v1/agents/**` |
| `call-service` | 8004 | `/api/v1/calls/**`, `/api/v1/engine/**` |
| `campaign-service` | 8005 | `/api/v1/campaigns/**` |
| `analytics-service` | 8006 | `/api/v1/analytics/**` |
| `knowledge-service` | 8007 | `/api/v1/knowledge/**` |
| `shared` | — | shared JPA entities (plain library jar) |

The gateway forwards paths unchanged, and every controller is mapped under `/api/v1/...`.

## Build and run

```bash
mvn -B verify                                  # compile, test, package all modules
java -jar call-service/target/call-service-1.0.0.jar
```

Docker images build from the repository root:

```bash
docker build -f call-service/Dockerfile -t vayvora/call-service .
```

Services read `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST` and
`KAFKA_BOOTSTRAP_SERVERS`. The defaults point at the `postgres`, `redis` and `kafka` hosts.

## Authentication

The gateway's `AuthorizationFilter` checks an HS256 bearer token signed with
`JWT_SECRET`. auth-service must use the same secret, and it must be at least 32 bytes. The filter
forwards the token's identity as headers. It removes any copies of these headers sent by the client:

| Claim | Forwarded header |
|---|---|
| `sub` | `X-User-Id` |
| `organizationId` | `X-Organization-Id` |
| `role` | `X-User-Role` |

Browsers can't set headers on a WebSocket, so `/api/v1/media-stream` also accepts the token as
`?access_token=…`. The gateway removes that parameter before forwarding.
To turn the check off for local development, set `GATEWAY_AUTH_ENABLED=false`. Allowed CORS
origins come from `CORS_ALLOWED_ORIGINS` (default `*`).

## AI Voice Agent & Conversational Engine

`call-service` integrates the Unified AI Voice Agent & Conversational Engine
(status, multi-turn RAG sessions, real-time voice and MCP tools) through
`VoiceEngineClient`, and exposes it to clients via the gateway.

| Gateway route | Engine endpoint | Purpose |
|---|---|---|
| `GET  /api/v1/engine/status` | `GET /api/status` | Subsystem modes and initialization notes |
| `POST /api/v1/engine/sessions` | `POST /api/sessions` | Open a session (`direction`, `domain`, caller context) |
| `POST /api/v1/engine/sessions/{id}/turns` | `POST /api/sessions/{id}/turns` | Send a caller turn, get the grounded reply |
| `POST /api/v1/engine/sessions/{id}/reset` | `POST /api/sessions/{id}/reset` | Clear conversational memory |
| `GET  /api/v1/engine/tools` | `POST /mcp` `tools/list` | Registered MCP tools |
| `POST /api/v1/engine/tools/{name}` | `POST /mcp` `tools/call` | Run a tool with `{"arguments": {...}}` |
| `GET  /api/v1/engine/media-stream-url` | — | Build the voice WebSocket URL for a call |
| `WS   /api/v1/media-stream` | `WSS /media-stream` | Real-time PCM16 voice pipeline (proxied) |

`POST /api/v1/calls` also opens an outbound engine session for the call and
returns `engineSessionId` and `mediaStreamUrl`. Optional call `variables`:
`domain`, `contact_name`, `agent_name`, `contact_email`, `contact_company`,
`campaign_objective`, `known_purpose`.

Errors are returned as `{"error": "..."}`. An unknown or expired session
returns 404, an engine failure returns 502, and an unreachable engine
returns 504.

### Configuration

| Variable | Default |
|---|---|
| `VAYVORA_ENGINE_URL` | `https://vayvora-ai-calling-ai-pzf9.onrender.com` |
| `VAYVORA_ENGINE_WS_URL` | `wss://vayvora-ai-calling-ai-pzf9.onrender.com` |
| `VAYVORA_ENGINE_DOMAIN` | `vayvora` (`edusaas` or `vayvora`) |

The hosted engine sleeps when idle. The read timeout is 90s to cover cold starts.

Engine docs: Swagger UI at `/docs`, and a browser mic test at `/voice-test`.
