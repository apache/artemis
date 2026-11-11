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

package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.AMQ_5_11;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageAccessor;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OldOpenWireTest extends ClasspathBase {

   EmbeddedActiveMQ server;

   @BeforeEach
   public void setServer() throws Throwable {

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.setJournalType(JournalType.NIO);
      configuration.addAcceptorConfiguration("artemis", "tcp://0.0.0.0:61616");
      configuration.setSecurityEnabled(false);
      configuration.setPersistenceEnabled(false);

      server = new EmbeddedActiveMQ();
      server.setConfiguration(configuration);
      server.start();
      server.getActiveMQServer().addAddressInfo(new AddressInfo("Test").addRoutingType(RoutingType.ANYCAST));
      server.getActiveMQServer().createQueue(QueueConfiguration.of("Test").setDurable(true).setRoutingType(RoutingType.ANYCAST));

      server.getActiveMQServer().addAddressInfo(new AddressInfo("DLQ").addRoutingType(RoutingType.ANYCAST));
      server.getActiveMQServer().createQueue(QueueConfiguration.of("DLQ").setDurable(true).setRoutingType(RoutingType.ANYCAST));

      server.getActiveMQServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("DLQ")));
   }

   @AfterEach
   public void shutdownServer() throws Throwable {
      if (server != null) {
         server.stop();
      }
   }

   @Test
   public void testIDOverflow() throws Throwable {
      Queue queue = server.getActiveMQServer().locateQueue("Test");
      Queue dlq = server.getActiveMQServer().locateQueue("DLQ");

      NullStorageAccessor.setNextID(server.getActiveMQServer().getStorageManager(), Integer.MAX_VALUE);
      evaluate(getClasspath(SNAPSHOT), "oldOpenWire/sendCore.groovy", "0", "10");
      Wait.assertEquals(10L, queue::getMessageCount, 1000, 10);

      NullStorageAccessor.setNextID(server.getActiveMQServer().getStorageManager(), Integer.MAX_VALUE * 2L);
      evaluate(getClasspath(SNAPSHOT), "oldOpenWire/sendCore.groovy", "10", "20");

      Wait.assertEquals(20L, queue::getMessageCount, 1000, 10);

      evaluate(getClasspath(AMQ_5_11), "oldOpenWire/receiveOW.groovy", "20");

      Wait.assertEquals(0L, queue::getMessageCount, 1000, 100);
      assertEquals(0L, dlq.getMessageCount());
   }

}