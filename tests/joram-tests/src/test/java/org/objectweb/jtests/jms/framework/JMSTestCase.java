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
package org.objectweb.jtests.jms.framework;

import java.io.IOException;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.objectweb.jtests.jms.admin.Admin;
import org.objectweb.jtests.jms.admin.AdminFactory;

/**
 * Class extending {@code junit.framework.TestCase} to provide a new {@code fail()} method with an {@code Exception} as
 * parameter.
 * <p>
 * Every Test Case for JMS should extend this class instead of {@code junit.framework.TestCase}
 */
public abstract class JMSTestCase extends Assert {

   public static String _PROP_FILE_NAME = "provider.properties";

   public static String getPropFileName() {
      return System.getProperty("joram.provider", _PROP_FILE_NAME);
   }

   public static void setPropFileName(String fileName) {
      System.setProperty("joram.provider", fileName);
      _PROP_FILE_NAME = fileName;
   }

   public static boolean startServer = true;

   protected Admin admin;

   /**
    * Fails a test with an exception which will be used for a message.
    * <p>
    * If the exception is an instance of {@code javax.jms.JMSException}, the message of the failure will contained both
    * the JMSException and its linked exception (provided there's one).
    */
   public void fail(final Exception e) {
      if (e instanceof javax.jms.JMSException exception) {
         String message = e.toString();
         Exception linkedException = exception.getLinkedException();
         if (linkedException != null) {
            message += " [linked exception: " + linkedException + "]";
         }
         Assert.fail(message);
      } else {
         Assert.fail(e.getMessage());
      }
   }

   /**
    * Should be overridden
    */
   protected Properties getProviderProperties() throws IOException {
      Properties props = new Properties();
      props.load(ClassLoader.getSystemResourceAsStream(getPropFileName()));
      return props;
   }

   @Before
   public void setUp() throws Exception {

      // Admin step
      // gets the provider administration wrapper...
      Properties props = getProviderProperties();
      admin = AdminFactory.getAdmin(props);

      if (startServer) {
         admin.startServer();
      }
      admin.start();
   }

   @After
   public void tearDown() throws Exception {
      try {
         admin.stop();

         if (startServer) {
            admin.stopServer();
         }
      } finally {

      }
   }

}
