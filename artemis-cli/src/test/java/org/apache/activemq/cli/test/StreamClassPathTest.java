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

package org.apache.activemq.cli.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;

import org.apache.activemq.artemis.cli.commands.Create;
import org.apache.activemq.artemis.cli.commands.messages.Producer;
import org.junit.jupiter.api.Test;

public class StreamClassPathTest {

   /**
    * Validate if all the known resources are available on the classpath for the jar
    */
   @Test
   public void testFindStreams() throws Exception {
      testStream(Create.class, Create.BIN_ARTEMIS_CMD);
      testStream(Create.class, Create.BIN_ARTEMIS_SERVICE_EXE);
      testStream(Create.class, Create.BIN_ARTEMIS_SERVICE_EXE_CONFIG);
      testStream(Create.class, Create.BIN_ARTEMIS_SERVICE_XML);
      testStream(Create.class, "etc/" + Create.ETC_ARTEMIS_PROFILE_CMD);
      testStream(Create.class, "etc/" + Create.ETC_ARTEMIS_UTILITY_PROFILE_CMD);
      testStream(Create.class, Create.BIN_ARTEMIS);
      testStream(Create.class, Create.BIN_ARTEMIS_SERVICE);
      testStream(Create.class, "etc/" + Create.ETC_ARTEMIS_PROFILE);
      testStream(Create.class, "etc/" + Create.ETC_ARTEMIS_UTILITY_PROFILE);
      testStream(Create.class, "etc/" + Create.ETC_LOG4J2_PROPERTIES);
      testStream(Create.class, "etc/" + Create.ETC_BOOTSTRAP_XML);
      testStream(Create.class, "etc/" + Create.ETC_MANAGEMENT_XML);
      testStream(Create.class, "etc/" + Create.ETC_BROKER_XML);
      testStream(Create.class, "etc/" + Create.ETC_ARTEMIS_ROLES_PROPERTIES);
      testStream(Create.class, "etc/" + Create.ETC_ARTEMIS_USERS_PROPERTIES);
      testStream(Create.class, Create.ETC_REPLICATED_PRIMARY_SETTINGS_TXT);
      testStream(Create.class, Create.ETC_REPLICATED_BACKUP_SETTINGS_TXT);
      testStream(Create.class, Create.ETC_SHARED_STORE_SETTINGS_TXT);
      testStream(Create.class, Create.ETC_CLUSTER_SECURITY_SETTINGS_TXT);
      testStream(Create.class, Create.ETC_CLUSTER_SETTINGS_TXT);
      testStream(Create.class, Create.ETC_CLUSTER_STATIC_SETTINGS_TXT);
      testStream(Create.class, Create.ETC_CONNECTOR_SETTINGS_TXT);
      testStream(Create.class, Create.ETC_BOOTSTRAP_WEB_SETTINGS_TXT);
      testStream(Create.class, Create.ETC_JOURNAL_BUFFER_SETTINGS);
      testStream(Create.class, Create.ETC_AMQP_ACCEPTOR_TXT);
      testStream(Create.class, Create.ETC_MQTT_ACCEPTOR_TXT);
      testStream(Create.class, Create.ETC_HORNETQ_ACCEPTOR_TXT);
      testStream(Create.class, Create.ETC_STOMP_ACCEPTOR_TXT);
      testStream(Create.class, Create.ETC_PING_TXT);
      testStream(Create.class, Create.ETC_COMMENTED_PING_TXT);
      testStream(Create.class, Create.ETC_GLOBAL_MAX_SPECIFIED_TXT);
      testStream(Create.class, Create.ETC_GLOBAL_MAX_DEFAULT_TXT);
      testStream(Create.class, "etc/" + Create.ETC_JOLOKIA_ACCESS_XML);
      testStream(Create.class, Create.ETC_DATABASE_STORE_TXT);
      testStream(Producer.class, Producer.DEMO_TEXT);

   }

   private void testStream(Class clazz, String source) throws Exception {
      InputStream in = clazz.getResourceAsStream(source);
      assertNotNull(in, source + " not found");
      in.close();
   }
}
