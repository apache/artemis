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

import java.lang.ref.WeakReference;

import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SessionCloseOnGCTest extends ActiveMQTestBase {

   private ActiveMQServer server;
   private ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false);
      server.start();

      locator = createInVMNonHALocator();
   }

   /**
    * Make sure Sessions are not leaking after closed. Also... we want to make sure the SessionFactory will close itself
    * when there are not references into it
    */
   @Test
   public void testValidateFactoryGC1() throws Exception {
      ClientSessionFactory factory = locator.createSessionFactory();

      ClientSession s1 = factory.createSession();
      ClientSession s2 = factory.createSession();

      s1.close();
      s2.close();

      WeakReference<ClientSession> wrs1 = new WeakReference<>(s1);
      WeakReference<ClientSession> wrs2 = new WeakReference<>(s2);

      s1 = null;
      s2 = null;

      locator.close();

      locator = null;
      ActiveMQTestBase.checkWeakReferences(wrs1, wrs2);

      WeakReference<ClientSessionFactory> fref = new WeakReference<>(factory);

      factory.close();

      factory = null;

      ActiveMQTestBase.checkWeakReferences(fref, wrs1, wrs2);
   }

   @Test
   public void testValidateFactoryGC2() throws Exception {
      locator.setUseGlobalPools(false);

      ClientSessionFactory factory = locator.createSessionFactory();

      ClientSession s1 = factory.createSession();
      ClientSession s2 = factory.createSession();

      s1.close();
      s2.close();

      WeakReference<ClientSession> wrs1 = new WeakReference<>(s1);
      WeakReference<ClientSession> wrs2 = new WeakReference<>(s2);

      s1 = null;
      s2 = null;

      locator.close();

      locator = null;
      ActiveMQTestBase.checkWeakReferences(wrs1, wrs2);

      WeakReference<ClientSessionFactory> fref = new WeakReference<>(factory);

      factory.close();

      factory = null;

      ActiveMQTestBase.checkWeakReferences(fref, wrs1, wrs2);
   }

   @Test
   public void testValidateFactoryGC3() throws Exception {
      ClientSessionFactory factory = locator.createSessionFactory();

      ClientSession s1 = factory.createSession();
      ClientSession s2 = factory.createSession();

      s1.close();
      s2.close();

      WeakReference<ClientSession> wrs1 = new WeakReference<>(s1);
      WeakReference<ClientSession> wrs2 = new WeakReference<>(s2);

      s1 = null;
      s2 = null;

      locator.close();

      locator = null;
      ActiveMQTestBase.checkWeakReferences(wrs1, wrs2);

      WeakReference<ClientSessionFactory> fref = new WeakReference<>(factory);

      factory = null;

      ActiveMQTestBase.checkWeakReferences(fref, wrs1, wrs2);
   }

   @Test
   public void testValidateFactoryGC4() throws Exception {
      ClientSessionFactory factory = locator.createSessionFactory();

      ClientSession s1 = factory.createSession();
      ClientSession s2 = factory.createSession();

      WeakReference<ClientSession> wrs1 = new WeakReference<>(s1);
      WeakReference<ClientSession> wrs2 = new WeakReference<>(s2);

      s1 = null;
      s2 = null;

      locator.close();

      locator = null;
      ActiveMQTestBase.checkWeakReferences(wrs1, wrs2);

      WeakReference<ClientSessionFactory> fref = new WeakReference<>(factory);

      factory = null;

      ActiveMQTestBase.checkWeakReferences(fref, wrs1, wrs2);
   }

   @Test
   public void testValidateFactoryGC5() throws Exception {
      ClientSessionFactory factory = locator.createSessionFactory();

      WeakReference<ClientSessionFactory> fref = new WeakReference<>(factory);

      factory = null;

      locator.close();

      locator = null;
      ActiveMQTestBase.checkWeakReferences(fref);
   }
}
