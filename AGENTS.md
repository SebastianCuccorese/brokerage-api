# Working agreement for AI agents

This project is built by a human developer together with AI coding agents. The
human owns every design decision; agents propose, implement and verify. These
rules apply to any agent working in this repository.

## How we work

- Step by step: one feature per branch, one pull request per branch, merged
  into `main` with a merge commit. Never commit straight to `main`.
- An agent proposes a design before writing code when there is more than one
  reasonable option, and waits for the human's decision.
- Every design decision and assumption is recorded in the README with its
  reason. A pull request description explains what changed and why.
- Commit messages follow Conventional Commits (`feat:`, `fix:`, `docs:`, ...).

## Domain rules

- Money is always a `Money` value (currency + amount), never a bare number,
  and amounts are `BigDecimal`, never `double` or `float`. The only currency
  today is ARS, but no code assumes it.
- Cash and positions are derived from the `orders` table (the ledger). Do not
  store balances anywhere else without a documented decision.

## Database

- The schema may be changed or extended when it brings a clear benefit
  (performance, integrity). Every change is a versioned SQL script in `db/`
  and is justified in the README.
- Hibernate validates the schema and never generates or alters it.

## Secrets

- The database connection lives in `.env`, which is git-ignored and never
  committed. `.env.example` documents the variables without values.
- Never print, log or paste secret values, including in commit messages,
  pull requests or test output.

## Tests

- Every behaviour change comes with a test.
- Tests run against a throwaway Postgres (Testcontainers), never against a
  shared database.
- No `Thread.sleep` or waiting on real time in tests; synchronise on events
  (latches, futures) and inject a `Clock`.
- Run `./mvnw verify` before every commit; it must pass.

## Code style

- Comments only at function or type level, and only when they add something
  the code cannot say (the why, a contract, a gotcha). No line-by-line
  comments and no comments that restate the code.
- Prefer the standard library and the framework over hand-rolled code.
- Use Lombok for boilerplate: `@Getter` and a protected `@NoArgsConstructor` on
  entities, `@RequiredArgsConstructor` for constructor injection. Records stay
  records (DTOs, value objects). Never `@Data` on an entity: its equals/hashCode
  over every field breaks with JPA proxies and lazy associations.
