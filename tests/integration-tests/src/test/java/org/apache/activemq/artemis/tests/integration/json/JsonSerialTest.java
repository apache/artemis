/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.json;

import javax.management.openmbean.CompositeData;

import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.reader.TextMessageUtil;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Base64;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonSerialTest extends ActiveMQTestBase {

   private static final int NON_RELATED_FAILURE = -1;
   private static final int DESERIALIZED = 1;
   private static final int DESERIALIZED_CCE = 2;
   private static final int REJECTED_SERIALIZATION = 3;

   @Test
   public void testCoreCompositeData() throws Exception {
      CoreMessage message = new CoreMessage();
      message.initBuffer(1024);
      message.putStringProperty("propKey", "propValue");
      message.putIntProperty("propInt", 1);
      message.putCharProperty("propChar", 'a');
      message.putBytesProperty("propBytes", RandomUtil.randomBytes());
      message.putByteProperty("propByte", RandomUtil.randomByte());
      message.putShortProperty("propShort", RandomUtil.randomShort());
      message.putDoubleProperty("propDouble", RandomUtil.randomDouble());
      message.putFloatProperty("propFloat", RandomUtil.randomFloat());
      message.putBooleanProperty("propBoolean", RandomUtil.randomBoolean());
      message.putLongProperty("propLong", RandomUtil.randomLong());
      message.putStringProperty(SimpleString.of("propSimpleString"), RandomUtil.randomUUIDSimpleString());

      TextMessageUtil.writeBodyText(message.getBodyBuffer(), SimpleString.of("hello"));

      internalMessageTest(message);
   }

   @Test
   public void testAMQPCompositeData() throws Exception {
      org.apache.qpid.proton.message.impl.MessageImpl protonMessage = (org.apache.qpid.proton.message.impl.MessageImpl) Proton.message();

      Header header = new Header();
      header.setDurable(true);
      protonMessage.setHeader(header);

      Properties properties = new Properties();
      properties.setMessageId(UUID.randomUUID());
      properties.setTo("testAddress");
      properties.setCreationTime(new Date(System.currentTimeMillis()));
      protonMessage.setProperties(properties);

      ApplicationProperties applicationProperties = new ApplicationProperties(new LinkedHashMap<>());
      applicationProperties.getValue().put("propKey", "propValue");
      applicationProperties.getValue().put("propInt", 1);
      applicationProperties.getValue().put("propByte", RandomUtil.randomByte());
      applicationProperties.getValue().put("propShort", RandomUtil.randomShort());
      applicationProperties.getValue().put("propDouble", RandomUtil.randomDouble());
      applicationProperties.getValue().put("propFloat", RandomUtil.randomFloat());
      applicationProperties.getValue().put("propBoolean", RandomUtil.randomBoolean());
      applicationProperties.getValue().put("propLong", RandomUtil.randomLong());
      protonMessage.setApplicationProperties(applicationProperties);

      protonMessage.setBody(new AmqpValue("hello"));

      ByteBuf nettyBuffer = Unpooled.buffer(10 * 1024);
      protonMessage.encode(new NettyWritable(nettyBuffer));
      byte[] encodedMessage = new byte[nettyBuffer.writerIndex()];
      nettyBuffer.readBytes(encodedMessage);

      AMQPStandardMessage message = new AMQPStandardMessage(0, encodedMessage, null);

      internalMessageTest(message);
   }

   private void internalMessageTest(Message message) throws Exception {
      CompositeData data = message.toCompositeData(10, 1);
      JsonArrayBuilder builder = JsonLoader.createArrayBuilder();
      JsonUtil.addToArray(new CompositeData[] {data}, builder);

      String json = builder.build().toString();

      JsonArray arrayOutput = JsonUtil.readJsonArray(json);
      Object[] output = JsonUtil.fromJsonArray(arrayOutput);
      HashMap<String, Object> objectHashMap = (HashMap<String, Object>) output[0];
      CompositeData[] compositeDataOutput = (CompositeData[]) objectHashMap.get("javax.management.openmbean.CompositeData");
      assertEquals(1, compositeDataOutput.length);
      assertEquals(data, compositeDataOutput[0]);
      assertThrows(ActiveMQException.class, () -> JsonUtil.fromJsonArray(arrayOutput, false));

      {
         // this portion is to validate a very restrictive filter would REJECT everything
         Process process = SpawnedVMSupport.spawnVM(JsonSerialTest.class.getName(), new String[]{"-Dartemis.json.composite.data.serial.filter=!*"}, true, json);
         assertTrue(process.waitFor(10, TimeUnit.SECONDS));
         assertEquals(REJECTED_SERIALIZATION, process.exitValue(), "Expected to reject deserialization");
      }

      {
         Process process = SpawnedVMSupport.spawnVM(JsonSerialTest.class.getName(), json);
         assertTrue(process.waitFor(10, TimeUnit.SECONDS));
         assertEquals(DESERIALIZED, process.exitValue(), "Expected to allow deserialization");
      }
   }

   @Test
   public void testInvalidDataByDepth() throws Exception {
      int n = 10;
      final ArrayList<Object> top = new ArrayList<>();
      ArrayList<Object> current = top;
      for (int i = 0; i < n; i++) {
         ArrayList<Object> sub = new ArrayList<>();
         current.add(sub);
         current.add("top" + i);
         sub.add("a" + i);
         current = sub;
      }

      // passing in invalidData to exploit serialization
      // if serialization is successful this will cause ClassCastException on JsonUtil
      internalInvalidDataTest(createCompositeData(top));
   }

   @Test
   public void testFilteredOutClass() throws Exception {
      // passing in invalidData to exploit serialization
      // if serialization is successful this will cause ClassCastException on JsonUtil
      internalInvalidDataTest(createCompositeData(new HashSet<>()));
   }

   private static String createCompositeData(Object payload) throws Exception {
      // Serialize the payload to base64
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(payload);
      oos.flush();
      String serializedPayload = Base64.encodeBytes(baos.toByteArray());

      return "[{\"javax.management.openmbean.CompositeData\":[\"" + serializedPayload + "\"]}]";
   }

   private static void internalInvalidDataTest(String json) throws Exception {
      {
         Process process = SpawnedVMSupport.spawnVM(JsonSerialTest.class.getName(), true, json);
         assertTrue(process.waitFor(10, TimeUnit.SECONDS));
         assertEquals(REJECTED_SERIALIZATION, process.exitValue(), "Expected to reject deserialization");
      }

      {
         // Positive test allowing everything, to validate the test infrastructure itself.
         Process process = SpawnedVMSupport.spawnVM(JsonSerialTest.class.getName(), new String[]{"-Dartemis.json.composite.data.serial.filter=*"}, true, json);
         assertTrue(process.waitFor(10, TimeUnit.SECONDS));
         assertEquals(DESERIALIZED_CCE, process.exitValue(), "Expected to allow deserialization");
      }
   }

   public static void main(String[] arg) {
      try {
         String json = arg[0];
         JsonArray arrayOutput = JsonUtil.readJsonArray(json);
         Object[] output = JsonUtil.fromJsonArray(arrayOutput);
         assertNotNull(output);
         System.exit(DESERIALIZED);
      } catch (InvalidClassException e) {
         e.printStackTrace(System.out);
         System.exit(REJECTED_SERIALIZATION);
      } catch (ClassCastException e) {
         // if I got ClassCastException, it means deserialization was allowed to happen
         e.printStackTrace(System.out);
         System.exit(DESERIALIZED_CCE);
      } catch (Throwable e) {
         e.printStackTrace(System.out);
         System.exit(NON_RELATED_FAILURE);
      }
   }
}