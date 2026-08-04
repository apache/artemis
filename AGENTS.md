# Apache Artemis - AI Agent Guidelines

Guidelines for AI agents working on this codebase.

## Project Info
- Running
  - **Java**: 17+
- Building
  - **Java**: 25+
  - **Maven**: 3.9+

## AI Agent Rules of Engagement

These rules apply to ALL AI agents working on this codebase.

### Attribution
All AI-generated content must clearly identify itself as AI-generated and mention the human operator. Example format: "_Claude Code on behalf of [Human Name]_"

### PR Volume
- Maximum 10 PRs per day per operator
- Prioritize quality over quantity

### Git Branch
- NEVER push commits to branches not created by the agent
- Use own fork instead of main repository
- Provide useful branch names containing topic and JIRA issue number
- Delete branches after PR is merged or rejected
- NEVER use `git push -f` on shared branches
- Work in topic branches, not `main`

### JIRA Ticket Ownership
- JIRA project: https://issues.apache.org/jira/browse/ARTEMIS
- ONLY pick up **Unassigned** tickets
- Assign ticket to operator before starting work
- Transition to "In Progress"
- Set correct `fixVersions` field before resolving

### PR Description Maintenance
Update PR description and title after each commit to reflect current state using `gh pr edit`

### PR Reviewers
- Identify relevant committers using git history analysis
- Request reviews from at least 2 relevant committers
- Re-request review when all comments are addressed and checks are green

### Merge Requirements
- NO merge with unresolved review conversations
- Require at least one human approval
- NEVER approve own PRs

### Code Quality
- Include tests for new functionality/bug fixes
- Update documentation when needed
- Pass all checks including CheckStyle when using `-Pdev` profile
- Follow existing code conventions and patterns
- NEVER introduce security vulnerabilities (command injection, XSS, SQL injection, OWASP Top 10)
- Always apply the Apache License to new source files

### Quality Expectations
Avoid introducing:
- Code smells
- Maintainability regressions
- CWE (Common Weakness Enumeration)
- OWASP vulnerabilities
- Deprecated code usage
- Resource leaks in tests

### Testing Best Practices
**NEVER use `Thread.sleep()` in tests**. Use proper wait mechanisms:
- Use `Wait.waitFor()` utility methods
- Use `Wait.assertTrue()` with timeout and conditions
- Example:
  ```java
  Wait.assertTrue(() -> queue.getMessageCount() == 1, 5000);
  ```

**Avoid resource leaks in tests**:
- Extend `ActiveMQTestBase` which provides automatic cleanup
- Use `createServer()` methods which auto-register servers for cleanup
- Use `createInVMNonHALocator()` or `createNettyNonHALocator()` for locators
- Use `createSessionFactory()` for session factories
- Or manually register resources with `addServer()`, `addServerLocator()`, `addSessionFactory()`
- See `SimpleTest` for a basic example

**Use appropriate logging**:
- Use `logger.debug()` for test logging, NEVER `logger.info()`
- NEVER use `System.out.println()`
- Import: `private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());`

### Issue Investigation (Before Implementation)
Required steps:
1. Validate the issue is real and reproducible
2. Check git history: `git log` and `git blame` on affected files
3. Search for related JIRA issues at https://issues.apache.org/jira/browse/ARTEMIS
4. Review relevant documentation in `docs/user-manual`
5. Understand broader context
6. Check if proposed fix would revert prior intentional work

### Knowledge Cutoff Awareness
- Never make authoritative claims about external projects based on training data
- Verify external dependency versions exist before questioning
- Admit uncertainty rather than asserting based on potentially stale knowledge

### Git History Review (When Reviewing PRs)
- Check git log/blame on modified files
- Verify changes don't revert deliberate behavior
- Check documentation
- Search related JIRA tickets

## Security Model

**Authoritative Reference**: See [docs/user-manual/threat-model.adoc](docs/user-manual/threat-model.adoc) for the complete threat model including trust boundaries, adversary model, security properties provided/not provided, and triage dispositions.

### Security Review Checklist
When reviewing code changes:
- Protocol inputs from network clients properly validated?
- `ObjectInputStream` without `ObjectInputFilter`?
- New config parameters affecting security defaults? Documented in upgrade guide?
- Authentication/authorization properly enforced?
- Sensitive data protected (not logged, masked in config)?
- Does change affect threat model (new protocol, API surface, deployment mode)?

## Structure
This is the structure of the most common modules:
```
artemis/
├── artemis-bom/              # Bill of Materials
├── artemis-cli/              # Command-line tools
├── artemis-commons/          # Common utilities
├── artemis-core-client/      # Core client API
├── artemis-jms-client/       # JMS client
├── artemis-jakarta-client/   # Jakarta Messaging client
├── artemis-journal/          # Persistence journal
├── artemis-protocols/        # Protocol implementations (AMQP, MQTT, STOMP, etc.)
│   ├── artemis-amqp-protocol/
│   ├── artemis-mqtt-protocol/
│   └── artemis-stomp-protocol/
├── artemis-server/           # Core broker
├── artemis-jms-server/       # JMS broker
├── artemis-jakarta-server/   # Jakarta Messaging broker
├── artemis-web/              # Embedded web server
├── artemis-docker/           # Docker configurations
├── docs/                     # AsciiDoc documentation
│   ├── user-manual/
│   ├── migration-guide/
│   └── hacking-guide/
├── tests/                    # Integration & smoke tests
│   ├── integration-tests/
│   ├── smoke-tests/
│   └── unit-tests/
```

## Build Policy

- **Only build when explicitly asked** by the operator, or at the very end of a task as a final validation step
- **Do NOT rebuild after every file edit** — compile/build loops waste time on a large multi-module project
- If a build fails, fix the issue and rebuild **once**, do not loop indefinitely

## Build Commands

**Authoritative Reference**: See [docs/hacking-guide/_building.adoc](docs/hacking-guide/_building.adoc) for complete build documentation.

### Most Common Commands
```bash
# Fast build (no tests, no docs)
mvn -T1C install -DskipTests

# Development build with CheckStyle (recommended)
mvn -T1C -Pdev install -DskipTests

# PR build (what CI runs)
mvn -T1C -Pfast-tests -Pcompatibility-tests install

# Single test
mvn -Ptests -DfailIfNoTests=false -Dtest=MyTest test
```

## Testing

**Authoritative Reference**: See [docs/hacking-guide/_tests.adoc](docs/hacking-guide/_tests.adoc) for complete testing guide.

### Key Test Patterns
- Extend `ActiveMQTestBase` for automatic resource cleanup
- Use `SingleServerTestBase` for simple single-broker tests
- See `SimpleTest` and `SingleServerSimpleTest` for examples
- Web console tests in `smoke-tests/` use Selenium

## Conventions

**Authoritative Reference**: See [docs/hacking-guide/_code.adoc](docs/hacking-guide/_code.adoc) for complete workflow and conventions.

### Commit Messages (50/72 format)
```
ARTEMIS-XXXX Brief summary (max 50 chars)

Detailed description wrapped at 72 characters explaining
the why, not just the what.

Assisted-by: <AgentName>
```
- Use `NO-JIRA` prefix ONLY for trivial changes (typos, small doc fixes)
- Bug fixes and features require a JIRA ticket
- Add `Assisted-by: <AgentName>` trailer when an AI agent contributed meaningfully to the work (implementation, bug fix, refactor). Omit it for trivial automation (formatting, rename, boilerplate generation).

### Key Conventions
- Tests: `*Test.java` (JUnit 5)
- Deprecation: `@Deprecated` annotation + Javadoc + migration guide entry
- Documentation: AsciiDoc in `docs/user-manual/`, use relative xrefs for internal links

## Adding New Dependencies

**Authoritative Reference**: See [docs/hacking-guide/_code.adoc](docs/hacking-guide/_code.adoc#adding-new-dependencies) and https://www.apache.org/legal/3party.html

**Critical**: Dependencies must be Apache v2.0 compatible. Add to top-level `pom.xml` dependency management with version and license comment. Individual modules inherit version from parent.

## Database Storage Architecture

The database storage layer uses a **group commit** (write coalescing) architecture to maximize throughput. Instead of committing each write individually, operations are buffered and a single JDBC `commit` covers many operations. This is the same approach used by database engines internally (e.g. InnoDB group commit).

### Core Design Patterns

**Group commit** — `DataManager` extends `ActiveMQScheduledComponent`. Callers submit data into `pendingData`; a scheduled timer fires `flush()`, which hands the accumulated batch to a `DataWorker`. One `commit` covers all operations in the batch, amortizing the most expensive part (network round-trip + fsync).

**Command pattern** — Each `DBData` subclass encapsulates a single operation and dispatches itself to the correct `BatchableStatement` via `store(DataWorker)`. This cleanly separates "what to persist" from "how to persist."

**Heterogeneous batching** — A single transaction mixes inserts and deletes across different tables (messages, references, addresses, queues, pages). This provides atomicity for logical operations that touch multiple tables (e.g. routing a message creates a message row + reference rows).

**Worker pool with dedicated connections** — Multiple `DataWorker` instances (each owning its own JDBC `Connection` and `PreparedStatement` set) cycle through a `LinkedBlockingDeque`. When a worker finishes, it returns itself to the pool.

**Async write-behind with IOCompletion callbacks** — Callers don't block on commit. Each data item carries an `IOCompletion` callback: `storeLineUp()` is called on enqueue, `done()` after successful commit, `onError()` on failure. This mirrors the existing Artemis journal's async model.

**Retry with reconnect** — `DataAgent.run()` retries up to 5 times on `SQLException`, rolling back and re-establishing the connection between attempts. The `commit` is outside the retry loop so a successful batch is committed exactly once.

### Key Classes

| Class | Role |
|-------|------|
| `DataManager` | Public API. Buffers `DBData` items, triggers flush on timer, dispatches to workers. |
| `DataWorker` | Owns a JDBC connection + all `BatchableStatement` instances. Executes a batch: `doBeforeCommit` (flush all statements) → `commit` → `doAfterCommit` (fire callbacks). |
| `DataAgent` | Abstract base for workers. Handles connection lifecycle, retry loop, commit/rollback. |
| `BatchableStatement<E>` | Wraps a `PreparedStatement`. Accumulates items via `addData()`, executes via `executeBatch()`. |
| `DBData<W>` | Abstract command. Each subclass holds entity fields and dispatches to the right statement. |
| `DatabaseStoreTX` | Collects `DBData` items for a single broker transaction, flushed on commit. |

## Adding a New Database Entity Type (Database Storage)

When adding a new entity type to the database storage layer (e.g. Message, Address, Queue, Reference), follow the pattern established by existing types. Each entity requires up to 6 files across 4 packages, plus SQL in properties and wiring in `DataWorker` and `DataManager`.

### Package Layout

**`artemis-direct-db`** — SQL provider layer (under `org.apache.artemis.database`):
```
SQLProvider.java          # Abstract: table name getters + abstract create/insert/delete SQL methods
OracleSQLProvider.java    # Oracle-specific SQL (NUMBER(19), BLOB, etc.)
MySQLSqlProvider.java     # MySQL-specific SQL (BIGINT, LONGBLOB, etc.)
DatabaseProvider.java     # createSchema() calls JDBCUtils.createTable for each entity
```

**`artemis-server`** — persistence layer (under `org.apache.activemq.artemis.core.persistence.impl.database`):
```
dbdata/          # Data carrier classes (extend DBData<DataWorker>)
queries/         # JDBC read queries (SELECT, used at startup/reload)
statements/      # Batched write statements (extend BatchableStatement<XxxData>)
worker/          # DataWorker (owns statement instances) + DataManager (public API)
```

### Step-by-step Checklist

Using `Foo` as the example entity name:

#### 1. SQL provider — `artemis-direct-db`

**`SQLProvider.java`** — add:
- `public String getFoo() { return "DB_FOO"; }` — table name getter
- `public abstract String createFoo(String tableName);` — CREATE TABLE
- `public abstract String insertFoo(String tableName);` — INSERT
- `public abstract String deleteFoo(String tableName);` — DELETE

**`OracleSQLProvider.java`** — implement all three with Oracle types (`NUMBER(19)`, `BLOB`, etc.)

**`MySQLSqlProvider.java`** — implement all three with MySQL types (`BIGINT`, `LONGBLOB`, etc.)

**`DatabaseProvider.createSchema()`** — add:
```java
String fooTableName = sqlProvider.getFoo();
JDBCUtils.createTable(connection, sqlProvider, fooTableName, sqlProvider.createFoo(fooTableName));
```

#### 2. Data classes — `dbdata/`
- **`FooData.java`** — extends `DBData<DataWorker>`. Holds the entity's fields as public members. Constructor takes the domain fields + `IOCompletion context` (passed to `super`). Implement `store(DataWorker worker)` to call `worker.insertFooStatement.addData(this, context)`.
- **`DeleteFooData.java`** — extends `DBData<DataWorker>`. Holds the key fields needed for deletion. Implement `store(DataWorker worker)` to call `worker.deleteFooStatement.addData(this, context)`.

#### 3. Statements — `statements/`
- **`InsertFooStatement.java`** — extends `BatchableStatement<FooData>`. Constructor takes `(DatabaseProvider, Connection, DatabaseStorageConfiguration, int expectedSize)`, calls `super(...)` with the INSERT SQL from a static `getSQL()` method. Override `doOne(FooData task)` to bind PreparedStatement parameters.
- **`DeleteFooStatement.java`** — extends `BatchableStatement<DeleteFooData>`. Same constructor pattern. Override `doOne(DeleteFooData task)` with DELETE SQL parameter binding.

#### 4. Query class — `queries/`
- **`FooJDBCQuery.java`** — takes `Connection` in constructor. Has a `query(Consumer<FooData> consumer)` method that executes a SELECT, iterates the ResultSet, constructs `FooData` from each row, and passes it to the consumer.

#### 5. SQL in properties — `artemis-direct-db/src/main/resources/journal-sql.properties`
- Add `create-parallelDB-foo.mysql=CREATE TABLE %s(...)` and `create-parallelDB-foo.oracle=CREATE TABLE %s(...)` entries for table creation.

#### 6. Wire into `DataWorker` — `worker/DataWorker.java`
- Add a `public InsertFooStatement insertFooStatement` and `public DeleteFooStatement deleteFooStatement` field.
- In `connect()`: instantiate both statements.
- In `doBeforeCommit()`: call `insertFooStatement.flushPending(false)` and `deleteFooStatement.flushPending(false)`.
- In `doAfterCommit()`: call `.confirmData()` on both.
- In `doError()`: call `.onError(exception)` on both.
- In `doCleanup()`: call `.clear()` on both.

#### 7. Wire into `DataManager` — `worker/DataManager.java`
- Add public methods like `storeFoo(StorageTX, ..., IOCompletion)` and `deleteFoo(...)` that create the data objects and call `castTX(storageTX).addData(...)` or `flushData(...)`.

#### 8. Use from `DatabaseStorageManager`
- Import the new query class and call it during data loading.
- Call `DataManager` methods for store/delete operations.

#### 9. Tests — `tests/db-tests`

Statement-level tests go in `tests/db-tests/src/test/java/org/apache/activemq/artemis/tests/db/dbstorage/statements/`.

- **`FooStatementTest.java`** — extends `AbstractStatementTest`. Uses `@DisabledIf("isNoDatabaseSelected")` and `@ExtendWith(ParameterizedTestExtension.class)`. Each test method uses `@TestTemplate`.
- Pattern: create a `DatabaseStorageManager`, call `start()`, get a `Connection` from `storageConfiguration.getDatabaseProvider()`, create the statement directly, insert/delete data, assert row counts via `selectCount(connection, "TABLE_NAME")`.
- Use `CountDownCompletion` as the `IOCompletion` callback and assert `latch.await(...)` to verify callbacks fired.
- Test both insert-only and insert-then-delete flows.
- See `MessagesStatementTest`, `AddressInfoTest`, `QueueInfoTest`, `PageStatementTest` for examples.

### Existing Entity Examples
| Entity    | Data classes                          | Insert statement             | Delete statement              | Query class           | Test class              |
|-----------|---------------------------------------|------------------------------|-------------------------------|-----------------------|-------------------------|
| Message   | `MessageData`, `DeleteMessageData`    | `InsertMessageStatement`     | `DeleteMessageStatement`      | `MessagesJDBCQuery`   | `MessagesStatementTest` |
| Address   | `AddressData`, `DeleteAddressData`    | `InsertAddressStatement`     | `DeleteAddressStatement`      | `AddressJDBCQuery`    | `AddressInfoTest`       |
| Queue     | `QueueData`                           | `InsertQueueStatement`       | *(not yet)*                   | `QueueJDBCQuery`      | `QueueInfoTest`         |
| Reference | `MessageReferenceData`, `DeleteReferenceData` | `InsertReferencesStatement` | `DeleteReferenceStatement` | `ReferencesJDBCQuery` | `MessagesStatementTest` |
| Page      | `PageData`, `DeletePageData`          | `InsertPageStatement`        | `DeletePageStatement`         | `PageJDBCQuery`       | `PageStatementTest`     |
| PageRef   | `PageRefData`, `DeletePageRefData`    | `InsertPageRefStatement`     | `DeletePageRefStatement`      | `PageRefJDBCQuery`    | `PageStatementTest`     |

## Links
- https://artemis.apache.org/
- https://github.com/apache/artemis
- https://artemis.apache.org/contributing
