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
package org.apache.activemq;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.InvalidClientIDException;
import javax.jms.JMSException;
import javax.jms.Session;

import junit.framework.Test;

import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReconnectWithSameClientIDTest extends EmbeddedBrokerTestSupport {

   private static final Logger LOG = LoggerFactory.getLogger(ReconnectWithSameClientIDTest.class);

   protected Connection connection;
   protected boolean transacted;
   protected int authMode = Session.AUTO_ACKNOWLEDGE;
   public boolean useFailover = false;

   public static Test suite() {
      return suite(ReconnectWithSameClientIDTest.class);
   }

   public void initCombosForTestReconnectMultipleTimesWithSameClientID() {
      addCombinationValues("useFailover", new Object[]{Boolean.FALSE, Boolean.TRUE});
   }

   public void testReconnectMultipleTimesWithSameClientID() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         connection = connectionFactory.createConnection();
         useConnection(connection);

         // now lets create another which should fail
         for (int i = 1; i < 11; i++) {
            Connection connection2 = connectionFactory.createConnection();
            try {
               useConnection(connection2);
               fail("Should have thrown InvalidClientIDException on attempt" + i);
            } catch (InvalidClientIDException e) {
               LOG.info("Caught expected: " + e);
            } finally {
               connection2.close();
            }
         }

         // now lets try closing the original connection and creating a new
         // connection with the same ID
         connection.close();
         connection = connectionFactory.createConnection();
         useConnection(connection);
         Assert.assertFalse(loggerHandler.findText("Failed to register MBean"));
      }
   }

   @Override
   protected ConnectionFactory createConnectionFactory() throws Exception {
      return new ActiveMQConnectionFactory((useFailover ? "failover:" : "") + newURI("localhost", 0));
   }

   @Override
   protected void setUp() throws Exception {
      bindAddress = "tcp://localhost:0";
      disableWrapper = true;
      super.setUp();
   }

   @Override
   protected void tearDown() throws Exception {
      if (connection != null) {
         connection.close();
         connection = null;
      }
      super.tearDown();
   }

   protected void useConnection(Connection connection) throws JMSException {
      connection.setClientID("foo");
      connection.start();
   }
}
