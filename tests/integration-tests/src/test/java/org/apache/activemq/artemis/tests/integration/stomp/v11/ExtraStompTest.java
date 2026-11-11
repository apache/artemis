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
package org.apache.activemq.artemis.tests.integration.stomp.v11;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.tests.integration.stomp.StompTestBase;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ExtraStompTest extends StompTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private StompClientConnection connV10;
   private StompClientConnection connV11;

   public ExtraStompTest() {
      super("tcp+v11.stomp");
   }

   @Override
   public boolean isPersistenceEnabled() {
      return true;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      URI v10Uri = new URI(uri.toString().replace("v11", "v10"));
      connV10 = StompClientConnectionFactory.createClientConnection(v10Uri);
      connV10.connect(defUser, defPass);

      connV11 = StompClientConnectionFactory.createClientConnection(uri);
      connV11.connect(defUser, defPass);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         connV10.disconnect();
         connV11.disconnect();
      } finally {
         super.tearDown();
      }
   }

   @Test
   public void testSendAndReceive10() throws Exception {
      testSendAndReceive(connV10);
   }

   @Test
   public void testSendAndReceive11() throws Exception {
      testSendAndReceive(connV11);
   }

   public void testSendAndReceive(StompClientConnection conn) throws Exception {
      String msg1 = "Hello World 1!";
      String msg2 = "Hello World 2!";

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND);
      frame.addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName());
      frame.addHeader(Stomp.Headers.CONTENT_LENGTH, String.valueOf(msg1.getBytes(StandardCharsets.UTF_8).length));
      frame.addHeader(Stomp.Headers.Send.PERSISTENT, Boolean.TRUE.toString());
      frame.setBody(msg1);

      conn.sendFrame(frame);

      ClientStompFrame frame2 = conn.createFrame(Stomp.Commands.SEND);
      frame2.addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName());
      frame2.addHeader(Stomp.Headers.CONTENT_LENGTH, String.valueOf(msg2.getBytes(StandardCharsets.UTF_8).length));
      frame2.addHeader(Stomp.Headers.Send.PERSISTENT, Boolean.TRUE.toString());
      frame2.setBody(msg2);

      conn.sendFrame(frame2);

      subscribe(conn, "a-sub");

      frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("a-sub", frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));
      assertNotNull(frame.getHeader(Stomp.Headers.Message.MESSAGE_ID));
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Subscribe.DESTINATION));
      assertEquals(msg1, frame.getBody());

      frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("a-sub", frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));
      assertNotNull(frame.getHeader(Stomp.Headers.Message.MESSAGE_ID));
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Subscribe.DESTINATION));
      assertEquals(msg2, frame.getBody());

      unsubscribe(conn, "a-sub");
   }

   @Test
   public void testNoGarbageAfterPersistentMessageV10() throws Exception {
      testNoGarbageAfterPersistentMessage(connV10);
   }

   @Test
   public void testNoGarbageAfterPersistentMessageV11() throws Exception {
      testNoGarbageAfterPersistentMessage(connV11);
   }

   public void testNoGarbageAfterPersistentMessage(StompClientConnection conn) throws Exception {
      subscribe(conn, "a-sub");

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND);
      frame.addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName());
      frame.addHeader(Stomp.Headers.CONTENT_LENGTH, "11");
      frame.addHeader(Stomp.Headers.Send.PERSISTENT, Boolean.TRUE.toString());
      frame.setBody("Hello World");

      conn.sendFrame(frame);

      frame = conn.createFrame(Stomp.Commands.SEND);
      frame.addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName());
      frame.addHeader(Stomp.Headers.CONTENT_LENGTH, "11");
      frame.addHeader(Stomp.Headers.Send.PERSISTENT, Boolean.TRUE.toString());
      frame.setBody("Hello World");

      conn.sendFrame(frame);

      frame = conn.receiveFrame(10000);

      assertEquals("Hello World", frame.getBody());

      //if activemq sends trailing garbage bytes, the second message
      //will not be normal
      frame = conn.receiveFrame(10000);

      assertEquals("Hello World", frame.getBody());

      unsubscribe(conn, "a-sub");
   }

   @Test
   public void testNoGarbageOnPersistentRedeliveryV10() throws Exception {
      testNoGarbageOnPersistentRedelivery(connV10);
   }

   @Test
   public void testNoGarbageOnPersistentRedeliveryV11() throws Exception {
      testNoGarbageOnPersistentRedelivery(connV11);
   }

   public void testNoGarbageOnPersistentRedelivery(StompClientConnection conn) throws Exception {
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND);
      frame.addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName());
      frame.addHeader(Stomp.Headers.CONTENT_LENGTH, "11");
      frame.addHeader(Stomp.Headers.Send.PERSISTENT, Boolean.TRUE.toString());
      frame.setBody("Hello World");

      conn.sendFrame(frame);

      frame = conn.createFrame(Stomp.Commands.SEND);
      frame.addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName());
      frame.addHeader(Stomp.Headers.CONTENT_LENGTH, "11");
      frame.addHeader(Stomp.Headers.Send.PERSISTENT, Boolean.TRUE.toString());
      frame.setBody("Hello World");

      conn.sendFrame(frame);

      frame = subscribe(conn, "a-sub", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      // receive but don't ack
      frame = conn.receiveFrame(10000);
      logger.debug("{}", frame);

      frame = conn.receiveFrame(10000);
      logger.debug("{}", frame);

      unsubscribe(conn, "a-sub");

      frame = subscribe(conn, "a-sub");

      frame = conn.receiveFrame(10000);

      //second receive will get problem if trailing bytes
      assertEquals("Hello World", frame.getBody());

      //unsub
      unsubscribe(conn, "a-sub");
   }

}
