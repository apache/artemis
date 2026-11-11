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
package org.apache.activemq.artemis.jms;

import java.io.IOException;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.objectweb.jtests.jms.admin.Admin;
import org.objectweb.jtests.jms.admin.AdminFactory;
import org.objectweb.jtests.jms.conform.connection.ConnectionTest;
import org.objectweb.jtests.jms.conform.connection.TopicConnectionTest;
import org.objectweb.jtests.jms.conform.message.MessageBodyTest;
import org.objectweb.jtests.jms.conform.message.MessageDefaultTest;
import org.objectweb.jtests.jms.conform.message.MessageTypeTest;
import org.objectweb.jtests.jms.conform.message.headers.MessageHeaderTest;
import org.objectweb.jtests.jms.conform.message.properties.JMSXPropertyTest;
import org.objectweb.jtests.jms.conform.message.properties.MessagePropertyConversionTest;
import org.objectweb.jtests.jms.conform.message.properties.MessagePropertyTest;
import org.objectweb.jtests.jms.conform.queue.QueueBrowserTest;
import org.objectweb.jtests.jms.conform.queue.TemporaryQueueTest;
import org.objectweb.jtests.jms.conform.selector.SelectorSyntaxTest;
import org.objectweb.jtests.jms.conform.selector.SelectorTest;
import org.objectweb.jtests.jms.conform.session.QueueSessionTest;
import org.objectweb.jtests.jms.conform.session.SessionTest;
import org.objectweb.jtests.jms.conform.session.TopicSessionTest;
import org.objectweb.jtests.jms.conform.session.UnifiedSessionTest;
import org.objectweb.jtests.jms.conform.topic.TemporaryTopicTest;
import org.objectweb.jtests.jms.framework.JMSTestCase;

@RunWith(Suite.class)
@SuiteClasses({TopicConnectionTest.class, ConnectionTest.class, MessageBodyTest.class, MessageDefaultTest.class, MessageTypeTest.class, MessageHeaderTest.class, JMSXPropertyTest.class, MessagePropertyConversionTest.class, MessagePropertyTest.class, QueueBrowserTest.class, TemporaryQueueTest.class, SelectorSyntaxTest.class, SelectorTest.class, QueueSessionTest.class, SessionTest.class, TopicSessionTest.class, UnifiedSessionTest.class, TemporaryTopicTest.class})
public class JoramCoreAggregationTest extends Assert {

   /**
    * Should be overridden
    */
   protected static Properties getProviderProperties() throws IOException {
      Properties props = new Properties();
      props.load(ClassLoader.getSystemResourceAsStream(JMSTestCase.getPropFileName()));
      return props;
   }

   static Admin admin;

   @BeforeClass
   public static void setUpServer() throws Exception {
      JMSTestCase.setPropFileName("provider.properties");
      JMSTestCase.startServer = false;
      // Admin step
      // gets the provider administration wrapper...
      Properties props = getProviderProperties();
      admin = AdminFactory.getAdmin(props);
      admin.startServer();
   }

   @AfterClass
   public static void tearDownServer() throws Exception {
      admin.stopServer();
      JMSTestCase.startServer = true;
   }
}
