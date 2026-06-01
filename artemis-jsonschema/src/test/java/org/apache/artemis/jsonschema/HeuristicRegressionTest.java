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

package org.apache.artemis.jsonschema;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.artemis.jsonschema.annotation.Heuristic;
import org.apache.artemis.jsonschema.enrichment.*;
import org.apache.artemis.jsonschema.factories.FactoryVariantBuilder;
import org.apache.artemis.jsonschema.ir.IRBuilder;
import org.apache.artemis.jsonschema.ir.PolymorphismResolver;
import org.apache.artemis.jsonschema.ir.SchemaIR;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

/**
 * Regression test for @Heuristic-annotated methods. Verifies that: 1. All heuristic-based
 * extractors produce non-trivial output against the current source tree 2.
 * Every @Heuristic-annotated method has corresponding coverage in this test
 */
public class HeuristicRegressionTest {

   private static final Set<String> TESTED_HEURISTICS = new HashSet<>();

   private Path findArtemisRoot() {
      Path candidate = Paths.get("").toAbsolutePath().getParent();
      if (Files.exists(candidate.resolve("artemis-server/src/main/java"))) {
         return candidate;
      }
      return null;
   }

   @Test
   public void allHeuristicsAreCoveredByThisTest() {
      Reflections reflections =
            new Reflections(
                  new ConfigurationBuilder()
                        .forPackages("org.apache.artemis.jsonschema")
                        .setScanners(Scanners.MethodsAnnotated));

      Set<Method> heuristicMethods = reflections.getMethodsAnnotatedWith(Heuristic.class);

      Set<String> annotatedMethodNames = new HashSet<>();
      for (Method m : heuristicMethods) {
         annotatedMethodNames.add(m.getDeclaringClass().getSimpleName() + "." + m.getName());
      }

      Set<String> untested = new HashSet<>(annotatedMethodNames);
      untested.removeAll(TESTED_HEURISTICS);

      assertTrue(
            untested.isEmpty(),
            "The following @Heuristic methods have no regression coverage: " + untested);
   }

   @Test
   public void polymorphismResolverFindsSubclasses() throws Exception {
      TESTED_HEURISTICS.add("PolymorphismResolver.findSubclasses");

      PolymorphismResolver resolver = new PolymorphismResolver(new SchemaIR());
      List<Class<?>> subclasses =
            resolver.findSubclasses(
                  org.apache
                        .activemq
                        .artemis
                        .core
                        .config
                        .amqpBrokerConnectivity
                        .AMQPBrokerConnectionElement
                        .class);

      assertFalse(
            subclasses.isEmpty(), "findSubclasses should discover AMQP subclasses under core.config");
      assertTrue(
            subclasses.size() >= 4, "Expected at least 4 AMQP subclasses, got " + subclasses.size());
   }

   @Test
   public void factoryFilterProducesCorrectVariants() throws Exception {
      TESTED_HEURISTICS.add("PolymorphismResolver.filterAcceptorOrConnectorFactories");

      Path artemisRoot = findArtemisRoot();
      if (artemisRoot == null) {
         return;
      }

      IRBuilder irGenerator = new IRBuilder();
      irGenerator.generateIR();
      SchemaIR ir = irGenerator.getIR();

      for (FactoryVariantBuilder builder : FactoryVariantBuilder.createAll(ir, artemisRoot)) {
         builder.buildVariants(ir);
      }

      SchemaIR.ClassNode root =
            ir.getOrCreateNode(
                  org.apache.activemq.artemis.core.config.impl.ConfigurationImpl.class.getName());
      SchemaIR.PropertyNode acceptorProp = root.getProperties().get("acceptorConfigurations");

      assertNotNull(acceptorProp, "acceptorConfigurations property should exist");
      assertFalse(
            acceptorProp.getFactoryVariants().isEmpty(),
            "Should produce factory variants for acceptors");

      for (String factoryClass : acceptorProp.getFactoryVariants().keySet()) {
         assertTrue(
               factoryClass.contains("Acceptor"),
               "Acceptor context should only contain Acceptor factories, found: " + factoryClass);
         assertFalse(
               factoryClass.contains("Connector"),
               "Acceptor context should NOT contain Connector factories, found: " + factoryClass);
      }
   }
}
