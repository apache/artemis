# Resource Quotas

## Overview

Resource quotas provide hierarchical limits on broker resources to prevent any single tenant, region, or application from consuming excessive broker capacity. Unlike global limits which apply to the entire broker, or address settings that constrain matched addresses, resource quotas allow control over resource consumption across arbitrary groups of addresses.

Resource quotas can limit three types of resources:
- **Message bytes**: Total memory or disk consumed by messages across all addresses in the quota
- **Address count**: Maximum number of addresses that can be created within the quota
- **Queue count**: Maximum number of queues that can be created within the quota

## Key Features

### Hierarchical Quotas

Quotas can be organized in parent-child hierarchies where child quota usage counts toward parent limits. This allows modeling organizational structures like:
- Enterprise → Region → Tenant
- Department → Team → Application
- Global → Geography → Customer

Child quotas are constrained by both their own limits AND their parent's limits, enforcing quotas at multiple organizational levels simultaneously.

### Wildcard Templates

Quota templates with wildcards automatically create quota instances when addresses match the pattern. For example:
- Template `EU.*` with address `eu.fr.orders` creates instance `EU.fr`
- Template `tenant.*` with address `tenant.acme.data` creates instance `tenant.acme`

Instances inherit the template's limits and parent relationships, allowing dynamic multi-tenant configurations.

### Integration with Address Settings

Quotas are applied through address-settings, allowing arbitrary address patterns to participate in shared quota schemes. An address can only belong to one quota, determined by its matching address-settings entry.

## Configuration

### Defining Resource Quotas

Resource quotas are defined in the `<resource-quotas>` section of `broker.xml`:

```xml
<configuration>
   <core>
      <resource-quotas>
         <!-- Top-level quota for Europe -->
         <resource-quota name="EUROPE">
            <max-message-bytes>10G</max-message-bytes>
            <max-addresses>1000</max-addresses>
            <max-queues>5000</max-queues>
         </resource-quota>

         <!-- Country-level template - creates instances like EU.fr, EU.de -->
         <resource-quota name="EU.*">
            <max-message-bytes>2G</max-message-bytes>
            <max-addresses>200</max-addresses>
            <max-queues>1000</max-queues>
            <part-of>EUROPE</part-of>
         </resource-quota>

         <!-- Tenant-level quota within the European region -->
         <resource-quota name="tenant.acme">
            <max-message-bytes>500M</max-message-bytes>
            <max-addresses>50</max-addresses>
            <max-queues>100</max-queues>
            <part-of>EUROPE</part-of>
         </resource-quota>
      </resource-quotas>
   </core>
</configuration>
```

### Configuration Elements

#### `<resource-quota name="...">`
Defines a quota with a unique name. The name can contain wildcards (`*`) for template instantiation.

**Attributes:**
- `name` (required): Unique identifier for the quota

**Child Elements:**
- `<max-message-bytes>`: Maximum total bytes for messages (supports K, M, G suffixes). Default: unlimited (-1)
- `<max-addresses>`: Maximum number of addresses. Default: unlimited (-1)  
- `<max-queues>`: Maximum number of queues. Default: unlimited (-1)
- `<part-of>`: Parent quota name for hierarchical enforcement. Must reference a statically-defined, non-wildcard quota name (not a dynamically-instantiated wildcard instance). Optional.

### Assigning Quotas to Addresses

Reference quotas from address-settings using the `<resource-quota>` element:

```xml
<address-settings>
   <!-- European tenant addresses use EU wildcard template -->
   <address-setting match="eu.#">
      <resource-quota>EU.*</resource-quota>
   </address-setting>

   <!-- Specific tenant quota -->
   <address-setting match="tenant.acme.#">
      <resource-quota>tenant.acme</resource-quota>
   </address-setting>

   <!-- US addresses use different quota -->
   <address-setting match="us.#">
      <resource-quota>US.*</resource-quota>
   </address-setting>
</address-settings>
```

## How It Works

### Quota Enforcement

Resource quotas enforce limits on three resource types using a consistent proactive check-then-increment pattern:

1. **Address creation**: Before creating an address, the broker checks if the address count quota would be exceeded (checking child and all parents in the hierarchy). If the limit would be exceeded, an exception is thrown and the address is not created. After successful address creation, the address count is incremented in the quota and all parent quotas.

2. **Queue creation**: Before creating a queue, the broker checks if the queue count quota would be exceeded (checking child and all parents). If the limit would be exceeded, an exception is thrown and the queue is not created. After successful queue creation, the queue count is incremented in the quota and all parent quotas.

3. **Message bytes**: Before routing a message, the broker checks if adding the message's size would exceed the byte quota (checking child and all parents). If the limit would be exceeded, an exception is thrown and the message is rejected. Message byte counters are updated as messages flow through the paging and queue subsystems.

All quota types follow the same enforcement pattern:
- **Check before**: Before allocating resources, check if the operation would exceed quota limits
- **Increment after success**: Only after successful completion, increment the counters

Address and queue count limits are enforced precisely using atomic counters. Byte limits use striped counters (`LongAdder`) for high-throughput message paths, which means enforcement is best-effort under heavy concurrent load — a small transient overshoot is possible before the limit takes effect.

### Hierarchical Enforcement

When checking limits, quotas walk up the parent chain:

1. Check if child quota limit would be exceeded
2. Check if parent quota limit would be exceeded (recursively up the chain)
3. If any ancestor would be exceeded, reject the operation
4. Otherwise, increment counters in child and all ancestors

This ensures that parent limits are respected even when child limits are generous.

### Wildcard Template Resolution

When an address operation (creation, queue creation, or message send) triggers a quota lookup with a wildcard quota reference:

1. Extract the wildcard value from the address name (e.g., `eu.fr.orders` → `fr`)
2. Create instance name by substituting wildcard (e.g., `EU.*` → `EU.fr`)
3. Check if instance already exists; if not, create from template
4. Establish parent relationship if template has `<part-of>`
5. Return the quota instance for enforcement

## Behavior on Limit Exceeded

When a quota limit is exceeded, the broker throws `ActiveMQResourceQuotaExceededException`:

- **Address creation**: Address creation fails with quota exception
- **Queue creation**: Queue creation fails with quota exception
- **Message routing**: Message is rejected at send time (before entering the broker)

Clients receive the exception and can handle it (retry, route elsewhere, alert, etc).

## Counter Rebuild on Restart

Resource quota counters are **not persisted**. After broker restart:

1. Quota configurations are loaded from `broker.xml`
2. Quota instances are created with zero counters
3. Parent relationships are established
4. On first access, the broker scans existing addresses and queues via the PostOffice
5. Counters are rebuilt by incrementing for each discovered resource (propagating to parent chains)
6. Broker continues with accurate counts

## Live Reload

Resource quotas are reloaded automatically when `broker.xml` is modified (as part of the broker's standard configuration reload). During reload:

- New quotas are added and their counters populated by scanning existing addresses
- Removed quotas are unregistered and their counters subtracted from parent chains
- Modified quotas have their limits updated in place, preserving existing counters
- Hierarchy changes (modified `part-of`) are handled by adjusting parent chain counters without resetting child counters
- Address-to-quota mappings are rebalanced when address-settings changes cause addresses to map to different quotas

## JMX Management

Each resource quota is registered as a JMX MBean exposing the following attributes:

| Attribute | Type | Description |
|-----------|------|-------------|
| `Name` | String | Quota name |
| `PartOf` | String | Parent quota name, or null |
| `MaxAddresses` | int | Address limit (-1 if unlimited) |
| `CurrentAddressCount` | int | Current address count |
| `AddressUtilizationPercent` | double | Percentage of address limit used |
| `MaxQueues` | int | Queue limit (-1 if unlimited) |
| `CurrentQueueCount` | int | Current queue count |
| `QueueUtilizationPercent` | double | Percentage of queue limit used |
| `MaxMessageBytes` | long | Byte limit (-1 if unlimited) |
| `CurrentMessageBytes` | long | Current byte count |
| `MessageBytesUtilizationPercent` | double | Percentage of byte limit used |
| `HasLimits` | boolean | Whether any limit is configured |
| `LimitReached` | boolean | Whether any limit is currently reached |

### Logging

Quota operations are logged at DEBUG level:
- Quota instance creation from templates
- Counter increments/decrements  
- Limit checks and rejections
- Parent relationship establishment

Enable DEBUG logging for `org.apache.activemq.artemis.core.server.quota` and `org.apache.activemq.artemis.core.settings.impl.ResourceQuota` to trace quota activity.

## Best Practices

- **Size parent limits to accommodate children**: A parent quota's limits should be at least the sum of its children's expected usage, since all child counters propagate upward.
- **Use wildcard templates for dynamic tenants**: Rather than defining a quota per tenant, use a template like `tenant.*` so new tenants get quota instances automatically.
- **Avoid referencing wildcard instances in `part-of`**: The `part-of` field must reference a statically-defined quota. Dynamically-instantiated wildcard instances (e.g., `EU.fr` from template `EU.*`) cannot be used as parents.
- **Set limits on the dimensions you care about**: Omitted limits default to unlimited (-1). Only configure the resource types you need to constrain.
- **Monitor via JMX**: Use the utilization percentage attributes to set up alerts before quotas are fully consumed.

## Examples

### Multi-Tenant SaaS Platform

```xml
<resource-quotas>
   <!-- Root quota for all tenants -->
   <resource-quota name="TENANTS">
      <max-message-bytes>50G</max-message-bytes>
      <max-addresses>10000</max-addresses>
      <max-queues>50000</max-queues>
   </resource-quota>

   <!-- Tenant template - creates tenant.acme, tenant.beta, etc -->
   <resource-quota name="tenant.*">
      <max-message-bytes>5G</max-message-bytes>
      <max-addresses>1000</max-addresses>
      <max-queues>5000</max-queues>
      <part-of>TENANTS</part-of>
   </resource-quota>
</resource-quotas>

<address-settings>
   <address-setting match="tenant.#">
      <resource-quota>tenant.*</resource-quota>
   </address-setting>
</address-settings>
```

### Geographic Segmentation

```xml
<resource-quotas>
   <!-- Regional quotas -->
   <resource-quota name="AMERICAS">
      <max-message-bytes>20G</max-message-bytes>
   </resource-quota>

   <resource-quota name="EUROPE">
      <max-message-bytes>15G</max-message-bytes>
   </resource-quota>

   <resource-quota name="APAC">
      <max-message-bytes>10G</max-message-bytes>
   </resource-quota>

   <!-- Country templates within regions -->
   <resource-quota name="US.*">
      <max-message-bytes>5G</max-message-bytes>
      <part-of>AMERICAS</part-of>
   </resource-quota>

   <resource-quota name="EU.*">
      <max-message-bytes>3G</max-message-bytes>
      <part-of>EUROPE</part-of>
   </resource-quota>
</resource-quotas>

<address-settings>
   <address-setting match="us.#">
      <resource-quota>US.*</resource-quota>
   </address-setting>

   <address-setting match="eu.#">
      <resource-quota>EU.*</resource-quota>
   </address-setting>
</address-settings>
```

### Department Quotas

```xml
<resource-quotas>
   <!-- Department quotas without hierarchy -->
   <resource-quota name="dept.engineering">
      <max-message-bytes>10G</max-message-bytes>
      <max-queues>1000</max-queues>
   </resource-quota>

   <resource-quota name="dept.sales">
      <max-message-bytes>5G</max-message-bytes>
      <max-queues>500</max-queues>
   </resource-quota>

   <resource-quota name="dept.support">
      <max-message-bytes>2G</max-message-bytes>
      <max-queues>200</max-queues>
   </resource-quota>
</resource-quotas>

<address-settings>
   <address-setting match="eng.#">
      <resource-quota>dept.engineering</resource-quota>
   </address-setting>

   <address-setting match="sales.#">
      <resource-quota>dept.sales</resource-quota>
   </address-setting>

   <address-setting match="support.#">
      <resource-quota>dept.support</resource-quota>
   </address-setting>
</address-settings>
```

## Relationship to Other Limits

Resource quotas operate independently of the broker's other limiting mechanisms. An address can be subject to all of them simultaneously — when multiple limits apply, the strictest one determines the outcome.

| Mechanism | Scope | What it limits | How it differs from resource quotas |
|-----------|-------|----------------|--------------------------------------|
| `global-max-size` | Whole broker | Total message memory across all addresses | A single broker-wide ceiling; resource quotas partition usage across groups |
| `max-size-bytes` (address-setting) | Per address (or wildcard match) | Message bytes on individual addresses | Limits each address independently; resource quotas aggregate across addresses |
| `resource-limit-settings` | Per user | Sessions and queues created by a specific user | Tied to user identity; resource quotas are tied to address naming, regardless of which user sends |
| **Resource quotas** | Arbitrary address groups | Bytes, addresses, and queues across grouped addresses | Hierarchical, cross-address enforcement by address naming convention |

For example, an address `eu.fr.orders` might be constrained by:
- Its resource quota `EU.fr` (max 2G across all `eu.fr.*` addresses)
- Its parent quota `EUROPE` (max 10G across all European addresses)
- Its address-setting `max-size-bytes` (max 500M on this specific address)
- The broker's `global-max-size` (max 30G broker-wide)
- A `resource-limit-settings` entry for the connected user (max 100 queues per user)