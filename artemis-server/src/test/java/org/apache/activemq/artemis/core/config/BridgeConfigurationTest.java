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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.junit.jupiter.api.Test;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonValue;

import java.io.StringReader;
import java.util.Set;

public class BridgeConfigurationTest {

   @Test
   public void testFromJSON() {
      String jsonString = createFullJsonObject().toString();

      BridgeConfiguration bridgeConfiguration = BridgeConfiguration.fromJSON(jsonString);

      assertNotNull(bridgeConfiguration);
      assertEquals("name", bridgeConfiguration.getName());
      assertEquals("queue-name", bridgeConfiguration.getQueueName());
      assertEquals("forwarding-address", bridgeConfiguration.getForwardingAddress());
      assertEquals("filter-string", bridgeConfiguration.getFilterString());
      assertArrayEquals(new String[]{"connector1", "connector2"},
            bridgeConfiguration.getStaticConnectors().toArray());
      assertEquals("dg", bridgeConfiguration.getDiscoveryGroupName());
      assertTrue(bridgeConfiguration.isHA());
      assertEquals("ClassName", bridgeConfiguration.getTransformerConfiguration().getClassName());
      Set keys = bridgeConfiguration.getTransformerConfiguration().getProperties().keySet();
      assertTrue(keys.contains("prop1"), keys + " doesn't contain prop1");
      assertTrue(keys.contains("prop2"), keys + " doesn't contain prop2");
      assertEquals("val1", bridgeConfiguration.getTransformerConfiguration().getProperties().get("prop1"));
      assertEquals("val2", bridgeConfiguration.getTransformerConfiguration().getProperties().get("prop2"));
      assertEquals(1, bridgeConfiguration.getRetryInterval());
      assertEquals(2.0, bridgeConfiguration.getRetryIntervalMultiplier(), 0);
      assertEquals(3, bridgeConfiguration.getInitialConnectAttempts());
      assertEquals(4, bridgeConfiguration.getReconnectAttempts());
      assertEquals(5, bridgeConfiguration.getReconnectAttemptsOnSameNode());
      assertTrue(bridgeConfiguration.isUseDuplicateDetection());
      assertEquals(6, bridgeConfiguration.getConfirmationWindowSize());
      assertEquals(7, bridgeConfiguration.getProducerWindowSize());
      assertEquals(8, bridgeConfiguration.getClientFailureCheckPeriod());
      assertEquals("user", bridgeConfiguration.getUser());
      assertEquals("password", bridgeConfiguration.getPassword());
      assertEquals(9, bridgeConfiguration.getConnectionTTL());
      assertEquals(10, bridgeConfiguration.getMaxRetryInterval());
      assertEquals(11, bridgeConfiguration.getMinLargeMessageSize());
      assertEquals(12, bridgeConfiguration.getCallTimeout());
      assertEquals(ComponentConfigurationRoutingType.MULTICAST, bridgeConfiguration.getRoutingType());
      assertEquals(1, bridgeConfiguration.getConcurrency());
      assertEquals(321, bridgeConfiguration.getPendingAckTimeout());
      assertEquals("myClientID", bridgeConfiguration.getClientId());
   }

   @Test
   public void testToJSON() {
      // create bc instance from a JSON object, all attributes are set
      JsonObject jsonObject = createFullJsonObject();
      BridgeConfiguration bridgeConfiguration = BridgeConfiguration.fromJSON(jsonObject.toString());
      assertNotNull(bridgeConfiguration);

      // serialize it back to JSON
      String serializedBridgeConfiguration = bridgeConfiguration.toJSON();
      JsonObject serializedBridgeConfigurationJsonObject = JsonLoader.readObject(new StringReader(serializedBridgeConfiguration));

      // verify that the original JSON object is identical to the one serialized via the toJSON() method
      assertEquals(jsonObject, serializedBridgeConfigurationJsonObject);
   }

   @Test
   public void testDefaultsToJson() {
      // create and serialize BridgeConfiguration instance without modifying any fields
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration();
      String jsonString = bridgeConfiguration.toJSON();
      JsonObject jsonObject = JsonLoader.readObject(new StringReader(jsonString));

      // the serialized JSON string should contain default values of primitive type fields
      assertEquals("2000", jsonObject.get(BridgeConfiguration.RETRY_INTERVAL).toString());
      assertEquals("1.0", jsonObject.get(BridgeConfiguration.RETRY_INTERVAL_MULTIPLIER).toString());
      assertEquals("-1", jsonObject.get(BridgeConfiguration.INITIAL_CONNECT_ATTEMPTS).toString());
      assertEquals("-1", jsonObject.get(BridgeConfiguration.RECONNECT_ATTEMPTS).toString());
      assertEquals("10", jsonObject.get(BridgeConfiguration.RECONNECT_ATTEMPTS_ON_SAME_NODE).toString());
      assertEquals("true", jsonObject.get(BridgeConfiguration.USE_DUPLICATE_DETECTION).toString());
      assertEquals("10485760", jsonObject.get(BridgeConfiguration.CONFIRMATION_WINDOW_SIZE).toString());
      assertEquals(Integer.toString(ActiveMQDefaultConfiguration.getDefaultBridgeProducerWindowSize()), jsonObject.get(BridgeConfiguration.PRODUCER_WINDOW_SIZE).toString());

      assertEquals("30000", jsonObject.get(BridgeConfiguration.CLIENT_FAILURE_CHECK_PERIOD).toString());
      assertEquals("2000", jsonObject.get(BridgeConfiguration.MAX_RETRY_INTERVAL).toString());
      assertEquals("102400", jsonObject.get(BridgeConfiguration.MIN_LARGE_MESSAGE_SIZE).toString());
      assertEquals("30000", jsonObject.get(BridgeConfiguration.CALL_TIMEOUT).toString());
      assertEquals("1", jsonObject.get(BridgeConfiguration.CONCURRENCY).toString());
      assertEquals("60000", jsonObject.get(BridgeConfiguration.PENDING_ACK_TIMEOUT).toString());

      // also should contain default non-null values of string fields
      assertEquals("\"ACTIVEMQ.CLUSTER.ADMIN.USER\"", jsonObject.get(BridgeConfiguration.USER).toString());
      assertEquals("\"CHANGE ME!!\"", jsonObject.get(BridgeConfiguration.PASSWORD).toString());
   }

   @Test
   public void testDefaultsFromJson() {
      // create BridgeConfiguration instance from empty JSON string
      final String jsonString = "{\"name\": \"name\"}"; // name field is required
      BridgeConfiguration deserializedConfiguration = BridgeConfiguration.fromJSON(jsonString);
      assertNotNull(deserializedConfiguration);

      // the deserialized object should return the same default values as a newly instantiated object
      assertEquals(new BridgeConfiguration("name"), deserializedConfiguration);
   }

   @Test
   public void testNullableFieldsFromJson() {
      // set string fields which default value is not null to null
      JsonObjectBuilder builder = JsonLoader.createObjectBuilder();
      builder.add(BridgeConfiguration.NAME, "name"); // required field
      builder.addNull(BridgeConfiguration.USER);
      builder.addNull(BridgeConfiguration.PASSWORD);

      BridgeConfiguration configuration = BridgeConfiguration.fromJSON(builder.build().toString());

      // in deserialized object the fields should still remain null
      assertNotNull(configuration);
      assertEquals("name", configuration.getName());
      assertNull(configuration.getUser());
      assertNull(configuration.getPassword());
   }

   @Test
   public void testNullableFieldsToJson() {
      // set string fields which default value is not null to null
      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration("name");
      bridgeConfiguration.setUser(null);
      bridgeConfiguration.setPassword(null);

      String jsonString = bridgeConfiguration.toJSON();
      JsonObject jsonObject = JsonLoader.readObject(new StringReader(jsonString));

      // after serialization the fields value should remain null
      assertEquals(JsonValue.ValueType.NULL, jsonObject.get(BridgeConfiguration.USER).getValueType());
      assertEquals(JsonValue.ValueType.NULL, jsonObject.get(BridgeConfiguration.PASSWORD).getValueType());
   }

   private static JsonObject createFullJsonObject() {
      JsonObjectBuilder objectBuilder = JsonLoader.createObjectBuilder();

      objectBuilder.add(BridgeConfiguration.NAME, "name");
      objectBuilder.add(BridgeConfiguration.QUEUE_NAME, "queue-name");
      objectBuilder.add(BridgeConfiguration.FORWARDING_ADDRESS, "forwarding-address");
      objectBuilder.add(BridgeConfiguration.FILTER_STRING, "filter-string");
      objectBuilder.add(BridgeConfiguration.STATIC_CONNECTORS,
            JsonLoader.createArrayBuilder()
                  .add("connector1")
                  .add("connector2"));
      objectBuilder.add(BridgeConfiguration.DISCOVERY_GROUP_NAME, "dg");
      objectBuilder.add(BridgeConfiguration.HA, true);
      objectBuilder.add(BridgeConfiguration.TRANSFORMER_CONFIGURATION,
            JsonLoader.createObjectBuilder()
                  .add("class-name", "ClassName")
                  .add("properties",
                        JsonLoader.createObjectBuilder()
                              .add("prop1", "val1")
                              .add("prop2", "val2")));
      objectBuilder.add(BridgeConfiguration.RETRY_INTERVAL, 1);
      objectBuilder.add(BridgeConfiguration.RETRY_INTERVAL_MULTIPLIER, 2.0);
      objectBuilder.add(BridgeConfiguration.INITIAL_CONNECT_ATTEMPTS, 3);
      objectBuilder.add(BridgeConfiguration.RECONNECT_ATTEMPTS, 4);
      objectBuilder.add(BridgeConfiguration.RECONNECT_ATTEMPTS_ON_SAME_NODE, 5);
      objectBuilder.add(BridgeConfiguration.USE_DUPLICATE_DETECTION, true);
      objectBuilder.add(BridgeConfiguration.CONFIRMATION_WINDOW_SIZE, 6);
      objectBuilder.add(BridgeConfiguration.PRODUCER_WINDOW_SIZE, 7);
      objectBuilder.add(BridgeConfiguration.CLIENT_FAILURE_CHECK_PERIOD, 8);
      objectBuilder.add(BridgeConfiguration.USER, "user");
      objectBuilder.add(BridgeConfiguration.PASSWORD, "password");
      objectBuilder.add(BridgeConfiguration.CONNECTION_TTL, 9);
      objectBuilder.add(BridgeConfiguration.MAX_RETRY_INTERVAL, 10);
      objectBuilder.add(BridgeConfiguration.MIN_LARGE_MESSAGE_SIZE, 11);
      objectBuilder.add(BridgeConfiguration.CALL_TIMEOUT, 12);
      objectBuilder.add(BridgeConfiguration.ROUTING_TYPE, "MULTICAST");
      objectBuilder.add(BridgeConfiguration.CONCURRENCY, 1);
      objectBuilder.add(BridgeConfiguration.CONFIGURATION_MANAGED, true);
      objectBuilder.add(BridgeConfiguration.PENDING_ACK_TIMEOUT, 321);
      objectBuilder.add(BridgeConfiguration.CLIENT_ID, "myClientID");

      return objectBuilder.build();
   }
}
