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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.fail;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionImpl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQSessionContext;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAPrepareMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.XidCodecSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InvalidCoreClientTest extends ActiveMQTestBase {

   private final Map<String, AddressSettings> addressSettings = new HashMap<>();
   private final SimpleString atestq = SimpleString.of("BasicXaTestq");
   private ActiveMQServer messagingService;
   private ClientSession clientSession;
   private ClientSessionFactory sessionFactory;
   private Configuration configuration;
   private ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      addressSettings.clear();

      configuration = createDefaultNettyConfig();

      messagingService = createServer(true, configuration, -1, -1, addressSettings);

      // start the server
      messagingService.start();

      locator = createInVMNonHALocator();
      sessionFactory = createSessionFactory(locator);

      clientSession = addClientSession(sessionFactory.createSession(true, false, false));

      clientSession.createQueue(QueueConfiguration.of(atestq));
   }

   @Test
   public void testInvalidBufferXIDInvalidSize() throws Exception {
      internalTestInvalidXID(false);
   }

   @Test
   public void testInvalidBufferXIDNegative() throws Exception {
      internalTestInvalidXID(true);
   }

   private void internalTestInvalidXID(boolean useNegative) throws Exception {

      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer = clientSession2.createProducer(atestq);
      ClientMessage m1 = createTextMessage(clientSession2, "m1");
      clientProducer.send(m1);

      Xid xid = newXID();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();

      ClientConsumer clientConsumer = clientSession.createConsumer(atestq);

      ClientMessage message = clientConsumer.receive(5000);
      message.acknowledge();
      clientSession.end(xid, XAResource.TMSUCCESS);
      Channel channel = ((ActiveMQSessionContext) (((ClientSessionImpl) clientSession).getSessionContext())).getSessionChannel();

      AtomicInteger connFailure = new AtomicInteger(0);
      clientSession.addFailureListener(new SessionFailureListener() {
         @Override
         public void beforeReconnect(ActiveMQException exception) {

         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {

         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
            connFailure.incrementAndGet();
         }
      });

      SessionXAPrepareMessage packet = new SessionXAPrepareMessage(xid) {
         @Override
         public void encodeRest(final ActiveMQBuffer buffer) {

            ActiveMQBuffer bufferTmp = ActiveMQBuffers.dynamicBuffer(255);

            XidCodecSupport.encodeXid(xid, bufferTmp);
            if (useNegative) {
               bufferTmp.setByte(4, (byte) 0x0F);
            } else {
               bufferTmp.setByte(4, (byte) 0xFF);
            }
            byte[] bytes = new byte[bufferTmp.readableBytes()];
            bufferTmp.readBytes(bytes);
            buffer.writeBytes(bytes);
         }
      };

      try {
         channel.sendBlocking(packet, PacketImpl.SESS_XA_RESP);
         fail("Failure expected");
      } catch (Exception failed) {
      }

      // the connection was supposed to fail on disconnect
      Wait.assertEquals(1, connFailure::get);

   }
}
