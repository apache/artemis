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
package org.apache.activemq.artemis.cli.factory.security;

import java.util.ServiceLoader;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.activemq.artemis.dto.SecurityDTO;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;

public class SecurityManagerFactory {

   public static ActiveMQSecurityManager create(SecurityDTO config) throws Exception {
      if (config == null) {
         throw new Exception("No security manager configured!");
      }
      String name = config.getClass().getAnnotation(XmlRootElement.class).name();
      ServiceLoader<SecurityHandler> loader = ServiceLoader.load(SecurityHandler.class, SecurityManagerFactory.class.getClassLoader());
      SecurityHandler securityHandler = null;
      for (SecurityHandler handler : loader) {
         if (handler.getName().equals(name)) {
            securityHandler = handler;
            break;
         }
      }
      if (securityHandler == null) {
         throw new Exception("Invalid security configuration, can't find security handler: " + name);
      }
      return securityHandler.createSecurityManager(config);
   }
}
