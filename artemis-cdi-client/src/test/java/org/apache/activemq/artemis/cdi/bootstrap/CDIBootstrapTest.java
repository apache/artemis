/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.activemq.artemis.cdi.bootstrap;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Queue;

import org.apache.artemis.client.cdi.configuration.ArtemisClientConfiguration;
import org.apache.artemis.client.cdi.configuration.DefaultArtemisClientConfigurationImpl;
import org.apache.artemis.client.cdi.extension.ArtemisExtension;
import org.apache.artemis.client.cdi.factory.ConnectionFactoryProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(Arquillian.class)
public class CDIBootstrapTest {

   @Deployment
   public static Archive<?> createArchive() {
      return ShrinkWrap.create(JavaArchive.class).addAsServiceProviderAndClasses(Extension.class, ArtemisExtension.class).addClasses(NativeConfig.class, ConnectionFactoryProvider.class).addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
   }

   @Inject
   private JMSContext jmsContext;

   @Test
   public void shouldStartJMS() throws Exception {
      String body = "This is a test";
      Queue queue = jmsContext.createQueue("test");
      jmsContext.createProducer().send(queue, body);
      String receivedBody = jmsContext.createConsumer(queue).receiveBody(String.class, 5000);
      Assert.assertNotNull(receivedBody);
      assertEquals(body, receivedBody);
   }

   @ApplicationScoped
   public static class NativeConfig extends DefaultArtemisClientConfigurationImpl {

      @Override
      public String getConnectorFactory() {
         return ArtemisClientConfiguration.IN_VM_CONNECTOR;
      }
   }
}
