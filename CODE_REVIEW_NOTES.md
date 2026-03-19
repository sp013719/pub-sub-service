# Code Review Notes (2026-03-19)

## Strengths
- Clear educational intent and architecture documentation.
- Good use of separate consumer groups to demonstrate pub-sub fan-out.
- Retry + DLT pipeline is easy to understand.

## Recommendations
1. Add request validation to controller DTOs (e.g., `@NotBlank`, `@Positive`, `@Email`) and `@Valid` in controllers.
2. Replace free-form `status` and `type` strings with enums to avoid typo-driven runtime behavior.
3. Introduce a global exception handler to return structured 4xx errors for invalid JSON/payloads.
4. Add Micrometer counters/timers for publish success/failure and consumer retries/DLT counts.
5. Add integration tests with `spring-kafka-test` and Testcontainers to verify partition routing and retry/DLT behavior.
6. Move environment-specific config from `application.yml` into profile-based files and environment variables.
7. Consider explicit producer reliability settings (`acks=all`, idempotence) when evolving beyond demo usage.
8. Remove unused imports and keep code warning-free.
9. Pin Docker image tags (avoid `latest`) for reproducibility.
10. Fix README run command (`./mvnw`) or add Maven Wrapper to repository.
