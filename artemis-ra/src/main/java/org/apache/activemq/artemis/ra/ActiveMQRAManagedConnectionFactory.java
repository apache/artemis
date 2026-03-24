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
package org.apache.activemq.artemis.ra;

import javax.jms.ConnectionMetaData;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterAssociation;
import javax.security.auth.Subject;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;

/**
 * ActiveMQ Artemis ManagedConnectionFactory
 */
public final class ActiveMQRAManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation {

   /**
    * Serial version UID
    */
   static final long serialVersionUID = -1452379518562456741L;

   /**
    * The resource adapter
    */
   private ActiveMQResourceAdapter ra;

   /**
    * Connection manager
    */
   private ConnectionManager cm;

   /**
    * The managed connection factory properties
    */
   private final ActiveMQRAMCFProperties mcfProperties;

   /**
    * Connection Factory used if properties are set
    */
   private ActiveMQConnectionFactory recoveryConnectionFactory;

   /**
    * The resource recovery if there is one
    */
   private XARecoveryConfig resourceRecovery;

   /**
    * Used to configure whether the connection should be part of the JTA TX. This is when the RA doesn not have access to the Transaction manager to deduct whether this is the case.
    */
   private boolean inJtaTransaction;

   /**
    * Constructor
    */
   public ActiveMQRAManagedConnectionFactory() {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("constructor()");
      }

      ra = null;
      cm = null;
      mcfProperties = new ActiveMQRAMCFProperties();
   }

   /**
    * Creates a Connection Factory instance
    *
    * @return javax.resource.cci.ConnectionFactory instance
    * @throws ResourceException Thrown if a connection factory can't be created
    */
   @Override
   public Object createConnectionFactory() throws ResourceException {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.debug("createConnectionFactory()");
      }

      return createConnectionFactory(new ActiveMQRAConnectionManager());
   }

   /**
    * Creates a Connection Factory instance
    *
    * @param cxManager The connection manager
    * @return javax.resource.cci.ConnectionFactory instance
    * @throws ResourceException Thrown if a connection factory can't be created
    */
   @Override
   public Object createConnectionFactory(final ConnectionManager cxManager) throws ResourceException {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("createConnectionFactory(" + cxManager + ")");
      }

      cm = cxManager;

      ActiveMQRAConnectionFactory cf = new ActiveMQRAConnectionFactoryImpl(this, cm);

      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("Created connection factory: " + cf +
                                          ", using connection manager: " +
                                          cm);
      }
      return cf;
   }

   /**
    * Creates a new physical connection to the underlying EIS resource manager.
    *
    * @param subject       Caller's security information
    * @param cxRequestInfo Additional resource adapter specific connection request information
    * @return The managed connection
    * @throws ResourceException Thrown if a managed connection can't be created
    */
   @Override
   public ManagedConnection createManagedConnection(final Subject subject,
                                                    final ConnectionRequestInfo cxRequestInfo) throws ResourceException {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("createManagedConnection(" + subject + ", " + cxRequestInfo + ")");
      }

      ActiveMQRAConnectionRequestInfo cri = getCRI((ActiveMQRAConnectionRequestInfo) cxRequestInfo);

      ActiveMQRACredential credential = ActiveMQRACredential.getCredential(this, subject, cri);

      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("jms credential: " + credential);
      }

      ActiveMQRAManagedConnection mc = new ActiveMQRAManagedConnection(this, cri, ra, credential.getUserName(), credential.getPassword());

      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("created new managed connection: " + mc);
      }

      registerRecovery();

      return mc;
   }

   private synchronized void registerRecovery() {
      if (recoveryConnectionFactory == null) {
         recoveryConnectionFactory = ra.createRecoveryActiveMQConnectionFactory(mcfProperties);

         Map<String, String> recoveryConfProps = new HashMap<>();
         recoveryConfProps.put(XARecoveryConfig.JNDI_NAME_PROPERTY_KEY, ra.getJndiName());
         resourceRecovery = ra.getRecoveryManager().register(recoveryConnectionFactory, null, null, recoveryConfProps);
      }
   }

   public XARecoveryConfig getResourceRecovery() {
      return resourceRecovery;
   }

   /**
    * Returns a matched connection from the candidate set of connections.
    *
    * @param connectionSet The candidate connection set
    * @param subject       Caller's security information
    * @param cxRequestInfo Additional resource adapter specific connection request information
    * @return The managed connection
    * @throws ResourceException Thrown if the managed connection can not be found
    */
   @Override
   public ManagedConnection matchManagedConnections(final Set connectionSet,
                                                    final Subject subject,
                                                    final ConnectionRequestInfo cxRequestInfo) throws ResourceException {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("matchManagedConnections(" + connectionSet +
                                          ", " +
                                          subject +
                                          ", " +
                                          cxRequestInfo +
                                          ")");
      }

      ActiveMQRAConnectionRequestInfo cri = getCRI((ActiveMQRAConnectionRequestInfo) cxRequestInfo);
      ActiveMQRACredential credential = ActiveMQRACredential.getCredential(this, subject, cri);

      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("Looking for connection matching credentials: " + credential);
      }

      for (Object obj : connectionSet) {
         if (obj instanceof ActiveMQRAManagedConnection) {
            ActiveMQRAManagedConnection mc = (ActiveMQRAManagedConnection) obj;
            ManagedConnectionFactory mcf = mc.getManagedConnectionFactory();

            if ((mc.getUserName() == null || mc.getUserName() != null && mc.getUserName().equals(credential.getUserName())) && mcf.equals(this)) {
               if (cri.equals(mc.getCRI())) {
                  if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
                     ActiveMQRALogger.LOGGER.trace("Found matching connection: " + mc);
                  }

                  return mc;
               }
            }
         }
      }

      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("No matching connection was found");
      }

      return null;
   }

   /**
    * Set the log writer -- NOT SUPPORTED
    *
    * @param out The writer
    * @throws ResourceException Thrown if the writer can't be set
    */
   @Override
   public void setLogWriter(final PrintWriter out) throws ResourceException {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("setLogWriter(" + out + ")");
      }
   }

   /**
    * Get the log writer -- NOT SUPPORTED
    *
    * @return The writer
    * @throws ResourceException Thrown if the writer can't be retrieved
    */
   @Override
   public PrintWriter getLogWriter() throws ResourceException {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("getLogWriter()");
      }

      return null;
   }

   /**
    * Get the resource adapter
    *
    * @return The resource adapter
    */
   @Override
   public ResourceAdapter getResourceAdapter() {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("getResourceAdapter()");
      }

      return ra;
   }

   public boolean isIgnoreJTA() {
      return ra.isIgnoreJTA();
   }

   /**
    * Set the resource adapter
    * <br>
    * This should ensure that when the RA is stopped, this MCF will be stopped as well.
    *
    * @param ra The resource adapter
    * @throws ResourceException Thrown if incorrect resource adapter
    */
   @Override
   public void setResourceAdapter(final ResourceAdapter ra) throws ResourceException {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("setResourceAdapter(" + ra + ")");
      }

      if (ra == null || !(ra instanceof ActiveMQResourceAdapter)) {
         throw new ResourceException("Resource adapter is " + ra);
      }

      this.ra = (ActiveMQResourceAdapter) ra;
      this.ra.setManagedConnectionFactory(this);
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    *
    * @param obj Object with which to compare
    * @return True if this object is the same as the obj argument; false otherwise.
    */
   @Override
   public boolean equals(final Object obj) {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("equals(" + obj + ")");
      }

      if (obj == null) {
         return false;
      }

      if (obj instanceof ActiveMQRAManagedConnectionFactory) {
         ActiveMQRAManagedConnectionFactory other = (ActiveMQRAManagedConnectionFactory) obj;

         return mcfProperties.equals(other.getProperties()) && ra.equals(other.getResourceAdapter());
      } else {
         return false;
      }
   }

   /**
    * Return the hash code for the object
    *
    * @return The hash code
    */
   @Override
   public int hashCode() {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("hashCode()");
      }

      int hash = mcfProperties.hashCode();
      hash += 31 * (ra != null ? ra.hashCode() : 0);

      return hash;
   }

   /**
    * Get the default session type
    *
    * @return The value
    */
   public String getSessionDefaultType() {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("getSessionDefaultType()");
      }

      return mcfProperties.getSessionDefaultType();
   }

   /**
    * Set the default session type
    *
    * @param type either javax.jms.Topic or javax.jms.Queue
    */
   public void setSessionDefaultType(final String type) {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("setSessionDefaultType(" + type + ")");
      }

      mcfProperties.setSessionDefaultType(type);
   }

   /**
    * @return the connectionParameters
    */
   public String getConnectionParameters() {
      return mcfProperties.getStrConnectionParameters();
   }

   public void setConnectionParameters(final String configuration) {
      mcfProperties.setConnectionParameters(configuration);
   }

   /**
    * @return the transportType
    */
   public String getConnectorClassName() {
      return mcfProperties.getConnectorClassName();
   }

   public void setConnectorClassName(final String value) {
      mcfProperties.setConnectorClassName(value);
   }

   public String getConnectionLoadBalancingPolicyClassName() {
      return mcfProperties.getConnectionLoadBalancingPolicyClassName();
   }

   public void setConnectionLoadBalancingPolicyClassName(final String connectionLoadBalancingPolicyClassName) {
      mcfProperties.setConnectionLoadBalancingPolicyClassName(connectionLoadBalancingPolicyClassName);
   }

   public String getDiscoveryAddress() {
      return mcfProperties.getDiscoveryAddress();
   }

   public void setDiscoveryAddress(final String discoveryAddress) {
      mcfProperties.setDiscoveryAddress(discoveryAddress);
   }

   public Integer getDiscoveryPort() {
      return mcfProperties.getDiscoveryPort();
   }

   public void setDiscoveryPort(final Integer discoveryPort) {
      mcfProperties.setDiscoveryPort(discoveryPort);
   }

   public Long getDiscoveryRefreshTimeout() {
      return mcfProperties.getDiscoveryRefreshTimeout();
   }

   public void setDiscoveryRefreshTimeout(final Long discoveryRefreshTimeout) {
      mcfProperties.setDiscoveryRefreshTimeout(discoveryRefreshTimeout);
   }

   public Long getDiscoveryInitialWaitTimeout() {
      return mcfProperties.getDiscoveryInitialWaitTimeout();
   }

   public void setDiscoveryInitialWaitTimeout(final Long discoveryInitialWaitTimeout) {
      mcfProperties.setDiscoveryInitialWaitTimeout(discoveryInitialWaitTimeout);
   }

   public String getClientID() {
      return mcfProperties.getClientID();
   }

   public void setClientID(final String clientID) {
      mcfProperties.setClientID(clientID);
   }

   public Integer getDupsOKBatchSize() {
      return mcfProperties.getDupsOKBatchSize();
   }

   public void setDupsOKBatchSize(final Integer dupsOKBatchSize) {
      mcfProperties.setDupsOKBatchSize(dupsOKBatchSize);
   }

   public Integer getTransactionBatchSize() {
      return mcfProperties.getTransactionBatchSize();
   }

   public void setTransactionBatchSize(final Integer transactionBatchSize) {
      mcfProperties.setTransactionBatchSize(transactionBatchSize);
   }

   public Long getClientFailureCheckPeriod() {
      return mcfProperties.getClientFailureCheckPeriod();
   }

   public void setClientFailureCheckPeriod(final Long clientFailureCheckPeriod) {
      mcfProperties.setClientFailureCheckPeriod(clientFailureCheckPeriod);
   }

   public Long getConnectionTTL() {
      return mcfProperties.getConnectionTTL();
   }

   public void setConnectionTTL(final Long connectionTTL) {
      mcfProperties.setConnectionTTL(connectionTTL);
   }

   public Long getCallTimeout() {
      return mcfProperties.getCallTimeout();
   }

   public void setCallTimeout(final Long callTimeout) {
      mcfProperties.setCallTimeout(callTimeout);
   }

   public Integer getConsumerWindowSize() {
      return mcfProperties.getConsumerWindowSize();
   }

   public void setConsumerWindowSize(final Integer consumerWindowSize) {
      mcfProperties.setConsumerWindowSize(consumerWindowSize);
   }

   public Integer getConsumerMaxRate() {
      return mcfProperties.getConsumerMaxRate();
   }

   public void setConsumerMaxRate(final Integer consumerMaxRate) {
      mcfProperties.setConsumerMaxRate(consumerMaxRate);
   }

   public Integer getConfirmationWindowSize() {
      return mcfProperties.getConfirmationWindowSize();
   }

   public void setConfirmationWindowSize(final Integer confirmationWindowSize) {
      mcfProperties.setConfirmationWindowSize(confirmationWindowSize);
   }

   public Integer getProducerMaxRate() {
      return mcfProperties.getProducerMaxRate();
   }

   public void setProducerMaxRate(final Integer producerMaxRate) {
      mcfProperties.setProducerMaxRate(producerMaxRate);
   }

   public Integer getMinLargeMessageSize() {
      return mcfProperties.getMinLargeMessageSize();
   }

   public void setMinLargeMessageSize(final Integer minLargeMessageSize) {
      mcfProperties.setMinLargeMessageSize(minLargeMessageSize);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getBlockOnAcknowledge() {
      return mcfProperties.isBlockOnAcknowledge();
   }

   public Boolean isBlockOnAcknowledge() {
      return mcfProperties.isBlockOnAcknowledge();
   }

   public void setBlockOnAcknowledge(final Boolean blockOnAcknowledge) {
      mcfProperties.setBlockOnAcknowledge(blockOnAcknowledge);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getBlockOnNonDurableSend() {
      return mcfProperties.isBlockOnNonDurableSend();
   }

   public Boolean isBlockOnNonDurableSend() {
      return mcfProperties.isBlockOnNonDurableSend();
   }

   public void setBlockOnNonDurableSend(final Boolean blockOnNonDurableSend) {
      mcfProperties.setBlockOnNonDurableSend(blockOnNonDurableSend);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getBlockOnDurableSend() {
      return mcfProperties.isBlockOnDurableSend();
   }

   public Boolean isBlockOnDurableSend() {
      return mcfProperties.isBlockOnDurableSend();
   }

   public void setBlockOnDurableSend(final Boolean blockOnDurableSend) {
      mcfProperties.setBlockOnDurableSend(blockOnDurableSend);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getAutoGroup() {
      return mcfProperties.isAutoGroup();
   }

   public Boolean isAutoGroup() {
      return mcfProperties.isAutoGroup();
   }

   public void setAutoGroup(final Boolean autoGroup) {
      mcfProperties.setAutoGroup(autoGroup);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getPreAcknowledge() {
      return mcfProperties.isPreAcknowledge();
   }

   public Boolean isPreAcknowledge() {
      return mcfProperties.isPreAcknowledge();
   }

   public void setPreAcknowledge(final Boolean preAcknowledge) {
      mcfProperties.setPreAcknowledge(preAcknowledge);
   }

   public Long getRetryInterval() {
      return mcfProperties.getRetryInterval();
   }

   public void setRetryInterval(final Long retryInterval) {
      mcfProperties.setRetryInterval(retryInterval);
   }

   public Double getRetryIntervalMultiplier() {
      return mcfProperties.getRetryIntervalMultiplier();
   }

   public void setRetryIntervalMultiplier(final Double retryIntervalMultiplier) {
      mcfProperties.setRetryIntervalMultiplier(retryIntervalMultiplier);
   }

   public Integer getReconnectAttempts() {
      return mcfProperties.getReconnectAttempts();
   }

   public void setReconnectAttempts(final Integer reconnectAttempts) {
      mcfProperties.setReconnectAttempts(reconnectAttempts);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getUseGlobalPools() {
      return mcfProperties.isUseGlobalPools();
   }

   public Boolean isUseGlobalPools() {
      return mcfProperties.isUseGlobalPools();
   }

   public void setUseGlobalPools(final Boolean useGlobalPools) {
      mcfProperties.setUseGlobalPools(useGlobalPools);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getCacheDestinations() {
      return mcfProperties.isCacheDestinations();
   }

   public Boolean isCacheDestinations() {
      return mcfProperties.isCacheDestinations();
   }

   public void setCacheDestinations(final Boolean cacheDestinations) {
      mcfProperties.setCacheDestinations(cacheDestinations);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getEnable1xPrefixes() {
      return mcfProperties.isEnable1xPrefixes();
   }

   public Boolean isEnable1xPrefixes() {
      return mcfProperties.isEnable1xPrefixes();
   }

   public void setEnable1xPrefixes(final Boolean enable1xPrefixes) {
      mcfProperties.setEnable1xPrefixes(enable1xPrefixes);
   }

   public Integer getScheduledThreadPoolMaxSize() {
      return mcfProperties.getScheduledThreadPoolMaxSize();
   }

   public void setScheduledThreadPoolMaxSize(final Integer scheduledThreadPoolMaxSize) {
      mcfProperties.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
   }

   public Integer getThreadPoolMaxSize() {
      return mcfProperties.getThreadPoolMaxSize();
   }

   public void setThreadPoolMaxSize(final Integer threadPoolMaxSize) {
      mcfProperties.setThreadPoolMaxSize(threadPoolMaxSize);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getHA() {
      return mcfProperties.isHA();
   }

   public Boolean isHA() {
      return mcfProperties.isHA();
   }

   public void setAllowLocalTransactions(Boolean allowLocalTransactions) {
      mcfProperties.setAllowLocalTransactions(allowLocalTransactions);
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getAllowLocalTransactions() {
      return mcfProperties.isAllowLocalTransactions();
   }

   public Boolean isAllowLocalTransactions() {
      return mcfProperties.isAllowLocalTransactions();
   }

   /**
    * A getter as well as a setter for those servers that use introspection
    */
   public Boolean getInJtaTransaction() {
      return mcfProperties.isInJtaTransaction();
   }

   public Boolean isInJtaTransaction() {
      return mcfProperties.isInJtaTransaction();
   }

   public void setInJtaTransaction(Boolean inJtaTransaction) {
      mcfProperties.setInJtaTransaction(inJtaTransaction);
   }

   public void setHA(Boolean ha) {
      mcfProperties.setHA(ha);
   }


   /**
    * Get the useTryLock.
    *
    * @return the useTryLock.
    */
   public Integer getUseTryLock() {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("getUseTryLock()");
      }

      return mcfProperties.getUseTryLock();
   }

   /**
    * Set the useTryLock.
    *
    * @param useTryLock the useTryLock.
    */
   public void setUseTryLock(final Integer useTryLock) {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("setUseTryLock(" + useTryLock + ")");
      }

      mcfProperties.setUseTryLock(useTryLock);
   }

   /**
    * Get the connection metadata
    *
    * @return The metadata
    */
   public ConnectionMetaData getMetaData() {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("getMetadata()");
      }

      return new ActiveMQRAConnectionMetaData();
   }

   /**
    * Get the managed connection factory properties
    *
    * @return The properties
    */
   protected ActiveMQRAMCFProperties getProperties() {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("getProperties()");
      }

      return mcfProperties;
   }

   /**
    * Get a connection request info instance
    *
    * @param info The instance that should be updated; may be <code>null</code>
    * @return The instance
    */
   private ActiveMQRAConnectionRequestInfo getCRI(final ActiveMQRAConnectionRequestInfo info) {
      if (ActiveMQRALogger.LOGGER.isTraceEnabled()) {
         ActiveMQRALogger.LOGGER.trace("getCRI(" + info + ")");
      }

      if (info == null) {
         // Create a default one
         return new ActiveMQRAConnectionRequestInfo(ra.getProperties(), mcfProperties.getType());
      } else {
         // Fill the one with any defaults
         info.setDefaults(ra.getProperties());
         return info;
      }
   }

   // this should be called when ActiveMQResourceAdapter.stop() is called since this MCF is registered with it
   public void stop() {
      if (resourceRecovery != null) {
         ra.getRecoveryManager().unRegister(resourceRecovery);
      }

      if (recoveryConnectionFactory != null) {
         recoveryConnectionFactory.close();
         recoveryConnectionFactory = null;
      }
   }
}
