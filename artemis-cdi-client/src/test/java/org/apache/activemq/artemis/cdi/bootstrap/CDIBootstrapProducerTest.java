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

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Inject;

import org.apache.artemis.client.cdi.configuration.ArtemisClientConfiguration;
import org.apache.artemis.client.cdi.extension.ArtemisExtension;
import org.apache.artemis.client.cdi.factory.ConnectionFactoryProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class CDIBootstrapProducerTest {
   @Deployment
   public static Archive<?> createArchive() {
      return ShrinkWrap.create(JavaArchive.class)
         .addAsServiceProviderAndClasses(Extension.class, ArtemisExtension.class)
         .addClasses(ConnectionFactoryProvider.class, CDIProducers.class)
         .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
   }

   @Inject
   private Instance<ArtemisClientConfiguration> artemisClientConfigurations;

   @Test
   public void shouldStartJMS() throws Exception {
      assertFalse(artemisClientConfigurations.isAmbiguous());
      assertFalse(artemisClientConfigurations.isUnsatisfied());
      assertTrue(artemisClientConfigurations.iterator().hasNext());
   }

}
