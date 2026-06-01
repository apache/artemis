# Quick Start Guide: JSON Schema Validation

## For Developers

### Generate Schema During Artemis Build

```bash
cd /path/to/artemis

# Build with schema generation
mvn clean install -Pgenerate-schema

# Schema is now in:
# artemis-jsonschema/target/artemis-jsonschema-*.jar!/org.apache.artemis/jsonschema/broker-config-schema.json
```

### Use Schema in Your IDE

Many IDEs support JSON Schema validation:

**VS Code:**
1. Install "JSON Schema Store" extension
2. Create `.vscode/settings.json`:
```json
{
  "json.schemas": [
    {
      "fileMatch": ["broker*.json"],
      "url": "file:///path/to/artemis/artemis-jsonschema/target/schema/org.apache.artemis/jsonschema/broker-config-schema.json"
    }
  ]
}
```

**IntelliJ IDEA:**
1. Settings → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings
2. Add schema URL: `file:///path/to/artemis-jsonschema/target/schema/.../broker-config-schema.json`
3. Map to file pattern: `broker*.json`

## For Production Users

### Enable Validation in Running Broker

**Step 1:** Build and install artemis-jsonschema

```bash
cd /path/to/artemis-source
mvn install -Pgenerate-schema -pl artemis-jsonschema -DskipTests
```

**Step 2:** Copy JAR to broker instance

```bash
cp artemis-jsonschema/target/artemis-jsonschema-2.55.0-SNAPSHOT.jar \
   $ARTEMIS_INSTANCE/lib/
```

**Step 3:** Enable validation in `etc/artemis.profile`

```bash
# Add to JAVA_ARGS
JAVA_ARGS="$JAVA_ARGS -Dartemis.config.validate-json=true"
```

**Step 4:** Create JSON configuration

`etc/broker.json`:
```json
{
  "name": "my-broker",
  "persistenceEnabled": true,
  "journalDirectory": "./data/journal",
  "bindingsDirectory": "./data/bindings",
  "journalMinFiles": 10,
  "journalPoolFiles": 20,
  "acceptorConfigurations": {
    "artemis": {
      "factoryClassName": "org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory",
      "params": {
        "host": "0.0.0.0",
        "port": "61616",
        "protocols": "CORE"
      }
    }
  }
}
```

**Step 5:** Reference config in `etc/broker.xml`

```xml
<configuration>
  <core>
    <!-- Load JSON configuration -->
    <property-file-name>broker.json</property-file-name>
    
    <!-- Rest of XML configuration... -->
  </core>
</configuration>
```

**Step 6:** Start broker

```bash
./bin/artemis run
```

If validation is enabled and `broker.json` is invalid, you'll see errors like:

```
ERROR: JSON configuration validation failed:
  - $.persistenceEnabled: must be boolean (found: string)
  - $.journalMinFiles: must be >= 2 (found: -1)
  - $.acceptorConfigurations.artemis.params.port: must be string (found: integer)
```

## Validation Behavior

### When Validation is Enabled

- **Valid JSON:** Broker starts normally
- **Invalid JSON:** Broker logs validation errors and **ignores** the invalid configuration
- **Missing schema:** Broker logs warning and skips validation

### When Validation is Disabled (default)

- No validation performed
- Configuration loaded as-is (may fail later during broker startup if invalid)

## Troubleshooting

### Validation Not Working

**Check 1:** Verify JAR is on classpath
```bash
jps -v | grep artemis
# Look for artemis-jsonschema in classpath
```

**Check 2:** Verify system property is set
```bash
# Should see in broker logs:
INFO: JSON schema validation enabled
```

**Check 3:** Verify schema is in JAR
```bash
jar tf lib/artemis-jsonschema-*.jar | grep broker-config-schema.json
```

### Schema Not Found Error

```
ERROR: JSON schema not found: /org.apache.artemis/jsonschema/broker-config-schema.json
Ensure artemis-jsonschema module was built with -Pgenerate-schema profile.
```

**Fix:** Rebuild with schema generation:
```bash
mvn install -Pgenerate-schema -pl artemis-jsonschema
```

### False Positive Validation Errors

If a valid configuration is rejected, the schema may be out of date:

```bash
# Rebuild schema from latest code
cd /path/to/artemis-source
mvn install -pl artemis-server -DskipTests
mvn install -Pgenerate-schema -pl artemis-jsonschema -DskipTests

# Replace broker's schema JAR
cp artemis-jsonschema/target/artemis-jsonschema-*.jar $ARTEMIS_INSTANCE/lib/
```

## Performance Impact

Schema validation adds minimal overhead:
- **First load:** ~50-100ms to load and parse schema (one-time cost)
- **Per validation:** ~5-10ms for typical broker configurations
- **Memory:** ~5MB for cached schema

For production use, validation can be disabled after initial configuration is verified.

## CI/CD Integration

### Validate Configs in Build Pipeline

```bash
# Build validator
mvn install -Pgenerate-schema -pl artemis-jsonschema -DskipTests

# Validate your config
java -cp artemis-jsonschema/target/artemis-jsonschema-*.jar \
  org.apache.artemis.jsonschema.validation.SchemaValidator \
  path/to/broker.json
```

Exit codes:
- `0` = Valid
- `1` = Invalid (errors printed to stderr)
- `2` = Schema not found

## Examples

See `artemis-jsonschema/examples/` for sample configurations:
- `simple-broker-config.json` - Minimal valid configuration
- `test-acceptors-and-addresses.json` - Acceptors and address settings
- `test-byte-notation.json` - Memory size notation (10MB, 1GB, etc.)
- `test-jaas-config.json` - Security and JAAS configuration
