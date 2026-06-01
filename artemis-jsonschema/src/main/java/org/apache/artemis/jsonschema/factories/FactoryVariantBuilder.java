/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.artemis.jsonschema.factories;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.artemis.jsonschema.ir.Location;
import org.apache.artemis.jsonschema.ir.SchemaIR;
import org.apache.artemis.jsonschema.ir.SchemaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for building synthetic oneOf variant ClassNodes from a factory registry.
 *
 * <h3>Why synthetic nodes?</h3>
 *
 * <p>Artemis uses a map-of-strings pattern for plugin extensibility: a configuration class (e.g.
 * {@code TransportConfiguration}, {@code JaasAppConfigurationEntry}) holds a discriminator field
 * ({@code factoryClassName}, {@code loginModuleClass}) selecting the implementation, and an opaque
 * {@code Map<String, Object> params} whose valid keys depend on which implementation is selected.
 *
 * <p>There is no {@code NettyAcceptorFactoryConfiguration.class} with typed bean properties for
 * {@code host}, {@code port}, {@code sslEnabled} — those are convention-based string keys looked up
 * via {@code params.get("host")} in the implementation code. Same for JAAS: no {@code
 * LDAPLoginModuleConfiguration.class} — the 25 LDAP params live as strings in a map, discovered
 * from {@code LDAPLoginModule}'s {@code ConfigKey} enum.
 *
 * <p>Because the IRBuilder walks the Java class graph via reflection, it only sees the base class
 * with its opaque map. The factory-specific params are invisible to reflection. This builder runs
 * <b>after</b> the IR is built to create synthetic ClassNodes that reconstruct the type information
 * from convention ({@code *_PROP_NAME} constants, {@code ConfigKey} enums) rather than from the
 * type system.
 *
 * <h3>What each variant node contains</h3>
 *
 * <ul>
 *   <li>All base class properties copied from the reflected ClassNode
 *   <li>The discriminator field locked to a {@code const} + {@code default} + {@code required}
 *   <li>The params field enriched with implementation-specific property names from the registry
 * </ul>
 *
 * <p>Subclasses define which target class triggers variant building, which fields are the
 * discriminator and params, and how to filter the factory registry for relevant implementations.
 *
 * @see TransportFactoryVariantBuilder
 * @see LoginModuleVariantBuilder
 */
public abstract class FactoryVariantBuilder {

   private static final Logger LOG = LoggerFactory.getLogger(FactoryVariantBuilder.class);

   protected final SchemaIR ir;
   protected final FactoryParameterRegistry factoryRegistry;

   protected FactoryVariantBuilder(SchemaIR ir, FactoryParameterRegistry factoryRegistry) {
      this.ir = ir;
      this.factoryRegistry = factoryRegistry;
   }

   /**
    * Create all variant builders, performing factory discovery once.
    *
    * @param ir the IR graph to populate with synthetic variant nodes
    * @param artemisRoot path to Artemis source root (for factory discovery)
    * @return list of variant builders ready to use, or empty list if discovery fails
    */
   public static List<FactoryVariantBuilder> createAll(SchemaIR ir, Path artemisRoot) {
      FactoryDiscovery discovery = new FactoryDiscovery();
      FactoryParameterRegistry registry;
      try {
         registry = discovery.extractRegistry(artemisRoot);
      } catch (Exception e) {
         LOG.warn(
               "Factory discovery failed, schema will lack factory-specific params: {}", e.getMessage());
         return List.of();
      }

      if (registry == null) {
         return List.of();
      }

      LOG.info(
            "Discovered {} factories with {} total parameters",
            registry.getFactoryCount(),
            registry.getTotalParameterCount());

      return List.of(
            new TransportFactoryVariantBuilder(ir, registry),
            new LoginModuleVariantBuilder(ir, registry));
   }

   /** Fully qualified name of the class this builder handles. */
   protected abstract String getTargetClassName();

   /** Property name used as the oneOf discriminator (e.g. "factoryClassName"). */
   protected abstract String getDiscriminatorField();

   /** Property name holding implementation-specific key-value params. */
   protected abstract String getParamsField();

   /**
    * Filter the full factory list to only those relevant for the given property context.
    *
    * @param propertyName the leaf property name where this map appears
    * @return filtered factory class names
    */
   protected abstract List<String> filterFactories(String propertyName);

   /**
    * Scan the built IR for property nodes targeting this builder's class, and create synthetic
    * variant nodes for each.
    *
    * <p>This runs as pass 2 after the full IR is built, so base ClassNodes are fully populated and
    * can be copied into variants.
    *
    * @param ir the fully populated IR graph to scan
    */
   public void buildVariants(SchemaIR ir) {
      if (factoryRegistry == null) {
         return;
      }

      String targetClass = getTargetClassName();
      ir.markAsFactoryBase(targetClass);

      // Snapshot: iterate a copy since createVariantNode adds nodes to the IR
      for (SchemaIR.ClassNode classNode : List.copyOf(ir.getAllNodes())) {
         for (Map.Entry<String, SchemaIR.PropertyNode> entry : classNode.getProperties().entrySet()) {
            SchemaIR.PropertyNode propNode = entry.getValue();

            if (!targetClass.equals(propNode.getTargetClassName())) {
               continue;
            }

            Location location = propNode.getLocation();
            if (location == null) {
               continue;
            }

            String propertyName = location.leafName();
            List<String> factories = filterFactories(propertyName);
            SchemaIR.ClassNode baseNode = ir.getOrCreateNode(targetClass);

            for (String factoryClassName : factories) {
               List<String> params = factoryRegistry.getParameters(factoryClassName);
               propNode.addFactoryVariant(factoryClassName, params);
               createVariantNode(factoryClassName, params, baseNode, location.wildcard());
            }
         }
      }
   }

   /**
    * Create a synthetic ClassNode for one factory variant. Copies base class properties, locks the
    * discriminator to a const, and injects factory-specific params into the params field.
    */
   private void createVariantNode(
         String factoryClassName,
         List<String> factoryParams,
         SchemaIR.ClassNode baseNode,
         Location contextLocation) {
      String simpleName = getSimpleClassName(factoryClassName);
      SchemaIR.ClassNode variantNode = ir.getOrCreateNode(simpleName);

      variantNode.getClassMetadata().setDescription("Configuration for " + simpleName);
      variantNode.getClassMetadata().setFactoryVariant(true);
      variantNode.getClassMetadata().setContextPath(contextLocation.toDotted());
      variantNode.getClassMetadata().setContextPath(contextLocation.toDotted());

      String discriminator = getDiscriminatorField();
      String paramsField = getParamsField();

      for (Map.Entry<String, SchemaIR.PropertyNode> baseProp : baseNode.getProperties().entrySet()) {
         String propName = baseProp.getKey();
         SchemaIR.PropertyNode basePropNode = baseProp.getValue();
         SchemaIR.PropertyNode variantProp = variantNode.getOrCreateProperty(propName);

         if (propName.equals(discriminator)) {
            variantProp.setSchemaField("const", factoryClassName);
            variantProp.setSchemaField("default", factoryClassName);
            variantProp.setSchemaType(new SchemaType(SchemaType.Kind.STRING));
            variantProp.setSchemaField("x-access", "RW");
            variantNode.setRequired(List.of(discriminator));
         } else if (propName.equals(paramsField)) {
            for (Map.Entry<String, Object> schemaEntry : basePropNode.getSchema().entrySet()) {
               variantProp.setSchemaField(schemaEntry.getKey(), schemaEntry.getValue());
            }
            variantProp.setPropertyType(SchemaIR.PropertyType.PRIMITIVE);

            Map<String, Object> paramProperties = new LinkedHashMap<>();
            for (String paramName : factoryParams) {
               Map<String, Object> paramSchema = new LinkedHashMap<>();
               paramSchema.put("type", new SchemaType(SchemaType.Kind.STRING).toSchemaValue());
               paramSchema.put("x-access", "RW");
               paramProperties.put(paramName, paramSchema);
            }
            variantProp.setSchemaField("properties", paramProperties);
         } else {
            for (Map.Entry<String, Object> schemaEntry : basePropNode.getSchema().entrySet()) {
               variantProp.setSchemaField(schemaEntry.getKey(), schemaEntry.getValue());
            }
            if (basePropNode.getTargetClassName() != null) {
               variantProp.setTargetClassName(basePropNode.getTargetClassName());
            }
            variantProp.setPropertyType(basePropNode.getPropertyType());
         }
      }

      ir.recordUsage(simpleName, "factory-variant");
      ir.recordUsage(simpleName, "factory-variant-extracted");
   }

   protected static String getSimpleClassName(String fullClassName) {
      int lastDot = fullClassName.lastIndexOf('.');
      return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
   }
}
