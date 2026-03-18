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
package org.apache.activemq.artemis.tests.integration.stomp.v12;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.stomp.StompTestBase;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionV11;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionV12;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Testing Stomp version 1.2 functionalities
 */
public class StompV12Test extends StompTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String CLIENT_ID = "myclientid";

   private StompClientConnectionV12 conn;

   private URI v10Uri;

   private URI v11Uri;

   public StompV12Test() {
      super("tcp+v12.stomp");
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      v10Uri = new URI(uri.toString().replace("v12", "v10"));
      v11Uri = new URI(uri.toString().replace("v12", "v11"));
      conn = (StompClientConnectionV12) StompClientConnectionFactory.createClientConnection(uri);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         boolean connected = conn != null && conn.isConnected();
         logger.debug("Connection 1.2 : {}", connected);
         if (connected) {
            conn.disconnect();
         }
      } finally {
         super.tearDown();
         conn.closeTransport();
      }
   }

   @Test
   public void testSubscribeWithReceipt() throws Exception {
      conn.connect(defUser, defPass);

      Pattern p = Pattern.compile("receipt-id:.*\\nreceipt-id");
      assertFalse(p.matcher(subscribe(conn, null).toString()).find());

      conn.disconnect();
   }

   @Test
   public void testConnection() throws Exception {
      server.getSecurityStore().setSecurityEnabled(true);
      StompClientConnection connection = StompClientConnectionFactory.createClientConnection(v10Uri);

      connection.connect(defUser, defPass);

      assertTrue(connection.isConnected());

      assertEquals("1.0", connection.getVersion());

      connection.disconnect();

      connection = StompClientConnectionFactory.createClientConnection(uri);

      connection.connect(defUser, defPass);

      assertTrue(connection.isConnected());

      assertEquals("1.2", connection.getVersion());

      connection.disconnect();

      connection = StompClientConnectionFactory.createClientConnection(uri);

      connection.connect();

      assertFalse(connection.isConnected());

      //new way of connection
      StompClientConnectionV11 conn = (StompClientConnectionV11) StompClientConnectionFactory.createClientConnection(v11Uri);
      conn.connect1(defUser, defPass);

      assertTrue(conn.isConnected());

      conn.disconnect();
   }

   @Test
   public void testConnectionAsInSpec() throws Exception {
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(v10Uri);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.CONNECT);
      frame.addHeader(Stomp.Headers.Connect.LOGIN, this.defUser);
      frame.addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);
      frame.addHeader(Stomp.Headers.ACCEPT_VERSION, "1.2");
      frame.addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1");

      ClientStompFrame reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());
      assertEquals("1.2", reply.getHeader(Stomp.Headers.Error.VERSION));

      conn.disconnect();

      //need 1.2 client
      conn = StompClientConnectionFactory.createClientConnection(uri);

      frame = conn.createFrame(Stomp.Commands.STOMP);
      frame.addHeader(Stomp.Headers.Connect.LOGIN, this.defUser);
      frame.addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);
      frame.addHeader(Stomp.Headers.ACCEPT_VERSION, "1.2");
      frame.addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1");

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());
      assertEquals("1.2", reply.getHeader(Stomp.Headers.Error.VERSION));

      conn.disconnect();
   }

   @Test
   public void testNegotiation() throws Exception {
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(v10Uri);
      // case 1 accept-version absent. It is a 1.0 connect
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.CONNECT);
      frame.addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1");
      frame.addHeader(Stomp.Headers.Connect.LOGIN, this.defUser);
      frame.addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      ClientStompFrame reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      //reply headers: version, session, server
      assertNull(reply.getHeader(Stomp.Headers.Error.VERSION));

      conn.disconnect();

      // case 2 accept-version=1.0, result: 1.0
      conn = StompClientConnectionFactory.createClientConnection(v11Uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT);
      frame.addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0");
      frame.addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1");
      frame.addHeader(Stomp.Headers.Connect.LOGIN, this.defUser);
      frame.addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      //reply headers: version, session, server
      assertEquals("1.0", reply.getHeader(Stomp.Headers.Error.VERSION));

      conn.disconnect();

      // case 3 accept-version=1.1, result: 1.1
      conn = StompClientConnectionFactory.createClientConnection(v11Uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT);
      frame.addHeader(Stomp.Headers.ACCEPT_VERSION, "1.1");
      frame.addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1");
      frame.addHeader(Stomp.Headers.Connect.LOGIN, this.defUser);
      frame.addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      //reply headers: version, session, server
      assertEquals("1.1", reply.getHeader(Stomp.Headers.Error.VERSION));

      conn.disconnect();

      // case 4 accept-version=1.0,1.1,1.3, result 1.2
      conn = StompClientConnectionFactory.createClientConnection(v11Uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT);
      frame.addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0,1.1,1.3");
      frame.addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1");
      frame.addHeader(Stomp.Headers.Connect.LOGIN, this.defUser);
      frame.addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      //reply headers: version, session, server
      assertEquals("1.1", reply.getHeader(Stomp.Headers.Error.VERSION));

      conn.disconnect();

      // case 5 accept-version=1.3, result error
      conn = StompClientConnectionFactory.createClientConnection(v11Uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT);
      frame.addHeader(Stomp.Headers.ACCEPT_VERSION, "1.3");
      frame.addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1");
      frame.addHeader(Stomp.Headers.Connect.LOGIN, this.defUser);
      frame.addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.ERROR, reply.getCommand());

      conn.disconnect();

      logger.debug("Got error frame {}", reply);

   }

   @Test
   public void testSendAndReceive() throws Exception {
      conn.connect(defUser, defPass);

      ClientStompFrame response = send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World 1!");

      assertNull(response);

      send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World 2!", true);

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);
      subscribe(newConn, "a-sub");

      ClientStompFrame frame = newConn.receiveFrame();

      logger.debug("received {}", frame);

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      assertEquals("a-sub", frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));

      //'auto' ack mode doesn't require 'ack' header
      assertNull(frame.getHeader(Stomp.Headers.Message.ACK));

      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Subscribe.DESTINATION));

      assertEquals("Hello World 1!", frame.getBody());

      frame = newConn.receiveFrame();

      logger.debug("received {}", frame);

      //unsub
      unsubscribe(newConn, "a-sub");

      newConn.disconnect();
   }

   @Test
   public void testHeaderContentType() throws Exception {
      conn.connect(defUser, defPass);

      send(conn, getQueuePrefix() + getQueueName(), "application/xml", "Hello World 1!");

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(v11Uri);
      newConn.connect(defUser, defPass);
      subscribe(newConn, "a-sub");

      ClientStompFrame frame = newConn.receiveFrame();

      logger.debug("received {}", frame);

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      assertEquals("application/xml", frame.getHeader(Stomp.Headers.CONTENT_TYPE));

      //unsub
      unsubscribe(newConn, "a-sub");

      newConn.disconnect();
   }

   @Test
   public void testHeaderContentLength() throws Exception {
      conn.connect(defUser, defPass);

      String body = "Hello World 1!";
      String cLen = String.valueOf(body.getBytes(StandardCharsets.UTF_8).length);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.CONTENT_TYPE, "application/xml")
                                   .addHeader(Stomp.Headers.CONTENT_LENGTH, cLen)
                                   .setBody(body + "extra");

      conn.sendFrame(frame);

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);
      subscribe(newConn, "a-sub");

      frame = newConn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(body, frame.getBody());

      assertEquals(cLen, frame.getHeader(Stomp.Headers.CONTENT_LENGTH));

      //send again without content-length header
      frame = conn.createFrame(Stomp.Commands.SEND)
                  .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                  .addHeader(Stomp.Headers.CONTENT_TYPE, "application/xml")
                  .setBody(body + "extra");

      conn.sendFrame(frame);

      //receive again. extra should received.
      frame = newConn.receiveFrame();

      assertEquals(body + "extra", frame.getBody());

      //although sender didn't send the content-length header,
      //the server should add it anyway
      assertEquals((body + "extra").getBytes(StandardCharsets.UTF_8).length, Integer.valueOf(frame.getHeader(Stomp.Headers.CONTENT_LENGTH)).intValue());

      //unsub
      unsubscribe(newConn, "a-sub");

      newConn.disconnect();
   }

   //test that repetitive headers
   @Test
   public void testHeaderRepetitive() throws Exception {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAutoCreateQueues(false);
      addressSettings.setAutoCreateAddresses(false);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);

      conn.connect(defUser, defPass);

      String body = "Hello World!";
      String cLen = String.valueOf(body.getBytes(StandardCharsets.UTF_8).length);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, "aNonexistentQueue")
                                   .addHeader(Stomp.Headers.CONTENT_TYPE, "application/xml")
                                   .addHeader(Stomp.Headers.CONTENT_LENGTH, cLen)
                                   .addHeader("foo", "value1")
                                   .addHeader("foo", "value2")
                                   .addHeader("foo", "value3");

      frame.setBody(body);

      conn.sendFrame(frame);

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);
      subscribe(newConn, "a-sub", null, null, true);

      frame = newConn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(body, frame.getBody());

      logger.debug("received: {}", frame);
      assertEquals("value1", frame.getHeader("foo"));

      //unsub
      unsubscribe(newConn, "a-sub", true);

      newConn.disconnect();

      //should get error

      body = "Hello World!";
      cLen = String.valueOf(body.getBytes(StandardCharsets.UTF_8).length);

      frame = conn.createFrame(Stomp.Commands.SEND)
                  .addHeader(Stomp.Headers.Subscribe.DESTINATION, "aNonexistentQueue")
                  .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                  .addHeader(Stomp.Headers.CONTENT_TYPE, "application/xml")
                  .addHeader(Stomp.Headers.CONTENT_LENGTH, cLen)
                  .addHeader(Stomp.Headers.RECEIPT_REQUESTED, "1234")
                  .setBody(body);

      ClientStompFrame reply = conn.sendFrame(frame);
      // TODO this is broken because queue auto-creation is always on
      assertEquals(Stomp.Responses.ERROR, reply.getCommand());
   }

   //padding shouldn't be trimmed
   @Test
   public void testHeadersPadding() throws Exception {
      conn.connect(defUser, defPass);

      String body = "<p>Hello World!</p>";
      String cLen = String.valueOf(body.getBytes(StandardCharsets.UTF_8).length);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.CONTENT_TYPE, "application/xml")
                                   .addHeader(Stomp.Headers.CONTENT_LENGTH, cLen)
                                   .addHeader(" header1", "value1 ")
                                   .addHeader("  header2", "value2   ")
                                   .addHeader("header3 ", " value3")
                                   .addHeader(" header4 ", " value4 ")
                                   .addHeader(" header 5  ", " value 5 ")
                                   .addHeader("header6", "\t value\t 6 \t")
                                   .setBody(body);

      conn.sendFrame(frame);

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);
      subscribe(newConn, "a-sub");

      frame = newConn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(body, frame.getBody());

      logger.debug("received: {}", frame);
      assertNull(frame.getHeader("header1"));
      assertEquals("value1 ", frame.getHeader(" header1"));
      assertEquals("value2   ", frame.getHeader("  header2"));
      assertEquals(" value3", frame.getHeader("header3 "));
      assertEquals(" value4 ", frame.getHeader(" header4 "));
      assertEquals(" value 5 ", frame.getHeader(" header 5  "));
      assertEquals("\t value\t 6 \t", frame.getHeader("header6"));

      //unsub
      unsubscribe(newConn, "a-sub");

      newConn.disconnect();
   }

   /**
    * Since 1.2 the CR ('\r') needs to be escaped.
    */
   @Test
   public void testHeaderEncoding() throws Exception {
      conn.connect(defUser, defPass);
      String body = "Hello World 1!";
      String cLen = String.valueOf(body.getBytes(StandardCharsets.UTF_8).length);
      String hKey = "\\rspecial-header\\\\\\n\\c\\r\\n";
      String hVal = "\\c\\\\\\ngood\\n\\r";

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.CONTENT_TYPE, "application/xml")
                                   .addHeader(Stomp.Headers.CONTENT_LENGTH, cLen)
                                   .addHeader(hKey, hVal)
                                   .setBody(body);

      logger.debug("key: |{}| val: |{}|", hKey, hVal);

      conn.sendFrame(frame);

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);

      subscribe(newConn, "a-sub");

      frame = newConn.receiveFrame();

      logger.debug("received {}", frame);

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      String value = frame.getHeader("\r" + "special-header" + "\\" + "\n" + ":" + "\r\n");

      assertEquals(":" + "\\" + "\n" + "good\n\r", value);

      //unsub
      unsubscribe(newConn, "a-sub");

      newConn.disconnect();
   }

   /**
    * In 1.2, undefined escapes must cause a fatal protocol error.
    */
   @Test
   public void testHeaderUndefinedEscape() throws Exception {
      conn.connect(defUser, defPass);
      ClientStompFrame frame = conn.createFrame("SEND");

      String body = "Hello World 1!";
      String cLen = String.valueOf(body.getBytes(StandardCharsets.UTF_8).length);

      frame.addHeader("destination", getQueuePrefix() + getQueueName());
      frame.addHeader("content-type", "text/plain");
      frame.addHeader("content-length", cLen);
      String hKey = "undefined-escape";
      String hVal = "is\\ttab";
      frame.addHeader(hKey, hVal);

      logger.debug("key: |{}| val: |{}|", hKey, hVal);

      frame.setBody(body);

      conn.sendFrame(frame);

      ClientStompFrame error = conn.receiveFrame();

      logger.debug("received {}", error);

      String desc = "Should have received an ERROR for undefined escape sequence";
      assertNotNull(error, desc);
      assertEquals("ERROR", error.getCommand(), desc);

      waitDisconnect(conn);
      assertFalse(conn.isConnected(), "Should be disconnected in STOMP 1.2 after ERROR");
   }

   @Test
   public void testHeartBeat() throws Exception {
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      //no heart beat at all if heat-beat absent
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.CONNECT)
                                   .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                                   .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                                   .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      ClientStompFrame reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      Thread.sleep(5000);

      assertEquals(0, conn.getFrameQueueSize());

      conn.disconnect();

      //no heart beat for (0,0)
      conn = StompClientConnectionFactory.createClientConnection(uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT)
                  .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                  .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                  .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                  .addHeader(Stomp.Headers.Connect.HEART_BEAT, "0,0")
                  .addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0,1.1,1.2");

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("0,30000", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      Thread.sleep(5000);

      assertEquals(0, conn.getFrameQueueSize());

      conn.disconnect();

      //heart-beat (1,0), should receive a min client ping accepted by server
      conn = StompClientConnectionFactory.createClientConnection(uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT)
                  .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                  .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                  .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                  .addHeader(Stomp.Headers.Connect.HEART_BEAT, "1,0")
                  .addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0,1.2");

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("0,500", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      Thread.sleep(2000);

      //now server side should be disconnected because we didn't send ping for 2 sec
      //send will fail
      try {
         send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World");
         fail("connection should have been destroyed by now");
      } catch (IOException e) {
         //ignore
      }

      //heart-beat (1,0), start a ping, then send a message, should be ok.
      conn = StompClientConnectionFactory.createClientConnection(uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT)
                  .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                  .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                  .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                  .addHeader(Stomp.Headers.Connect.HEART_BEAT, "1,0")
                  .addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0,1.1,1.2");

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("0,500", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      conn.startPinger(500);

      Thread.sleep(2000);

      //send will be ok
      send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World");

      conn.stopPinger();

      conn.disconnect();
   }

   //server ping
   @Test
   public void testHeartBeat2() throws Exception {
      //heart-beat (1,1)
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.CONNECT)
                                   .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                                   .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                                   .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                                   .addHeader(Stomp.Headers.Connect.HEART_BEAT, "1,1")
                                   .addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0,1.1,1.2");

      ClientStompFrame reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());
      assertEquals("500,500", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      conn.disconnect();

      //heart-beat (500,1000)
      conn = (StompClientConnectionV12) StompClientConnectionFactory.createClientConnection(uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT)
                  .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                  .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                  .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                  .addHeader(Stomp.Headers.Connect.HEART_BEAT, "500,1000")
                  .addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0,1.1,1.2");

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("1000,500", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      conn.startPinger(500);

      Thread.sleep(10000);

      //now check the frame size
      int size = conn.getServerPingNumber();

      logger.debug("ping received: {}", size);

      assertTrue(size > 5, "size: " + size);

      //now server side should be disconnected because we didn't send ping for 2 sec
      //send will be ok
      send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World");

      conn.disconnect();
   }

   @Test
   public void testSendWithHeartBeatsAndReceive() throws Exception {
      StompClientConnection newConn = null;
      try {
         ClientStompFrame frame = conn.createFrame(Stomp.Commands.CONNECT);
         frame.addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
              .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
              .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
              .addHeader(Stomp.Headers.Connect.HEART_BEAT, "500,1000")
              .addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0,1.1,1.2");

         conn.sendFrame(frame);

         conn.startPinger(500);

         for (int i = 0; i < 10; i++) {
            send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World " + i + "!");
            Thread.sleep(500);
         }

         // subscribe
         newConn = StompClientConnectionFactory.createClientConnection(uri);
         newConn.connect(defUser, defPass);
         subscribe(newConn, "a-sub");

         int cnt = 0;

         frame = newConn.receiveFrame();

         while (frame != null) {
            cnt++;
            Thread.sleep(500);
            frame = newConn.receiveFrame(5000);
         }

         assertEquals(10, cnt);

         // unsub
         unsubscribe(newConn, "a-sub");
      } finally {
         if (newConn != null) {
            newConn.disconnect();
         }
         conn.disconnect();
      }
   }

   @Test
   public void testSendAndReceiveWithHeartBeats() throws Exception {
      conn.connect(defUser, defPass);

      for (int i = 0; i < 10; i++) {
         send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World " + i + "!");
         Thread.sleep(500);
      }

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(v11Uri);
      try {
         ClientStompFrame frame = newConn.createFrame(Stomp.Commands.CONNECT)
                                         .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                                         .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                                         .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                                         .addHeader(Stomp.Headers.Connect.HEART_BEAT, "500,1000")
                                         .addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0,1.1");

         newConn.sendFrame(frame);

         newConn.startPinger(500);

         Thread.sleep(500);

         subscribe(newConn, "a-sub");

         int cnt = 0;

         frame = newConn.receiveFrame();

         while (frame != null) {
            cnt++;
            Thread.sleep(500);
            frame = newConn.receiveFrame(5000);
         }

         assertEquals(10, cnt);

         // unsub
         unsubscribe(newConn, "a-sub");
      } finally {
         newConn.disconnect();
      }
   }

   @Test
   public void testSendWithHeartBeatsAndReceiveWithHeartBeats() throws Exception {
      StompClientConnection newConn = null;
      try {
         ClientStompFrame frame = conn.createFrame(Stomp.Commands.CONNECT)
                                      .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                                      .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                                      .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                                      .addHeader(Stomp.Headers.Connect.HEART_BEAT, "500,1000")
                                      .addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0,1.1,1.2");

         conn.sendFrame(frame);

         conn.startPinger(500);

         for (int i = 0; i < 10; i++) {
            send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World " + i + "!");
            Thread.sleep(500);
         }

         // subscribe
         newConn = StompClientConnectionFactory.createClientConnection(uri);
         frame = newConn.createFrame(Stomp.Commands.CONNECT)
                        .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                        .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                        .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                        .addHeader(Stomp.Headers.Connect.HEART_BEAT, "500,1000")
                        .addHeader(Stomp.Headers.ACCEPT_VERSION, "1.0,1.1,1.2");

         newConn.sendFrame(frame);

         newConn.startPinger(500);

         Thread.sleep(500);

         subscribe(newConn, "a-sub");

         int cnt = 0;

         frame = newConn.receiveFrame();

         while (frame != null) {
            cnt++;
            Thread.sleep(500);
            frame = newConn.receiveFrame(5000);
         }
         assertEquals(10, cnt);

         // unsub
         unsubscribe(newConn, "a-sub");
      } finally {
         if (newConn != null) {
            newConn.disconnect();
         }
         conn.disconnect();
      }
   }

   @Test
   public void testNack() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      nack(conn, messageID);

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //Nack makes the message be dropped.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receiveNoWait();
      assertNull(message);
   }

   @Test
   public void testNackWithWrongSubId() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.ACK);

      nack(conn, messageID + "0");

      ClientStompFrame error = conn.receiveFrame();

      assertEquals(Stomp.Responses.ERROR, error.getCommand());

      waitDisconnect(conn);
      assertFalse(conn.isConnected(), "Should be disconnected in STOMP 1.2 after ERROR");

      //message should be still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testNackWithWrongMessageId() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      assertNotNull(frame);

      assertNotNull(frame.getHeader(Stomp.Headers.Message.ACK));

      nack(conn, "someother");

      ClientStompFrame error = conn.receiveFrame();

      logger.debug("Receiver error: {}", error);

      waitDisconnect(conn);
      assertFalse(conn.isConnected(), "Should be disconnected in STOMP 1.2 after ERROR");

      //message should still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testAck() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.ACK);

      assertNotNull(messageID);

      ack(conn, messageID);

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //Nack makes the message be dropped.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receiveNoWait();
      assertNull(message);
   }

   @Test
   public void testAckNoIDHeader() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client-individual");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.ACK);

      assertNotNull(messageID);

      ClientStompFrame ackFrame = conn.createFrame(Stomp.Commands.ACK);

      conn.sendFrame(ackFrame);

      frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.ERROR, frame.getCommand());

      waitDisconnect(conn);
      assertFalse(conn.isConnected(), "Should be disconnected in STOMP 1.2 after ERROR");

      //message still there.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testAckWithWrongMessageId() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      assertNotNull(frame);

      ack(conn, "someother");

      ClientStompFrame error = conn.receiveFrame();

      logger.debug("Receiver error: {}", error);

      waitDisconnect(conn);
      assertFalse(conn.isConnected(), "Should be disconnected in STOMP 1.2 after ERROR");

      //message should still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testErrorWithReceipt() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      ClientStompFrame ackFrame = conn.createFrame(Stomp.Commands.ACK);
      //give it a wrong sub id
      ackFrame.addHeader(Stomp.Headers.Ack.SUBSCRIPTION, "sub2");
      ackFrame.addHeader(Stomp.Headers.Ack.MESSAGE_ID, messageID);
      ackFrame.addHeader(Stomp.Headers.RECEIPT_REQUESTED, "answer-me");

      ClientStompFrame error = conn.sendFrame(ackFrame);

      logger.debug("Receiver error: {}", error);

      assertEquals(Stomp.Responses.ERROR, error.getCommand());

      assertEquals("answer-me", error.getHeader("receipt-id"));

      waitDisconnect(conn);
      assertFalse(conn.isConnected(), "Should be disconnected in STOMP 1.2 after ERROR");

      //message should still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testErrorWithReceipt2() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      ClientStompFrame ackFrame = conn.createFrame(Stomp.Commands.ACK);
      //give it a wrong message id
      ackFrame.addHeader(Stomp.Headers.Ack.SUBSCRIPTION, "sub1");
      ackFrame.addHeader(Stomp.Headers.Ack.MESSAGE_ID, messageID + '1');
      ackFrame.addHeader(Stomp.Headers.RECEIPT_REQUESTED, "answer-me");

      ClientStompFrame error = conn.sendFrame(ackFrame);

      logger.debug("Receiver error: {}", error);

      assertEquals(Stomp.Responses.ERROR, error.getCommand());

      assertEquals("answer-me", error.getHeader("receipt-id"));

      waitDisconnect(conn);
      assertFalse(conn.isConnected(), "Should be disconnected in STOMP 1.2 after ERROR");

      //message should still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   protected void waitDisconnect(StompClientConnectionV12 connection) throws Exception {

      long timeout = System.currentTimeMillis() + 10000;
      while (timeout > System.currentTimeMillis() && connection.isConnected()) {
         Thread.sleep(10);
      }
   }

   @Test
   public void testAckModeClient() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      int num = 50;
      //send a bunch of messages
      for (int i = 0; i < num; i++) {
         this.sendJmsMessage("client-ack" + i);
      }

      ClientStompFrame frame = null;

      for (int i = 0; i < num; i++) {
         frame = conn.receiveFrame();
         assertNotNull(frame);
      }

      //ack the last
      ack(conn, frame);

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //no messages can be received.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receiveNoWait();
      assertNull(message);
   }

   @Test
   public void testAckModeClient2() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      int num = 50;
      //send a bunch of messages
      for (int i = 0; i < num; i++) {
         this.sendJmsMessage("client-ack" + i);
      }

      ClientStompFrame frame = null;

      for (int i = 0; i < num; i++) {
         frame = conn.receiveFrame();
         assertNotNull(frame);

         //ack the 49th
         if (i == num - 2) {
            ack(conn, frame);
         }
      }

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //one can be received.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
      message = consumer.receiveNoWait();
      assertNull(message);
   }

   //when ack is missing the mode default to auto
   @Test
   public void testAckModeDefault() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", null);

      int num = 50;
      //send a bunch of messages
      for (int i = 0; i < num; i++) {
         this.sendJmsMessage("auto-ack" + i);
      }

      ClientStompFrame frame = null;

      for (int i = 0; i < num; i++) {
         frame = conn.receiveFrame();
         assertNotNull(frame);
      }

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //no messages can be received.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receiveNoWait();
      assertNull(message);
   }

   @Test
   public void testAckModeAuto() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      int num = 50;
      //send a bunch of messages
      for (int i = 0; i < num; i++) {
         this.sendJmsMessage("auto-ack" + i);
      }

      ClientStompFrame frame = null;

      for (int i = 0; i < num; i++) {
         frame = conn.receiveFrame();
         assertNotNull(frame);
      }

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //no messages can be received.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receiveNoWait();
      assertNull(message);
   }

   @Test
   public void testAckModeClientIndividual() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client-individual");

      int num = 50;
      //send a bunch of messages
      for (int i = 0; i < num; i++) {
         this.sendJmsMessage("client-individual-ack" + i);
      }

      ClientStompFrame frame = null;

      for (int i = 0; i < num; i++) {
         frame = conn.receiveFrame();
         assertNotNull(frame);

         logger.debug("{} == received: {}", i, frame);
         //ack on even numbers
         if (i % 2 == 0) {
            ack(conn, frame);
         }
      }

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //no messages can be received.
      MessageConsumer consumer = session.createConsumer(queue);

      TextMessage message = null;
      for (int i = 0; i < num / 2; i++) {
         message = (TextMessage) consumer.receive(1000);
         assertNotNull(message);
         logger.debug("Legal: {}", message.getText());
      }

      message = (TextMessage) consumer.receiveNoWait();

      assertNull(message);
   }

   @Test
   public void testTwoSubscribers() throws Exception {
      conn.connect(defUser, defPass, CLIENT_ID);

      subscribeTopic(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO, null);

      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(v11Uri);
      newConn.connect(defUser, defPass, "myclientid2");

      subscribeTopic(newConn, "sub2", Stomp.Headers.Subscribe.AckModeValues.AUTO, null);

      send(conn, getTopicPrefix() + getTopicName(), null, "Hello World");

      // receive message from socket
      ClientStompFrame frame = conn.receiveFrame(1000);

      logger.debug("received frame : {}", frame);
      assertEquals("Hello World", frame.getBody());
      assertEquals("sub1", frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));

      frame = newConn.receiveFrame(1000);

      logger.debug("received 2 frame : {}", frame);
      assertEquals("Hello World", frame.getBody());
      assertEquals("sub2", frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));

      // remove suscription
      unsubscribe(conn, "sub1", true);
      unsubscribe(newConn, "sub2", true);

      conn.disconnect();
      newConn.disconnect();
   }

   @Test
   public void testSendAndReceiveOnDifferentConnections() throws Exception {
      conn.connect(defUser, defPass);

      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World");

      StompClientConnection connV12_2 = StompClientConnectionFactory.createClientConnection(v11Uri);
      connV12_2.connect(defUser, defPass);

      subscribe(connV12_2, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      ClientStompFrame frame = connV12_2.receiveFrame(2000);

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Hello World", frame.getBody());

      conn.disconnect();
      connV12_2.disconnect();
   }

   //----------------Note: tests below are adapted from StompTest

   @Test
   public void testBeginSameTransactionTwice() throws Exception {
      conn.connect(defUser, defPass);

      beginTransaction(conn, "tx1");

      beginTransaction(conn, "tx1");

      ClientStompFrame f = conn.receiveFrame();
      assertEquals(Stomp.Responses.ERROR, f.getCommand());
   }

   @Test
   public void testBodyWithUTF8() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, getName(), Stomp.Headers.Subscribe.AckModeValues.AUTO);

      String text = "A" + "\u00ea" + "\u00f1" + "\u00fc" + "C";
      logger.debug(text);
      sendJmsMessage(text);

      ClientStompFrame frame = conn.receiveFrame();
      logger.debug("{}", frame);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertNotNull(frame.getHeader(Stomp.Headers.Subscribe.DESTINATION));
      assertEquals(text, frame.getBody());

      conn.disconnect();
   }

   @Test
   public void testClientAckNotPartOfTransaction() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, getName(), "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertNotNull(frame.getHeader(Stomp.Headers.Message.DESTINATION));
      assertEquals(getName(), frame.getBody());
      assertNotNull(frame.getHeader(Stomp.Headers.Message.MESSAGE_ID));
      assertNotNull(frame.getHeader(Stomp.Headers.Message.ACK));

      String messageID = frame.getHeader(Stomp.Headers.Message.ACK);

      beginTransaction(conn, "tx1");

      ack(conn, messageID, "tx1");

      abortTransaction(conn, "tx1");

      frame = conn.receiveFrame(100);

      assertNull(frame);

      unsubscribe(conn, getName());

      conn.disconnect();
   }

   @Test
   public void testDisconnectAndError() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, getName(), "client");

      ClientStompFrame frame = conn.createFrame("DISCONNECT");
      frame.addHeader(Stomp.Headers.RECEIPT_REQUESTED, "1");

      ClientStompFrame result = conn.sendFrame(frame);

      if (result == null || (!Stomp.Responses.RECEIPT.equals(result.getCommand())) || (!"1".equals(result.getHeader("receipt-id")))) {
         fail("Disconnect failed! " + result);
      }

      final CountDownLatch latch = new CountDownLatch(1);

      Thread thr = new Thread(() -> {
         while (latch.getCount() != 0) {
            try {
               send(conn, getQueuePrefix() + getQueueName(), null, "Hello World");
               Thread.sleep(500);
            } catch (InterruptedException e) {
               //retry
            } catch (ClosedChannelException e) {
               //ok.
               latch.countDown();
               break;
            } catch (IOException e) {
               //ok.
               latch.countDown();
               break;
            } finally {
               conn.destroy();
            }
         }
      });

      thr.start();
      latch.await(10, TimeUnit.SECONDS);

      long count = latch.getCount();
      if (count != 0) {
         latch.countDown();
      }
      thr.join();

      assertEquals(0, count, "Server failed to disconnect.");
   }

   @Test
   public void testDurableSubscriber() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client", getName());

      ClientStompFrame frame = subscribe(conn, "sub1", "client", getName());

      assertEquals(Stomp.Responses.ERROR, frame.getCommand());

      waitDisconnect(conn);
      assertFalse(conn.isConnected(), "Should be disconnected in STOMP 1.2 after ERROR");
   }

   @Test
   public void testMultipleDurableSubscribers() throws Exception {
      conn.connect(defUser, defPass, "myClientID");
      StompClientConnectionV12 conn2 = (StompClientConnectionV12) StompClientConnectionFactory.createClientConnection(uri);
      conn2.connect(defUser, defPass, "myClientID");

      subscribe(conn, UUID.randomUUID().toString(), "client-individual", getName());
      subscribe(conn2, UUID.randomUUID().toString(), "clientindividual", getName());

      conn.closeTransport();
      waitDisconnect(conn);
      conn2.closeTransport();
      waitDisconnect(conn2);
   }

   @Test
   public void testMultipleConcurrentDurableSubscribers() throws Exception {
      int NUMBER_OF_THREADS = 25;
      SubscriberThread[] threads = new SubscriberThread[NUMBER_OF_THREADS];
      final CountDownLatch startFlag = new CountDownLatch(1);
      final CountDownLatch alignFlag = new CountDownLatch(NUMBER_OF_THREADS);

      for (int i = 0; i < threads.length; i++) {
         threads[i] = new SubscriberThread("subscriber::" + i, StompClientConnectionFactory.createClientConnection(uri), startFlag, alignFlag);
      }

      for (SubscriberThread t : threads) {
         t.start();
      }

      alignFlag.await();

      startFlag.countDown();

      for (SubscriberThread t : threads) {
         t.join();
         assertEquals(0, t.errors.get());
      }
   }

   class SubscriberThread extends Thread {
      final StompClientConnection connection;
      final CountDownLatch startFlag;
      final CountDownLatch alignFlag;
      final AtomicInteger errors = new AtomicInteger(0);

      SubscriberThread(String name, StompClientConnection connection, CountDownLatch startFlag, CountDownLatch alignFlag) {
         super(name);
         this.connection = connection;
         this.startFlag = startFlag;
         this.alignFlag = alignFlag;
      }

      @Override
      public void run() {
         try {
            alignFlag.countDown();
            startFlag.await();
            connection.connect(defUser, defPass, "myClientID");
            ClientStompFrame frame = subscribeTopic(connection, UUID.randomUUID().toString(), "client-individual", "123");
            if (frame.getCommand().equals(Stomp.Responses.ERROR)) {

               errors.incrementAndGet();
            }
         } catch (Exception e) {
            e.printStackTrace();
            errors.incrementAndGet();
         } finally {
            try {
               connection.disconnect();
               waitDisconnect((StompClientConnectionV12) connection);
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
   }

   @Test
   public void testDurableSubscriberWithReconnection() throws Exception {
      conn.connect(defUser, defPass, CLIENT_ID);

      subscribeTopic(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO, getName());

      ClientStompFrame frame = conn.createFrame("DISCONNECT");
      frame.addHeader(Stomp.Headers.RECEIPT_REQUESTED, "1");

      ClientStompFrame result = conn.sendFrame(frame);

      if (result == null || (!Stomp.Responses.RECEIPT.equals(result.getCommand())) || (!"1".equals(result.getHeader("receipt-id")))) {
         fail("Disconnect failed! " + result);
      }

      // send the message when the durable subscriber is disconnected
      sendJmsMessage(getName(), topic);

      conn.destroy();
      conn = (StompClientConnectionV12) StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass, CLIENT_ID);

      subscribeTopic(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO, getName());

      // we must have received the message
      frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertNotNull(frame.getHeader(Stomp.Headers.Subscribe.DESTINATION));
      assertEquals(getName(), frame.getBody());

      unsubscribe(conn, "sub1");

      conn.disconnect();
   }

   @Test
   public void testDurableUnSubscribe() throws Exception {
      conn.connect(defUser, defPass, CLIENT_ID);

      subscribeTopic(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO, getName());

      conn.disconnect();
      conn.destroy();
      conn = (StompClientConnectionV12) StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass, CLIENT_ID);

      unsubscribe(conn, getName(), null, false, true);

      SimpleString queueName = SimpleString.of(CLIENT_ID + "." + getName());
      Wait.assertTrue(() -> server.locateQueue(queueName) == null);

      conn.disconnect();
   }

   @Test
   public void testJMSXGroupIdCanBeSet() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND);
      frame.addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName());
      frame.addHeader("JMSXGroupID", "TEST");
      frame.setBody("Hello World");

      conn.sendFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      // differ from StompConnect
      assertEquals("TEST", message.getStringProperty("JMSXGroupID"));
   }

   @Test
   public void testMessagesAreInOrder() throws Exception {
      int ctr = 10;
      String[] data = new String[ctr];

      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      for (int i = 0; i < ctr; ++i) {
         data[i] = getName() + i;
         sendJmsMessage(data[i]);
      }

      ClientStompFrame frame = null;

      for (int i = 0; i < ctr; ++i) {
         frame = conn.receiveFrame();
         assertEquals(data[i], frame.getBody(), "Message not in order");
      }

      for (int i = 0; i < ctr; ++i) {
         data[i] = getName() + ":second:" + i;
         sendJmsMessage(data[i]);
      }

      for (int i = 0; i < ctr; ++i) {
         frame = conn.receiveFrame();
         assertEquals(data[i], frame.getBody(), "Message not in order");
      }

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithAutoAckAndSelector() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO, null, "foo = 'zzz'");

      sendJmsMessage("Ignored message", "foo", "1234");
      sendJmsMessage("Real message", "foo", "zzz");

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals("Real message", frame.getBody(), "Should have received the real message but got: " + frame);

      conn.disconnect();
   }

   @Test
   public void testRedeliveryWithClientAck() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "subId", "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      conn.disconnect();

      // message should be received since message was not acknowledged
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
      assertTrue(message.getJMSRedelivered());
   }

   @Test
   public void testSendManyMessages() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      int count = 1000;
      final CountDownLatch latch = new CountDownLatch(count);
      consumer.setMessageListener(arg0 -> {
         TextMessage m = (TextMessage) arg0;
         latch.countDown();
         try {
            logger.debug("___> latch now: {} m: {}", latch.getCount(), m.getText());
         } catch (JMSException e) {
            fail("here failed");
            e.printStackTrace();
         }
      });

      for (int i = 1; i <= count; i++) {
         send(conn, getQueuePrefix() + getQueueName(), null, "Hello World");
      }

      assertTrue(latch.await(60, TimeUnit.SECONDS));

      conn.disconnect();
   }

   @Test
   public void testSendMessage() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World");

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      // Assert default priority 4 is used when priority header is not set
      assertEquals(4, message.getJMSPriority(), "getJMSPriority");

      // Make sure that the timestamp is valid - should
      // be very close to the current time.
      long tnow = System.currentTimeMillis();
      long tmsg = message.getJMSTimestamp();
      assertTrue(Math.abs(tnow - tmsg) < 1000);
   }

   @Test
   public void testSendMessageWithContentLength() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      byte[] data = new byte[]{1, 0, 0, 4};

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .setBody(new String(data, StandardCharsets.UTF_8))
                                   .addHeader(Stomp.Headers.CONTENT_LENGTH, String.valueOf(data.length));

      conn.sendFrame(frame);

      BytesMessage message = (BytesMessage) consumer.receive(10000);
      assertNotNull(message);

      assertEquals(data.length, message.getBodyLength());
      assertEquals(data[0], message.readByte());
      assertEquals(data[1], message.readByte());
      assertEquals(data[2], message.readByte());
      assertEquals(data[3], message.readByte());
   }

   @Test
   public void testSendMessageWithCustomHeadersAndSelector() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue, "foo = 'abc'");

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader("foo", "abc")
                                   .addHeader("bar", "123")
                                   .setBody("Hello World");

      conn.sendFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      assertEquals("abc", message.getStringProperty("foo"), "foo");
      assertEquals("123", message.getStringProperty("bar"), "bar");
   }

   @Test
   public void testSendMessageWithLeadingNewLine() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .setBody("Hello World");

      conn.sendWickedFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());

      // Make sure that the timestamp is valid - should
      // be very close to the current time.
      long tnow = System.currentTimeMillis();
      long tmsg = message.getJMSTimestamp();
      assertTrue(Math.abs(tnow - tmsg) < 1000);

      assertNull(consumer.receiveNoWait());

      conn.disconnect();
   }

   @Test
   public void testSendMessageWithReceipt() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World", true);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());

      // Make sure that the timestamp is valid - should
      // be very close to the current time.
      long tnow = System.currentTimeMillis();
      long tmsg = message.getJMSTimestamp();
      assertTrue(Math.abs(tnow - tmsg) < 1000);

      conn.disconnect();
   }

   @Test
   public void testSendMessageWithStandardHeaders() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader("correlation-id", "c123")
                                   .addHeader("persistent", "true")
                                   .addHeader("priority", "3")
                                   .addHeader(Stomp.Headers.Message.TYPE, "t345")
                                   .addHeader("JMSXGroupID", "abc")
                                   .addHeader("foo", "abc")
                                   .addHeader("bar", "123")
                                   .setBody("Hello World");

      conn.sendFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      assertEquals("c123", message.getJMSCorrelationID(), "JMSCorrelationID");
      assertEquals("t345", message.getJMSType(), "getJMSType");
      assertEquals(3, message.getJMSPriority(), "getJMSPriority");
      assertEquals(javax.jms.DeliveryMode.PERSISTENT, message.getJMSDeliveryMode());
      assertEquals("abc", message.getStringProperty("foo"), "foo");
      assertEquals("123", message.getStringProperty("bar"), "bar");

      assertEquals("abc", message.getStringProperty("JMSXGroupID"), "JMSXGroupID");

      conn.disconnect();
   }

   @Test
   public void testSendMessageWithLongHeaders() throws Exception {
      final int headerSize = 2048;
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      String longHeader = "a".repeat(headerSize);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader("correlation-id", "c123")
                                   .addHeader("persistent", "true")
                                   .addHeader("priority", "3")
                                   .addHeader(Stomp.Headers.Message.TYPE, "t345")
                                   .addHeader("JMSXGroupID", "abc")
                                   .addHeader("foo", "abc")
                                   .addHeader("very-very-long-stomp-message-header", longHeader)
                                   .setBody("Hello World");

      conn.sendFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      assertEquals("c123", message.getJMSCorrelationID(), "JMSCorrelationID");
      assertEquals("t345", message.getJMSType(), "getJMSType");
      assertEquals(3, message.getJMSPriority(), "getJMSPriority");
      assertEquals(javax.jms.DeliveryMode.PERSISTENT, message.getJMSDeliveryMode());
      assertEquals("abc", message.getStringProperty("foo"), "foo");
      assertEquals(headerSize, message.getStringProperty("very-very-long-stomp-message-header").length(), "very-very-long-stomp-message-header");

      assertEquals("abc", message.getStringProperty("JMSXGroupID"), "JMSXGroupID");

      conn.disconnect();
   }

   @Test
   public void testSubscribeToTopic() throws Exception {
      conn.connect(defUser, defPass);

      subscribeTopic(conn, "sub1", null, null, true);

      sendJmsMessage(getName(), topic);

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(frame.getHeader(Stomp.Headers.Subscribe.DESTINATION), getTopicPrefix() + getTopicName());
      assertEquals(frame.getBody(), getName());

      unsubscribe(conn, "sub1", true);

      sendJmsMessage(getName(), topic);

      frame = conn.receiveFrame(100);
      assertNull(frame);

      conn.disconnect();
   }

   @Test
   public void testSubscribeToTopicWithNoLocal() throws Exception {
      conn.connect(defUser, defPass);

      subscribeTopic(conn, "sub1", null, null, true, true);

      // send a message on the same connection => it should not be received
      send(conn, getTopicPrefix() + getTopicName(), null, "Hello World");

      ClientStompFrame frame = conn.receiveFrame(100);

      assertNull(frame);

      // send message on another JMS connection => it should be received
      sendJmsMessage(getName(), topic);

      frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Subscribe.DESTINATION));
      assertEquals(getName(), frame.getBody());

      unsubscribe(conn, "sub1");

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithAutoAck() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertNotNull(frame.getHeader(Stomp.Headers.Subscribe.DESTINATION));
      assertEquals(getName(), frame.getBody());

      conn.disconnect();

      // message should not be received as it was auto-acked
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receiveNoWait();
      assertNull(message);
   }

   @Test
   public void testSubscribeWithAutoAckAndBytesMessage() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      byte[] payload = new byte[]{1, 2, 3, 4, 5};
      sendJmsMessage(payload, queue);

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      logger.debug("Message: {}", frame);

      assertEquals("5", frame.getHeader(Stomp.Headers.CONTENT_LENGTH));

      assertNull(frame.getHeader(Stomp.Headers.Message.TYPE));

      assertEquals(frame.getBody(), new String(payload, StandardCharsets.UTF_8));

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithClientAck() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      ack(conn, frame);

      conn.disconnect();

      // message should not be received since message was acknowledged by the client
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receiveNoWait();
      assertNull(message);
   }

   @Test
   public void testSubscribeWithClientAckThenConsumingAgainWithAutoAckWithExplicitDisconnect() throws Exception {
      assertSubscribeWithClientAckThenConsumeWithAutoAck(true);
   }

   @Test
   public void testSubscribeWithClientAckThenConsumingAgainWithAutoAckWithNoDisconnectFrame() throws Exception {
      assertSubscribeWithClientAckThenConsumeWithAutoAck(false);
   }

   @Test
   public void testSubscribeWithID() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "mysubid", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      assertNotNull(frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithMessageSentWithProperties() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      MessageProducer producer = session.createProducer(queue);
      BytesMessage message = session.createBytesMessage();
      message.setStringProperty("S", "value");
      message.setBooleanProperty("n", false);
      message.setByteProperty("byte", (byte) 9);
      message.setDoubleProperty("d", 2.0);
      message.setFloatProperty("f", (float) 6.0);
      message.setIntProperty("i", 10);
      message.setLongProperty("l", 121);
      message.setShortProperty("s", (short) 12);
      message.writeBytes("Hello World".getBytes(StandardCharsets.UTF_8));
      producer.send(message);

      ClientStompFrame frame = conn.receiveFrame();
      assertNotNull(frame);

      assertNotNull(frame.getHeader("S"));
      assertNotNull(frame.getHeader("n"));
      assertNotNull(frame.getHeader("byte"));
      assertNotNull(frame.getHeader("d"));
      assertNotNull(frame.getHeader("f"));
      assertNotNull(frame.getHeader("i"));
      assertNotNull(frame.getHeader("l"));
      assertNotNull(frame.getHeader("s"));
      assertEquals("Hello World", frame.getBody());

      conn.disconnect();
   }

   @Test
   public void testSuccessiveTransactionsWithSameID() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      // first tx
      beginTransaction(conn, "tx1");

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.TRANSACTION, "tx1")
                                   .setBody("Hello World");

      conn.sendFrame(frame);

      commitTransaction(conn, "tx1");

      Message message = consumer.receive(1000);
      assertNotNull(message, "Should have received a message");

      // 2nd tx with same tx ID
      beginTransaction(conn, "tx1");

      frame = conn.createFrame(Stomp.Commands.SEND)
                  .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                  .addHeader(Stomp.Headers.TRANSACTION, "tx1")
                  .setBody("Hello World");

      conn.sendFrame(frame);

      commitTransaction(conn, "tx1");

      message = consumer.receive(1000);
      assertNotNull(message, "Should have received a message");

      conn.disconnect();
   }

   @Test
   public void testTransactionCommit() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      beginTransaction(conn, "tx1");

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.TRANSACTION, "tx1")
                                   .addHeader(Stomp.Headers.RECEIPT_REQUESTED, "123")
                                   .setBody("Hello World");

      frame = conn.sendFrame(frame);

      assertEquals("123", frame.getHeader("receipt-id"));

      // check the message is not committed
      assertNull(consumer.receiveNoWait());

      commitTransaction(conn, "tx1", true);

      Message message = consumer.receive(1000);
      assertNotNull(message, "Should have received a message");

      conn.disconnect();
   }

   @Test
   public void testTransactionRollback() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      beginTransaction(conn, "tx1");

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.TRANSACTION, "tx1")
                                   .setBody("first message");

      conn.sendFrame(frame);

      // rollback first message
      abortTransaction(conn, "tx1");

      beginTransaction(conn, "tx1");

      frame = conn.createFrame(Stomp.Commands.SEND)
                  .addHeader(Stomp.Headers.Subscribe.DESTINATION, getQueuePrefix() + getQueueName())
                  .addHeader(Stomp.Headers.TRANSACTION, "tx1")
                  .setBody("second message");

      conn.sendFrame(frame);

      commitTransaction(conn, "tx1", true);

      // only second msg should be received since first msg was rolled back
      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("second message", message.getText());

      conn.disconnect();
   }

   @Test
   public void testUnsubscribe() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      // send a message to our queue
      sendJmsMessage("first message");

      // receive message from socket
      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      // remove suscription
      unsubscribe(conn, "sub1", true);

      // send a message to our queue
      sendJmsMessage("second message");

      frame = conn.receiveFrame(100);
      assertNull(frame);

      conn.disconnect();
   }

   @Test
   public void testDisconnectWithoutUnsubscribe() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      // send a message to our queue
      sendJmsMessage("first message");

      // receive message from socket
      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      //now disconnect without unsubscribe
      conn.disconnect();

      // send a message to our queue
      sendJmsMessage("second message");

      //reconnect
      conn = (StompClientConnectionV12) StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      frame = conn.receiveFrame(100);
      assertNull(frame, "not expected: " + frame);

      //subscribe again.
      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      // receive message from socket
      frame = conn.receiveFrame();

      assertNotNull(frame);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      frame = conn.receiveFrame(100);
      assertNull(frame, "not expected: " + frame);

      unsubscribe(conn, "sub1", true);

      frame = conn.receiveFrame(100);
      assertNull(frame);

      conn.disconnect();
   }

   protected void assertSubscribeWithClientAckThenConsumeWithAutoAck(boolean sendDisconnect) throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", "client");

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      logger.debug("Reconnecting!");

      if (sendDisconnect) {
         conn.disconnect();
      } else {
         conn.destroy();
      }
      conn = (StompClientConnectionV12) StompClientConnectionFactory.createClientConnection(uri);

      // message should be received since message was not acknowledged
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", null);

      frame = conn.receiveFrame();
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      conn.disconnect();

      // now let's make sure we don't see the message again
      conn.destroy();
      conn = (StompClientConnectionV12) StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", null, null, true);

      sendJmsMessage("shouldBeNextMessage");

      frame = conn.receiveFrame();
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("shouldBeNextMessage", frame.getBody());
   }

   @Test
   public void testSendMessageToNonExistentQueueWithAutoCreation() throws Exception {
      conn.connect(defUser, defPass);

      send(conn, "NonExistentQueue" + UUID.randomUUID().toString(), null, "Hello World", true, RoutingType.ANYCAST);

      conn.disconnect();
   }

   @Test
   public void testInvalidStompCommand() throws Exception {
      try {
         conn.connect(defUser, defPass);

         ClientStompFrame frame = conn.createAnyFrame("INVALID");
         frame.setBody("Hello World");
         frame.addHeader(Stomp.Headers.RECEIPT_REQUESTED, "1234");//make the client receives this reply.

         frame = conn.sendFrame(frame);

         assertEquals(Stomp.Responses.ERROR, frame.getCommand());
      } finally {
         //because the last frame is ERROR, the connection
         //might already have closed by the server.
         //this is expected so we ignore it.
         conn.destroy();
      }
   }

   @Test
   public void testSendAndReceiveWithEscapedCharactersInSenderId() throws Exception {
      conn.connect(defUser, defPass);

      ClientStompFrame response = send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World 1!");

      assertNull(response);

      //subscribe
      subscribe(conn, "ID\\cMYMACHINE-50616-635482262727823605-1\\c1\\c1\\c1");

      ClientStompFrame frame = conn.receiveFrame();

      logger.debug("Received: {}", frame);

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("ID:MYMACHINE-50616-635482262727823605-1:1:1:1", frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));
      assertNotNull(frame.getHeader(Stomp.Headers.Message.MESSAGE_ID));
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Subscribe.DESTINATION));
      assertEquals("Hello World 1!", frame.getBody());

      //unsub
      unsubscribe(conn, "ID\\cMYMACHINE-50616-635482262727823605-1\\c1\\c1\\c1");

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithZeroConsumerWindowSize() throws Exception {
      internalSubscribeWithZeroConsumerWindowSize(Stomp.Headers.Subscribe.CONSUMER_WINDOW_SIZE, true);
   }

   @Test
   public void testSubscribeWithZeroConsumerWindowSizeLegacyHeader() throws Exception {
      internalSubscribeWithZeroConsumerWindowSize(Stomp.Headers.Subscribe.ACTIVEMQ_PREFETCH_SIZE, true);
   }

   @Test
   public void testSubscribeWithZeroConsumerWindowSizeAndNack() throws Exception {
      internalSubscribeWithZeroConsumerWindowSize(Stomp.Headers.Subscribe.CONSUMER_WINDOW_SIZE, false);
   }

   @Test
   public void testSubscribeWithZeroConsumerWindowSizeLegacyHeaderAndNack() throws Exception {
      internalSubscribeWithZeroConsumerWindowSize(Stomp.Headers.Subscribe.ACTIVEMQ_PREFETCH_SIZE, false);
   }

   private void internalSubscribeWithZeroConsumerWindowSize(String consumerWindowSizeHeader, boolean ack) throws Exception {
      final int TIMEOUT = 1000;
      // to be used when we expect it to be null
      final int NEGATIVE_TIMEOUT = 100;
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.CLIENT_INDIVIDUAL, null, null, getQueuePrefix() + getQueueName(), true, 0, consumerWindowSizeHeader);

      sendJmsMessage(getName());
      sendJmsMessage(getName());
      ClientStompFrame frame1 = conn.receiveFrame(TIMEOUT);
      assertNotNull(frame1);
      assertEquals(Stomp.Responses.MESSAGE, frame1.getCommand());
      String messageID = frame1.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      ClientStompFrame frame2 = conn.receiveFrame(NEGATIVE_TIMEOUT);
      assertNull(frame2);
      if (ack) {
         ack(conn, messageID);
      } else {
         nack(conn, messageID);
      }

      ClientStompFrame frame3 = conn.receiveFrame(TIMEOUT);
      assertNotNull(frame3);
      assertEquals(Stomp.Responses.MESSAGE, frame3.getCommand());
      messageID = frame3.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      if (ack) {
         ack(conn, messageID);
      } else {
         nack(conn, messageID);
      }

      conn.disconnect();

      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(100);
      assertNull(message);
   }

   @Test
   public void testSubscribeWithNonZeroConsumerWindowSize() throws Exception {
      internalSubscribeWithNonZeroConsumerWindowSize(Stomp.Headers.Subscribe.CONSUMER_WINDOW_SIZE, true);
   }

   @Test
   public void testSubscribeWithNonZeroConsumerWindowSizeLegacyHeader() throws Exception {
      internalSubscribeWithNonZeroConsumerWindowSize(Stomp.Headers.Subscribe.ACTIVEMQ_PREFETCH_SIZE, true);
   }

   @Test
   public void testSubscribeWithNonZeroConsumerWindowSizeAndNack() throws Exception {
      internalSubscribeWithNonZeroConsumerWindowSize(Stomp.Headers.Subscribe.CONSUMER_WINDOW_SIZE, false);
   }

   @Test
   public void testSubscribeWithNonZeroConsumerWindowSizeLegacyHeaderAndNack() throws Exception {
      internalSubscribeWithNonZeroConsumerWindowSize(Stomp.Headers.Subscribe.ACTIVEMQ_PREFETCH_SIZE, false);
   }

   private void internalSubscribeWithNonZeroConsumerWindowSize(String consumerWindowSizeHeader, boolean ack) throws Exception {
      // the size of each message was determined from the DEBUG logging from org.apache.activemq.artemis.core.protocol.stomp.StompConnection
      final int MESSAGE_SIZE = 270;
      final int TIMEOUT = 1000;
      final String MESSAGE = "foo-foo-foo";

      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.CLIENT_INDIVIDUAL, null, null, getQueuePrefix() + getQueueName(), true, MESSAGE_SIZE * 2, consumerWindowSizeHeader);

      sendJmsMessage(MESSAGE);
      sendJmsMessage(MESSAGE);
      sendJmsMessage(MESSAGE);
      ClientStompFrame frame1 = conn.receiveFrame(TIMEOUT);
      assertNotNull(frame1);
      assertEquals(Stomp.Responses.MESSAGE, frame1.getCommand());
      String messageID1 = frame1.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      ClientStompFrame frame2 = conn.receiveFrame(TIMEOUT);
      assertNotNull(frame2);
      assertEquals(Stomp.Responses.MESSAGE, frame2.getCommand());
      String messageID2 = frame2.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      ClientStompFrame frame3 = conn.receiveFrame(100);
      assertNull(frame3);
      if (ack) {
         ack(conn, messageID1);
         ack(conn, messageID2);
      } else {
         nack(conn, messageID1);
         nack(conn, messageID2);
      }

      ClientStompFrame frame4 = conn.receiveFrame(TIMEOUT);
      assertNotNull(frame4);
      assertEquals(Stomp.Responses.MESSAGE, frame4.getCommand());
      String messageID4 = frame4.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      if (ack) {
         ack(conn, messageID4);
      } else {
         nack(conn, messageID4);
      }

      conn.disconnect();

      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receiveNoWait();
      assertNull(message);
   }

   @Test
   public void testSubscribeWithNonZeroConsumerWindowSizeAndClientAck() throws Exception {
      // the size of each message was determined from the DEBUG logging from org.apache.activemq.artemis.core.protocol.stomp.StompConnection
      final int MESSAGE_SIZE = 270;
      final int TIMEOUT = 1000;
      final String MESSAGE = "foo-foo-foo";

      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.CLIENT, null, null, getQueuePrefix() + getQueueName(), true, MESSAGE_SIZE * 2, Stomp.Headers.Subscribe.CONSUMER_WINDOW_SIZE);

      sendJmsMessage(MESSAGE);
      sendJmsMessage(MESSAGE);
      sendJmsMessage(MESSAGE);
      sendJmsMessage(MESSAGE);

      ClientStompFrame frame1 = conn.receiveFrame(TIMEOUT);
      assertNotNull(frame1);
      assertEquals(Stomp.Responses.MESSAGE, frame1.getCommand());
      ClientStompFrame frame2 = conn.receiveFrame(TIMEOUT);
      assertNotNull(frame2);
      assertEquals(Stomp.Responses.MESSAGE, frame2.getCommand());
      String messageID2 = frame2.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      ClientStompFrame frame3 = conn.receiveFrame(100);
      assertNull(frame3);
      // this should clear the first 2 messages since we're using CLIENT ack mode
      ack(conn, messageID2);

      ClientStompFrame frame4 = conn.receiveFrame(TIMEOUT);
      assertNotNull(frame4);
      assertEquals(Stomp.Responses.MESSAGE, frame4.getCommand());
      ClientStompFrame frame5 = conn.receiveFrame(TIMEOUT);
      assertNotNull(frame5);
      assertEquals(Stomp.Responses.MESSAGE, frame5.getCommand());
      String messageID5 = frame5.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      // this should clear the next 2 messages
      ack(conn, messageID5);

      conn.disconnect();

      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receiveNoWait();
      assertNull(message);
   }

   @Test
   public void testSameMessageHasDifferentAckIdPerConsumer() throws Exception {
      conn.connect(defUser, defPass);

      subscribeTopic(conn, "sub1", "client-individual", null);
      subscribeTopic(conn, "sub2", "client-individual", null);

      sendJmsMessage(getName(), topic);

      ClientStompFrame frame1 = conn.receiveFrame();
      String firstAckID = frame1.getHeader(Stomp.Headers.Message.ACK);
      assertNotNull(firstAckID);

      ClientStompFrame frame2 = conn.receiveFrame();
      String secondAckID = frame2.getHeader(Stomp.Headers.Message.ACK);
      assertNotNull(secondAckID);
      assertNotEquals(firstAckID, secondAckID);

      ack(conn, frame1);
      ack(conn, frame2);

      unsubscribe(conn, "sub1");
      unsubscribe(conn, "sub2");

      conn.disconnect();
   }

   private void ack(StompClientConnection conn, ClientStompFrame frame) throws IOException, InterruptedException {
      String messageID = frame.getHeader(Stomp.Headers.Message.ACK);

      ClientStompFrame ackFrame = conn.createFrame(Stomp.Commands.ACK);

      ackFrame.addHeader(Stomp.Headers.Ack.ID, messageID);

      ClientStompFrame response = conn.sendFrame(ackFrame);
      if (response != null) {
         throw new IOException("failed to ack " + response);
      }
   }

   // STOMP 1.2-specific ACK and NACK methods

   private void ack(StompClientConnection conn, String mid) throws IOException, InterruptedException {
      ClientStompFrame ackFrame = conn.createFrame(Stomp.Commands.ACK);
      ackFrame.addHeader(Stomp.Headers.Ack.ID, mid);

      conn.sendFrame(ackFrame);
   }

   private void ack(StompClientConnection conn, String mid, String txID) throws IOException, InterruptedException {
      ClientStompFrame ackFrame = conn.createFrame(Stomp.Commands.ACK);
      ackFrame.addHeader(Stomp.Headers.Ack.ID, mid);
      ackFrame.addHeader(Stomp.Headers.TRANSACTION, txID);

      conn.sendFrame(ackFrame);
   }

   private void nack(StompClientConnection conn, String mid) throws IOException, InterruptedException {
      ClientStompFrame ackFrame = conn.createFrame(Stomp.Commands.NACK);
      ackFrame.addHeader(Stomp.Headers.Ack.ID, mid);

      conn.sendFrame(ackFrame);
   }
}
