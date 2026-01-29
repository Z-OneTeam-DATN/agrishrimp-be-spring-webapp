# Repository Guidelines

## Project Structure & Module Organization
- Backend sources live under `src/main/java/com/chatapp/realtime`, grouped by layer (`controller`, `service`, `repository`, `entity`, etc.).
- Shared resources (message bundles, Spring configuration) are in `src/main/resources`.
- Integration and unit tests belong in `src/test/java`; reuse package mirrors of the main source tree.
- WebSocket entrypoints are rooted in `config` and `controller`, with DTOs collocated under `dto/chat`.

## Build, Test, and Development Commands
- `./mvnw clean install` — compile, run checks, and produce the runnable JAR.
- `./mvnw test` — execute the `spring-boot-starter-test` and security test suites.
- `./mvnw spring-boot:run` — start the application locally using the default profile.
- Add `-Dspring.profiles.active=dev` when you need non-default configuration.

## Coding Style & Naming Conventions
- Target Java `${java.version}` from `pom.xml` (currently 24); enable preview features if the build requires them.
- Follow 2-space indentation used across the project; keep lines within 120 characters.
- Favor descriptive CamelCase for classes (`ChatGroupService`), lowerCamelCase for methods/fields, and CONSTANT_CASE for static finals.
- Rely on Lombok for boilerplate; avoid manual getters/setters where annotations already exist.
- Run code formatters configured for standard IntelliJ/Spring conventions before submitting.

## Testing Guidelines
- Use JUnit 5 (bundled with Spring Boot 3) and `spring-security-test` for authenticated scenarios.
- Name tests after the method behavior, e.g., `ChatGroupServiceTests` with methods `renameGroup_updates_name`.
- Prefer the in-memory H2 database for repository/service tests; load test data via SQL or builders.
- Ensure WebSocket flows include membership and token assertions to guard regressions.

## Commit & Pull Request Guidelines
- Follow Conventional Commits as seen in history (`feat:`, `fix:`, `refactor:`). Scope optional but encouraged (`feat(chat): ...`).
- Each commit should focus on a single concern and leave the tree buildable with `./mvnw test`.
- Pull requests must describe the change, list verification steps (commands run), and reference Jira/GitHub issues when applicable.
- Include screenshots or logs for UI or WebSocket behavior changes to aid reviewers.

## Security & Configuration Tips
- JWT configuration lives in `src/main/resources/application*.yml`; never commit secrets. Use environment variables or Spring Cloud Config for overrides.
- Redis and PostgreSQL credentials are required for full deployments; provide fallbacks or docker-compose instructions in PRs that introduce new dependencies.
