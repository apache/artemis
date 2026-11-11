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
package org.apache.activemq.artemis.uri.schemas.clusterConnection;

import java.net.URI;
import java.util.Map;

import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.URISchema;
import org.apache.activemq.artemis.utils.uri.URISupport;

public class ClusterConnectionStaticSchema extends URISchema<ClusterConnectionConfiguration, String> {

   @Override
   public String getSchemaName() {
      return "static";
   }

   @Override
   protected ClusterConnectionConfiguration internalNewObject(URI uri,
                                                              Map<String, String> query,
                                                              String param) throws Exception {

      ClusterConnectionConfiguration configuration = new ClusterConnectionConfiguration();

      populateObject(uri, configuration);

      return configuration;
   }

   @Override
   public void populateObject(URI uri, ClusterConnectionConfiguration bean) throws Exception {
      URISupport.CompositeData compositeData = URISupport.parseComposite(uri);
      bean.setCompositeMembers(compositeData);

      BeanSupport.setData(uri, bean, compositeData.getParameters());
   }

   @Override
   protected URI internalNewURI(ClusterConnectionConfiguration bean) throws Exception {
      return null;
   }
}
