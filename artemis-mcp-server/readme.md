# Artemis MCP Server (ARTEMIS-6118)

Model Context Protocol (MCP) server exposing Apache ActiveMQ Artemis broker operations as tools
for AI agents, over the STDIO transport.

## Tools

Read-only tools (always available):

| Tool | Args | Description |
|---|---|---|
| `list_queues` | — | Names of all queues on the broker. |
| `list_addresses` | — | Names of all addresses on the broker. |
| `get_broker_overview` | — | Version, uptime, connection/consumer/message counts. |
| `get_queue_stats` | `queue` | Message and consumer counters for one queue. |
| `browse_messages` | `queue`, `limit?`, `selector?` | Browse a queue without consuming (non-destructive). |

Admin tools (only registered when `mode=admin`):

| Tool | Args | Description |
|---|---|---|
| `create_queue` | `name`, `address?`, `routingType?`, `durable?` | Create a queue. |
| `create_address` | `name`, `routingType?` | Create an address. |
| `delete_queue` | `name`, `confirm` | Delete a queue (destructive). |
| `delete_address` | `name`, `confirm` | Delete an address (destructive). |
| `purge_queue` | `queue`, `confirm` | Remove all messages from a queue (destructive). |
| `delete_messages` | `queue`, `confirm`, `filter?` | Remove messages matching a filter (destructive). |
| `move_messages` | `queue`, `target`, `confirm`, `filter?` | Move messages to another queue (destructive). |
| `retry_dlq` | `queue`, `confirm` | Retry messages, redelivering to their original address. |
| `send_message` | `target`, `body`, `properties?`, `durable?` | Send a text message to a queue or address. |
| `consume_message` | `queue`, `confirm`, `limit?`, `selector?`, `timeoutMillis?` | Consume (remove) messages from a queue (destructive). |

Destructive admin tools require `confirm: true`; the requirement is enforced both by the tool
input schema and by the handler.

## Configuration

Resolved from system properties first, then environment variables, then defaults.

| System property | Env var | Default |
|---|---|---|
| `artemis.mcp.brokerUrl` | `ARTEMIS_MCP_BROKER_URL` | `tcp://localhost:61616` |
| `artemis.mcp.user` | `ARTEMIS_MCP_USER` | — |
| `artemis.mcp.password` | `ARTEMIS_MCP_PASSWORD` | — |
| `artemis.mcp.mode` | `ARTEMIS_MCP_MODE` | `read-only` |
| `artemis.mcp.browseLimit` | `ARTEMIS_MCP_BROWSE_LIMIT` | `50` |

## Build & test

```bash
mvn clean install
```

Integration tests run against an in-VM embedded broker, so no external broker is required.

## Run

The server speaks JSON-RPC over STDIO. Logs go to stderr (stdout is reserved for the protocol).

```bash
java -cp "target/classes:$(cat target/cp.txt)" \
  org.apache.activemq.artemis.mcp.ArtemisMcpServer
```

## Design

- Monitoring/management uses Artemis management messages sent to `activemq.management`
  (single broker connection, no open JMX required).
- Messaging uses the Core/JMS client; `browse_messages` uses a JMS `QueueBrowser`.
- The MCP layer uses the MCP Java SDK (MIT / ASF Category A).

## Reactor integration (for the PR)

This module ships with a standalone POM for fast iteration. To contribute it to the
`apache/activemq-artemis` reactor, replace the `<groupId>`/`<version>`/`<properties>` block in
`pom.xml` with the reactor parent:

```xml
<parent>
   <groupId>org.apache.artemis</groupId>
   <artifactId>artemis-pom</artifactId>
   <version>${project.version}</version>
   <relativePath>../artemis-pom/pom.xml</relativePath>
</parent>
```

Since the Artemis TLP migration the Maven groupId is `org.apache.artemis`
(the old `org.apache.activemq` coordinates are relocation stubs).
