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
package org.apache.activemq.artemis.core.server.plugin;

import java.util.Map;
import org.apache.activemq.artemis.core.server.ActiveMQServer;

public interface ActiveMQServerBasePlugin {

   default void setInit(Map<String, String> props) {
      init(props);
   }

   /**
    * used to pass configured properties to Plugin
    */
   default void init(Map<String, String> properties) {
   }

   /**
    * The plugin has been registered with the server
    *
    * @param server The ActiveMQServer the plugin has been registered to
    */
   default void registered(ActiveMQServer server) {
   }

   /**
    * The plugin has been unregistered with the server
    *
    * @param server The ActiveMQServer the plugin has been unregistered to
    */
   default void unregistered(ActiveMQServer server) {
   }
}
