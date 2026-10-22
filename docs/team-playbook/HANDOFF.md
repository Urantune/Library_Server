# Hà Minh Mẫn - Infrastructure handoff

Date: 2026-10-23 (Asia/Saigon).

## Task IDs and delivered work

- MSS-001: ADR-001 and Mermaid architecture diagram in root README; service, SQLite, REST, Redis and Kafka ownership boundaries.
- MSS-002: parent Maven reactor with exactly seven canonical modules; Java 21, Boot 4.1.1 / Cloud 2025.1.2, shared SQLite version, coding conventions and isolated Maven test configuration. Legacy renting-sevice excluded and documented deprecated.
- MSS-003: standalone Eureka server on 8761; all six client applications register under canonical service IDs.
- MSS-004: Gateway on 8080; five canonical /api service routes, preserved legacy aliases, explicit CORS and X-Trace-Id propagation.
- MSS-005: root Compose with persisted Redis 6379, Kafka KRaft 9092 (internal 19092), topic initialization, application environment variables and isolated database volumes. SQLite is retained per the explicit team contract; no PostgreSQL is added.
- MSS-045: shared Java 21 multi-stage Dockerfile, non-root runtime, Actuator health/probes/metrics/Prometheus on all seven applications, readiness checks and reusable stack verification script.

## Files changed

The documented Assigment/ directory corresponds to this repository root. Only common build/configuration, infrastructure modules, documentation and the verification script were changed. Domain Java and existing migrations were untouched.

- `.dockerignore`
- `.gitignore`
- `Dockerfile`
- `README.md`
- `api-gateway/pom.xml`
- `api-gateway/src/main/java/customer/apigateway/TraceIdFilter.java`
- `api-gateway/src/main/resources/application-local.properties`
- `api-gateway/src/main/resources/application.properties`
- `api-gateway/src/test/java/customer/apigateway/GatewayRoutingTests.java`
- `catalog-service/pom.xml`
- `catalog-service/src/main/resources/application-local.properties`
- `catalog-service/src/main/resources/application.properties`
- `circulation-service/pom.xml`
- `circulation-service/src/main/resources/application-local.properties`
- `circulation-service/src/main/resources/application.properties`
- `compose.yaml`
- `discovery-service/compose.yaml`
- `discovery-service/pom.xml`
- `discovery-service/src/main/resources/application-local.properties`
- `discovery-service/src/main/resources/application.properties`
- `docs/team-playbook/CHANGELOG.md`
- `docs/team-playbook/HANDOFF.md`
- `identity-service/pom.xml`
- `identity-service/src/main/resources/application-local.properties`
- `identity-service/src/main/resources/application.properties`
- `notification-service/pom.xml`
- `notification-service/src/main/resources/application-local.properties`
- `notification-service/src/main/resources/application.properties`
- `pom.xml`
- `reservation-service/pom.xml`
- `reservation-service/src/main/resources/application-local.properties`
- `reservation-service/src/main/resources/application.properties`
- `scripts/verify-stack.ps1`

An unrelated `.vscode/settings.json` appeared during work and was left untouched; it is not part of this handoff.

## Endpoints and events

Gateway prefixes: `/api/identity/**`, `/api/catalog/**`, `/api/circulation/**`, `/api/reservation/**`, `/api/notification/**`; each strips `/api/<service>` before forwarding. Legacy aliases remain as documented in README. Eureka API: `:8761/eureka/apps`. Each application exposes `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/metrics`, `/actuator/prometheus` and `/actuator/info`.

No new business event type or payload is introduced. Compose provisions the agreed topics `library.loan.approved.v1`, `library.loan.returned.v1`, `library.loan.overdue.v1`, and `library.reservation.ready.v1`. This scope neither produces nor consumes business events.

## Migration files

None added or edited. Flyway executes the existing V1 migrations; all five services keep their own SQLite database. Existing repository database files were not moved, deleted or written by Maven tests. Docker uses new independent named volumes.

## Test commands and actual results

Executed with JDK 21.0.12 and Maven 3.9.12 (using its installed absolute executable because mvn is not on this host's PATH):

- `mvn clean package`: PASS, all seven service modules plus parent succeeded; 11 tests, zero failures/errors/skips; seven executable JARs generated.
- `mvn -pl api-gateway test`: PASS, five tests. HTTP assertions cover all five routes, path stripping/query preservation, valid and invalid trace IDs, legacy alias, CORS preflight, readiness, metrics, Prometheus and unknown-route 404.
- `docker compose config --quiet`: PASS.
- `docker compose up --build -d --wait --wait-timeout 300`: PASS after Docker Engine recovered from an initial connection interruption. The Dockerfile shares a single Maven reactor build across images.
- `powershell -ExecutionPolicy Bypass -File scripts/verify-stack.ps1`: PASS. All seven applications have UP health/liveness/readiness and available metrics/Prometheus; all six clients register UP in Eureka; Redis PING returns PONG; Kafka lists all four agreed topics; all five Gateway routes resolve through Eureka and return downstream 404 for an intentionally absent controller endpoint rather than a load-balancer 503.
- `git diff --check`: PASS.

Eureka registration and registry cache convergence can take about a minute. Readiness may become UP before aggregate discovery health stabilizes. The verification script retries probe requests during this convergence. After completion, the tested Compose stack is left running for inspection; stop it using `docker compose down` to retain its data volumes.

## Known limitations

This is local infrastructure. Domain controllers and workflows are supplied by the other owners, so absent business endpoints return 404. Authentication/JWT/RBAC is not yet implemented (MSS-006/008); the gateway currently routes without authentication. Domain event outbox, consumer idempotency and Redis caching remain MSS-039/041. Kafka is single-node with one replica for local development. Existing domain identifier types and migration history were not redesigned.

## Exact next integration dependencies

1. Trương Thiếu Trọng: MSS-006/007 Identity APIs and JWT/refresh-token contract; use identity-service and the documented Gateway prefix.
2. Nguyễn Điền Trung Hiếu: MSS-008, depending on MSS-004 and MSS-006; implement JWT/RBAC for canonical routes AND compatibility aliases. Services also validate sensitive identity/role claims.
3. Dương Phúc Khang and domain owners: implement controllers matching the stripped downstream paths; MSS-041 consumes the configured Redis connection and agreed key/TTL contract.
4. Trần Đức Toàn with Circulation/Reservation owners: MSS-039, depending on MSS-005 and MSS-035; add Kafka producer/consumer dependencies, transactional outbox, agreed payloads and eventId deduplication using the configured bootstrap servers and existing four topics.
