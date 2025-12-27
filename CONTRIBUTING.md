# Contributing Guide

Thank you for considering contributing to async2databricks! This document provides guidelines for contributing to the project.

## Development Setup

### Prerequisites

- Java 11 or later
- SBT 1.9.7
- Docker and Docker Compose
- Git

### Getting Started

1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/YOUR_USERNAME/async2databricks.git
   cd async2databricks
   ```
3. Set up the development environment:
   ```bash
   docker compose up -d
   sbt compile
   ```

## Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
```

### 2. Make Your Changes

Follow the project structure:

```
src/
├── main/
│   ├── scala/com/async2databricks/
│   │   ├── config/      # Configuration models
│   │   ├── database/    # Database access layer
│   │   ├── etl/         # ETL pipeline logic
│   │   ├── model/       # Domain models
│   │   └── s3/          # S3 writer
│   └── resources/
│       └── application.conf  # Configuration
└── test/
    └── scala/com/async2databricks/  # Unit tests
```

### 3. Write Tests

All new functionality should include tests:

```scala
package com.async2databricks.yourpackage

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class YourSpec extends AnyFlatSpec with Matchers {
  "YourClass" should "do something" in {
    // test implementation
  }
}
```

Run tests:

```bash
sbt test
```

### 4. Follow Code Style

The project uses standard Scala conventions:

- Use 2 spaces for indentation
- Line length: 120 characters
- Use meaningful variable names
- Add scaladoc comments for public APIs

Format your code:

```bash
sbt scalafmt
```

### 5. Commit Your Changes

Write clear commit messages:

```bash
git add .
git commit -m "feat: add support for incremental loads

- Add watermark tracking
- Implement checkpoint mechanism
- Update tests"
```

Follow conventional commits:
- `feat:` for new features
- `fix:` for bug fixes
- `docs:` for documentation
- `test:` for test changes
- `refactor:` for refactoring

### 6. Push and Create PR

```bash
git push origin feature/your-feature-name
```

Then create a Pull Request on GitHub.

## Code Guidelines

### Functional Programming

This project uses functional programming with Cats Effect:

```scala
// Good: Pure functional code
def loadData[F[_]: Async](config: Config): F[List[Data]] = {
  for {
    conn <- createConnection(config)
    data <- fetchData(conn)
  } yield data
}

// Avoid: Imperative code with side effects
def loadData(config: Config): List[Data] = {
  val conn = createConnection(config)  // side effect
  fetchData(conn)
}
```

### Error Handling

Use `Either`, `Option`, or effect types for error handling:

```scala
// Good
def parse(input: String): Either[ParseError, Result] = ???

// Avoid
def parse(input: String): Result = {
  if (invalid) throw new Exception("Invalid")
  else result
}
```

### Type Safety

Leverage Scala's type system:

```scala
// Good: Type-safe configuration
case class DatabaseConfig(
  url: String,
  user: String,
  password: String,
  poolSize: Int
)

// Avoid: Stringly-typed configuration
def getConfig(key: String): String = ???
```

### Resource Management

Always use `Resource` for managing resources:

```scala
// Good
def createConnection[F[_]: Async]: Resource[F, Connection] = {
  Resource.make(acquire)(release)
}

// Avoid
def createConnection[F[_]: Async]: F[Connection] = {
  acquire // no cleanup
}
```

## Testing Guidelines

### Unit Tests

Test individual components in isolation:

```scala
"DataRepository" should "stream data correctly" in {
  val repo = DataRepository(transactor)
  val result = repo.streamData("SELECT * FROM test", 100)
    .compile
    .toList
  
  result should have size 10
}
```

### Integration Tests

Test interactions between components. Use Docker for integration tests.

### Test Coverage

Aim for:
- Core business logic: 80%+ coverage
- Configuration: 70%+ coverage
- Integration points: Test happy path and error cases

## Documentation

### Code Documentation

Add scaladoc for public APIs:

```scala
/**
 * Repository for accessing data from the database.
 *
 * @tparam F the effect type
 */
trait DataRepository[F[_]] {
  /**
   * Stream data from the database.
   *
   * @param query the SQL query to execute
   * @param batchSize the number of records to fetch at once
   * @return a stream of SampleData
   */
  def streamData(query: String, batchSize: Int): Stream[F, SampleData]
}
```

### README Updates

Update README.md if you:
- Add new features
- Change configuration
- Modify deployment process
- Add dependencies

### Architecture Decisions

For significant changes, document the decision:

1. Create `docs/adr/` directory if it doesn't exist
2. Add `NNN-decision-title.md` with:
   - Context
   - Decision
   - Consequences

## Pull Request Process

1. **Update Documentation**: Ensure README and other docs are current
2. **Add Tests**: All new code must have tests
3. **Pass CI**: All tests and checks must pass
4. **Update Changelog**: Add entry to CHANGELOG.md
5. **Request Review**: Tag maintainers for review

### PR Description Template

```markdown
## Description
Brief description of changes

## Motivation
Why is this change needed?

## Changes
- List of changes

## Testing
How was this tested?

## Checklist
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] Changelog updated
- [ ] CI passing
```

## Release Process

Maintainers will:

1. Update version in `build.sbt`
2. Update CHANGELOG.md
3. Create git tag
4. Publish release

## Getting Help

- **Issues**: Open an issue on GitHub
- **Discussions**: Use GitHub Discussions
- **Questions**: Tag your issue with `question`

## License

By contributing, you agree that your contributions will be licensed under the project's license (see LICENSE file).

## Code of Conduct

- Be respectful and inclusive
- Welcome newcomers
- Focus on constructive feedback
- Follow the Scala Code of Conduct

## Thank You!

Your contributions make this project better for everyone. Thank you for taking the time to contribute!
