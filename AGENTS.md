# Repository Guidelines

## Project Structure & Module Organization

The repository is split by responsibility. `backend/` contains the Spring Boot modular monolith, with production code under `src/main` and JUnit tests under `src/test`. `frontend/` is the Angular application; feature pages and services live in `src/app`, and browser flows live in `e2e/specs`. `contracts/` holds versioned API contracts, `specs/` records feature requirements, and `docs/` contains architecture, integration, and local setup guides. `scripts/verify.sh` is the supported entry point for local verification.

## Build, Test, and Development Commands

- `docker compose --profile local up -d` starts local infrastructure; copy `.env.example` to `.env` only when needed and set local credentials.
- `./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=18080` runs the API.
- `npm ci --prefix frontend` installs the locked frontend dependencies; `npm --prefix frontend start` serves Angular locally.
- `scripts/verify.sh backend`, `scripts/verify.sh contracts`, and `scripts/verify.sh frontend` run the repository's backend, API-contract, and frontend gates. See `docs/ci.md` for scope and limitations.
- In the frontend, `npm --prefix frontend test` runs unit tests and `npm --prefix frontend run build` creates a production build.

## Coding Style & Naming Conventions

Use English for code, identifiers, and filenames; use pt-BR for store-facing copy and guides. Follow existing module boundaries and naming: Java types use `UpperCamelCase`, methods and fields use `lowerCamelCase`, and Angular files follow the feature and type suffix patterns already present. Keep formatting consistent with adjacent code and the configured Maven/Angular tooling. Update contracts, examples, tests, and documentation alongside behavior changes.

## Testing Guidelines

Backend tests use JUnit under `backend/src/test/java`; frontend unit tests use the Angular CLI/Vitest setup, while end-to-end specs are in `frontend/e2e/specs`. Name tests for the behavior or scenario they cover, and keep integration tests explicit about external dependencies. Run the relevant `scripts/verify.sh` target before proposing a change; report any unavailable gate rather than implying it passed.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commits with a scope where useful, such as `feat(payments): ...`, `test(payments): ...`, and `docs(checkout): ...`. Keep each commit focused. Pull requests should explain the behavior and motivation, link related issues or plan items, list verification commands and results, and include screenshots for visible UI changes. Call out configuration or migration changes and never commit secrets.

## Security & Configuration

Keep credentials in local environment configuration and out of Git. Sandbox integrations are opt-in; use the documented local and integration guides before enabling them. Product, producer, price, and origin data in this portfolio project are demonstrations.
