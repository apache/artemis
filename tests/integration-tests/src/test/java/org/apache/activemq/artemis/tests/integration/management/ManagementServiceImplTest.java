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

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.management.impl.ManagementServiceImpl;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.tests.integration.server.FakeStorageManager;
import org.apache.activemq.artemis.tests.unit.core.postoffice.impl.FakeQueue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.Assert;
import org.junit.Test;

public class ManagementServiceImplTest extends ActiveMQTestBase {

   @Test
   public void testHandleManagementMessageWithOperation() throws Exception {
      String queue = RandomUtil.randomString();
      String address = RandomUtil.randomString();

      Configuration config = createBasicConfig().setJMXManagementEnabled(false);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      // invoke attribute and operation on the server
      CoreMessage message = new CoreMessage(1, 100);
      ManagementHelper.putOperationInvocation(message, ResourceNames.BROKER, "createQueue", queue, address);

      Message reply = server.getManagementService().handleMessage(message);

      Assert.assertTrue(ManagementHelper.hasOperationSucceeded(reply));
   }

   @Test
   public void testHandleManagementMessageWithOperationWhichFails() throws Exception {
      Configuration config = createBasicConfig().setJMXManagementEnabled(false);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      // invoke attribute and operation on the server
      CoreMessage message = new CoreMessage(1, 100);
      ManagementHelper.putOperationInvocation(message, ResourceNames.BROKER, "thereIsNoSuchOperation");

      ICoreMessage reply = server.getManagementService().handleMessage(message);

      Assert.assertFalse(ManagementHelper.hasOperationSucceeded(reply));
      Assert.assertNotNull(ManagementHelper.getResult(reply));
   }

   @Test
   public void testHandleManagementMessageWithUnknowResource() throws Exception {
      Configuration config = createBasicConfig().setJMXManagementEnabled(false);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      // invoke attribute and operation on the server
      ICoreMessage message = new CoreMessage(1, 100);
      ManagementHelper.putOperationInvocation(message, "Resouce.Does.Not.Exist", "toString");

      ICoreMessage reply = server.getManagementService().handleMessage(message);

      Assert.assertFalse(ManagementHelper.hasOperationSucceeded(reply));
      Assert.assertNotNull(ManagementHelper.getResult(reply));
   }

   @Test
   public void testHandleManagementMessageWithUnknownAttribute() throws Exception {
      Configuration config = createBasicConfig().setJMXManagementEnabled(false);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      // invoke attribute and operation on the server
      ICoreMessage message = new CoreMessage(1, 100);

      ManagementHelper.putAttribute(message, ResourceNames.BROKER, "started");

      ICoreMessage reply = server.getManagementService().handleMessage(message);

      Assert.assertTrue(ManagementHelper.hasOperationSucceeded(reply));
      Assert.assertTrue((Boolean) ManagementHelper.getResult(reply));
   }

   @Test
   public void testHandleManagementMessageWithKnownAttribute() throws Exception {
      Configuration config = createBasicConfig().setJMXManagementEnabled(false);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      // invoke attribute and operation on the server
      ICoreMessage message = new CoreMessage(1, 100);

      ManagementHelper.putAttribute(message, ResourceNames.BROKER, "attribute.Does.Not.Exist");

      ICoreMessage reply = server.getManagementService().handleMessage(message);

      Assert.assertFalse(ManagementHelper.hasOperationSucceeded(reply));
      Assert.assertNotNull(ManagementHelper.getResult(reply));
   }

   @Test
   public void testGetResources() throws Exception {
      Configuration config = createBasicConfig().setJMXManagementEnabled(false);
      ManagementServiceImpl managementService = new ManagementServiceImpl(null, config);
      managementService.setStorageManager(new NullStorageManager());

      SimpleString address = RandomUtil.randomSimpleString();
      managementService.registerAddress(new AddressInfo(address));
      Queue queue = new FakeQueue(RandomUtil.randomSimpleString());
      managementService.registerQueue(queue, RandomUtil.randomSimpleString(), new FakeStorageManager());

      Object[] addresses = managementService.getResources(AddressControl.class);
      Assert.assertEquals(1, addresses.length);
      Assert.assertTrue(addresses[0] instanceof AddressControl);
      AddressControl addressControl = (AddressControl) addresses[0];
      Assert.assertEquals(address.toString(), addressControl.getAddress());

      Object[] queues = managementService.getResources(QueueControl.class);
      Assert.assertEquals(1, queues.length);
      Assert.assertTrue(queues[0] instanceof QueueControl);
      QueueControl queueControl = (QueueControl) queues[0];
      Assert.assertEquals(queue.getName().toString(), queueControl.getName());
   }

   @Test
   public void testCorrelateResponseByCorrelationID() throws Exception {
      String queue = RandomUtil.randomString();
      String address = RandomUtil.randomString();
      String correlationID = UUIDGenerator.getInstance().generateStringUUID();

      Configuration config = createBasicConfig().setJMXManagementEnabled(false);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      // invoke attribute and operation on the server
      CoreMessage message = new CoreMessage(1, 100);
      MessageUtil.setJMSCorrelationID(message, correlationID);
      ManagementHelper.putOperationInvocation(message, ResourceNames.BROKER, "createQueue", queue, address);

      Message reply = server.getManagementService().handleMessage(message);
      Assert.assertTrue(ManagementHelper.hasOperationSucceeded(reply));
      Assert.assertEquals(correlationID, MessageUtil.getJMSCorrelationID(reply));
   }

   @Test
   public void testCorrelateResponseByMessageID() throws Exception {
      String queue = RandomUtil.randomString();
      String address = RandomUtil.randomString();
      UUID messageId =  UUIDGenerator.getInstance().generateUUID();

      Configuration config = createBasicConfig().setJMXManagementEnabled(false);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      // invoke attribute and operation on the server
      CoreMessage message = new CoreMessage(1, 100);
      message.setUserID(messageId);
      ManagementHelper.putOperationInvocation(message, ResourceNames.BROKER, "createQueue", queue, address);

      Message reply = server.getManagementService().handleMessage(message);
      Assert.assertTrue(ManagementHelper.hasOperationSucceeded(reply));
      Assert.assertEquals(messageId.toString(), MessageUtil.getJMSCorrelationID(reply));
   }



}
