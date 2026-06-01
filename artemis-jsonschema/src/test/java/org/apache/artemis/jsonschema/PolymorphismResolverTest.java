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

import java.util.List;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.artemis.jsonschema.ir.PolymorphismResolver;
import org.apache.artemis.jsonschema.ir.SchemaIR;
import org.junit.jupiter.api.Test;

public class PolymorphismResolverTest {

   private final PolymorphismResolver resolver = new PolymorphismResolver(new SchemaIR());

   @Test
   public void findSubclassesReturnsNonEmptyForKnownPolymorphicClass() {
      List<Class<?>> subclasses = resolver.findSubclasses(AMQPBrokerConnectionElement.class);

      assertFalse(
            subclasses.isEmpty(), "AMQPBrokerConnectionElement should have concrete subclasses");

      for (int i = 1; i < subclasses.size(); i++) {
         assertTrue(
               subclasses.get(i - 1).getSimpleName().compareTo(subclasses.get(i).getSimpleName()) <= 0,
               "Subclasses should be sorted by simple name");
      }

      for (Class<?> sub : subclasses) {
         assertTrue(AMQPBrokerConnectionElement.class.isAssignableFrom(sub));
         assertFalse(java.lang.reflect.Modifier.isAbstract(sub.getModifiers()));
      }
   }

   @Test
   public void findSubclassesReturnsEmptyForClassOutsideArtemisConfig() {
      List<Class<?>> subclasses = resolver.findSubclasses(java.util.List.class);

      assertTrue(subclasses.isEmpty());
   }

   @Test
   public void findSubclassesReturnsEmptyForObjectClass() {
      List<Class<?>> subclasses = resolver.findSubclasses(Object.class);

      assertTrue(subclasses.isEmpty());
   }
}
