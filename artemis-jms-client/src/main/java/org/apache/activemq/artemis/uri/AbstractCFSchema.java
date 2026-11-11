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

package org.apache.activemq.artemis.uri;

import java.net.URI;
import java.util.Map;

import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.URISchema;

public abstract class AbstractCFSchema extends URISchema<ActiveMQConnectionFactory, String> {

   protected JMSConnectionOptions newConectionOptions(URI uri, Map<String, String> query) throws Exception {
      String type = query.get("type");
      // We do this check here to guarantee proper logging
      if (JMSConnectionOptions.convertCFType(type) == null) {
         ActiveMQClientLogger.LOGGER.invalidCFType(type, uri.toString());
      }
      return BeanSupport.setData(uri, new JMSConnectionOptions(), query);
   }


   protected ActiveMQConnectionFactory setData(URI uri,
                                               Map<String, String> query,
                                               ActiveMQConnectionFactory factory) throws Exception {
      BeanSupport.setData(uri, factory.getServerLocator(), query);
      return BeanSupport.setData(uri, factory, query);
   }


}
