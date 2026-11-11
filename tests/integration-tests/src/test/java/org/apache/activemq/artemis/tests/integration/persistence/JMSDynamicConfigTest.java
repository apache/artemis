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
package org.apache.activemq.artemis.tests.integration.persistence;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.ConnectionFactoryConfigurationImpl;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.Test;

public class JMSDynamicConfigTest extends JMSTestBase {

   @Override
   protected boolean usePersistence() {
      return true;
   }



   @Test
   public void testStart() throws Exception {
      List<String> connectors = new ArrayList<>();

      connectors.add("invm");

      ConnectionFactoryConfiguration cfg = new ConnectionFactoryConfigurationImpl().setName("tst").setConnectorNames(connectors).setBindings("tt");
      jmsServer.createConnectionFactory(true, cfg, "tst");

      assertNotNull(namingContext.lookup("tst"));
      jmsServer.removeConnectionFactoryFromBindingRegistry("tst");

      try {
         namingContext.lookup("tst");
         fail("failure expected");
      } catch (NamingException excepted) {
      }

      jmsServer.stop();

      OperationContextImpl.clearContext();
      jmsServer.start();

      try {
         namingContext.lookup("tst");
         fail("failure expected");
      } catch (NamingException excepted) {
      }
   }

}
