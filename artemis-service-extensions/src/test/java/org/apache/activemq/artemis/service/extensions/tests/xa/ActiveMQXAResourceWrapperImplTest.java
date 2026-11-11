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
package org.apache.activemq.artemis.service.extensions.tests.xa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.transaction.xa.XAResource;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper;
import org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapperImpl;
import org.junit.jupiter.api.Test;

public class ActiveMQXAResourceWrapperImplTest {

   @Test
   public void testXAResourceWrapper() {
      String jndiName = "java://jmsXA";
      String nodeId = "0";
      XAResource xaResource = new MockXAResource();

      Map<String, Object> xaResourceWrapperProperties = new HashMap<>();
      xaResourceWrapperProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_JNDI_NAME, jndiName);
      xaResourceWrapperProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_NODE_ID, nodeId);
      xaResourceWrapperProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_VERSION, "6");
      xaResourceWrapperProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_NAME, "ActiveMQ Artemis");
      ActiveMQXAResourceWrapperImpl xaResourceWrapper = new ActiveMQXAResourceWrapperImpl(xaResource, xaResourceWrapperProperties);

      String expectedJndiNodeId = jndiName + " NodeId:" + nodeId;
      assertEquals(xaResource, xaResourceWrapper.getResource());
      assertEquals(expectedJndiNodeId, xaResourceWrapper.getJndiName());
   }
}
