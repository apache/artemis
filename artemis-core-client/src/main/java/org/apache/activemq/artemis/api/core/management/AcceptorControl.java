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
package org.apache.activemq.artemis.api.core.management;

import javax.management.MBeanOperationInfo;
import java.util.Map;

/**
 * An AcceptorControl is used to manage Acceptors.
 */
public interface AcceptorControl extends ActiveMQComponentControl {

   /**
    * {@return the name of the acceptor}
    */
   @Attribute(desc = "name of the acceptor")
   String getName();

   /**
    * {@return the class name of the AcceptorFactory implementation used by this acceptor}
    */
   @Attribute(desc = "class name of the AcceptorFactory implementation used by this acceptor")
   String getFactoryClassName();

   /**
    * {@return the parameters used to configure this acceptor}
    */
   @Attribute(desc = "parameters used to configure this acceptor")
   Map<String, Object> getParameters();

   /**
    * Re-create the acceptor with the existing configuration values. Useful, for example, for reloading key/trust stores
    * on acceptors which support SSL.
    */
   @Operation(desc = "Re-create the acceptor with the existing configuration values. Useful, for example, for reloading key/trust stores on acceptors which support SSL.", impact = MBeanOperationInfo.ACTION)
   void reload();
}
