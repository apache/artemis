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
package org.apache.activemq.artemis.jms.tests.message;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A test that sends/receives object messages to the JMS provider and verifies their integrity.
 */
public class ObjectMessageTest extends MessageTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      message = session.createObjectMessage();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      message = null;
      super.tearDown();
   }

   @Test
   public void testClassLoaderIsolation() throws Exception {

      ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      try {
         queueProd.setDeliveryMode(DeliveryMode.PERSISTENT);

         ObjectMessage om = (ObjectMessage) message;

         SomeObject testObject = new SomeObject(3, 7);

         ClassLoader testClassLoader = ObjectMessageTest.newClassLoader(testObject.getClass());

         om.setObject(testObject);

         queueProd.send(message);

         Thread.currentThread().setContextClassLoader(testClassLoader);

         ObjectMessage r = (ObjectMessage) queueCons.receive();

         Object testObject2 = r.getObject();

         ProxyAssertSupport.assertEquals("org.apache.activemq.artemis.jms.tests.message.SomeObject", testObject2.getClass().getName());
         ProxyAssertSupport.assertNotSame(testObject, testObject2);
         ProxyAssertSupport.assertNotSame(testObject.getClass(), testObject2.getClass());
         ProxyAssertSupport.assertNotSame(testObject.getClass().getClassLoader(), testObject2.getClass().getClassLoader());
         ProxyAssertSupport.assertSame(testClassLoader, testObject2.getClass().getClassLoader());
      } finally {
         Thread.currentThread().setContextClassLoader(originalClassLoader);
      }

   }

   @Test
   public void testVectorOnObjectMessage() throws Exception {
      java.util.Vector<String> vectorOnMessage = new java.util.Vector<>();
      vectorOnMessage.add("world!");
      ((ObjectMessage) message).setObject(vectorOnMessage);

      queueProd.send(message);

      ObjectMessage r = (ObjectMessage) queueCons.receive(5000);
      ProxyAssertSupport.assertNotNull(r);

      java.util.Vector v2 = (java.util.Vector) r.getObject();

      ProxyAssertSupport.assertEquals(vectorOnMessage.get(0), v2.get(0));
   }

   @Test
   public void testObjectIsolation() throws Exception {
      ObjectMessage msgTest = session.createObjectMessage();
      ArrayList<String> list = new ArrayList<>();
      list.add("hello");
      msgTest.setObject(list);

      list.clear();

      list = (ArrayList<String>) msgTest.getObject();

      ProxyAssertSupport.assertEquals(1, list.size());
      ProxyAssertSupport.assertEquals("hello", list.get(0));

      list.add("hello2");

      msgTest.setObject(list);

      list.clear();

      list = (ArrayList<String>) msgTest.getObject();

      ProxyAssertSupport.assertEquals(2, list.size());
      ProxyAssertSupport.assertEquals("hello", list.get(0));
      ProxyAssertSupport.assertEquals("hello2", list.get(1));

      msgTest.setObject(list);
      list.add("hello3");
      msgTest.setObject(list);

      list = (ArrayList<String>) msgTest.getObject();
      ProxyAssertSupport.assertEquals(3, list.size());
      ProxyAssertSupport.assertEquals("hello", list.get(0));
      ProxyAssertSupport.assertEquals("hello2", list.get(1));
      ProxyAssertSupport.assertEquals("hello3", list.get(2));

      list = (ArrayList<String>) msgTest.getObject();

      list.clear();

      queueProd.send(msgTest);

      msgTest = (ObjectMessage) queueCons.receive(5000);

      list = (ArrayList<String>) msgTest.getObject();

      ProxyAssertSupport.assertEquals(3, list.size());
      ProxyAssertSupport.assertEquals("hello", list.get(0));
      ProxyAssertSupport.assertEquals("hello2", list.get(1));
      ProxyAssertSupport.assertEquals("hello3", list.get(2));

   }

   @Test
   public void testReadOnEmptyObjectMessage() throws Exception {
      ObjectMessage obm = (ObjectMessage) message;
      ProxyAssertSupport.assertNull(obm.getObject());

      queueProd.send(message);
      ObjectMessage r = (ObjectMessage) queueCons.receive();

      ProxyAssertSupport.assertNull(r.getObject());

   }

   // Protected ------------------------------------------------------------------------------------

   @Override
   protected void prepareMessage(final Message m) throws JMSException {
      super.prepareMessage(m);

      ObjectMessage om = (ObjectMessage) m;
      om.setObject("this is the serializable object");

   }

   @Override
   protected void assertEquivalent(final Message m, final int mode, final boolean redelivery) throws JMSException {
      super.assertEquivalent(m, mode, redelivery);

      ObjectMessage om = (ObjectMessage) m;
      ProxyAssertSupport.assertEquals("this is the serializable object", om.getObject());
   }

   protected static ClassLoader newClassLoader(final Class anyUserClass) throws Exception {
      URL classLocation = anyUserClass.getProtectionDomain().getCodeSource().getLocation();
      StringTokenizer tokenString = new StringTokenizer(System.getProperty("java.class.path"), File.pathSeparator);
      String pathIgnore = System.getProperty("java.home");
      if (pathIgnore == null) {
         pathIgnore = classLocation.toString();
      }

      List<URL> urls = new ArrayList<>();
      while (tokenString.hasMoreElements()) {
         String value = tokenString.nextToken();
         URL itemLocation = new File(value).toURI().toURL();
         if (!itemLocation.equals(classLocation) && itemLocation.toString().indexOf(pathIgnore) >= 0) {
            urls.add(itemLocation);
         }
      }

      URL[] urlArray = urls.toArray(new URL[urls.size()]);

      ClassLoader mainClassLoader = URLClassLoader.newInstance(urlArray, null);

      ClassLoader appClassLoader = URLClassLoader.newInstance(new URL[]{classLocation}, mainClassLoader);

      return appClassLoader;
   }

}
