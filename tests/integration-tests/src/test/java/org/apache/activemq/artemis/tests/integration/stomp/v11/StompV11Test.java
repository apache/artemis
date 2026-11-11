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

import javax.jms.BytesMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.StompConnection;
import org.apache.activemq.artemis.core.protocol.stomp.v11.StompFrameHandlerV11;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.integration.stomp.StompTestBase;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionV11;
import org.apache.activemq.artemis.tests.util.Wait;
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

public class StompV11Test extends StompTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String CLIENT_ID = "myclientid";

   private StompClientConnection conn;

   private URI v10Uri;

   public StompV11Test() {
      super("tcp+v11.stomp");
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      v10Uri = new URI(uri.toString().replace("v11", "v10"));
      conn = StompClientConnectionFactory.createClientConnection(uri);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         boolean connected = conn != null && conn.isConnected();
         if (connected) {
            conn.disconnect();
         }
      } finally {
         super.tearDown();
         conn.closeTransport();
      }
   }

   @Test
   public void testConnection() throws Exception {
      server.getSecurityStore().setSecurityEnabled(true);
      StompClientConnection connection = StompClientConnectionFactory.createClientConnection(v10Uri);

      try {

         connection.connect(defUser, defPass);

         assertTrue(connection.isConnected());

         assertEquals("1.0", connection.getVersion());

         connection.disconnect();

         connection.closeTransport();

         connection = StompClientConnectionFactory.createClientConnection(uri);

         connection.connect(defUser, defPass);

         assertTrue(connection.isConnected());

         assertEquals("1.1", connection.getVersion());

         connection.disconnect();

         connection.closeTransport();

         connection = StompClientConnectionFactory.createClientConnection(uri);

         connection.connect();

         assertFalse(connection.isConnected());

         //new way of connection
         StompClientConnectionV11 conn = (StompClientConnectionV11) StompClientConnectionFactory.createClientConnection(uri);
         conn.connect1(defUser, defPass);

         assertTrue(conn.isConnected());

         conn.disconnect();

         conn.closeTransport();

         //invalid user
         conn = (StompClientConnectionV11) StompClientConnectionFactory.createClientConnection(uri);
         ClientStompFrame frame = conn.connect("invaliduser", defPass);
         assertFalse(conn.isConnected());
         assertEquals(Stomp.Responses.ERROR, frame.getCommand());
         assertTrue(frame.getBody().contains("Security Error occurred"));

         conn.closeTransport();
      } finally {
         connection.closeTransport();
      }
   }

   @Test
   public void testNegotiation() throws Exception {
      // case 1 accept-version absent. It is a 1.0 connect
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.CONNECT)
                                   .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                                   .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                                   .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      ClientStompFrame reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      //reply headers: version, session, server
      assertNull(reply.getHeader("version"));

      conn.disconnect();

      // case 2 accept-version=1.0, result: 1.0
      conn = StompClientConnectionFactory.createClientConnection(uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT)
                  .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0")
                  .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                  .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                  .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      //reply headers: version, session, server
      assertEquals("1.0", reply.getHeader("version"));

      conn.disconnect();

      // case 3 accept-version=1.1, result: 1.1
      conn = StompClientConnectionFactory.createClientConnection(uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT)
                  .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.1")
                  .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                  .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                  .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      //reply headers: version, session, server
      assertEquals("1.1", reply.getHeader("version"));

      conn.disconnect();

      // case 4 accept-version=1.0,1.1,1.2, result 1.1
      conn = StompClientConnectionFactory.createClientConnection(uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT)
                  .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1,1.3")
                  .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                  .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                  .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      //reply headers: version, session, server
      assertEquals("1.1", reply.getHeader("version"));

      conn.disconnect();

      // case 5 accept-version=1.2, result error
      conn = StompClientConnectionFactory.createClientConnection(uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT)
                  .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.3")
                  .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                  .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                  .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.ERROR, reply.getCommand());

      logger.debug("Got error frame {}", reply);

   }

   @Test
   public void testServerFrame() throws Exception {
      { // the default case
         ClientStompFrame frame = conn.connect(defUser, defPass);
         conn.disconnect();
         assertNotNull(frame.getHeader(Stomp.Headers.Connected.SERVER));
         server.getRemotingService().destroyAcceptor("stomp");
      }

      { // explicitly set disableStompServerHeader=false
         server.getRemotingService().createAcceptor("stomp", "tcp://" + TransportConstants.DEFAULT_HOST + ":" + TransportConstants.DEFAULT_STOMP_PORT + "?" + TransportConstants.DISABLE_STOMP_SERVER_HEADER + "=false").start();
         conn = StompClientConnectionFactory.createClientConnection(uri);
         ClientStompFrame frame = conn.connect(defUser, defPass);
         conn.disconnect();
         assertNotNull(frame.getHeader(Stomp.Headers.Connected.SERVER));
         server.getRemotingService().destroyAcceptor("stomp");
      }

      { // explicitly set disableStompServerHeader=true
         server.getRemotingService().createAcceptor("stomp", "tcp://" + TransportConstants.DEFAULT_HOST + ":" + TransportConstants.DEFAULT_STOMP_PORT + "?" + TransportConstants.DISABLE_STOMP_SERVER_HEADER + "=true").start();
         conn = StompClientConnectionFactory.createClientConnection(uri);
         ClientStompFrame frame = conn.connect(defUser, defPass);
         conn.disconnect();
         assertNull(frame.getHeader(Stomp.Headers.Connected.SERVER));
      }
   }

   @Test
   public void testSendAndReceive() throws Exception {
      conn.connect(defUser, defPass);

      ClientStompFrame response = send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World 1!");

      assertNull(response);

      String uuid = UUID.randomUUID().toString();

      response = send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World 2!", true);

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);

      subscribe(newConn, "a-sub");

      ClientStompFrame frame = newConn.receiveFrame();

      logger.debug("received {}", frame);

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      assertEquals("a-sub", frame.getHeader(Stomp.Headers.Ack.SUBSCRIPTION));

      assertNotNull(frame.getHeader(Stomp.Headers.Message.MESSAGE_ID));

      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Message.DESTINATION));

      assertEquals("Hello World 1!", frame.getBody());

      frame = newConn.receiveFrame();

      logger.debug("received {}", frame);

      unsubscribe(newConn, "a-sub");

      newConn.disconnect();
   }

   @Test
   public void testHeaderContentType() throws Exception {
      conn.connect(defUser, defPass);
      send(conn, getQueuePrefix() + getQueueName(), "application/xml", "Hello World 1!");

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
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
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.CONTENT_TYPE, "application/xml")
                                   .addHeader(Stomp.Headers.CONTENT_LENGTH, cLen)
                                   .setBody(body + "extra");

      conn.sendFrame(frame);

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);

      subscribe(newConn, "a-sub");

      frame = newConn.receiveFrame();

      logger.debug("received {}", frame);

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      assertEquals(cLen, frame.getHeader(Stomp.Headers.CONTENT_LENGTH));

      //unsub
      unsubscribe(newConn, "a-sub");

      newConn.disconnect();
   }

   @Test
   public void testHeaderEncoding() throws Exception {
      conn.connect(defUser, defPass);

      String body = "Hello World 1!";
      String cLen = String.valueOf(body.getBytes(StandardCharsets.UTF_8).length);
      String hKey = "special-header\\\\\\n\\c";
      String hVal = "\\c\\\\\\ngood";

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.CONTENT_TYPE, "application/xml")
                                   .addHeader(Stomp.Headers.CONTENT_LENGTH, cLen)
                                   .addHeader(hKey, hVal);

      logger.debug("key: |{}| val: |{}|", hKey, hVal);

      frame.setBody(body);

      conn.sendFrame(frame);

      //subscribe
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);

      subscribe(newConn, "a-sub");

      frame = newConn.receiveFrame();

      logger.debug("received {}", frame);

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      String value = frame.getHeader("special-header" + "\\" + "\n" + ":");

      assertEquals(":" + "\\" + "\n" + "good", value);

      //unsub
      unsubscribe(newConn, "a-sub");

      newConn.disconnect();
   }

   /**
    * In 1.1, undefined escapes must cause a fatal protocol error.
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
      //frame.addHeader(Stomp.Headers.Connect.HEART_BEAT, "0,0");
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
   }

   @Test
   public void testHeartBeat() throws Exception {
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

      //default heart beat for (0,0) which is default connection TTL (60000) / default heartBeatToTtlModifier (2.0) = 30000
      conn = StompClientConnectionFactory.createClientConnection(uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT)
                  .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                  .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                  .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                  .addHeader(Stomp.Headers.Connect.HEART_BEAT, "0,0")
                  .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

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
                  .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

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
                  .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("0,500", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      logger.debug("========== start pinger!");

      conn.startPinger(500);

      Thread.sleep(2000);

      //now server side should be disconnected because we didn't send ping for 2 sec
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
                                   .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

      ClientStompFrame reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());
      assertEquals("500,500", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      conn.disconnect();

      //heart-beat (500,1000)
      conn = StompClientConnectionFactory.createClientConnection(uri);
      frame = conn.createFrame(Stomp.Commands.CONNECT)
                  .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                  .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                  .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                  .addHeader(Stomp.Headers.Connect.HEART_BEAT, "500,1000")
                  .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

      reply = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("1000,500", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      logger.debug("========== start pinger!");

      conn.startPinger(500);

      Thread.sleep(10000);

      //now check the frame size
      int size = conn.getServerPingNumber();

      logger.debug("ping received: {}", size);

      assertTrue(size > 5);

      //now server side should be disconnected because we didn't send ping for 2 sec
      //send will be ok
      send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World");

      conn.disconnect();
   }

   @Test
   public void testSendWithHeartBeatsAndReceive() throws Exception {
      StompClientConnection newConn = null;
      try {
         ClientStompFrame frame = conn.createFrame(Stomp.Commands.CONNECT)
                                      .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                                      .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                                      .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                                      .addHeader(Stomp.Headers.Connect.HEART_BEAT, "500,1000")
                                      .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

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
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      try {
         ClientStompFrame frame = newConn.createFrame(Stomp.Commands.CONNECT)
                        .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                        .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                        .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                        .addHeader(Stomp.Headers.Connect.HEART_BEAT, "500,1000")
                        .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

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
                                      .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

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
                        .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

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
   public void testHeartBeatToTTL() throws Exception {
      ClientStompFrame frame;
      ClientStompFrame reply;
      int port = 61614;

      uri = createStompClientUri(scheme, hostname, port);

      server.getRemotingService().createAcceptor("test", "tcp://127.0.0.1:" + port + "?connectionTtl=1000&connectionTtlMin=5000&connectionTtlMax=10000").start();
      StompClientConnection connection = StompClientConnectionFactory.createClientConnection(uri);

      //no heart beat at all if heat-beat absent
      frame = connection.createFrame(Stomp.Commands.CONNECT)
                        .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                        .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                        .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass);

      reply = connection.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      Thread.sleep(3000);

      assertEquals(Stomp.Responses.ERROR, connection.receiveFrame(1000).getCommand());

      assertEquals(0, connection.getFrameQueueSize());

      try {
         assertFalse(connection.isConnected());
      } catch (Exception e) {
         // ignore
      } finally {
         connection.closeTransport();
      }

      //no heart beat for (0,0)
      connection = StompClientConnectionFactory.createClientConnection(uri);
      frame = connection.createFrame(Stomp.Commands.CONNECT)
                        .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                        .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                        .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                        .addHeader(Stomp.Headers.Connect.HEART_BEAT, "0,0")
                        .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

      reply = connection.sendFrame(frame);

      logger.debug("Reply: {}", reply);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("0,500", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      Thread.sleep(3000);

      assertEquals(Stomp.Responses.ERROR, connection.receiveFrame(1000).getCommand());

      assertEquals(0, connection.getFrameQueueSize());

      try {
         assertFalse(connection.isConnected());
      } catch (Exception e) {
         // ignore
      } finally {
         connection.closeTransport();
      }

      //heart-beat (1,0), should receive a min client ping accepted by server
      connection = StompClientConnectionFactory.createClientConnection(uri);
      frame = connection.createFrame(Stomp.Commands.CONNECT)
                        .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                        .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                        .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                        .addHeader(Stomp.Headers.Connect.HEART_BEAT, "1,0")
                        .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

      reply = connection.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("0,2500", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      Thread.sleep(7000);

      //now server side should be disconnected because we didn't send ping for 2 sec
      //send will fail
      try {
         assertFalse(connection.isConnected());
      } catch (Exception e) {
         // ignore
      } finally {
         connection.closeTransport();
      }

      //heart-beat (1,0), start a ping, then send a message, should be ok.
      connection = StompClientConnectionFactory.createClientConnection(uri);
      frame = connection.createFrame(Stomp.Commands.CONNECT)
                        .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                        .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                        .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                        .addHeader(Stomp.Headers.Connect.HEART_BEAT, "1,0")
                        .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

      reply = connection.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("0,2500", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      logger.debug("========== start pinger!");

      connection.startPinger(2500);

      Thread.sleep(7000);

      //now server side should be disconnected because we didn't send ping for 2 sec
      //send will be ok
      send(connection, getQueuePrefix() + getQueueName(), "text/plain", "Hello World");

      connection.stopPinger();

      connection.disconnect();

      //heart-beat (20000,0), should receive a max client ping accepted by server
      connection = StompClientConnectionFactory.createClientConnection(uri);
      frame = connection.createFrame(Stomp.Commands.CONNECT)
                        .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                        .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                        .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                        .addHeader(Stomp.Headers.Connect.HEART_BEAT, "20000,0")
                        .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

      reply = connection.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("0,5000", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      Thread.sleep(12000);

      //now server side should be disconnected because we didn't send ping for 2 sec
      //send will fail
      try {
         assertFalse(connection.isConnected());
      } catch (Exception e) {
         // ignore
      } finally {
         connection.closeTransport();
      }
   }

   @Test
   public void testHeartBeatToConnectionTTLModifier() throws Exception {
      ClientStompFrame frame;
      ClientStompFrame reply;
      StompClientConnection connection;
      int port = 61614;

      server.getRemotingService().createAcceptor("test", "tcp://127.0.0.1:" + port + "?heartBeatToConnectionTtlModifier=1").start();

      connection = StompClientConnectionFactory.createClientConnection(uri);
      frame = connection.createFrame(Stomp.Commands.CONNECT)
                        .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                        .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                        .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                        .addHeader(Stomp.Headers.Connect.HEART_BEAT, "5000,0")
                        .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

      reply = connection.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("0,5000", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      Thread.sleep(6000);

      try {
         assertFalse(connection.isConnected());
      } finally {
         connection.closeTransport();
      }

      server.getRemotingService().destroyAcceptor("test");
      server.getRemotingService().createAcceptor("test", "tcp://127.0.0.1:" + port + "?heartBeatToConnectionTtlModifier=1.5").start();

      connection = StompClientConnectionFactory.createClientConnection(uri);
      frame = connection.createFrame(Stomp.Commands.CONNECT)
                        .addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1")
                        .addHeader(Stomp.Headers.Connect.LOGIN, this.defUser)
                        .addHeader(Stomp.Headers.Connect.PASSCODE, this.defPass)
                        .addHeader(Stomp.Headers.Connect.HEART_BEAT, "5000,0")
                        .addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");

      reply = connection.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, reply.getCommand());

      assertEquals("0,5000", reply.getHeader(Stomp.Headers.Connect.HEART_BEAT));

      Thread.sleep(6000);

      connection.disconnect();
   }

   @Test
   public void testNack() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      nack(conn, "sub1", messageID);

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //Nack makes the message be dropped.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(100);
      assertNull(message);
   }

   @Test
   public void testNackWithWrongSubId() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      nack(conn, "sub2", messageID);

      ClientStompFrame error = conn.receiveFrame();

      logger.debug("Receiver error: {}", error);

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //message should be still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testNackWithWrongMessageId() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      nack(conn, "sub2", "someother");

      ClientStompFrame error = conn.receiveFrame();

      logger.debug("Receiver error: {}", error);

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //message should still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testAck() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      ack(conn, "sub1", messageID, null);

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //Nack makes the message be dropped.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(100);
      assertNull(message);
   }

   @Test
   public void testAckWithWrongSubId() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      ack(conn, "sub2", messageID, null);

      ClientStompFrame error = conn.receiveFrame();

      logger.debug("Receiver error: {}", error);

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //message should be still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testAckWithWrongMessageId() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      ack(conn, "sub2", "someother", null);

      ClientStompFrame error = conn.receiveFrame();

      logger.debug("Receiver error: {}", error);

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //message should still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testErrorWithReceipt() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      //give it a wrong sub id
      ClientStompFrame ackFrame = conn.createFrame(Stomp.Commands.ACK)
                                      .addHeader(Stomp.Headers.Ack.SUBSCRIPTION, "sub2")
                                      .addHeader(Stomp.Headers.Ack.MESSAGE_ID, messageID)
                                      .addHeader(Stomp.Headers.RECEIPT_REQUESTED, "answer-me");

      ClientStompFrame error = conn.sendFrame(ackFrame);

      logger.debug("Receiver error: {}", error);

      assertEquals(Stomp.Responses.ERROR, error.getCommand());

      assertEquals("answer-me", error.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //message should still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testErrorWithReceipt2() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      //give it a wrong message id
      ClientStompFrame ackFrame = conn.createFrame(Stomp.Commands.ACK)
                                      .addHeader(Stomp.Headers.Ack.SUBSCRIPTION, "sub1")
                                      .addHeader(Stomp.Headers.Ack.MESSAGE_ID, messageID + '1')
                                      .addHeader(Stomp.Headers.RECEIPT_REQUESTED, "answer-me");

      ClientStompFrame error = conn.sendFrame(ackFrame);

      logger.debug("Receiver error: {}", error);

      assertEquals(Stomp.Responses.ERROR, error.getCommand());

      assertEquals("answer-me", error.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //message should still there
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
   }

   @Test
   public void testAckModeClient() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

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
      ack(conn, "sub1", frame);

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //no messages can be received.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(100);
      assertNull(message);
   }

   @Test
   public void testAckModeClient2() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      Thread.sleep(1000);
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
            ack(conn, "sub1", frame);
         }
      }

      unsubscribe(conn, "sub1");

      conn.disconnect();

      //no messages can be received.
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(10000);
      assertNotNull(message);
      message = consumer.receive(100);
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
      Message message = consumer.receive(100);
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
            ack(conn, "sub1", frame);
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

      message = (TextMessage) consumer.receive(100);

      assertNull(message);
   }

   @Test
   public void testTwoSubscribers() throws Exception {
      conn.connect(defUser, defPass, CLIENT_ID);

      subscribeTopic(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO, null);

      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass, "myclientid2");

      subscribeTopic(newConn, "sub2", Stomp.Headers.Subscribe.AckModeValues.AUTO, null);

      send(newConn, getTopicPrefix() + getTopicName(), null, "Hello World");

      // receive message from socket
      ClientStompFrame frame = conn.receiveFrame(5000);

      logger.debug("received frame : {}", frame);
      assertEquals("Hello World", frame.getBody());
      assertEquals("sub1", frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));

      frame = newConn.receiveFrame(5000);

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

      StompClientConnection connV11_2 = StompClientConnectionFactory.createClientConnection(uri);
      connV11_2.connect(defUser, defPass);

      subscribe(connV11_2, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      ClientStompFrame frame = connV11_2.receiveFrame(2000);

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Hello World", frame.getBody());

      conn.disconnect();
      connV11_2.disconnect();
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
      assertNotNull(frame.getHeader(Stomp.Headers.Message.DESTINATION));
      assertEquals(text, frame.getBody());

      conn.disconnect();
   }

   @Test
   public void testClientAckNotPartOfTransaction() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, getName(), Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertNotNull(frame.getHeader(Stomp.Headers.Message.DESTINATION));
      assertEquals(getName(), frame.getBody());
      assertNotNull(frame.getHeader(Stomp.Headers.Message.MESSAGE_ID));

      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);

      beginTransaction(conn, "tx1");

      ack(conn, getName(), messageID, "tx1");

      abortTransaction(conn, "tx1");

      frame = conn.receiveFrame(100);

      assertNull(frame);

      unsubscribe(conn, getName());

      conn.disconnect();
   }

   @Test
   public void testDisconnectAndError() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, getName(), Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      String uuid = UUID.randomUUID().toString();

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.DISCONNECT)
                                   .addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);

      ClientStompFrame result = conn.sendFrame(frame);

      if (result == null || (!Stomp.Responses.RECEIPT.equals(result.getCommand())) || (!uuid.equals(result.getHeader(Stomp.Headers.Response.RECEIPT_ID)))) {
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

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT, getName());

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT, getName(), false);
      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.ERROR, frame.getCommand());

      conn.disconnect();
   }

   @Test
   public void testDurableSubscriberWithReconnection() throws Exception {
      conn.connect(defUser, defPass, CLIENT_ID);

      subscribeTopic(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO, getName());

      String uuid = UUID.randomUUID().toString();

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.DISCONNECT)
                                   .addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);

      ClientStompFrame result = conn.sendFrame(frame);

      if (result == null || (!Stomp.Responses.RECEIPT.equals(result.getCommand())) || (!uuid.equals(result.getHeader(Stomp.Headers.Response.RECEIPT_ID)))) {
         fail("Disconnect failed! " + result);
      }

      // send the message when the durable subscriber is disconnected
      sendJmsMessage(getName(), topic);

      conn.destroy();
      conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass, CLIENT_ID);

      subscribeTopic(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO, getName());

      // we must have received the message
      frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertNotNull(frame.getHeader(Stomp.Headers.Message.DESTINATION));
      assertEquals(getName(), frame.getBody());

      unsubscribe(conn, "sub1");

      conn.disconnect();
   }

   @Test
   public void testDurableUnSubscribe() throws Exception {
      SimpleString queueName = SimpleString.of(CLIENT_ID + "." + getName());

      conn.connect(defUser, defPass, CLIENT_ID);

      subscribeTopic(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO, getName());

      Wait.assertTrue(() -> server.locateQueue(queueName) != null);

      conn.disconnect();
      conn.destroy();

      Queue queue = server.locateQueue(queueName);
      assertNotNull(queue);

      Wait.assertEquals(0, queue::getConsumerCount, 10_000, 10);

      conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass, CLIENT_ID);

      unsubscribe(conn, getName(), null, false, true);

      Wait.assertTrue(() -> server.locateQueue(queueName) == null);

      conn.disconnect();
   }

   @Test
   public void testJMSXGroupIdCanBeSet() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader("JMSXGroupID", "TEST")
                                   .setBody("Hello World");

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

      subscribe(conn, "subscriptionId", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

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
      consumer.setMessageListener(arg0 -> latch.countDown());

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
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
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
                                   .addHeader("foo", "abc")
                                   .addHeader("bar", "123")
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
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
      Thread.sleep(1000);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
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

      assertNull(consumer.receive(100));

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
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.Message.CORRELATION_ID, "c123")
                                   .addHeader(Stomp.Headers.Message.PERSISTENT, "true")
                                   .addHeader(Stomp.Headers.Message.PRIORITY, "3")
                                   .addHeader(Stomp.Headers.Message.TYPE, "t345")
                                   .addHeader("JMSXGroupID", "abc")
                                   .addHeader("foo", "abc")
                                   .addHeader("bar", "123")
                                   .setBody("Hello World");

      frame = conn.sendFrame(frame);

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
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.Message.CORRELATION_ID, "c123")
                                   .addHeader(Stomp.Headers.Message.PERSISTENT, "true")
                                   .addHeader(Stomp.Headers.Message.PRIORITY, "3")
                                   .addHeader(Stomp.Headers.Message.TYPE, "t345")
                                   .addHeader("JMSXGroupID", "abc")
                                   .addHeader("foo", "abc")
                                   .addHeader("longHeader", longHeader)
                                   .setBody("Hello World");

      frame = conn.sendFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      assertEquals("c123", message.getJMSCorrelationID(), "JMSCorrelationID");
      assertEquals("t345", message.getJMSType(), "getJMSType");
      assertEquals(3, message.getJMSPriority(), "getJMSPriority");
      assertEquals(javax.jms.DeliveryMode.PERSISTENT, message.getJMSDeliveryMode());
      assertEquals("abc", message.getStringProperty("foo"), "foo");
      assertEquals(headerSize, message.getStringProperty("longHeader").length(), "longHeader");

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
      assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Message.DESTINATION));
      assertEquals(getName(), frame.getBody());

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

      send(conn, getTopicPrefix() + getTopicName(), null, "Hello World");

      ClientStompFrame frame = conn.receiveFrame(100);

      assertNull(frame);

      // send message on another JMS connection => it should be received
      sendJmsMessage(getName(), topic);

      frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Message.DESTINATION));
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
      assertNotNull(frame.getHeader(Stomp.Headers.Message.DESTINATION));
      assertEquals(getName(), frame.getBody());

      conn.disconnect();

      // message should not be received as it was auto-acked
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(100);
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

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(getName().length(), Integer.parseInt(frame.getHeader(Stomp.Headers.CONTENT_LENGTH)));

      ack(conn, "sub1", frame);

      conn.disconnect();

      // message should not be received since message was acknowledged by the client
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(100);
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

      assertNotNull(frame.getHeader(Stomp.Headers.Ack.SUBSCRIPTION));

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
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.TRANSACTION, "tx1")
                                   .setBody("Hello World");

      conn.sendFrame(frame);

      commitTransaction(conn, "tx1");

      Message message = consumer.receive(1000);
      assertNotNull(message, "Should have received a message");

      // 2nd tx with same tx ID
      beginTransaction(conn, "tx1");

      frame = conn.createFrame(Stomp.Commands.SEND)
                  .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
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
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.TRANSACTION, "tx1")
                                   .addHeader(Stomp.Headers.RECEIPT_REQUESTED, "123")
                                   .setBody("Hello World");

      frame = conn.sendFrame(frame);

      assertEquals("123", frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      // check the message is not committed
      assertNull(consumer.receive(100));

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
                                   .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
                                   .addHeader(Stomp.Headers.TRANSACTION, "tx1")
                                   .setBody("first message");

      conn.sendFrame(frame);

      // rollback first message
      abortTransaction(conn, "tx1");

      beginTransaction(conn, "tx1");

      frame = conn.createFrame(Stomp.Commands.SEND)
                  .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
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
   public void testHeartBeat3() throws Exception {

      connection.close();
      assertEquals(0, server.getRemotingService().getConnections().size());
      ClientStompFrame frame = conn.createFrame("CONNECT");
      frame.addHeader("host", "127.0.0.1");
      frame.addHeader("login", this.defUser);
      frame.addHeader("passcode", this.defPass);
      frame.addHeader("heart-beat", "500,500");
      frame.addHeader("accept-version", "1.0,1.1");

      ClientStompFrame reply = conn.sendFrame(frame);

      assertEquals("CONNECTED", reply.getCommand());

      assertEquals("500,500", reply.getHeader("heart-beat"));


      logger.debug("========== start pinger!");

      conn.startPinger(100);


      assertEquals(1, server.getRemotingService().getConnections().size());
      StompConnection stompConnection = (StompConnection)server.getRemotingService().getConnections().iterator().next();
      StompFrameHandlerV11 stompFrameHandler = (StompFrameHandlerV11)stompConnection.getStompVersionHandler();

      Thread.sleep(1000);

      //now check the frame size
      int size = conn.getServerPingNumber();

      conn.stopPinger();

      Wait.assertEquals(0, () -> server.getRemotingService().getConnections().size());

      Wait.assertFalse("HeartBeater is still running!!", () -> stompFrameHandler.getHeartBeater().isStarted());
   }

   @Test
   public void testHeartBeat4() throws Exception {
      connection.close();
      ClientStompFrame frame = conn.createFrame("CONNECT");
      frame.addHeader("host", "127.0.0.1");
      frame.addHeader("login", this.defUser);
      frame.addHeader("passcode", this.defPass);
      frame.addHeader("heart-beat", "500,500");
      frame.addHeader("accept-version", "1.1,1.2");

      ClientStompFrame reply = conn.sendFrame(frame);

      logger.debug("Reply: {}", reply.toString());

      assertEquals("CONNECTED", reply.getCommand());

      // Obtain a reference to the server StompConnection object
      RemotingConnection remotingConnection = null;
      StompConnection stompConnection = null;
      Iterator<RemotingConnection> iterator = server.getRemotingService().getConnections().iterator();
      while (iterator.hasNext()) {
         remotingConnection = iterator.next();
         if (remotingConnection instanceof StompConnection stompConnection1) {
            stompConnection = stompConnection1;
         }
      }

      StompFrameHandlerV11 stompFrameHandler = (StompFrameHandlerV11) stompConnection.getStompVersionHandler();

      logger.debug("========== start pinger!");

      conn.startPinger(100);

      ClientStompFrame subFrame = conn.createFrame("SUBSCRIBE");
      subFrame.addHeader("destination", getTopicPrefix() + getTopicName());
      subFrame.addHeader("id", "0");

      Wait.assertTrue(() -> "HeartBeater is not running!! -> " + System.identityHashCode(stompFrameHandler.getHeartBeater()), () -> stompFrameHandler.getHeartBeater().isStarted(), 5000, 100);

      ClientStompFrame f = conn.sendFrame(subFrame);
      f = conn.sendFrame(subFrame);

      // Send subscription with a duplicate ID, triggering a server error and closing of the session.
      f = conn.sendFrame(subFrame);

      f = conn.receiveFrame(1000);
      logger.debug("Received {}", f.toString());
      assertEquals("ERROR", f.getCommand());

      conn.stopPinger();

      Wait.assertEquals(0, () -> server.getRemotingService().getConnections().size());

      Wait.assertFalse(() -> "HeartBeater is still running!! -> " + System.identityHashCode(stompFrameHandler.getHeartBeater()), () -> stompFrameHandler.getHeartBeater().isStarted(), 5000, 100);
   }


   protected void assertSubscribeWithClientAckThenConsumeWithAutoAck(boolean sendDisconnect) throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      logger.info("Reconnecting!");

      if (sendDisconnect) {
         conn.disconnect();
      } else {
         conn.destroy();
      }
      conn = StompClientConnectionFactory.createClientConnection(uri);

      // message should be received since message was not acknowledged
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", null);

      frame = conn.receiveFrame();
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      conn.disconnect();

      // now let's make sure we don't see the message again
      conn.destroy();
      conn = StompClientConnectionFactory.createClientConnection(uri);
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
   public void testSendAndReceiveWithEscapedCharactersInSenderId() throws Exception {
      conn.connect(defUser, defPass);
      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World 1!");
      subscribe(conn, "ID\\cMYMACHINE-50616-635482262727823605-1\\c1\\c1\\c1");

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("ID:MYMACHINE-50616-635482262727823605-1:1:1:1", frame.getHeader(Stomp.Headers.Ack.SUBSCRIPTION));
      assertNotNull(frame.getHeader(Stomp.Headers.Message.MESSAGE_ID));
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Message.DESTINATION));
      assertEquals("Hello World 1!", frame.getBody());

      //unsub
      unsubscribe(conn, "ID\\cMYMACHINE-50616-635482262727823605-1\\c1\\c1\\c1");

      conn.disconnect();
   }

   @Test
   public void testReceiveContentType() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame response = send(conn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World");

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("text/plain", message.getStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_CONTENT_TYPE.toString()));
   }

   @Test
   public void testSendContentType() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, "sub1", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      MessageProducer producer = session.createProducer(queue);
      BytesMessage message = session.createBytesMessage();
      message.setStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_CONTENT_TYPE.toString(), "text/plain");
      message.writeBytes("Hello World".getBytes(StandardCharsets.UTF_8));
      producer.send(message);

      ClientStompFrame frame = conn.receiveFrame();
      assertNotNull(frame);

      assertEquals("text/plain", frame.getHeader(Stomp.Headers.CONTENT_TYPE));

      conn.disconnect();
   }

   @Test
   public void testSameMessageHasDifferentMessageIdPerConsumer() throws Exception {
      conn.connect(defUser, defPass);

      subscribeTopic(conn, "sub1", "client-individual", null);
      subscribeTopic(conn, "sub2", "client-individual", null);

      sendJmsMessage(getName(), topic);

      ClientStompFrame frame1 = conn.receiveFrame();
      String firstMessageID = frame1.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      assertNotNull(firstMessageID);

      ClientStompFrame frame2 = conn.receiveFrame();
      String secondMessageID = frame2.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      assertNotNull(secondMessageID);
      assertNotEquals(firstMessageID, secondMessageID);

      ack(conn, "sub1", frame1);
      ack(conn, "sub2", frame2);

      unsubscribe(conn, "sub1");
      unsubscribe(conn, "sub2");

      conn.disconnect();
   }

}
