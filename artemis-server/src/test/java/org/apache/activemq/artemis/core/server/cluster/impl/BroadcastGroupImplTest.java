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
package org.apache.activemq.artemis.core.server.cluster.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.BroadcastEndpoint;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.Test;

/**
 * Test the {@link BroadcastGroupImpl}.
 */
public class BroadcastGroupImplTest extends ServerTestBase {

   static class BroadcastEndpointFactoryImpl implements BroadcastEndpointFactory {

      private static final long serialVersionUID = 1L;
      int sent = 0;

      @Override
      public BroadcastEndpoint createBroadcastEndpoint() throws Exception {
         return new BroadcastEndpointImpl(this);
      }

   }

   static class BroadcastEndpointImpl implements BroadcastEndpoint {

      private BroadcastEndpointFactoryImpl factory;

      BroadcastEndpointImpl(BroadcastEndpointFactoryImpl factory) {
         this.factory = factory;
      }

      @Override
      public void openClient() {
      }

      @Override
      public void openBroadcaster() {
      }

      @Override
      public void close(boolean isBroadcast) {
      }

      @Override
      public void broadcast(byte[] data) throws Exception {
         if (data == null) {
            fail("Attempted to send datagram with null content");
         }
         if (data.length == 0) {
            fail("Attempted to send datagram with blank content");
         }
         if (data.length > 1500) { // Common MTU size, not specification!
            fail("Attempted to send a datagram with " + data.length + " bytes");
         }
         factory.sent++;
      }

      @Override
      public byte[] receiveBroadcast() throws Exception {
         return null;
      }

      @Override
      public byte[] receiveBroadcast(long time, TimeUnit unit) throws Exception {
         return null;
      }
   }

   /**
    * Test the broadcasted packages length.
    * <p>
    * Broadcast and MultiCast techniques are commonly limited in size by underlying hardware. Broadcast and MultiCast
    * protocols are typically not guaranteed (UDP) and as such large packages may be silently discarded by underlying
    * hardware.
    * <p>
    * This test validates that Artemis Server does not broadcast packages above a size of 1500 bytes. The limit is not
    * derived from any normative documents, but is rather derived from common MTU for network equipment.
    */
   @Test
   public void testBroadcastDatagramLength() throws Throwable {
      BroadcastEndpointFactoryImpl befi;
      befi = new BroadcastEndpointFactoryImpl();
      InVMNodeManager node;
      node = new InVMNodeManager(false);
      String name;
      name = "BroadcastGroupImplTest";
      BroadcastGroupImpl test;
      test = new BroadcastGroupImpl(node, name, 1000, null, befi);
      TransportConfiguration tcon;
      tcon = new TransportConfiguration(getClass().getName());
      test.addConnector(tcon);
      // Broadcast
      test.broadcastConnectors();
      // Make sure we sent one package
      assertEquals(1, befi.sent, "Incorrect number of sent datagrams");
   }
}
