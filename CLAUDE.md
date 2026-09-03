# Running Tests in Apache Artemis

This document explains how to run tests in the Apache Artemis repository, which uses Maven skip properties to control test execution.

## Skip Test Properties

The repository uses different skip properties for different test modules:

- **`skipUnitTests`** - Controls unit tests (default: `true`)
- **`skipIntegrationTests`** - Controls integration tests (default: `true`)
- **`e2e-tests.skipTests`** - Controls end-to-end tests (default: `true`)

By default, tests are **skipped** to speed up the build. You must explicitly set these properties to `false` to run tests.

## Running Unit Tests

Unit tests are located in `tests/unit-tests/`.

### Run a specific unit test:
```bash
mvn test -pl tests/unit-tests -Dtest=ResourceQuotaManagerTest -DskipUnitTests=false
```

### Run all unit tests in the module:
```bash
mvn test -pl tests/unit-tests -DskipUnitTests=false
```

### Run all unit tests from root:
```bash
mvn test -DskipUnitTests=false
```

## Running Integration Tests

Integration tests are located in `tests/integration-tests/`.

### Run a specific integration test:
```bash
mvn test -pl tests/integration-tests -Dtest=ResourceQuotaReloadTest -DskipIntegrationTests=false
```

### Run all integration tests in the module:
```bash
mvn test -pl tests/integration-tests -DskipIntegrationTests=false
```

## Running Tests from artemis-server

Some tests are located directly in the `artemis-server` module. These use the standard `-DskipTests` property:

```bash
mvn test -pl artemis-server -Dtest=SomeTest -DskipTests=false
```

## Common Patterns

### Run tests with wildcard pattern:
```bash
mvn test -pl tests/unit-tests -Dtest=ResourceQuota* -DskipUnitTests=false
```

### Run multiple specific tests:
```bash
mvn test -pl tests/unit-tests -Dtest=TestA,TestB,TestC -DskipUnitTests=false
```

### Build a module and run its tests:
```bash
mvn clean install -pl tests/unit-tests -DskipUnitTests=false
```

### Run tests with debug output:
```bash
mvn test -pl tests/unit-tests -Dtest=ResourceQuotaManagerTest -DskipUnitTests=false -X
```

## Maven Build Profiles

The repository defines profiles in the root `pom.xml` that control test execution:

- **Default profile**: All tests skipped (`skipUnitTests=true`, `skipIntegrationTests=true`)
- **`dev` profile**: Unit tests enabled (`skipUnitTests=false`)
- **`release` profile**: Unit and integration tests enabled

### Activate a profile:
```bash
mvn test -Pdev
```

## Quick Reference

| Test Location | Module Path | Skip Property |
|---------------|-------------|---------------|
| artemis-server tests | `artemis-server` | `-DskipTests=false` |
| Unit tests | `tests/unit-tests` | `-DskipUnitTests=false` |
| Integration tests | `tests/integration-tests` | `-DskipIntegrationTests=false` |
| E2E tests | `tests/e2e-tests` | `-De2e-tests.skipTests=false` |


**IMPORTANT:** After modifying any source code in `artemis-server/`, you MUST rebuild before running tests:
```bash
mvn clean install -pl artemis-server -DskipTests
```
Otherwise tests will run against the old compiled code and won't reflect your changes.
