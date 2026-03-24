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
package org.apache.activemq.artemis.tests.unit.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.EnclosingClass;
import org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass1;
import org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass2;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ObjectInputStreamWithClassLoader;
import org.junit.Assert;
import org.junit.Test;

public class ObjectInputStreamWithClassLoaderTest extends ActiveMQTestBase {



   public static ClassLoader newClassLoader(final Class... userClasses) throws Exception {

      Set<URL> userClassUrls = new HashSet<>();
      for (Class anyUserClass : userClasses) {
         ProtectionDomain protectionDomain = anyUserClass.getProtectionDomain();
         CodeSource codeSource = protectionDomain.getCodeSource();
         URL classLocation = codeSource.getLocation();
         userClassUrls.add(classLocation);
      }
      StringTokenizer tokenString = new StringTokenizer(System.getProperty("java.class.path"), File.pathSeparator);
      String pathIgnore = System.getProperty("java.home");
      if (pathIgnore == null) {
         pathIgnore = userClassUrls.iterator().next().toString();
      }

      List<URL> urls = new ArrayList<>();
      while (tokenString.hasMoreElements()) {
         String value = tokenString.nextToken();
         URL itemLocation = new File(value).toURI().toURL();
         if (!userClassUrls.contains(itemLocation) && itemLocation.toString().indexOf(pathIgnore) >= 0) {
            urls.add(itemLocation);
         }
      }
      URL[] urlArray = urls.toArray(new URL[urls.size()]);

      ClassLoader masterClassLoader = URLClassLoader.newInstance(urlArray, null);
      ClassLoader appClassLoader = URLClassLoader.newInstance(userClassUrls.toArray(new URL[0]), masterClassLoader);
      return appClassLoader;
   }


   @Test
   public void testClassLoaderIsolation() throws Exception {

      ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      try {
         AnObject obj = new AnObjectImpl();
         byte[] bytes = ObjectInputStreamWithClassLoaderTest.toBytes(obj);

         //Class.isAnonymousClass() call used in ObjectInputStreamWithClassLoader
         //need to access the enclosing class and its parent class of the obj
         //i.e. ActiveMQTestBase and Assert.
         ClassLoader testClassLoader = ObjectInputStreamWithClassLoaderTest.newClassLoader(obj.getClass(), ActiveMQTestBase.class, Assert.class);
         Thread.currentThread().setContextClassLoader(testClassLoader);

         ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
         ObjectInputStreamWithClassLoader ois = new ObjectInputStreamWithClassLoader(bais);

         Object deserializedObj = ois.readObject();

         Assert.assertNotSame(obj, deserializedObj);
         Assert.assertNotSame(obj.getClass(), deserializedObj.getClass());
         Assert.assertNotSame(obj.getClass().getClassLoader(), deserializedObj.getClass().getClassLoader());
         Assert.assertSame(testClassLoader, deserializedObj.getClass().getClassLoader());
      } finally {
         Thread.currentThread().setContextClassLoader(originalClassLoader);
      }

   }

   @Test
   public void testClassLoaderIsolationWithProxy() throws Exception {

      ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      try {
         AnObject originalProxy = (AnObject) Proxy.newProxyInstance(AnObject.class.getClassLoader(), new Class[]{AnObject.class}, new AnObjectInvocationHandler());
         originalProxy.setMyInt(100);
         byte[] bytes = ObjectInputStreamWithClassLoaderTest.toBytes(originalProxy);

         ClassLoader testClassLoader = ObjectInputStreamWithClassLoaderTest.newClassLoader(this.getClass(), ActiveMQTestBase.class, Assert.class);
         Thread.currentThread().setContextClassLoader(testClassLoader);
         ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
         ObjectInputStreamWithClassLoader ois = new ObjectInputStreamWithClassLoader(bais);

         Runnable toRun = (Runnable) testClassLoader.loadClass(ProxyReader.class.getName()).newInstance();
         toRun.getClass().getField("ois").set(toRun, ois);
         toRun.getClass().getField("testClassLoader").set(toRun, testClassLoader);
         toRun.getClass().getField("originalProxy").set(toRun, originalProxy);

         toRun.run();

      } finally {
         Thread.currentThread().setContextClassLoader(originalClassLoader);
      }

   }

   @Test
   public void testWhiteBlackList() throws Exception {
      File serailizeFile = new File(temporaryFolder.getRoot(), "testclass.bin");
      ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(serailizeFile));
      try {
         outputStream.writeObject(new TestClass1());
         outputStream.flush();
      } finally {
         outputStream.close();
      }

      //default
      assertNull(readSerializedObject(null, null, serailizeFile));

      //white list
      String whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization";
      assertNull(readSerializedObject(whiteList, null, serailizeFile));
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1";
      assertNull(readSerializedObject(whiteList, null, serailizeFile));

      whiteList = "some.other.package";
      Exception result = readSerializedObject(whiteList, null, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //blacklist
      String blackList = "org.apache.activemq.artemis.tests.unit.util";
      result = readSerializedObject(null, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      blackList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1";
      result = readSerializedObject(null, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      blackList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg2";
      result = readSerializedObject(null, blackList, serailizeFile);
      assertNull(result);

      blackList = "some.other.package";
      whiteList = "some.other.package1";
      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //blacklist priority
      blackList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1, some.other.package";
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1";
      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      blackList = "org.apache.activemq.artemis.tests.unit, some.other.package";
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1";
      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      blackList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.pkg2, some.other.package";
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1";
      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertNull(result);

      blackList = "some.other.package, org.apache.activemq.artemis.tests.unit.util.deserialization.pkg2";
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1";
      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertNull(result);

      //wildcard
      blackList = "*";
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1";
      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      blackList = "*";
      whiteList = "*";
      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);
      result = readSerializedObject(whiteList, null, serailizeFile);
      assertNull(result);
   }

   @Test
   public void testWhiteBlackListAgainstArrayObject() throws Exception {
      File serailizeFile = new File(temporaryFolder.getRoot(), "testclass.bin");
      TestClass1[] sourceObject = new TestClass1[]{new TestClass1()};

      ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(serailizeFile));
      try {
         outputStream.writeObject(sourceObject);
         outputStream.flush();
      } finally {
         outputStream.close();
      }

      //default ok
      String blackList = null;
      String whiteList = null;

      Object result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertNull(result);

      //now blacklist TestClass1
      blackList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass1";
      whiteList = null;

      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //now whitelist TestClass1, it should pass.
      blackList = null;
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass1";

      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertNull(result);
   }

   @Test
   public void testWhiteBlackListAgainstListObject() throws Exception {
      File serailizeFile = new File(temporaryFolder.getRoot(), "testclass.bin");
      List<TestClass1> sourceObject = new ArrayList<>();
      sourceObject.add(new TestClass1());

      ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(serailizeFile));
      try {
         outputStream.writeObject(sourceObject);
         outputStream.flush();
      } finally {
         outputStream.close();
      }

      //default ok
      String blackList = null;
      String whiteList = null;

      Object result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertNull(result);

      //now blacklist TestClass1
      blackList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass1";
      whiteList = null;

      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //now whitelist TestClass1, should fail because the List type is not allowed
      blackList = null;
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass1";

      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //now add List to white list, it should pass
      blackList = null;
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass1," + "java.util.ArrayList";
      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertNull(result);

   }

   @Test
   public void testWhiteBlackListAgainstListMapObject() throws Exception {
      File serailizeFile = new File(temporaryFolder.getRoot(), "testclass.bin");
      Map<TestClass1, TestClass2> sourceObject = new HashMap<>();
      sourceObject.put(new TestClass1(), new TestClass2());

      ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(serailizeFile));
      try {
         outputStream.writeObject(sourceObject);
         outputStream.flush();
      } finally {
         outputStream.close();
      }

      String blackList = null;
      String whiteList = null;

      Object result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertNull(result);

      //now blacklist the key
      blackList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass1";
      whiteList = null;

      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //now blacklist the value
      blackList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass2";
      whiteList = null;

      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //now white list the key, should fail too because value is forbidden
      blackList = null;
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass1";

      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //now white list the value, should fail too because the key is forbidden
      blackList = null;
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass2";

      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //both key and value are in the whitelist, it should fail because HashMap not permitted
      blackList = null;
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass1," + "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass2";

      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //now add HashMap, test should pass.
      blackList = null;
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass1," +
         "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.TestClass2," +
         "java.util.HashMap";

      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertNull(result);

   }

   @Test
   public void testWhiteBlackListAnonymousObject() throws Exception {
      File serailizeFile = new File(temporaryFolder.getRoot(), "testclass.bin");
      ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(serailizeFile));
      try {
         Serializable object = EnclosingClass.anonymousObject;
         assertTrue(object.getClass().isAnonymousClass());
         outputStream.writeObject(object);
         outputStream.flush();
      } finally {
         outputStream.close();
      }

      //default
      String blackList = null;
      String whiteList = null;
      assertNull(readSerializedObject(whiteList, blackList, serailizeFile));

      //forbidden by specifying the enclosing class
      blackList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.EnclosingClass";
      Object result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //do it in whiteList
      blackList = null;
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.EnclosingClass";
      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertNull(result);
   }

   @Test
   public void testWhiteBlackListLocalObject() throws Exception {
      File serailizeFile = new File(temporaryFolder.getRoot(), "testclass.bin");
      ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(serailizeFile));
      try {
         Object object = EnclosingClass.getLocalObject();
         assertTrue(object.getClass().isLocalClass());
         outputStream.writeObject(object);
         outputStream.flush();
      } finally {
         outputStream.close();
      }

      //default
      String blackList = null;
      String whiteList = null;
      assertNull(readSerializedObject(whiteList, blackList, serailizeFile));

      //forbidden by specifying the enclosing class
      blackList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.EnclosingClass";
      Object result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertTrue(result instanceof ClassNotFoundException);

      //do it in whiteList
      blackList = null;
      whiteList = "org.apache.activemq.artemis.tests.unit.util.deserialization.pkg1.EnclosingClass";
      result = readSerializedObject(whiteList, blackList, serailizeFile);
      assertNull(result);
   }

   @Test
   public void testWhiteBlackListSystemProperty() throws Exception {

      File serailizeFile = new File(temporaryFolder.getRoot(), "testclass.bin");
      ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(serailizeFile));
      try {
         outputStream.writeObject(new TestClass1());
         outputStream.flush();
      } finally {
         outputStream.close();
      }

      System.setProperty(ObjectInputStreamWithClassLoader.BLACKLIST_PROPERTY, "system.defined.black.list");
      System.setProperty(ObjectInputStreamWithClassLoader.WHITELIST_PROPERTY, "system.defined.white.list");
      try {
         ObjectInputStreamWithClassLoader ois = new ObjectInputStreamWithClassLoader(new FileInputStream(serailizeFile));
         String bList = ois.getBlackList();
         String wList = ois.getWhiteList();
         assertEquals("wrong black list: " + bList, "system.defined.black.list", bList);
         assertEquals("wrong white list: " + wList, "system.defined.white.list", wList);
         ois.close();
      } finally {
         System.clearProperty(ObjectInputStreamWithClassLoader.BLACKLIST_PROPERTY);
         System.clearProperty(ObjectInputStreamWithClassLoader.WHITELIST_PROPERTY);
      }
   }

   private Exception readSerializedObject(String whiteList, String blackList, File serailizeFile) {
      Exception result = null;

      ObjectInputStreamWithClassLoader ois = null;

      try {
         ois = new ObjectInputStreamWithClassLoader(new FileInputStream(serailizeFile));
         ois.setWhiteList(whiteList);
         ois.setBlackList(blackList);
         ois.readObject();
      } catch (Exception e) {
         result = e;
      } finally {
         try {
            ois.close();
         } catch (IOException e) {
            result = e;
         }
      }
      return result;
   }




   public static class ProxyReader implements Runnable {

      public java.io.ObjectInputStream ois;
      public Object originalProxy;
      public ClassLoader testClassLoader;

      // We don't have access to the junit framework on the classloader where this is running
      void myAssertNotSame(Object obj, Object obj2) {
         if (obj == obj2) {
            throw new RuntimeException("Expected to be different objects");
         }
      }

      // We don't have access to the junit framework on the classloader where this is running
      void myAssertSame(Object obj, Object obj2) {
         if (obj != obj2) {
            throw new RuntimeException("Expected to be the same objects");
         }
      }

      @Override
      public void run() {

         try {
            Object deserializedObj = ois.readObject();

            // cannot use logging here, as this test is playing with classLoading here.
            System.out.println("Deserialized Object " + deserializedObj);

            myAssertNotSame(originalProxy, deserializedObj);
            myAssertNotSame(originalProxy.getClass(), deserializedObj.getClass());
            myAssertNotSame(originalProxy.getClass().getClassLoader(), deserializedObj.getClass().getClassLoader());
            myAssertSame(testClassLoader, deserializedObj.getClass().getClassLoader());

            AnObject myInterface = (AnObject) deserializedObj;

            if (myInterface.getMyInt() != 200) {
               throw new RuntimeException("invalid result");
            }
         } catch (ClassNotFoundException e) {
            throw new RuntimeException(e.getMessage(), e);
         } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
         }

      }
   }

   private static byte[] toBytes(final Object obj) throws IOException {
      Assert.assertTrue(obj instanceof Serializable);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(obj);
      oos.flush();
      return baos.toByteArray();
   }


   private interface AnObject extends Serializable {

      int getMyInt();

      void setMyInt(int value);

      long getMyLong();

      void setMyLong(long value);
   }

   private static class AnObjectImpl implements AnObject {

      private static final long serialVersionUID = -5172742084489525256L;

      int myInt = 0;
      long myLong = 0L;

      @Override
      public int getMyInt() {
         return myInt;
      }

      @Override
      public void setMyInt(int value) {
         this.myInt = value;
      }

      @Override
      public long getMyLong() {
         return myLong;
      }

      @Override
      public void setMyLong(long value) {
         this.myLong = value;
      }
   }

   private static class AnObjectInvocationHandler implements InvocationHandler, Serializable {

      private static final long serialVersionUID = -3875973764178767452L;
      private final AnObject anObject = new AnObjectImpl();

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         Object obj = method.invoke(anObject, args);
         if (obj instanceof Integer) {
            return ((Integer) obj).intValue() * 2;
         } else {
            return obj;
         }

      }
   }
}
