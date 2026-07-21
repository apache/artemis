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
package org.apache.activemq.artemis.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.NetworkHealthCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class NetworkHealthTest {

   private static final InetAddress INVALID_ADDRESS;

   private static String IPV6_LOCAL = "::1";

   static {
      InetAddress address = null;
      try {
         address = InetAddress.getByName("203.0.113.1");
      } catch (Exception e) {
         e.printStackTrace();
      }
      INVALID_ADDRESS = address;
   }

   Set<NetworkHealthCheck> list = new HashSet<>();

   NetworkHealthCheck addCheck(NetworkHealthCheck check) {
      list.add(check);
      return check.setIgnoreLoopback(true);
   }

   HttpServer httpServer;

   final ReusableLatch latch = new ReusableLatch(1);

   ActiveMQComponent component = new ActiveMQComponent() {
      boolean started = true;

      @Override
      public void start() throws Exception {
         started = true;
         latch.countDown();
      }

      @Override
      public void stop() throws Exception {
         started = false;
         latch.countDown();
      }

      @Override
      public boolean isStarted() {
         return started;
      }
   };

   @BeforeEach
   public void before() throws Exception {
      latch.setCount(1);
   }

   private void startHTTPServer() throws IOException {
      assertNull(httpServer);
      InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8787);
      httpServer = HttpServer.create(address, 100);
      httpServer.start();
      httpServer.createContext("/", t -> {
         String response = "<html><body><b>This is a unit test</b></body></html>";
         t.sendResponseHeaders(200, response.length());
         OutputStream os = t.getResponseBody();
         os.write(response.getBytes());
         os.close();
      });
   }

   private void stopHTTPServer() {
      if (httpServer != null) {
         try {
            httpServer.stop(0);
         } catch (Throwable ignored) {
         }
         httpServer = null;
      }
   }

   @AfterEach
   public void after() {
      stopHTTPServer();
      for (NetworkHealthCheck check : this.list) {
         check.stop();
      }
   }

   @Test
   public void testCheck6() throws Exception {
      assumeTrue(purePingWorks(IPV6_LOCAL));
      NetworkHealthCheck check = addCheck(new NetworkHealthCheck(null, 100, 100));
      check.addComponent(component);

      InetAddress address = InetAddress.getByName(IPV6_LOCAL);

      assertInstanceOf(Inet6Address.class, address);

      assertTrue(check.purePing(address));

      assertTrue(check.check(address));
   }

   @Test
   public void testAlreadyShutdown() throws Exception {
      assumeTrue(purePingWorks(IPV6_LOCAL));
      ReusableLatch latch = new ReusableLatch(0);
      NetworkHealthCheck check = addCheck(new NetworkHealthCheck(null, 100, 100) {
         @Override
         public void run() {
            super.run();
            latch.countDown();
         }
      });
      check.addComponent(component);
      check.addAddress("127.0.0.1");

      component.stop();

      latch.setCount(1);
      assertTrue(latch.await(1, TimeUnit.MINUTES));

      assertFalse(component.isStarted(), "NetworkHealthCheck should have no business on restarting the component, the network was never down, hence no check needed!");

   }

   @Test
   public void testParseSpaces() throws Exception {
      NetworkHealthCheck check = addCheck(new NetworkHealthCheck(null, 100, 100));

      // using two addresses for URI and localhost
      check.parseAddressList("localhost, , 127.0.0.2");
      assertEquals(2, check.getAddresses().size());
   }

   @Test
   public void testParseLogger() throws Exception {
      NetworkHealthCheck check = addCheck(new NetworkHealthCheck(null, 100, 100));

      // using two addresses for URI and localhost
      check.parseAddressList("localhost, , 127.0.0.2");
      assertEquals(2, check.getAddresses().size());
      assertEquals(0, check.getUrls().size());
   }

   @Test
   public void testPings() throws Exception {
      assumeTrue(purePingWorks("127.0.0.1"));
      doCheck("127.0.0.1");
   }

   private void doCheck(String localaddress) throws Exception {
      NetworkHealthCheck check = addCheck(new NetworkHealthCheck(null, 100, 100));
      check.addComponent(component);

      // Any external IP, to make sure we would use a PING
      InetAddress address = InetAddress.getByName(localaddress);

      assertTrue(check.check(address));

      assertTrue(check.purePing(address));

      assertFalse(check.purePing(INVALID_ADDRESS));

   }

   @Test
   public void testPingsIPV6() throws Exception {
      assumeTrue(purePingWorks(IPV6_LOCAL));
      doCheck(IPV6_LOCAL);
   }

   private boolean purePingWorks(String localaddress) throws Exception {
      try {
         return addCheck(new NetworkHealthCheck(null, 100, 100)).purePing(InetAddress.getByName(localaddress));
      } catch (Exception e) {
         return false;
      }
   }

   @Test
   public void testCheckNoNodes() throws Exception {
      NetworkHealthCheck check = addCheck(new NetworkHealthCheck());
      assertTrue(check.check());
   }

   @Test
   public void testCheckUsingHTTP() throws Exception {

      startHTTPServer();

      NetworkHealthCheck check = addCheck(new NetworkHealthCheck(null, 100, 1000));

      assertTrue(check.check(new URL("http://localhost:8787")));

      stopHTTPServer();

      assertFalse(check.check(new URL("http://localhost:8787")));

      check.addComponent(component);

      URL url = new URL("http://localhost:8787");
      assertFalse(check.check(url));

      startHTTPServer();

      assertTrue(check.check(url));

      check.addURL(url);

      assertFalse(latch.await(500, TimeUnit.MILLISECONDS));
      assertTrue(component.isStarted());

      // stopping the web server should stop the component
      stopHTTPServer();

      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertFalse(component.isStarted());

      latch.setCount(1);

      startHTTPServer();

      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertTrue(component.isStarted());

   }


   @Test
   public void testValidateCommand() throws Throwable {
      AtomicInteger purePing = new AtomicInteger(0);
      AtomicInteger isReacheable = new AtomicInteger(0);
      NetworkHealthCheck myCheck =
         new NetworkHealthCheck() {
            @Override
            protected boolean isReachable(InetAddress address) throws IOException {
               isReacheable.incrementAndGet();
               return false;
            }

            @Override
            public boolean purePing(InetAddress address) throws IOException, InterruptedException {
               purePing.incrementAndGet();
               return false;
            }
         };

      myCheck.setIpv4Command("whatever");
      myCheck.setIpv6Command("whatever");

      myCheck.check(InetAddress.getByName("127.0.0.1"));

      assertEquals(0, isReacheable.get());
      assertEquals(1, purePing.get());
   }

   @Test
   public void testValidateIsReachable() throws Throwable {
      AtomicInteger purePing = new AtomicInteger(0);
      AtomicInteger isReacheable = new AtomicInteger(0);
      NetworkHealthCheck myCheck =
         new NetworkHealthCheck() {
            @Override
            protected boolean isReachable(InetAddress address) throws IOException {
               isReacheable.incrementAndGet();
               return true;
            }

            @Override
            public boolean purePing(InetAddress address) throws IOException, InterruptedException {
               purePing.incrementAndGet();
               return false;
            }
         };

      //myCheck.setIpv4Command("whatever");
      //myCheck.setIpv6Command("whatever");

      myCheck.check(InetAddress.getByName("127.0.0.1"));

      assertEquals(1, isReacheable.get());
      assertEquals(0, purePing.get());
   }

   @Test
   @Timeout(30)
   public void testPurePingTimeout() throws Exception {
      int timeout = 2000;

      // Add a "tolerance" for Windows, since it reports less time spent in timeout than the networkTimeout parameter (around 200ms)
      int tolerance = 500;

      NetworkHealthCheck check = new NetworkHealthCheck(null, 100, timeout);

      long time = System.currentTimeMillis();
      //[RFC1166] reserves the address block 192.0.2.0/24 for test.
      assertFalse(check.purePing(InetAddress.getByName("192.0.2.0")));

      assertTrue(System.currentTimeMillis() - time >= (timeout - tolerance));
   }

}
