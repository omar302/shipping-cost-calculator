# Spring Boot SDD Starter

A starter for building Spring Boot services using a **Spec-Driven Development (SDD)** workflow with Claude Code. It bundles a working Spring Boot 4 / Gradle project together with a `.claude/` toolchain — skills, agents, and hooks — that turns business rules into specs, specs into acceptance tests, and tests into code, with the test suite enforced automatically on every change.

The project ships with a small example domain (a shipping cost calculator) so everything is runnable out of the box. See [Adapting This Starter](#adapting-this-starter) to make it your own.

## About This Course

This starter is a resource from the Udemy course **[Spec-Driven Development in Java with TDD and Claude Code](https://www.udemy.com/course/spring-boot-ai-tdd/?referralCode=3170E302C61D48703A94)**. The course walks through building a fully fledged Spring Boot application from the ground up, using all the features of Claude Code — skills, agents, hooks, and the Spec-Driven Development workflow that this project is built around.

## Requirements

- **JDK 21** (Spring Boot 4.1 / Spring Framework 7 require Java 17+; this project targets 21). Check with `java -version`.
- No Gradle install needed — the Gradle Wrapper (`./gradlew`) is included and downloads the right version on first use.
- Internet access on first build so Gradle can fetch dependencies.

## Build

```bash
./gradlew build          # compile, run tests, and assemble the jar
./gradlew assemble       # build the jar without running tests
./gradlew clean build    # clean rebuild
```

The runnable jar is written to `build/libs/`.

On Windows, use `gradlew.bat` in place of `./gradlew`.

## Test

```bash
./gradlew test                                   # run the full suite
./gradlew test --tests '*ShippingCostServiceTest' # a single test class
./gradlew test --tests '*.acceptance.*'          # only acceptance tests
./gradlew test --info                            # verbose output
```

Test report (HTML): `build/reports/tests/test/index.html`.

Tests come in two tiers (see `CLAUDE.md` → *Testing Conventions*):

- **Acceptance tests** — `src/test/java/.../acceptance/`, `@SpringBootTest` + `MockMvc`, exercising the full HTTP cycle.
- **Service / unit tests** — `src/test/java/.../service/`, plain JUnit 6, no Spring context.

Testing stack: **JUnit 6.1.0 (Jupiter)** and **AssertJ**, both pulled in via `spring-boot-starter-test`.

## Run

```bash
./gradlew bootRun                                # run from source (dev)
```

Or build and run the jar:

```bash
./gradlew bootJar
java -jar build/libs/*.jar
```

The service starts on **http://localhost:8080** by default.

Example request against the bundled shipping calculator:

```bash
curl -X POST http://localhost:8080/api/shipping/calculate \
  -H 'Content-Type: application/json' \
  -d '{ "weightKg": 3.2, "zone": "EUROPEAN", "orderTotal": 50.00 }'
```

To change the port or other settings, edit `src/main/resources/application.properties` (e.g. `server.port=9090`) or pass it at runtime: `./gradlew bootRun --args='--server.port=9090'`.

### API documentation (Swagger UI)

With the app running, interactive API docs are at **http://localhost:8080/swagger-ui.html**, generated automatically from the controllers by springdoc-openapi. The raw OpenAPI spec is at `/v3/api-docs`.

## The SDD Workflow

Claude Code drives features through three steps, each backed by a skill you invoke by name:

1. **`/discover`** — Turn a feature idea into a spec using Example Mapping (rule → example → counter-example → edge cases → open questions). Saves a draft spec to `docs/specs/<feature>.specs.md`.
2. **`/accept`** — Write a failing acceptance test for the next rule in a spec, hitting the real endpoint. One rule at a time.
3. **`/tdd`** — Run one TDD inner-loop cycle (RED → GREEN → REFACTOR) to drive that test to green with the minimum code.

Two review agents enforce quality on demand:

- **`architecture-guardian`** — checks that code respects the layer boundaries (controllers stay thin, services hold the logic, models stay data-only).
- **`spec-compliance`** — checks that every spec rule has a test, that precision/ordering rules hold, and that the API contract matches.

Three hooks (configured in `.claude/settings.json`) run automatically:

- **PreToolUse** — `protect-files.sh` blocks edits to protected files (prod config, secrets, CI).
- **PostToolUse** — runs `./gradlew test` after every edit, so regressions surface immediately.
- **Stop** — runs the full suite when a turn ends, gating completion on green tests.

## Project Layout

```
build.gradle                     Dependencies, Java/Spring Boot versions
settings.gradle                  Root project name
src/main/java/...                Application code (controller / service / model)
src/main/resources/              application.properties
src/test/java/.../acceptance/    Acceptance tests (@SpringBootTest + MockMvc)
src/test/java/.../service/       Service/unit tests (plain JUnit 6)
docs/specs/                      Business rules, one .specs.md file per feature
CLAUDE.md                        Project context Claude reads before any work
.claude/skills/                  discover, accept, tdd, and other workflow skills
.claude/agents/                  architecture-guardian, spec-compliance reviewers
.claude/hooks/                   protect-files.sh (file guard)
.claude/settings.json            Hook wiring
```

## Adapting This Starter

This repo is a template. The `.claude/` toolchain works for any Spring Boot / Gradle project — you replace the example domain with your own. Work through these in order; most files carry `ADAPT` comments pointing at exactly what to change.

1. **`CLAUDE.md`** — the most important file; Claude reads it before doing anything. Each section is a working default with an `<!-- ADAPT -->` comment explaining what to change. Replace the Project Overview, Architecture, API Design, and (if present) Processing Order / Monetary sections with your domain, and delete the *Worked Example* block at the bottom once your sections are accurate.

2. **`build.gradle`** — search for `ADAPT` comments. Set your `group`, the Java version, and the dependencies your project needs (e.g. `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`). Spring Security is commented out until you need it — note that adding it returns 401 on every endpoint until you configure a `SecurityFilterChain`.

3. **Rename the project** — change `rootProject.name` in `settings.gradle`, the `group` in `build.gradle`, `spring.application.name` in `application.properties`, and move the code from the `com.example.shipping` package to your own. Rename the main `@SpringBootApplication` class to match.

4. **`.claude/settings.json`** — if you don't use Gradle, change the test command (`./gradlew test`) in the PostToolUse and Stop hooks to your build tool (`mvn test`, etc.). The three-hook structure stays the same.

5. **`.claude/hooks/protect-files.sh`** — add or remove entries in `PROTECTED_PATTERNS` for your project's sensitive files (Dockerfiles, Terraform, lock files, etc.). Matching is substring-based; exit code 2 blocks an edit.

6. **`.claude/agents/architecture-guardian.md`** — replace the layer boundaries with your architecture (hexagonal, clean architecture, microservice, …). State the *rules*; the agent discovers your actual classes by reading the source tree.

7. **`.claude/agents/spec-compliance.md`** — update the Processing Order / Numeric Precision / Feature Interactions / API Contract checks for your domain, or remove sections that don't apply.

8. **`docs/specs/`** — starts empty. Create one `<feature>.specs.md` per feature as you `/discover` them; every rule should end up with at least one acceptance test.

Keep as-is: the `.claude/` directory structure, the hook exit-code convention (exit 2 to block), and the two-tier test layout — the skills and agents assume them.
