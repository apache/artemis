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
package org.apache.activemq.artemis.core.server;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ActiveMQServersTest {

   @Test
   public void testNewActiveMQServerFromConfigURLRespectsXmlPersistenceEnabled(@TempDir Path tempDir) throws Exception {
      Path configFile = tempDir.resolve("broker.xml");
      String xml = "<configuration xmlns=\"urn:activemq\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
         "               xsi:schemaLocation=\"urn:activemq ../../../../activemq-server/src/main/resources/schema/artemis-server.xsd\">\n" +
         "   <core xmlns=\"urn:activemq:core\">\n" +
         "      <persistence-enabled>false</persistence-enabled>\n" +
         "      <acceptors>\n" +
         "         <acceptor name=\"vm\">vm://0</acceptor>\n" +
         "      </acceptors>\n" +
         "   </core>\n" +
         "</configuration>\n";
      Files.writeString(configFile, xml);

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());
      ActiveMQServer server = ActiveMQServers.newActiveMQServer(configFile.toUri().toURL().toExternalForm(), null, securityManager);

      assertFalse(server.getConfiguration().isPersistenceEnabled(),
         "persistence-enabled should be false as specified in XML configuration");
   }
}
