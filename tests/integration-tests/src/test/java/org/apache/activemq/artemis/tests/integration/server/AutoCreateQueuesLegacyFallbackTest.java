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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests JMS-client behavior when {@code ActiveMQSession.createQueue} falls back to the
 * HornetQ 1.x compatibility path. Tests vary {@code auto-create-queues} and
 * {@code auto-create-addresses} flags on specific and catch-all matches to verify the
 * fix that prevents the legacy fallback from auto-creating prefixed queues.
 */
public class AutoCreateQueuesLegacyFallbackTest extends ActiveMQTestBase {

   private static final SimpleString DLA = SimpleString.of("DLA");
   private static final String QUEUE_NAME = "test.test2.somequeue";
   private static final String PREFIXED_QUEUE_NAME = "jms.queue." + QUEUE_NAME;
   private static final String DLQ_SUFFIX = ".DLQ";

   private static final int COMPAT_ACCEPTOR_PORT = TransportConstants.DEFAULT_PORT + 1;
   private static final String COMPAT_ACCEPTOR_URL =
      "tcp://localhost:" + COMPAT_ACCEPTOR_PORT + "?anycastPrefix=jms.queue.;multicastPrefix=jms.topic.";

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false);
      server.getConfiguration().setAddressQueueScanPeriod(100);
   }

   /**
    * Specific match: auto-create FALSE. Catch-all: auto-create TRUE. Verifies the legacy
    * fallback no longer auto-creates the prefixed queue when the specific match disables
    * auto-create.
    */
   @Test
   public void testAutoCreateFalseOnSpecificMatch() throws Exception {
      AddressSettings specific = baseDlaSettings()
         .setAutoCreateQueues(false)
         .setAutoCreateAddresses(false);
      server.getAddressSettingsRepository().addMatch("test.test2.#", specific);
      server.getAddressSettingsRepository().addMatch("#", baseDlaSettings()
         .setAutoCreateQueues(true)
         .setAutoCreateAddresses(true));

      server.start();

      runJmsScenarioAndDump("auto-create-queues=FALSE on test.test2.#");

      assertNull(server.locateQueue(SimpleString.of(PREFIXED_QUEUE_NAME)),
         "legacy fallback must not auto-create the prefixed queue when the un-prefixed match disables auto-create");
      assertNull(server.locateQueue(SimpleString.of(QUEUE_NAME)),
         "un-prefixed queue must not be auto-created either — auto-create-queues=false on the specific match");
   }

   /**
    * Specific match: auto-create TRUE. Catch-all: auto-create TRUE. The un-prefixed queue
    * is auto-created normally without entering the legacy fallback path.
    */
   @Test
   public void testAutoCreateTrueOnSpecificMatch() throws Exception {
      server.getAddressSettingsRepository().addMatch("test.test2.#", baseDlaSettings()
         .setAutoCreateQueues(true)
         .setAutoCreateAddresses(true));
      server.getAddressSettingsRepository().addMatch("#", baseDlaSettings()
         .setAutoCreateQueues(true)
         .setAutoCreateAddresses(true));

      server.start();

      runJmsScenarioAndDump("auto-create-queues=TRUE on test.test2.#");

      assertNotNull(server.locateQueue(SimpleString.of(QUEUE_NAME)),
         "un-prefixed queue '" + QUEUE_NAME + "' should be auto-created");
      assertNull(server.locateQueue(SimpleString.of(PREFIXED_QUEUE_NAME)),
         "legacy-prefixed queue '" + PREFIXED_QUEUE_NAME + "' must not appear");
   }

   /**
    * Specific match: auto-create TRUE. Catch-all: auto-create FALSE. The un-prefixed queue
    * is auto-created via the specific match; legacy fallback is never entered.
    */
   @Test
   public void testCatchAllFalseSpecificTrue() throws Exception {
      server.getAddressSettingsRepository().addMatch("test.test2.#", baseDlaSettings()
         .setAutoCreateQueues(true)
         .setAutoCreateAddresses(true));
      server.getAddressSettingsRepository().addMatch("#", baseDlaSettings()
         .setAutoCreateQueues(false)
         .setAutoCreateAddresses(false));

      server.start();

      runJmsScenarioAndDump("catch-all FALSE / test.test2.# TRUE");

      assertNotNull(server.locateQueue(SimpleString.of(QUEUE_NAME)),
         "un-prefixed queue '" + QUEUE_NAME + "' should be auto-created");
      assertNull(server.locateQueue(SimpleString.of(PREFIXED_QUEUE_NAME)),
         "legacy-prefixed queue '" + PREFIXED_QUEUE_NAME + "' must not appear");
   }

   /**
    * Specific match and catch-all: both auto-create FALSE. No queue is created by either
    * the normal or legacy fallback path.
    */
   @Test
   public void testBothMatchesFalse() throws Exception {
      server.getAddressSettingsRepository().addMatch("test.test2.#", baseDlaSettings()
         .setAutoCreateQueues(false)
         .setAutoCreateAddresses(false));
      server.getAddressSettingsRepository().addMatch("#", baseDlaSettings()
         .setAutoCreateQueues(false)
         .setAutoCreateAddresses(false));

      server.start();

      runJmsScenarioAndDump("both FALSE");

      assertNull(server.locateQueue(SimpleString.of(QUEUE_NAME)),
         "'" + QUEUE_NAME + "' must not be auto-created when auto-create is disabled");
      assertNull(server.locateQueue(SimpleString.of(PREFIXED_QUEUE_NAME)),
         "'" + PREFIXED_QUEUE_NAME + "' must not be auto-created by the legacy fallback either");
   }

   /**
    * Tests legacy fallback through an acceptor with {@code anycastPrefix=jms.queue.}.
    * Verifies the fix doesn't change behavior for deployments with the acceptor-side
    * workaround.
    */
   @Test
   public void testLegacyFallbackThroughAcceptorWithAnycastPrefix() throws Exception {
      server.getAddressSettingsRepository().addMatch("test.test2.#", baseDlaSettings()
         .setAutoCreateQueues(false)
         .setAutoCreateAddresses(false));
      server.getAddressSettingsRepository().addMatch("#", baseDlaSettings()
         .setAutoCreateQueues(true)
         .setAutoCreateAddresses(true));

      server.getConfiguration().addAcceptorConfiguration("compat", COMPAT_ACCEPTOR_URL);
      server.start();

      runJmsScenarioAndDump("acceptor anycastPrefix=jms.queue. + specific FALSE",
         "tcp://localhost:" + COMPAT_ACCEPTOR_PORT, QUEUE_NAME);

      assertNull(server.locateQueue(SimpleString.of(QUEUE_NAME)),
         "un-prefixed queue must not be auto-created — specific match disables auto-create");
      assertNull(server.locateQueue(SimpleString.of(PREFIXED_QUEUE_NAME)),
         "legacy-prefixed queue must not appear — broker's anycastPrefix already normalizes the address");
   }

   /**
    * Happy-path cover for legacy-1.x clients ({@code enable1xPrefixes=true}) on an
    * acceptor with {@code anycastPrefix=jms.queue.}. The normal lookup succeeds via
    * {@code isAutoCreateQueues=true}, so the legacy fallback is NOT entered here —
    * the compatibility-path fix is not exercised by this test. Compat-path coverage
    * in the same client configuration lives in
    * {@link #testAnycastPrefixClientHitsCompatPathCleanly}.
    */
   @Test
   public void testAnycastPrefixClientStillAutoCreates() throws Exception {
      server.getAddressSettingsRepository().addMatch("test.test2.#", baseDlaSettings()
         .setAutoCreateQueues(true)
         .setAutoCreateAddresses(true));
      server.getAddressSettingsRepository().addMatch("#", baseDlaSettings()
         .setAutoCreateQueues(true)
         .setAutoCreateAddresses(true));

      server.getConfiguration().addAcceptorConfiguration("compat", COMPAT_ACCEPTOR_URL);
      server.start();

      runJmsScenarioAndDump("acceptor anycastPrefix + client enable1xPrefixes",
         "tcp://localhost:" + COMPAT_ACCEPTOR_PORT + "?enable1xPrefixes=true", QUEUE_NAME);

      assertNotNull(server.locateQueue(SimpleString.of(QUEUE_NAME)),
         "un-prefixed queue '" + QUEUE_NAME + "' should be auto-created — prefix is stripped at the broker");
      assertNull(server.locateQueue(SimpleString.of(PREFIXED_QUEUE_NAME)),
         "legacy-prefixed queue must not appear — the acceptor normalizes back to the un-prefixed name");
   }

   /**
    * Forces the legacy fallback for {@code enable1xPrefixes=true} clients on an
    * {@code anycastPrefix=jms.queue.} acceptor: both matches set auto-create=false so
    * both regular {@code lookupQueue} calls return null and
    * {@code internalCreateQueueCompatibility} runs. The post-fix compat queueQuery
    * is normalized by the broker back to the un-prefixed name, finds nothing, and
    * the JMS call throws cleanly. Guards against accidental auto-creation when the
    * compat path is exercised in this client configuration.
    */
   @Test
   public void testAnycastPrefixClientHitsCompatPathCleanly() throws Exception {
      server.getAddressSettingsRepository().addMatch("test.test2.#", baseDlaSettings()
         .setAutoCreateQueues(false)
         .setAutoCreateAddresses(false));
      server.getAddressSettingsRepository().addMatch("#", baseDlaSettings()
         .setAutoCreateQueues(false)
         .setAutoCreateAddresses(false));

      server.getConfiguration().addAcceptorConfiguration("compat", COMPAT_ACCEPTOR_URL);
      server.start();

      runJmsScenarioAndDump("acceptor anycastPrefix + client enable1xPrefixes + all FALSE",
         "tcp://localhost:" + COMPAT_ACCEPTOR_PORT + "?enable1xPrefixes=true", QUEUE_NAME);

      assertNull(server.locateQueue(SimpleString.of(QUEUE_NAME)),
         "un-prefixed queue must not be auto-created — all auto-create disabled");
      assertNull(server.locateQueue(SimpleString.of(PREFIXED_QUEUE_NAME)),
         "legacy-prefixed queue must not appear — compat path returns null cleanly");
   }

   private static AddressSettings baseDlaSettings() {
      return new AddressSettings()
         .setAutoCreateDeadLetterResources(true)
         .setDeadLetterAddress(DLA)
         .setDeadLetterQueueSuffix(SimpleString.of(DLQ_SUFFIX))
         .setMaxDeliveryAttempts(1);
   }

   /**
    * Runs a JMS produce/receive/rollback flow to trigger DLA resource auto-creation and
    * dumps the resulting queue/address inventory. Captures JMS exceptions for diagnostics.
    */
   private void runJmsScenarioAndDump(String scenarioLabel) throws Exception {
      runJmsScenarioAndDump(scenarioLabel, "vm://0", QUEUE_NAME);
   }

   private void runJmsScenarioAndDump(String scenarioLabel, String brokerUrl, String jmsQueueName) throws Exception {
      Exception sendError = null;
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(brokerUrl);
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(jmsQueueName);
         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("hello"));
         session.commit();

         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         Message message = consumer.receive(2000);
         if (message != null) {
            session.rollback();
            // give the broker time to push to DLA & auto-create DLA resources
            Thread.sleep(500);
         }
      } catch (Exception e) {
         sendError = e;
      }

      String[] queues = server.getActiveMQServerControl().getQueueNames();
      String[] addresses = server.getActiveMQServerControl().getAddressNames();
      Arrays.sort(queues);
      Arrays.sort(addresses);
      System.out.println("==== [" + scenarioLabel + "] ====");
      System.out.println("  queues:    " + Arrays.toString(queues));
      System.out.println("  addresses: " + Arrays.toString(addresses));
      if (sendError != null) {
         System.out.println("  JMS flow threw: " + sendError);
      }
      System.out.println("==== /[" + scenarioLabel + "] ====");

      if (queues.length == 0 && sendError == null) {
         fail("[" + scenarioLabel + "] no queues at all and no JMS error — test setup is broken");
      }
   }
}
