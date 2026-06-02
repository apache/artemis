# Apache Artemis - AI Agent Guidelines

Guidelines for AI agents working on this codebase.

## Project Info
- Running
  - **Java**: 17+
- Building
  - **Java**: 17
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

## Build Commands

**Authoritative Reference**: See [docs/hacking-guide/_building.adoc](docs/hacking-guide/_building.adoc) for complete build documentation.

### Most Common Commands
```bash
# Fast build (no tests, no docs)
mvn install -DskipTests

# Development build with CheckStyle (recommended)
mvn -Pdev install -DskipTests

# PR build (what CI runs)
mvn -Pfast-tests -Pcompatibility-tests install

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
```
- Use `NO-JIRA` prefix ONLY for trivial changes (typos, small doc fixes)
- Bug fixes and features require a JIRA ticket

### Key Conventions
- Tests: `*Test.java` (JUnit 5)
- Deprecation: `@Deprecated` annotation + Javadoc + migration guide entry
- Documentation: AsciiDoc in `docs/user-manual/`, use relative xrefs for internal links

## Adding New Dependencies

**Authoritative Reference**: See [docs/hacking-guide/_code.adoc](docs/hacking-guide/_code.adoc#adding-new-dependencies) and https://www.apache.org/legal/3party.html

**Critical**: Dependencies must be Apache v2.0 compatible. Add to top-level `pom.xml` dependency management with version and license comment. Individual modules inherit version from parent.

## Links
- https://artemis.apache.org/
- https://github.com/apache/artemis
- https://artemis.apache.org/contributing
