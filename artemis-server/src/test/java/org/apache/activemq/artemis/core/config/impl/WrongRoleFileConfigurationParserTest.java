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
package org.apache.activemq.artemis.core.config.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.activemq.artemis.core.deployers.impl.FileConfigurationParser;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.Test;

/**
 * When running this test from an IDE add this to the test command line so that the {@link AssertionLoggerHandler} works
 * properly:
 * <pre>
 * -Dlog4j2.configurationFile=file:${path-to-source}/tests/config/log4j2-tests-config.properties
 * </pre>
 */
public class WrongRoleFileConfigurationParserTest extends ServerTestBase {

   @Test
   public void testParsingDefaultServerConfig() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         FileConfigurationParser parser = new FileConfigurationParser();
         ByteArrayInputStream input = new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8));
         parser.parseMainConfig(input);

         // Using the code only because I don't want a test failing just for someone editing Log text
         assertTrue(loggerHandler.findText("AMQ222177", "create-durable-queue"));
         assertTrue(loggerHandler.findText("AMQ222177", "delete-durable-queue"));
      }
   }

   private static final String configuration = """
      <configuration xmlns="urn:activemq"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="urn:activemq /schema/artemis-configuration.xsd">
         <name>ActiveMQ.main.config</name>
         <log-delegate-factory-class-name>org.apache.activemq.artemis.integration.logging.Log4jLogDelegateFactory</log-delegate-factory-class-name>
         <bindings-directory>${jboss.server.data.dir}/activemq/bindings</bindings-directory>
         <journal-directory>${jboss.server.data.dir}/activemq/journal</journal-directory>
         <journal-min-files>10</journal-min-files>
         <large-messages-directory>${jboss.server.data.dir}/activemq/largemessages</large-messages-directory>
         <paging-directory>${jboss.server.data.dir}/activemq/paging</paging-directory>
         <connectors>
            <connector name="netty">tcp://localhost:61616</connector>
            <connector name="netty-throughput">tcp://localhost:5545</connector>
            <connector name="in-vm">vm://0</connector>
         </connectors>
         <acceptors>
            <acceptor name="netty">tcp://localhost:5545</acceptor>
            <acceptor name="netty-throughput">tcp://localhost:5545</acceptor>
            <acceptor name="in-vm">vm://0</acceptor>
         </acceptors>
         <security-settings>
            <security-setting match="#">
               <permission type="createNonDurableQueue" roles="guest"/>
               <permission type="deleteNonDurableQueue" roles="guest"/>
               <permission type="create-durable-queue" roles="guest"/>
               <permission type="delete-durable-queue" roles="guest"/>
               <permission type="consume" roles="guest"/>
               <permission type="send" roles="guest"/>
            </security-setting>
         </security-settings>
         <address-settings>
            <address-setting match="#">
               <dead-letter-address>DLQ</dead-letter-address>
               <expiry-address>ExpiryQueue</expiry-address>
               <redelivery-delay>0</redelivery-delay>
               <max-size-bytes>10485760</max-size-bytes>
               <message-counter-history-day-limit>10</message-counter-history-day-limit>
               <address-full-policy>BLOCK</address-full-policy>
            </address-setting>
         </address-settings>
      </configuration>
      """;
}
