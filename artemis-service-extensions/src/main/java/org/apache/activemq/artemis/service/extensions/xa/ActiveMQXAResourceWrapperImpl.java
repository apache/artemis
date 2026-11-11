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
package org.apache.activemq.artemis.service.extensions.xa;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.Map;
import java.util.Objects;

public class ActiveMQXAResourceWrapperImpl implements ActiveMQXAResourceWrapper {

   private final XAResource xaResource;

   // The EIS Name
   private final String productName;

   // The EIS Version
   private final String productVersion;

   // A composite of NodeID + JNDIName that allows administrator looking at an XAResource to determine it's origin.
   private final String jndiNameNodeId;

   /**
    * Creates a new XAResourceWrapper.  PRODUCT_NAME, productVersion and jndiName are useful for log output in the
    * Transaction Manager.  For Apache Artemis only the resourceManagerID is required to allow Transaction Manager to
    * recover from relevant recovery scenarios.
    */
   public ActiveMQXAResourceWrapperImpl(XAResource xaResource, Map<String, Object> properties) {
      this.xaResource = xaResource;
      //this.productName = ActiveMQResourceAdapter.PRODUCT_NAME;
      this.productName = (String) properties.get(ACTIVEMQ_PRODUCT_NAME);
      //this.productVersion = VersionLoader.getVersion().getFullVersion();
      this.productVersion = (String) properties.get(ACTIVEMQ_PRODUCT_VERSION);

      String jndiName = (String) properties.get(ACTIVEMQ_JNDI_NAME);
      String nodeId = "NodeId:" + properties.get(ACTIVEMQ_NODE_ID);

      this.jndiNameNodeId = Objects.requireNonNullElse(jndiName, nodeId) + " " + nodeId;
   }

   public XAResource getResource() {
      return xaResource;
   }

   public String getProductName() {
      return productName;
   }

   public String getProductVersion() {
      return productVersion;
   }

   public String getJndiName() {
      return jndiNameNodeId;
   }

   @Override
   public void commit(Xid xid, boolean b) throws XAException {
      getResource().commit(xid, b);
   }

   @Override
   public void end(Xid xid, int i) throws XAException {
      getResource().end(xid, i);
   }

   @Override
   public void forget(Xid xid) throws XAException {
      getResource().forget(xid);
   }

   @Override
   public int getTransactionTimeout() throws XAException {
      return getResource().getTransactionTimeout();
   }

   @Override
   public boolean isSameRM(XAResource xaResource) throws XAException {
      return getResource().isSameRM(xaResource);
   }

   @Override
   public int prepare(Xid xid) throws XAException {
      return getResource().prepare(xid);
   }

   @Override
   public Xid[] recover(int i) throws XAException {
      return getResource().recover(i);
   }

   @Override
   public void rollback(Xid xid) throws XAException {
      getResource().rollback(xid);
   }

   @Override
   public boolean setTransactionTimeout(int i) throws XAException {
      return getResource().setTransactionTimeout(i);
   }

   @Override
   public void start(Xid xid, int i) throws XAException {
      getResource().start(xid, i);
   }

}
