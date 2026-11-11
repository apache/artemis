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
package org.apache.activemq.artemis.core.server;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;

public interface SecuritySettingPlugin extends Serializable {

   default void setInit(Map<String, String> props) {
      init(props);
   }

   /**
    * Initialize the plugin with the given configuration options. This method is called by the broker when the
    * file-based configuration is read (see
    * {@code org.apache.activemq.artemis.core.deployers.impl.FileConfigurationParser#parseSecurity(org.w3c.dom.Element,
    * org.apache.activemq.artemis.core.config.Configuration)}. If you're creating/configuring the plugin
    * programmatically then the recommended approach is to simply use the plugin's getters/setters rather than this
    * method.
    *
    * @param options name/value pairs used to configure the SecuritySettingPlugin instance
    * @return {@code this} instance
    */
   SecuritySettingPlugin init(Map<String, String> options);

   /**
    * Clean up all the associated resources associated with this plugin (e.g. LDAP connections, file handles, etc.)
    *
    * @return {@code this} instance
    */
   SecuritySettingPlugin stop();

   /**
    * Fetch the security role information from the external environment (e.g. file, LDAP, etc.) and return it.
    *
    * @return the Map's key corresponds to the "match" for the security setting and the corresponding value is the set
    * of {@code org.apache.activemq.artemis.core.security.Role} objects defining the appropriate authorization
    */
   Map<String, Set<Role>> getSecurityRoles();

   /**
    * This method is called by the broker during the start-up process. It's for plugins that might need to modify the
    * security settings during runtime (e.g. LDAP plugin that uses a listener to receive updates, etc.). Any changes
    * made to this {@code HierarchicalRepository} will be reflected in the broker.
    */
   void setSecurityRepository(HierarchicalRepository<Set<Role>> securityRepository);
}
