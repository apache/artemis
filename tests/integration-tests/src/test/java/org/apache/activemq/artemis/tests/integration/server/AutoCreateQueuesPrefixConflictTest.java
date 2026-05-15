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
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Locks in the JMS-client behavior when two address-settings match the destination of a
 * {@code createQueue} call:
 * <ul>
 *   <li>a specific match {@code test.test2.#}
 *   <li>a catch-all {@code #}
 * </ul>
 * The producer is a plain Artemis CORE JMS client targeting {@code test.test2.somequeue}
 * with no client-side prefix manipulation ({@code enable1xPrefixes} at its default {@code false}).
 * Each test varies the {@code auto-create-queues} / {@code auto-create-addresses} flags on
 * the two matches and asserts which (if any) broker-side queue is created.
 *
 * <p>Historical bug: when {@code auto-create-queues=false} on the specific match but
 * {@code true} on the catch-all, {@code ActiveMQSession.internalCreateQueue} would fall back
 * to its HornetQ 1.x compatibility path and call {@code lookupQueue("jms.queue." + name)}.
 * That prefixed lookup matched the catch-all, so the JMS client handed the producer a
 * {@code Queue} whose address was {@code jms.queue.test.test2.somequeue}, the producer sent
 * to that address, and the broker auto-created the queue under the prefixed name — silently
 * routing under a legacy-namespaced address despite the specific match disabling auto-create.
 *
 * <p>The fix in {@code ActiveMQSession.internalCreateQueueCompatibility} gates the legacy
 * path on existence ({@code session.queueQuery(...).isExists()}) instead of on
 * address-settings, so the prefixed fallback can no longer trigger auto-create. These tests
 * pin the post-fix outcomes across all four combinations of the two flags.
 */
public class AutoCreateQueuesPrefixConflictTest extends ActiveMQTestBase {

   private static final SimpleString DLA = SimpleString.of("DLA");
   private static final String QUEUE_NAME = "test.test2.somequeue";
   private static final String PREFIXED_QUEUE_NAME = "jms.queue." + QUEUE_NAME;
   private static final String DLQ_SUFFIX = ".DLQ";

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false);
      server.getConfiguration().setAddressQueueScanPeriod(100);
   }

   /**
    * Specific match: auto-create FALSE. Catch-all: auto-create TRUE. The historical bug
    * created {@code jms.queue.test.test2.somequeue} on the broker because the legacy lookup
    * matched the catch-all. After the fix the specific match's auto-create=false wins:
    * {@code internalCreateQueueCompatibility} returns null (no pre-existing prefixed queue),
    * {@code internalCreateQueue} throws {@code JMSException}, and the broker creates nothing.
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
         "legacy-prefixed queue must not be auto-created when auto-create-queues=false on the specific match");
      assertNull(server.locateQueue(SimpleString.of(QUEUE_NAME)),
         "un-prefixed queue must not exist either — auto-create=false on the specific match wins");
   }

   /**
    * Both matches: auto-create TRUE. The specific match's queueQuery response carries
    * {@code isAutoCreateQueues=true}, so the JMS client uses the un-prefixed name and the
    * broker auto-creates {@code test.test2.somequeue}. No legacy fallback is triggered.
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
    * Specific match: auto-create TRUE. Catch-all: auto-create FALSE. The specific match
    * applies to the un-prefixed address-settings lookup, so {@code isAutoCreateQueues=true}
    * is returned, the JMS client uses the un-prefixed name, and the broker auto-creates
    * {@code test.test2.somequeue}. The catch-all's auto-create=false is never consulted by
    * the producer path.
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
    * Both matches: auto-create FALSE. The un-prefixed lookup returns
    * {@code isExists=false}, {@code internalCreateQueueCompatibility} finds no pre-existing
    * legacy-prefixed queue either, and {@code internalCreateQueue} throws
    * {@code JMSException}. No queue is created.
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
         "'" + QUEUE_NAME + "' must not be auto-created when both matches disable it");
      assertNull(server.locateQueue(SimpleString.of(PREFIXED_QUEUE_NAME)),
         "'" + PREFIXED_QUEUE_NAME + "' must not appear either — both matches disable auto-create");
   }

   private static AddressSettings baseDlaSettings() {
      return new AddressSettings()
         .setAutoCreateDeadLetterResources(true)
         .setDeadLetterAddress(DLA)
         .setDeadLetterQueueSuffix(SimpleString.of(DLQ_SUFFIX))
         .setMaxDeliveryAttempts(1);
   }

   /**
    * Runs a CORE JMS produce/receive/rollback flow against {@link #QUEUE_NAME} so that the
    * single message crosses {@code max-delivery-attempts=1} and triggers DLA resource
    * auto-creation, then dumps the resulting queue/address inventory for diagnostic output.
    * Any JMS exception is captured rather than propagated so the assertions can observe the
    * broker-side state regardless of whether the producer flow succeeded.
    */
   private void runJmsScenarioAndDump(String scenarioLabel) throws Exception {
      Exception sendError = null;
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(QUEUE_NAME);
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
