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
package org.apache.activemq.artemis.core.config;

import java.io.StringReader;
import java.util.Set;

import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DivertConfigurationTest {

   @Test
   public void testFromJSON() {
      String jsonString = createFullJsonObject().toString();

      DivertConfiguration divertConfiguration = DivertConfiguration.fromJSON(jsonString);

      assertNotNull(divertConfiguration);
      assertEquals("name", divertConfiguration.getName());
      assertEquals("forwarding-address", divertConfiguration.getForwardingAddress());
      assertEquals("filter-string", divertConfiguration.getFilterString());
      assertEquals("ClassName", divertConfiguration.getTransformerConfiguration().getClassName());
      Set keys = divertConfiguration.getTransformerConfiguration().getProperties().keySet();
      assertTrue(keys.contains("prop1"), keys + " doesn't contain prop1");
      assertTrue(keys.contains("prop2"), keys + " doesn't contain prop2");
      assertEquals("val1", divertConfiguration.getTransformerConfiguration().getProperties().get("prop1"));
      assertEquals("val2", divertConfiguration.getTransformerConfiguration().getProperties().get("prop2"));
      assertEquals(ComponentConfigurationRoutingType.MULTICAST, divertConfiguration.getRoutingType());
   }

   @Test
   public void testToJSON() {
      // create bc instance from a JSON object, all attributes are set
      JsonObject jsonObject = createFullJsonObject();
      DivertConfiguration divertConfiguration = DivertConfiguration.fromJSON(jsonObject.toString());
      assertNotNull(divertConfiguration);

      // serialize it back to JSON
      String serializedDivertConfiguration = divertConfiguration.toJSON();
      JsonObject serializedDivertConfigurationJsonObject = JsonLoader.readObject(new StringReader(serializedDivertConfiguration));

      // verify that the original JSON object is identical to the one serialized via the toJSON() method
      assertEquals(jsonObject, serializedDivertConfigurationJsonObject);
   }

   private static JsonObject createFullJsonObject() {
      JsonObjectBuilder objectBuilder = JsonLoader.createObjectBuilder();

      objectBuilder.add(DivertConfiguration.NAME, "name");
      objectBuilder.add(DivertConfiguration.ROUTING_NAME, "routing-name");
      objectBuilder.add(DivertConfiguration.ADDRESS, "address");
      objectBuilder.add(DivertConfiguration.FORWARDING_ADDRESS, "forwarding-address");
      objectBuilder.add(DivertConfiguration.EXCLUSIVE, false);
      objectBuilder.add(DivertConfiguration.FILTER_STRING, "filter-string");
      objectBuilder.add(DivertConfiguration.TRANSFORMER_CONFIGURATION,
            JsonLoader.createObjectBuilder()
                  .add("class-name", "ClassName")
                  .add("properties",
                        JsonLoader.createObjectBuilder()
                              .add("prop1", "val1")
                              .add("prop2", "val2")));
      objectBuilder.add(DivertConfiguration.ROUTING_TYPE, "MULTICAST");

      return objectBuilder.build();
   }
}
