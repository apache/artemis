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
package org.apache.activemq.artemis.tests.integration.jms;

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.JMSSecurityException;
import javax.jms.JMSSecurityRuntimeException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.SensitiveDataCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JMSSecurityTest extends JMSTestBase {

   private static final String GOOD_USER = "IDo";
   private static final String GOOD_PASSWORD = "Exist";

   private static final String BAD_USER = "Idont";
   private static final String BAD_PASSWORD = "exist";

   @Override
   public boolean useSecurity() {
      return true;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Test
   public void testSecurityOnJMSContext() throws Exception {
      addUserToSecurityManager();
      try {
         JMSContext ctx = cf.createContext(BAD_USER, BAD_PASSWORD);
         ctx.close();
      } catch (JMSSecurityRuntimeException e) {
         // expected
      }
      try {
         JMSContext ctx = cf.createContext(BAD_USER, GOOD_PASSWORD);
         ctx.close();
      } catch (JMSSecurityRuntimeException e) {
         // expected
      }
      try {
         JMSContext ctx = cf.createContext(GOOD_USER, BAD_PASSWORD);
         ctx.close();
      } catch (JMSSecurityRuntimeException e) {
         // expected
      }
      JMSContext ctx = cf.createContext(GOOD_USER, GOOD_PASSWORD);
      ctx.close();
   }

   @Test
   public void testCreateQueueConnection() throws Exception {
      addUserToSecurityManager();
      try {
         QueueConnection queueC = ((QueueConnectionFactory) cf).createQueueConnection(BAD_USER, BAD_PASSWORD);
         fail("supposed to throw exception");
         queueC.close();
      } catch (JMSSecurityException e) {
         // expected
      }
      try {
         QueueConnection queueC = ((QueueConnectionFactory) cf).createQueueConnection(BAD_USER, GOOD_PASSWORD);
         fail("supposed to throw exception");
         queueC.close();
      } catch (JMSSecurityException e) {
         // expected
      }
      try {
         QueueConnection queueC = ((QueueConnectionFactory) cf).createQueueConnection(GOOD_USER, BAD_PASSWORD);
         fail("supposed to throw exception");
         queueC.close();
      } catch (JMSSecurityException e) {
         // expected
      }
      QueueConnection queueC = ((QueueConnectionFactory) cf).createQueueConnection(GOOD_USER, GOOD_PASSWORD);
      queueC.close();
   }

   @Test
   public void testMaskedPasswordOnJMSContext() throws Exception {

      String maskedPassword = PasswordMaskingUtil.getDefaultCodec().encode(GOOD_PASSWORD);
      String wrappedPassword = PasswordMaskingUtil.wrap(maskedPassword);

      addUserToSecurityManager();

      try {
         JMSContext ctx = cf.createContext(BAD_USER, wrappedPassword);
         ctx.close();
      } catch (JMSSecurityRuntimeException e) {
         // expected
      }
      JMSContext ctx = cf.createContext(GOOD_USER, wrappedPassword);
      ctx.close();
   }

   @Test
   public void testMaskedPasswordCreateQueueConnection() throws Exception {

      String maskedPassword = PasswordMaskingUtil.getDefaultCodec().encode(GOOD_PASSWORD);
      String wrappedPassword = PasswordMaskingUtil.wrap(maskedPassword);

      addUserToSecurityManager();

      try {
         QueueConnection queueC = ((QueueConnectionFactory) cf).createQueueConnection(BAD_USER, wrappedPassword);
         fail("supposed to throw exception");
         queueC.close();
      } catch (JMSSecurityException e) {
         // expected
      }
      QueueConnection queueC = ((QueueConnectionFactory) cf).createQueueConnection(GOOD_USER, wrappedPassword);
      queueC.close();
   }

   @Test
   public void testMaskedPasswordURL() throws Exception {
      String maskedPassword = PasswordMaskingUtil.getDefaultCodec().encode(GOOD_PASSWORD);
      String wrappedPassword = PasswordMaskingUtil.wrap(maskedPassword);

      addUserToSecurityManager();

      String brokerURL = "tcp://localhost:61616";
      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory(brokerURL)) {
         testCF.setUser(BAD_USER).setPassword(wrappedPassword);
         Connection conn = testCF.createConnection();
         fail("supposed to throw exception");
         conn.close();
      } catch (JMSSecurityException e) {
         // expected
      }

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory(brokerURL)) {
         testCF.setUser(GOOD_USER).setPassword(wrappedPassword);
         JMSContext ctx = testCF.createContext();
         ctx.close();
      }
   }

   @Test
   public void testMaskedPasswordURLUsernamePassword() throws Exception {
      String maskedPassword = PasswordMaskingUtil.getDefaultCodec().encode(GOOD_PASSWORD);
      String wrappedPassword = PasswordMaskingUtil.wrap(maskedPassword);

      addUserToSecurityManager();

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory("tcp://localhost:61616", BAD_USER, wrappedPassword)) {
         Connection conn = testCF.createConnection();
         fail("supposed to throw exception");
         conn.close();
      } catch (JMSSecurityException e) {
         // expected
      }

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory("tcp://localhost:61616", GOOD_USER, wrappedPassword)) {
         JMSContext ctx = testCF.createContext();
         ctx.close();
      }
   }

   @Test
   public void testMaskedPasswordCodec() throws Exception {
      String maskedPassword = JMSSecurityTestPasswordCodec.MASK;
      String wrappedPassword = PasswordMaskingUtil.wrap(maskedPassword);

      addUserToSecurityManager(GOOD_USER, JMSSecurityTestPasswordCodec.CLEARTEXT);

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory()) {
         testCF.setUser(BAD_USER).setPassword(wrappedPassword).setPasswordCodec(JMSSecurityTestPasswordCodec.class.getName());
         Connection conn = testCF.createConnection();
         fail("supposed to throw exception");
         conn.close();
      } catch (JMSSecurityException e) {
         // expected
      }

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory()) {
         testCF.setUser(GOOD_USER).setPassword(wrappedPassword).setPasswordCodec(JMSSecurityTestPasswordCodec.class.getName());
         JMSContext ctx = testCF.createContext();
         ctx.close();
      }
   }

   @Test
   public void testMaskedPasswordCodecURL() throws Exception {
      String maskedPassword = JMSSecurityTestPasswordCodec.MASK;
      String wrappedPassword = PasswordMaskingUtil.wrap(maskedPassword);

      addUserToSecurityManager(GOOD_USER, JMSSecurityTestPasswordCodec.CLEARTEXT);

      String brokerURL = "tcp://localhost:61616?passwordCodec=" + JMSSecurityTestPasswordCodec.class.getName();

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory(brokerURL)) {
         testCF.setUser(BAD_USER).setPassword(wrappedPassword);
         Connection conn = testCF.createConnection();
         fail("supposed to throw exception");
         conn.close();
      } catch (JMSSecurityException e) {
         // expected
      }

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory(brokerURL)) {
         testCF.setUser(GOOD_USER).setPassword(wrappedPassword);
         JMSContext ctx = testCF.createContext();
         ctx.close();
      }
   }

   @Test
   public void testMaskedPasswordCodecURLUsernamePassword() throws Exception {
      String maskedPassword = JMSSecurityTestPasswordCodec.MASK;
      String wrappedPassword = PasswordMaskingUtil.wrap(maskedPassword);

      addUserToSecurityManager(GOOD_USER, JMSSecurityTestPasswordCodec.CLEARTEXT);

      String brokerURL = "tcp://localhost:61616?passwordCodec=" + JMSSecurityTestPasswordCodec.class.getName();

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory(brokerURL, BAD_USER, wrappedPassword)) {
         Connection conn = testCF.createConnection();
         fail("supposed to throw exception");
         conn.close();
      } catch (JMSSecurityException e) {
         // expected
      }

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory(brokerURL, GOOD_USER, wrappedPassword)) {
         JMSContext ctx = testCF.createContext();
         ctx.close();
      }
   }

   @Test
   public void testBadMaskedPasswordCodecURL() throws Exception {
      String maskedPassword = JMSSecurityTestPasswordCodec.MASK + "ThisIsDesignedToFail";
      String wrappedPassword = PasswordMaskingUtil.wrap(maskedPassword);

      addUserToSecurityManager(GOOD_USER, JMSSecurityTestPasswordCodec.CLEARTEXT);

      String brokerURL = "tcp://localhost:61616?passwordCodec=" + JMSSecurityTestPasswordCodec.class.getName();

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory(brokerURL)) {
         testCF.setUser(GOOD_USER).setPassword(wrappedPassword);
         Connection conn = testCF.createConnection();
         fail("supposed to throw exception");
         conn.close();
      } catch (JMSSecurityException e) {
         // expected
      }
   }

   @Test
   public void testBadMaskedPasswordCodecURLUsernamePassword() throws Exception {
      String maskedPassword = JMSSecurityTestPasswordCodec.MASK + "ThisIsDesignedToFail";
      String wrappedPassword = PasswordMaskingUtil.wrap(maskedPassword);

      addUserToSecurityManager(GOOD_USER, JMSSecurityTestPasswordCodec.CLEARTEXT);

      String brokerURL = "tcp://localhost:61616?passwordCodec=" + JMSSecurityTestPasswordCodec.class.getName();

      try (ActiveMQConnectionFactory testCF = new ActiveMQConnectionFactory(brokerURL, GOOD_USER, wrappedPassword)) {
         Connection conn = testCF.createConnection();
         fail("supposed to throw exception");
         conn.close();
      } catch (JMSSecurityException e) {
         // expected
      }
   }

   private void addUserToSecurityManager() {
      addUserToSecurityManager(GOOD_USER, GOOD_PASSWORD);
   }

   private void addUserToSecurityManager(String user, String password) {
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser(user, password);
   }

   public static class JMSSecurityTestPasswordCodec implements SensitiveDataCodec<String> {

      private static final String MASK = "supersecureish";
      private static final String CLEARTEXT = "securepass";

      @Override
      public String decode(Object mask) throws Exception {
         if (!MASK.equals(mask)) {
            return mask.toString();
         }
         return CLEARTEXT;
      }

      @Override
      public String encode(Object secret) throws Exception {
         if (!CLEARTEXT.equals(secret)) {
            return secret.toString();
         }
         return MASK;
      }
   }

}
