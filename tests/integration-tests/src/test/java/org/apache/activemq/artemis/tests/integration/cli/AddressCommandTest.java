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
package org.apache.activemq.artemis.tests.integration.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.text.MessageFormat;
import java.util.EnumSet;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.address.CreateAddress;
import org.apache.activemq.artemis.cli.commands.address.DeleteAddress;
import org.apache.activemq.artemis.cli.commands.address.ShowAddress;
import org.apache.activemq.artemis.cli.commands.address.UpdateAddress;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressCommandTest extends JMSTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   //the command
   private ByteArrayOutputStream output;
   private ByteArrayOutputStream error;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      this.output = new ByteArrayOutputStream(1024);
      this.error = new ByteArrayOutputStream(1024);
   }

   @Test
   public void testCreateAddress() throws Exception {
      String address = "address";
      CreateAddress command = new CreateAddress();
      command.setName(address);
      command.setAnycast(true);
      command.setMulticast(true);
      command.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      checkExecutionPassed(command);
      AddressInfo addressInfo = server.getAddressInfo(SimpleString.of(address));
      assertNotNull(addressInfo);
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.ANYCAST));
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST));
   }

   @Test
   public void testCreateAddressAlreadyExistsShowsError() throws Exception {
      String address = "address";
      CreateAddress command = new CreateAddress();
      command.setName(address);
      command.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      checkExecutionPassed(command);
      assertNotNull(server.getAddressInfo(SimpleString.of(address)));

      command.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      checkExecutionFailure(command, "Address already exists");
   }

   @Test
   public void testDeleteAddress() throws Exception {
      String address = "address";
      CreateAddress command = new CreateAddress();
      command.setName(address);
      command.execute(new ActionContext());
      assertNotNull(server.getAddressInfo(SimpleString.of(address)));

      DeleteAddress deleteAddress = new DeleteAddress();
      deleteAddress.setName(address);
      deleteAddress.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      checkExecutionPassed(deleteAddress);
      assertNull(server.getAddressInfo(SimpleString.of(address)));
   }

   @Test
   public void testDeleteAddressDoesNotExistsShowsError() throws Exception {
      String address = "address";
      DeleteAddress deleteAddress = new DeleteAddress();
      deleteAddress.setName(address);
      deleteAddress.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      checkExecutionFailure(deleteAddress, "Address Does Not Exist");
   }

   @Test
   public void testFailDeleteAddressWhenExistsQueues() throws Exception {
      final String addressName = "address";
      final SimpleString addressSimpleString = SimpleString.of(addressName);
      final AddressInfo addressInfo = new AddressInfo(addressSimpleString, EnumSet.of(RoutingType.ANYCAST, RoutingType.MULTICAST));
      server.addAddressInfo(addressInfo);
      server.createQueue(QueueConfiguration.of("queue1").setAddress(addressSimpleString).setRoutingType(RoutingType.MULTICAST));

      final DeleteAddress deleteAddress = new DeleteAddress();
      deleteAddress.setName(addressName);
      deleteAddress.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      checkExecutionFailure(deleteAddress, "Address " + addressName + " has bindings");
   }

   @Test
   public void testForceDeleteAddressWhenExistsQueues() throws Exception {
      final String addressName = "address";
      final SimpleString addressSimpleString = SimpleString.of(addressName);
      final String queueName = "queue1";
      final AddressInfo addressInfo = new AddressInfo(addressSimpleString, EnumSet.of(RoutingType.ANYCAST, RoutingType.MULTICAST));
      server.addAddressInfo(addressInfo);
      Queue queue = server.createQueue(QueueConfiguration.of(queueName).setAddress(addressSimpleString).setRoutingType(RoutingType.MULTICAST));
      ServerLocator locator = ActiveMQClient.createServerLocator("tcp://127.0.0.1:61616");
      ClientSessionFactory csf = locator.createSessionFactory();
      ClientSession session = csf.createSession();
      ClientProducer producer = session.createProducer(addressName);
      producer.send(session.createMessage(true));
      Wait.assertEquals(1L, () -> queue.getMessageCount());

      final DeleteAddress deleteAddress = new DeleteAddress();
      deleteAddress.setName(addressName);
      deleteAddress.setForce(true);
      deleteAddress.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      checkExecutionPassed(deleteAddress);
      Wait.assertTrue(() -> server.locateQueue(queueName) == null);
   }

   @Test
   public void testShowAddress() throws Exception {
      String address = "address";
      CreateAddress command = new CreateAddress();
      command.setName(address);
      command.execute(new ActionContext());
      assertNotNull(server.getAddressInfo(SimpleString.of(address)));

      ShowAddress showAddress = new ShowAddress();
      showAddress.setName(address);
      showAddress.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      logger.debug(output.toString());
   }

   @Test
   public void testShowAddressDoesNotExist() throws Exception {
      String address = "address";
      ShowAddress showAddress = new ShowAddress();
      showAddress.setName(address);
      showAddress.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      checkExecutionFailure(showAddress, "Address Does Not Exist");
   }

   @Test
   public void testShowAddressBindings() throws Exception {

      // Create bindings
      SimpleString address = SimpleString.of("address");
      server.addAddressInfo(new AddressInfo(address, RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of("queue1").setAddress(address).setRoutingType(RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of("queue2").setAddress(address).setRoutingType(RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of("queue3").setAddress(address).setRoutingType(RoutingType.MULTICAST));

      DivertConfiguration divertConfiguration = new DivertConfiguration();
      divertConfiguration.setName(address.toString());
      divertConfiguration.setAddress(address.toString());
      server.deployDivert(divertConfiguration);

      ShowAddress showAddress = new ShowAddress();
      showAddress.setName(address.toString());
      showAddress.setBindings(true);
      showAddress.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      logger.debug(output.toString());
   }

   @Test
   public void testUpdateAddressRoutingTypes() throws Exception {
      final String addressName = "address";
      final SimpleString address = SimpleString.of(addressName);
      server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));

      final UpdateAddress updateAddress = new UpdateAddress();
      updateAddress.setName(addressName);
      updateAddress.setAnycast(true);
      updateAddress.setMulticast(true);
      updateAddress.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      checkExecutionPassed(updateAddress);

      final AddressInfo addressInfo = server.getAddressInfo(address);
      assertNotNull(addressInfo);
      assertEquals(EnumSet.of(RoutingType.ANYCAST, RoutingType.MULTICAST), addressInfo.getRoutingTypes());
   }

   @Test
   public void testFailUpdateAddressDoesNotExist() throws Exception {
      final String addressName = "address";
      final UpdateAddress updateAddress = new UpdateAddress();
      updateAddress.setName(addressName);
      updateAddress.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));
      checkExecutionFailure(updateAddress, "Address Does Not Exist");
   }

   @Test
   public void testFailUpdateAddressRoutingTypesWhenExistsQueues() throws Exception {
      final String addressName = "address";
      final SimpleString addressSimpleString = SimpleString.of(addressName);
      final AddressInfo addressInfo = new AddressInfo(addressSimpleString, EnumSet.of(RoutingType.ANYCAST, RoutingType.MULTICAST));
      server.addAddressInfo(addressInfo);
      server.createQueue(QueueConfiguration.of("queue1").setAddress(addressSimpleString).setRoutingType(RoutingType.MULTICAST));

      final UpdateAddress updateAddress = new UpdateAddress();
      updateAddress.setName(addressName);
      updateAddress.setAnycast(true);
      updateAddress.setMulticast(false);
      updateAddress.execute(new ActionContext(System.in, new PrintStream(output), new PrintStream(error)));

      final String expectedErrorMessage = MessageFormat.format("Can''t remove routing type {0}, queues exists for address: {1}. Please delete queues before removing this routing type.", RoutingType.MULTICAST, addressName);
      checkExecutionFailure(updateAddress, expectedErrorMessage);
   }

   private void checkExecutionPassed(ConnectionAbstract command) throws Exception {
      String fullMessage = output.toString();
      logger.debug("output: {}", fullMessage);
      assertTrue(fullMessage.contains("successfully"), fullMessage);
   }

   private void checkExecutionFailure(ConnectionAbstract command, String message) throws Exception {
      String fullMessage = error.toString();
      logger.debug("error: {}", fullMessage);
      assertTrue(fullMessage.contains(message), fullMessage);
   }
}
