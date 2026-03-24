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
package org.apache.activemq.artemis.tests.integration.management;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientRequestor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.Assert;
import org.junit.Before;

public abstract class SecurityManagementTestBase extends ActiveMQTestBase {


   private ActiveMQServer server;





   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      server = setupAndStartActiveMQServer();
   }

   protected abstract ActiveMQServer setupAndStartActiveMQServer() throws Exception;

   protected void doSendManagementMessage(final String user,
                                          final String password,
                                          final boolean expectSuccess) throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = locator.createSessionFactory();
      try {
         ClientSession session = null;
         if (user == null) {
            session = sf.createSession(false, true, true);
         } else {
            session = sf.createSession(user, password, false, true, true, false, 1);
         }

         session.start();

         ClientRequestor requestor = new ClientRequestor(session, ActiveMQDefaultConfiguration.getDefaultManagementAddress());

         ClientMessage mngmntMessage = session.createMessage(false);
         ManagementHelper.putAttribute(mngmntMessage, ResourceNames.BROKER, "started");
         ClientMessage reply = requestor.request(mngmntMessage, 500);
         if (expectSuccess) {
            Assert.assertNotNull(reply);
            Assert.assertTrue((Boolean) ManagementHelper.getResult(reply));
         } else {
            Assert.assertNull(reply);
         }

         requestor.close();
      } catch (Exception e) {
         if (expectSuccess) {
            Assert.fail("got unexpected exception " + e.getClass() + ": " + e.getMessage());
            e.printStackTrace();
         }
      } finally {
         sf.close();
      }
   }



}
