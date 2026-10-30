# Moodle CLI

A Kotlin command-line application for viewing Moodle courses, assignments, and
deadlines, with course material downloads planned as the next milestone.

Built as a practical study tool and a Kotlin learning project.

## Project status

The project is in the design phase. Requirements, architecture, and initial
dependency choices are documented. The Kotlin application and Gradle build have
not been set up yet; the commands below describe the planned interface.

## Planned features

- Configure a Moodle instance and authenticate with a supported method.
- Check connection health and available API capabilities.
- List enrolled courses and assignments.
- Filter assignments by course and show upcoming deadlines.
- Display submission status when the API makes it available.
- Download course materials in a later milestone.

The first release supports one user and one Moodle instance and reads data without
changing Moodle content. Assignment submissions and administration are outside its scope.

## Planned usage

```sh
moodle setup https://moodle.example.edu
moodle login
moodle doctor
moodle courses
moodle tasks --course 42
moodle upcoming --days 7
moodle logout
```

Available features depend on the API functions and permissions provided by the
target Moodle instance. A working browser login does not guarantee API access.

## Technology and architecture

- Kotlin/JVM and Gradle Kotlin DSL, with a Gradle Wrapper to be added during setup.
- Retrofit with kotlinx.serialization JSON and its Retrofit converter.
- Kotlin Test with JUnit.
- Command parsing, files, and date handling through Kotlin and JDK APIs.

Application services depend on a `MoodleRepository` interface. A Retrofit adapter
implements that interface, keeping network details separate from application logic
and terminal output. Dependencies are passed through constructors and connected in
`Main.kt`. Tests can replace Moodle with a fake repository.

## Development

The next step is to create the Kotlin application, pin compatible Kotlin, JDK,
Gradle, and dependency versions, and commit the Gradle Wrapper. Build and run
instructions will be added once those commands are available and verified.

Never commit Moodle tokens or passwords. Keep local configuration and credentials
outside tracked files. Secure persistent credential storage is still to be designed;
ignoring a file in Git does not make its contents securely stored.

## Documentation

- [Specification](Docs/SPEC.md): scope, commands, acceptance criteria, and milestones.
- [Architecture](Docs/ARCHITECTURE.md): class diagram, dependency boundaries, and Kotlin conventions.
- [Dependency decisions](Docs/DEPENDENCIES.md): initial libraries and their responsibilities.

This is an independent project and is not affiliated with Moodle or Moodle HQ.
