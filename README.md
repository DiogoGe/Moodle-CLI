# Moodle CLI

A Kotlin command-line project for accessing Moodle courses, assignments, deadlines,
and course materials. Built as a practical study tool and a Kotlin learning project.

## Scope

The target is a local, read-only tool for one user and one Moodle instance:

- View enrolled courses and assignments.
- Filter assignments by course and deadline.
- Display submission status when available.
- Download course materials.

These are project goals, not a list of completed features. See `--help` for the
command interface. API access depends on the target Moodle instance and permissions.

## Build and run

Use a JDK matching the toolchain declared in [build.gradle.kts](build.gradle.kts).
Open this folder as a Gradle project in IntelliJ IDEA, or use the Gradle Wrapper:

```sh
./gradlew build
./gradlew run --args="--help"
./gradlew test
```

Formatting follows `.editorconfig` and is checked as part of the build:

```sh
./gradlew ktlintFormat  # Apply Kotlin formatting
./gradlew ktlintCheck   # Check Kotlin formatting without modifying files
```

On Windows, use `gradlew.bat`. To generate a local launcher:

```sh
./gradlew installDist
./build/install/moodle/bin/moodle --help
```

## Structure

```text
src/main/kotlin/moodle/
├── Main.kt          # Dependency wiring and entry point
├── cli/             # Commands and terminal output
├── application/     # Workflows and filtering
├── domain/          # Models and repository contracts
├── infrastructure/  # Retrofit API and repository adapter
└── config/          # Local connection settings

src/test/kotlin/moodle/
```

The service depends on a repository interface. The Retrofit adapter implements it.
Dependencies are passed through constructors; tests can use a fake repository and
fixed clock. Library versions are defined in the build files.

Never commit tokens or passwords. A file being ignored by Git does not provide
secure credential storage.

## Documentation

- [Specification](Docs/SPEC.md): requirements and scope.
- [Architecture](Docs/ARCHITECTURE.md): dependency boundaries and Kotlin conventions.
- [Dependencies](Docs/DEPENDENCIES.md): library choices and rationale.

This is an independent project and is not affiliated with Moodle or Moodle HQ.
