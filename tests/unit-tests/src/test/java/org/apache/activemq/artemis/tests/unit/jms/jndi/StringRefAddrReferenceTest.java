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
package org.apache.activemq.artemis.tests.unit.jms.jndi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.activemq.artemis.jms.client.ActiveMQTopic;
import org.apache.activemq.artemis.jndi.JNDIReferenceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Test to simulate JNDI references created using String properties in containers such as Apache Tomcat.
 */
public class StringRefAddrReferenceTest {

   private static final String FACTORY = "factory";
   private static final String TYPE = "type";

   @Test
   @Timeout(10)
   public void testActiveMQQueueFromPropertiesJNDI() throws Exception {
      Properties properties = new Properties();
      properties.setProperty(TYPE, ActiveMQQueue.class.getName());
      properties.setProperty(FACTORY, JNDIReferenceFactory.class.getName());

      String address = "foo.bar.queue";

      properties.setProperty("address", address);

      Reference reference = from(properties);
      ActiveMQQueue object = getObject(reference, ActiveMQQueue.class);

      assertEquals(address, object.getAddress());

   }

   @Test
   @Timeout(10)
   public void testActiveMQTopicFromPropertiesJNDI() throws Exception {
      Properties properties = new Properties();
      properties.setProperty(TYPE, ActiveMQTopic.class.getName());
      properties.setProperty(FACTORY, JNDIReferenceFactory.class.getName());

      String address = "foo.bar.topic";

      properties.setProperty("address", address);

      Reference reference = from(properties);
      ActiveMQTopic object = getObject(reference, ActiveMQTopic.class);

      assertEquals(address, object.getAddress());

   }

   @Test
   @Timeout(10)
   public void testActiveMQConnectionFactoryFromPropertiesJNDI() throws Exception {
      Properties properties = new Properties();
      properties.setProperty(TYPE, ActiveMQConnectionFactory.class.getName());
      properties.setProperty(FACTORY, JNDIReferenceFactory.class.getName());

      String brokerURL = "vm://0";
      String connectionTTL = "1000";
      String preAcknowledge = "true";

      properties.setProperty("brokerURL", brokerURL);
      properties.setProperty("connectionTTL", connectionTTL);
      properties.setProperty("preAcknowledge", preAcknowledge);

      Reference reference = from(properties);
      ActiveMQConnectionFactory object = getObject(reference, ActiveMQConnectionFactory.class);

      assertEquals(Long.parseLong(connectionTTL), object.getConnectionTTL());
      assertEquals(Boolean.parseBoolean(preAcknowledge), object.isPreAcknowledge());

   }

   private <T> T getObject(Reference reference, Class<T> tClass) throws Exception {
      String factoryName = reference.getFactoryClassName();
      ObjectFactory factory = (ObjectFactory) Class.forName(factoryName).getDeclaredConstructor().newInstance();
      Object o = factory.getObjectInstance(reference, null, null, null);
      if (tClass.isAssignableFrom(tClass)) {
         return tClass.cast(o);
      } else {
         throw new IllegalStateException("Expected class, " + tClass.getName());
      }
   }

   public Reference from(Properties properties) {
      String type = (String) properties.remove("type");
      String factory = (String) properties.remove("factory");
      Reference result = new Reference(type, factory, null);
      for (Enumeration iter = properties.propertyNames(); iter.hasMoreElements();) {
         String key = (String)iter.nextElement();
         result.add(new StringRefAddr(key, properties.getProperty(key)));
      }
      return result;
   }
}
