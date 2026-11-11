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

package org.apache.artemis.client.cdi.extension;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessBean;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.artemis.client.cdi.configuration.ArtemisClientConfiguration;
import org.apache.artemis.client.cdi.logger.ActiveMQCDILogger;

public class ArtemisExtension implements Extension {

   private boolean foundEmbeddedConfig = false;
   private boolean foundConfiguration = false;

   void foundClientConfig(@Observes ProcessBean<?> processBean) {
      if (processBean.getBean().getTypes().contains(ArtemisClientConfiguration.class)) {
         ActiveMQCDILogger.LOGGER.discoveredConfiguration(processBean);
         foundConfiguration = true;
      }
   }

   void foundEmbeddedConfig(@Observes ProcessBean<?> processBean) {
      if (processBean.getBean().getTypes().contains(Configuration.class)) {
         ActiveMQCDILogger.LOGGER.discoveredClientConfiguration(processBean);
         foundEmbeddedConfig = true;
      }
   }

   void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
      if (!foundConfiguration) {
         afterBeanDiscovery.addBean(new ArtemisClientConfigBean());
      } else {
         ActiveMQCDILogger.LOGGER.notUsingDefaultConfiguration();
      }
      if (!foundEmbeddedConfig) {
         afterBeanDiscovery.addBean(new ArtemisEmbeddedServerConfigBean());
      } else {
         ActiveMQCDILogger.LOGGER.notUsingDefaultClientConfiguration();
      }

   }

}
