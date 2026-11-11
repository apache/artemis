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
package org.apache.activemq.artemis.cli.factory.jmx;


import org.apache.activemq.artemis.cli.ConfigurationException;
import org.apache.activemq.artemis.dto.ManagementContextDTO;
import org.apache.activemq.artemis.dto.XmlUtil;

import java.io.File;
import java.net.URI;

public class XmlJmxAclHandler implements JmxAclHandler {

   @Override
   public String getName() {
      return "xml";
   }

   @Override
   public ManagementContextDTO createJmxAcl(URI configURI, String artemisHome, String artemisInstance, URI artemisURIInstance) throws Exception {
      File file = new File(configURI.getSchemeSpecificPart());
      if (!file.exists()) {
         throw new ConfigurationException("Invalid configuration URI, can't find file: " + file.getName());
      }
      return XmlUtil.decode(ManagementContextDTO.class, file, artemisHome, artemisInstance, artemisURIInstance);
   }
}
