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
package org.objectweb.jtests.jms.conform.connection;

import javax.jms.JMSException;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PubSubTestCase;
// FIXME include in TestSuite @RunWith(Suite.class)@Suite.SuiteClasses(...)

/**
 * Test topic-specific connection features.
 * <p>
 * Test setting of client ID which is relevant only for Durable Subscription
 */
public class TopicConnectionTest extends PubSubTestCase {

   /**
    * Test that a call to {@code setClientID} will throw an {@code IllegalStateException} if a client ID has already
    * been set see JMS javadoc
    * http://java.sun.com/j2ee/sdk_1.3/techdocs/api/javax/jms/Connection.html#setClientID(java.lang.String)
    */
   @Test
   public void testSetClientID_1() {
      try {
         // we start from a clean state for the connection
         subscriberConnection.close();
         subscriberConnection = null;

         subscriberConnection = subscriberTCF.createTopicConnection();
         // if the JMS provider does not set a client ID, we do.
         if (subscriberConnection.getClientID() == null) {
            subscriberConnection.setClientID("testSetClientID_1");
            Assert.assertEquals("testSetClientID_1", subscriberConnection.getClientID());
         }
         // now the connection has a client ID (either "testSetClientID_1" or one set by the provider
         Assert.assertNotNull(subscriberConnection.getClientID());

         // an attempt to set a client ID should now throw an IllegalStateException
         subscriberConnection.setClientID("another client ID");
         Assert.fail("Should raise a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a " + e);
      } catch (java.lang.IllegalStateException e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a java.lang.IllegalStateException");
      }
   }

   /**
    * Test that a call to {@code setClientID} can occur only after connection creation and before any other action on
    * the connection.
    * <em>This test is relevant only if the ID is set by the JMS client</em>
    * see JMS javadoc
    * http://java.sun.com/j2ee/sdk_1.3/techdocs/api/javax/jms/Connection.html#setClientID(java.lang.String)
    */
   @Test
   public void testSetClientID_2() {
      try {
         // we start from a clean state for the first connection
         subscriberConnection.close();
         subscriberConnection = null;

         subscriberConnection = subscriberTCF.createTopicConnection();
         // if the JMS provider has set a client ID, this test is not relevant
         if (subscriberConnection.getClientID() != null) {
            return;
         }

         // we start the connection
         subscriberConnection.start();

         // an attempt to set the client ID now should throw an IllegalStateException
         subscriberConnection.setClientID("testSetClientID_2");
         Assert.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a " + e);
      } catch (java.lang.IllegalStateException e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a java.lang.IllegalStateException");
      }
   }
}
