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
package org.apache.activemq.artemis.protocol.amqp.federation;

import java.util.Map;

import org.apache.activemq.artemis.core.config.TransformerConfiguration;

/**
 * Interface that a Federation receive from (address or queue) policy should implement and provides some common APIs
 * that each should share.
 */
public interface FederationReceiveFromResourcePolicy {

   /**
    * {@return the federation type this policy configuration defines}
    */
   FederationType getPolicyType();

   /**
    * {@return the name assigned to this federation policy}
    */
   String getPolicyName();

   /**
    * {@return a {@link Map} of properties that were used in the policy configuration}
    */
   Map<String, Object> getProperties();

   /**
    * {@return the {@link TransformerConfiguration} that was specified in the policy configuration}
    */
   TransformerConfiguration getTransformerConfiguration();

}
