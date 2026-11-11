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
package org.apache.activemq;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.naming.Context;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.Objects;

import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.apache.activemq.blob.BlobTransferPolicy;
import org.apache.activemq.broker.region.policy.RedeliveryPolicyMap;
import org.apache.activemq.jndi.JNDIBaseStorable;
import org.apache.activemq.management.JMSStatsImpl;
import org.apache.activemq.management.StatsCapable;
import org.apache.activemq.management.StatsImpl;
import org.apache.activemq.thread.TaskRunnerFactory;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.TransportFactory;
import org.apache.activemq.transport.TransportListener;
import org.apache.activemq.util.ClassLoadingAwareObjectInputStream;
import org.apache.activemq.util.IdGenerator;
import org.apache.activemq.util.IntrospectionSupport;
import org.apache.activemq.util.JMSExceptionSupport;
import org.apache.activemq.util.URISupport;
import org.apache.activemq.util.URISupport.CompositeData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActiveMQConnectionFactory extends JNDIBaseStorable implements ConnectionFactory, QueueConnectionFactory, TopicConnectionFactory, StatsCapable, Cloneable {

   private static final Logger LOG = LoggerFactory.getLogger(ActiveMQConnectionFactory.class);
   private static final String DEFAULT_BROKER_HOST;
   private static final int DEFAULT_BROKER_PORT;
   private static URI defaultTcpUri;

   static {
      String host = null;
      String port = null;
      try {
         host = SecurityManagerShim.doPrivileged((PrivilegedAction<String>) () -> {
            String result = System.getProperty("org.apache.activemq.AMQ_HOST");
            result = (result == null || result.isEmpty()) ? System.getProperty("AMQ_HOST", "localhost") : result;
            return result;
         });
         port = SecurityManagerShim.doPrivileged((PrivilegedAction<String>) () -> {
            String result = System.getProperty("org.apache.activemq.AMQ_PORT");
            result = (result == null || result.isEmpty()) ? System.getProperty("AMQ_PORT", "61616") : result;
            return result;
         });
      } catch (Throwable e) {
         LOG.debug("Failed to look up System properties for host and port", e);
      }
      host = (host == null || host.isEmpty()) ? "localhost" : host;
      port = (port == null || port.isEmpty()) ? "61616" : port;
      DEFAULT_BROKER_HOST = host;
      DEFAULT_BROKER_PORT = Integer.parseInt(port);
   }

   public static final String DEFAULT_BROKER_BIND_URL;

   static {
      final String defaultURL = "tcp://" + DEFAULT_BROKER_HOST + ":" + DEFAULT_BROKER_PORT;
      String bindURL = null;

      try {
         bindURL = SecurityManagerShim.doPrivileged((PrivilegedAction<String>) () -> {
            String result = System.getProperty("org.apache.activemq.BROKER_BIND_URL");
            result = (result == null || result.isEmpty()) ? System.getProperty("BROKER_BIND_URL", defaultURL) : result;
            return result;
         });
      } catch (Throwable e) {
         LOG.debug("Failed to look up System properties for host and port", e);
      }
      bindURL = (bindURL == null || bindURL.isEmpty()) ? defaultURL : bindURL;
      DEFAULT_BROKER_BIND_URL = bindURL;
      try {
         defaultTcpUri = new URI(defaultURL);
      } catch (URISyntaxException e) {
         LOG.debug("Failed to build default tcp url", e);
      }

   }

   public static final String DEFAULT_BROKER_URL = "failover://" + DEFAULT_BROKER_BIND_URL;
   public static final String DEFAULT_USER = null;
   public static final String DEFAULT_PASSWORD = null;
   public static final int DEFAULT_PRODUCER_WINDOW_SIZE = 0;

   protected URI brokerURL;
   protected URI vmBrokerUri;
   protected String userName;
   protected String password;
   protected String clientID;
   protected boolean dispatchAsync = true;
   protected boolean alwaysSessionAsync = true;

   JMSStatsImpl factoryStats = new JMSStatsImpl();

   private IdGenerator clientIdGenerator;
   private String clientIDPrefix;
   private IdGenerator connectionIdGenerator;
   private String connectionIDPrefix;

   // client policies
   private ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
   private RedeliveryPolicyMap redeliveryPolicyMap = new RedeliveryPolicyMap();

   {
      redeliveryPolicyMap.setDefaultEntry(new RedeliveryPolicy());
   }

   private BlobTransferPolicy blobTransferPolicy = new BlobTransferPolicy();
   private MessageTransformer transformer;

   private boolean disableTimeStampsByDefault;
   private boolean optimizedMessageDispatch = true;
   private long optimizeAcknowledgeTimeOut = 300;
   private long optimizedAckScheduledAckInterval = 0;
   private boolean copyMessageOnSend = true;
   private boolean useCompression;
   private boolean objectMessageSerializationDefered;
   private boolean useAsyncSend;
   private boolean optimizeAcknowledge;
   private int closeTimeout = 15000;
   private boolean useRetroactiveConsumer;
   private boolean exclusiveConsumer;
   private boolean nestedMapAndListEnabled = true;
   private boolean alwaysSyncSend;
   private boolean watchTopicAdvisories = true;
   private int producerWindowSize = DEFAULT_PRODUCER_WINDOW_SIZE;
   private long warnAboutUnstartedConnectionTimeout = 500L;
   private int sendTimeout = 0;
   private boolean sendAcksAsync = true;
   private TransportListener transportListener;
   private ExceptionListener exceptionListener;
   private int auditDepth = ActiveMQMessageAuditNoSync.DEFAULT_WINDOW_SIZE;
   private int auditMaximumProducerNumber = ActiveMQMessageAuditNoSync.MAXIMUM_PRODUCER_COUNT;
   private boolean useDedicatedTaskRunner;
   private long consumerFailoverRedeliveryWaitPeriod = 0;
   private boolean checkForDuplicates = true;
   private ClientInternalExceptionListener clientInternalExceptionListener;
   private boolean messagePrioritySupported = false;
   private boolean transactedIndividualAck = false;
   private boolean nonBlockingRedelivery = false;
   private int maxThreadPoolSize = ActiveMQConnection.DEFAULT_THREAD_POOL_SIZE;
   private TaskRunnerFactory sessionTaskRunner;
   private RejectedExecutionHandler rejectedTaskHandler = null;
   protected int xaAckMode = -1; // ensure default init before setting via brokerUrl introspection in sub class
   private boolean rmIdFromConnectionId = false;
   private boolean consumerExpiryCheckEnabled = true;
   private List<String> trustedPackages = Arrays.asList(ClassLoadingAwareObjectInputStream.serializablePackages);
   private boolean trustAllPackages = false;

   // /////////////////////////////////////////////
   //
   // ConnectionFactory, QueueConnectionFactory, TopicConnectionFactory Methods
   //
   // /////////////////////////////////////////////

   public ActiveMQConnectionFactory() {
      this(DEFAULT_BROKER_URL);
   }

   public ActiveMQConnectionFactory(String brokerURL) {
      this(createURI(brokerURL));
      try {
         URI uri = new URI(brokerURL);
         String scheme = uri.getScheme();
         if ("vm".equals(scheme)) {
            Map<String, String> params = URISupport.parseParameters(uri);
            params.clear();

            this.vmBrokerUri = URISupport.createRemainingURI(uri, params);
         }
      } catch (URISyntaxException e) {
      }

   }

   public ActiveMQConnectionFactory(URI brokerURL) {

      setBrokerURL(brokerURL.toString());
   }

   public ActiveMQConnectionFactory(String userName, String password, URI brokerURL) {
      setUserName(userName);
      setPassword(password);
      setBrokerURL(brokerURL.toString());
   }

   public ActiveMQConnectionFactory(String userName, String password, String brokerURL) {
      setUserName(userName);
      setPassword(password);
      setBrokerURL(brokerURL);
   }

   public ActiveMQConnectionFactory copy() {
      try {
         return (ActiveMQConnectionFactory) super.clone();
      } catch (CloneNotSupportedException e) {
         throw new RuntimeException("This should never happen: " + e, e);
      }
   }

   private static URI createURI(String brokerURL) {
      try {
         URI uri = new URI(brokerURL);
         String scheme = uri.getScheme();
         if ("vm".equals(scheme)) {
            Map<String, String> params = URISupport.parseParameters(uri);
            //EMPTY_MAP is immutable, so use a normal map instead.
            if (params == Collections.EMPTY_MAP) {
               params = new HashMap<>();
            }
            params.put("invmBrokerId", Objects.requireNonNullElse(uri.getHost(), "localhost"));
            defaultTcpUri = URISupport.createRemainingURI(defaultTcpUri, params);
            return defaultTcpUri;
         }
         return uri;
      } catch (URISyntaxException e) {
         throw (IllegalArgumentException) new IllegalArgumentException("Invalid broker URI: " + brokerURL).initCause(e);
      }
   }

   @Override
   public Connection createConnection() throws JMSException {
      return createActiveMQConnection();
   }

   @Override
   public Connection createConnection(String userName, String password) throws JMSException {
      return createActiveMQConnection(userName, password);
   }

   @Override
   public QueueConnection createQueueConnection() throws JMSException {
      return createActiveMQConnection().enforceQueueOnlyConnection();
   }

   @Override
   public QueueConnection createQueueConnection(String userName, String password) throws JMSException {
      return createActiveMQConnection(userName, password).enforceQueueOnlyConnection();
   }

   @Override
   public TopicConnection createTopicConnection() throws JMSException {
      return createActiveMQConnection();
   }

   @Override
   public TopicConnection createTopicConnection(String userName, String password) throws JMSException {
      return createActiveMQConnection(userName, password);
   }

   @Override
   public StatsImpl getStats() {
      return this.factoryStats;
   }

   // /////////////////////////////////////////////
   //
   // Implementation methods.
   //
   // /////////////////////////////////////////////

   protected ActiveMQConnection createActiveMQConnection() throws JMSException {
      return createActiveMQConnection(userName, password);
   }

   protected Transport createTransport() throws JMSException {
      try {
         System.out.println("xxxxxcreating conn: " + brokerURL.toString());
         Transport t = TransportFactory.connect(brokerURL);
         System.out.println("xxxxxxxxxxxx created transport" + t);
         return t;
      } catch (Exception e) {
         throw JMSExceptionSupport.create("Could not create Transport. Reason: " + e, e);
      }
   }

   protected ActiveMQConnection createActiveMQConnection(String userName, String password) throws JMSException {
      if (brokerURL == null) {
         throw new ConfigurationException("brokerURL not set.");
      }
      ActiveMQConnection connection = null;
      try {
         Transport transport = createTransport();
         connection = createActiveMQConnection(transport, factoryStats);

         connection.setUserName(userName);
         connection.setPassword(password);

         configureConnection(connection);

         transport.start();

         if (clientID != null) {
            connection.setDefaultClientID(clientID);
         }

         return connection;
      } catch (JMSException e) {
         // Clean up!
         try {
            connection.close();
         } catch (Throwable ignore) {
         }
         throw e;
      } catch (Exception e) {
         // Clean up!
         try {
            connection.close();
         } catch (Throwable ignore) {
         }
         throw JMSExceptionSupport.create("Could not connect to broker URL: " + brokerURL + ". Reason: " + e, e);
      }
   }

   protected ActiveMQConnection createActiveMQConnection(Transport transport, JMSStatsImpl stats) throws Exception {
      ActiveMQConnection connection = new ActiveMQConnection(transport, getClientIdGenerator(), getConnectionIdGenerator(), stats);
      return connection;
   }

   protected void configureConnection(ActiveMQConnection connection) throws JMSException {
      connection.setPrefetchPolicy(getPrefetchPolicy());
      connection.setDisableTimeStampsByDefault(isDisableTimeStampsByDefault());
      connection.setOptimizedMessageDispatch(isOptimizedMessageDispatch());
      connection.setCopyMessageOnSend(isCopyMessageOnSend());
      connection.setUseCompression(isUseCompression());
      connection.setObjectMessageSerializationDefered(isObjectMessageSerializationDefered());
      connection.setDispatchAsync(isDispatchAsync());
      connection.setUseAsyncSend(isUseAsyncSend());
      connection.setAlwaysSyncSend(isAlwaysSyncSend());
      connection.setAlwaysSessionAsync(isAlwaysSessionAsync());
      connection.setOptimizeAcknowledge(isOptimizeAcknowledge());
      connection.setOptimizeAcknowledgeTimeOut(getOptimizeAcknowledgeTimeOut());
      connection.setOptimizedAckScheduledAckInterval(getOptimizedAckScheduledAckInterval());
      connection.setUseRetroactiveConsumer(isUseRetroactiveConsumer());
      connection.setExclusiveConsumer(isExclusiveConsumer());
      connection.setRedeliveryPolicyMap(getRedeliveryPolicyMap());
      connection.setTransformer(getTransformer());
      connection.setBlobTransferPolicy(getBlobTransferPolicy().copy());
      connection.setWatchTopicAdvisories(isWatchTopicAdvisories());
      connection.setProducerWindowSize(getProducerWindowSize());
      connection.setWarnAboutUnstartedConnectionTimeout(getWarnAboutUnstartedConnectionTimeout());
      connection.setSendTimeout(getSendTimeout());
      connection.setCloseTimeout(getCloseTimeout());
      connection.setSendAcksAsync(isSendAcksAsync());
      connection.setAuditDepth(getAuditDepth());
      connection.setAuditMaximumProducerNumber(getAuditMaximumProducerNumber());
      connection.setUseDedicatedTaskRunner(isUseDedicatedTaskRunner());
      connection.setConsumerFailoverRedeliveryWaitPeriod(getConsumerFailoverRedeliveryWaitPeriod());
      connection.setCheckForDuplicates(isCheckForDuplicates());
      connection.setMessagePrioritySupported(isMessagePrioritySupported());
      connection.setTransactedIndividualAck(isTransactedIndividualAck());
      connection.setNonBlockingRedelivery(isNonBlockingRedelivery());
      connection.setMaxThreadPoolSize(getMaxThreadPoolSize());
      connection.setSessionTaskRunner(getSessionTaskRunner());
      connection.setRejectedTaskHandler(getRejectedTaskHandler());
      connection.setNestedMapAndListEnabled(isNestedMapAndListEnabled());
      connection.setRmIdFromConnectionId(isRmIdFromConnectionId());
      connection.setConsumerExpiryCheckEnabled(isConsumerExpiryCheckEnabled());
      connection.setTrustedPackages(getTrustedPackages());
      connection.setTrustAllPackages(isTrustAllPackages());
      if (transportListener != null) {
         connection.addTransportListener(transportListener);
      }
      if (exceptionListener != null) {
         connection.setExceptionListener(exceptionListener);
      }
      if (clientInternalExceptionListener != null) {
         connection.setClientInternalExceptionListener(clientInternalExceptionListener);
      }
   }

   // /////////////////////////////////////////////
   //
   // Property Accessors
   //
   // /////////////////////////////////////////////

   public String getBrokerURL() {
      System.out.println("vm uri: " + vmBrokerUri);
      if (vmBrokerUri != null)
         return vmBrokerUri.toString();
      return Objects.toString(brokerURL, null);
   }

   public void setBrokerURL(String brokerURL) {
      URI uri = null;
      try {
         uri = new URI(brokerURL);
         String scheme = uri.getScheme();
         if ("vm".equals(scheme)) {
            this.vmBrokerUri = uri;
         }
      } catch (URISyntaxException e) {
      }
      this.brokerURL = createURI(brokerURL);

      // Use all the properties prefixed with 'jms.' to set the connection
      // factory
      // options.
      if (this.brokerURL.getQuery() != null) {
         // It might be a standard URI or...
         try {

            Map<String, String> map = URISupport.parseQuery(this.brokerURL.getQuery());
            Map<String, Object> jmsOptionsMap = IntrospectionSupport.extractProperties(map, "jms.");
            if (buildFromMap(jmsOptionsMap)) {
               if (!jmsOptionsMap.isEmpty()) {
                  String msg = "There are " + jmsOptionsMap.size() + " jms options that couldn't be set on the ConnectionFactory." + " Check the options are spelled correctly." + " Unknown parameters=[" + jmsOptionsMap + "]." + " This connection factory cannot be started.";
                  throw new IllegalArgumentException(msg);
               }

               this.brokerURL = URISupport.createRemainingURI(this.brokerURL, map);
            }

         } catch (URISyntaxException e) {
         }

      } else {

         // It might be a composite URI.
         try {
            CompositeData data = URISupport.parseComposite(this.brokerURL);
            Map<String, Object> jmsOptionsMap = IntrospectionSupport.extractProperties(data.getParameters(), "jms.");
            if (buildFromMap(jmsOptionsMap)) {
               if (!jmsOptionsMap.isEmpty()) {
                  String msg = "There are " + jmsOptionsMap.size() + " jms options that couldn't be set on the ConnectionFactory." + " Check the options are spelled correctly." + " Unknown parameters=[" + jmsOptionsMap + "]." + " This connection factory cannot be started.";
                  throw new IllegalArgumentException(msg);
               }

               this.brokerURL = data.toURI();
            }
         } catch (URISyntaxException e) {
         }
      }
   }

   public String getClientID() {
      return clientID;
   }

   public void setClientID(String clientID) {
      this.clientID = clientID;
   }

   public boolean isCopyMessageOnSend() {
      return copyMessageOnSend;
   }

   public void setCopyMessageOnSend(boolean copyMessageOnSend) {
      this.copyMessageOnSend = copyMessageOnSend;
   }

   public boolean isDisableTimeStampsByDefault() {
      return disableTimeStampsByDefault;
   }

   public void setDisableTimeStampsByDefault(boolean disableTimeStampsByDefault) {
      this.disableTimeStampsByDefault = disableTimeStampsByDefault;
   }

   public boolean isOptimizedMessageDispatch() {
      return optimizedMessageDispatch;
   }

   public void setOptimizedMessageDispatch(boolean optimizedMessageDispatch) {
      this.optimizedMessageDispatch = optimizedMessageDispatch;
   }

   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public ActiveMQPrefetchPolicy getPrefetchPolicy() {
      return prefetchPolicy;
   }

   public void setPrefetchPolicy(ActiveMQPrefetchPolicy prefetchPolicy) {
      this.prefetchPolicy = prefetchPolicy;
   }

   public boolean isUseAsyncSend() {
      return useAsyncSend;
   }

   public BlobTransferPolicy getBlobTransferPolicy() {
      return blobTransferPolicy;
   }

   public void setBlobTransferPolicy(BlobTransferPolicy blobTransferPolicy) {
      this.blobTransferPolicy = blobTransferPolicy;
   }

   public void setUseAsyncSend(boolean useAsyncSend) {
      this.useAsyncSend = useAsyncSend;
   }

   public synchronized boolean isWatchTopicAdvisories() {
      return watchTopicAdvisories;
   }

   public synchronized void setWatchTopicAdvisories(boolean watchTopicAdvisories) {
      this.watchTopicAdvisories = watchTopicAdvisories;
   }

   public boolean isAlwaysSyncSend() {
      return this.alwaysSyncSend;
   }

   public void setAlwaysSyncSend(boolean alwaysSyncSend) {
      this.alwaysSyncSend = alwaysSyncSend;
   }

   public String getUserName() {
      return userName;
   }

   public void setUserName(String userName) {
      this.userName = userName;
   }

   public boolean isUseRetroactiveConsumer() {
      return useRetroactiveConsumer;
   }

   public void setUseRetroactiveConsumer(boolean useRetroactiveConsumer) {
      this.useRetroactiveConsumer = useRetroactiveConsumer;
   }

   public boolean isExclusiveConsumer() {
      return exclusiveConsumer;
   }

   public void setExclusiveConsumer(boolean exclusiveConsumer) {
      this.exclusiveConsumer = exclusiveConsumer;
   }

   public RedeliveryPolicy getRedeliveryPolicy() {
      return redeliveryPolicyMap.getDefaultEntry();
   }

   public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
      this.redeliveryPolicyMap.setDefaultEntry(redeliveryPolicy);
   }

   public RedeliveryPolicyMap getRedeliveryPolicyMap() {
      return this.redeliveryPolicyMap;
   }

   public void setRedeliveryPolicyMap(RedeliveryPolicyMap redeliveryPolicyMap) {
      this.redeliveryPolicyMap = redeliveryPolicyMap;
   }

   public MessageTransformer getTransformer() {
      return transformer;
   }

   public int getSendTimeout() {
      return sendTimeout;
   }

   public void setSendTimeout(int sendTimeout) {
      this.sendTimeout = sendTimeout;
   }

   public boolean isSendAcksAsync() {
      return sendAcksAsync;
   }

   public void setSendAcksAsync(boolean sendAcksAsync) {
      this.sendAcksAsync = sendAcksAsync;
   }

   public boolean isMessagePrioritySupported() {
      return this.messagePrioritySupported;
   }

   public void setMessagePrioritySupported(boolean messagePrioritySupported) {
      this.messagePrioritySupported = messagePrioritySupported;
   }

   public void setTransformer(MessageTransformer transformer) {
      this.transformer = transformer;
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   @Override
   public void buildFromProperties(Properties properties) {

      if (properties == null) {
         properties = new Properties();
      }

      String temp = properties.getProperty(Context.PROVIDER_URL);
      if (temp == null || temp.length() == 0) {
         temp = properties.getProperty("brokerURL");
      }
      if (temp != null && temp.length() > 0) {
         setBrokerURL(temp);
      }

      Map<String, Object> p = new HashMap(properties);
      buildFromMap(p);
   }

   public boolean buildFromMap(Map<String, Object> properties) {
      boolean rc = false;

      ActiveMQPrefetchPolicy p = new ActiveMQPrefetchPolicy();
      if (IntrospectionSupport.setProperties(p, properties, "prefetchPolicy.")) {
         setPrefetchPolicy(p);
         rc = true;
      }

      RedeliveryPolicy rp = new RedeliveryPolicy();
      if (IntrospectionSupport.setProperties(rp, properties, "redeliveryPolicy.")) {
         setRedeliveryPolicy(rp);
         rc = true;
      }

      BlobTransferPolicy blobTransferPolicy = new BlobTransferPolicy();
      if (IntrospectionSupport.setProperties(blobTransferPolicy, properties, "blobTransferPolicy.")) {
         setBlobTransferPolicy(blobTransferPolicy);
         rc = true;
      }

      rc |= IntrospectionSupport.setProperties(this, properties);

      return rc;
   }

   @Override
   public void populateProperties(Properties props) {
      props.setProperty("dispatchAsync", Boolean.toString(isDispatchAsync()));

      if (getBrokerURL() != null) {
         props.setProperty(Context.PROVIDER_URL, getBrokerURL());
         props.setProperty("brokerURL", getBrokerURL());
      }

      if (getClientID() != null) {
         props.setProperty("clientID", getClientID());
      }

      IntrospectionSupport.getProperties(getPrefetchPolicy(), props, "prefetchPolicy.");
      IntrospectionSupport.getProperties(getRedeliveryPolicy(), props, "redeliveryPolicy.");
      IntrospectionSupport.getProperties(getBlobTransferPolicy(), props, "blobTransferPolicy.");

      props.setProperty("copyMessageOnSend", Boolean.toString(isCopyMessageOnSend()));
      props.setProperty("disableTimeStampsByDefault", Boolean.toString(isDisableTimeStampsByDefault()));
      props.setProperty("objectMessageSerializationDefered", Boolean.toString(isObjectMessageSerializationDefered()));
      props.setProperty("optimizedMessageDispatch", Boolean.toString(isOptimizedMessageDispatch()));

      if (getPassword() != null) {
         props.setProperty("password", getPassword());
      }

      props.setProperty("useAsyncSend", Boolean.toString(isUseAsyncSend()));
      props.setProperty("useCompression", Boolean.toString(isUseCompression()));
      props.setProperty("useRetroactiveConsumer", Boolean.toString(isUseRetroactiveConsumer()));
      props.setProperty("watchTopicAdvisories", Boolean.toString(isWatchTopicAdvisories()));

      if (getUserName() != null) {
         props.setProperty("userName", getUserName());
      }

      props.setProperty("closeTimeout", Integer.toString(getCloseTimeout()));
      props.setProperty("alwaysSessionAsync", Boolean.toString(isAlwaysSessionAsync()));
      props.setProperty("optimizeAcknowledge", Boolean.toString(isOptimizeAcknowledge()));
      props.setProperty("statsEnabled", Boolean.toString(isStatsEnabled()));
      props.setProperty("alwaysSyncSend", Boolean.toString(isAlwaysSyncSend()));
      props.setProperty("producerWindowSize", Integer.toString(getProducerWindowSize()));
      props.setProperty("sendTimeout", Integer.toString(getSendTimeout()));
      props.setProperty("sendAcksAsync", Boolean.toString(isSendAcksAsync()));
      props.setProperty("auditDepth", Integer.toString(getAuditDepth()));
      props.setProperty("auditMaximumProducerNumber", Integer.toString(getAuditMaximumProducerNumber()));
      props.setProperty("checkForDuplicates", Boolean.toString(isCheckForDuplicates()));
      props.setProperty("messagePrioritySupported", Boolean.toString(isMessagePrioritySupported()));
      props.setProperty("transactedIndividualAck", Boolean.toString(isTransactedIndividualAck()));
      props.setProperty("nonBlockingRedelivery", Boolean.toString(isNonBlockingRedelivery()));
      props.setProperty("maxThreadPoolSize", Integer.toString(getMaxThreadPoolSize()));
      props.setProperty("nestedMapAndListEnabled", Boolean.toString(isNestedMapAndListEnabled()));
      props.setProperty("consumerFailoverRedeliveryWaitPeriod", Long.toString(getConsumerFailoverRedeliveryWaitPeriod()));
      props.setProperty("rmIdFromConnectionId", Boolean.toString(isRmIdFromConnectionId()));
      props.setProperty("consumerExpiryCheckEnabled", Boolean.toString(isConsumerExpiryCheckEnabled()));
   }

   public boolean isUseCompression() {
      return useCompression;
   }

   public void setUseCompression(boolean useCompression) {
      this.useCompression = useCompression;
   }

   public boolean isObjectMessageSerializationDefered() {
      return objectMessageSerializationDefered;
   }

   public void setObjectMessageSerializationDefered(boolean objectMessageSerializationDefered) {
      this.objectMessageSerializationDefered = objectMessageSerializationDefered;
   }

   public boolean isDispatchAsync() {
      return dispatchAsync;
   }

   public void setDispatchAsync(boolean asyncDispatch) {
      this.dispatchAsync = asyncDispatch;
   }

   public int getCloseTimeout() {
      return closeTimeout;
   }

   public void setCloseTimeout(int closeTimeout) {
      this.closeTimeout = closeTimeout;
   }

   public boolean isAlwaysSessionAsync() {
      return alwaysSessionAsync;
   }

   public void setAlwaysSessionAsync(boolean alwaysSessionAsync) {
      this.alwaysSessionAsync = alwaysSessionAsync;
   }

   public boolean isOptimizeAcknowledge() {
      return optimizeAcknowledge;
   }

   public void setOptimizeAcknowledge(boolean optimizeAcknowledge) {
      this.optimizeAcknowledge = optimizeAcknowledge;
   }

   public void setOptimizeAcknowledgeTimeOut(long optimizeAcknowledgeTimeOut) {
      this.optimizeAcknowledgeTimeOut = optimizeAcknowledgeTimeOut;
   }

   public long getOptimizeAcknowledgeTimeOut() {
      return optimizeAcknowledgeTimeOut;
   }

   public boolean isNestedMapAndListEnabled() {
      return nestedMapAndListEnabled;
   }

   public void setNestedMapAndListEnabled(boolean structuredMapsEnabled) {
      this.nestedMapAndListEnabled = structuredMapsEnabled;
   }

   public String getClientIDPrefix() {
      return clientIDPrefix;
   }

   public void setClientIDPrefix(String clientIDPrefix) {
      this.clientIDPrefix = clientIDPrefix;
   }

   protected synchronized IdGenerator getClientIdGenerator() {
      if (clientIdGenerator == null) {
         if (clientIDPrefix != null) {
            clientIdGenerator = new IdGenerator(clientIDPrefix);
         } else {
            clientIdGenerator = new IdGenerator();
         }
      }
      return clientIdGenerator;
   }

   protected void setClientIdGenerator(IdGenerator clientIdGenerator) {
      this.clientIdGenerator = clientIdGenerator;
   }

   public void setConnectionIDPrefix(String connectionIDPrefix) {
      this.connectionIDPrefix = connectionIDPrefix;
   }

   protected synchronized IdGenerator getConnectionIdGenerator() {
      if (connectionIdGenerator == null) {
         if (connectionIDPrefix != null) {
            connectionIdGenerator = new IdGenerator(connectionIDPrefix);
         } else {
            connectionIdGenerator = new IdGenerator();
         }
      }
      return connectionIdGenerator;
   }

   protected void setConnectionIdGenerator(IdGenerator connectionIdGenerator) {
      this.connectionIdGenerator = connectionIdGenerator;
   }

   public boolean isStatsEnabled() {
      return this.factoryStats.isEnabled();
   }

   public void setStatsEnabled(boolean statsEnabled) {
      this.factoryStats.setEnabled(statsEnabled);
   }

   public synchronized int getProducerWindowSize() {
      return producerWindowSize;
   }

   public synchronized void setProducerWindowSize(int producerWindowSize) {
      this.producerWindowSize = producerWindowSize;
   }

   public long getWarnAboutUnstartedConnectionTimeout() {
      return warnAboutUnstartedConnectionTimeout;
   }

   public void setWarnAboutUnstartedConnectionTimeout(long warnAboutUnstartedConnectionTimeout) {
      this.warnAboutUnstartedConnectionTimeout = warnAboutUnstartedConnectionTimeout;
   }

   public TransportListener getTransportListener() {
      return transportListener;
   }

   public void setTransportListener(TransportListener transportListener) {
      this.transportListener = transportListener;
   }

   public ExceptionListener getExceptionListener() {
      return exceptionListener;
   }

   public void setExceptionListener(ExceptionListener exceptionListener) {
      this.exceptionListener = exceptionListener;
   }

   public int getAuditDepth() {
      return auditDepth;
   }

   public void setAuditDepth(int auditDepth) {
      this.auditDepth = auditDepth;
   }

   public int getAuditMaximumProducerNumber() {
      return auditMaximumProducerNumber;
   }

   public void setAuditMaximumProducerNumber(int auditMaximumProducerNumber) {
      this.auditMaximumProducerNumber = auditMaximumProducerNumber;
   }

   public void setUseDedicatedTaskRunner(boolean useDedicatedTaskRunner) {
      this.useDedicatedTaskRunner = useDedicatedTaskRunner;
   }

   public boolean isUseDedicatedTaskRunner() {
      return useDedicatedTaskRunner;
   }

   public void setConsumerFailoverRedeliveryWaitPeriod(long consumerFailoverRedeliveryWaitPeriod) {
      this.consumerFailoverRedeliveryWaitPeriod = consumerFailoverRedeliveryWaitPeriod;
   }

   public long getConsumerFailoverRedeliveryWaitPeriod() {
      return consumerFailoverRedeliveryWaitPeriod;
   }

   public ClientInternalExceptionListener getClientInternalExceptionListener() {
      return clientInternalExceptionListener;
   }

   public void setClientInternalExceptionListener(ClientInternalExceptionListener clientInternalExceptionListener) {
      this.clientInternalExceptionListener = clientInternalExceptionListener;
   }

   public boolean isCheckForDuplicates() {
      return this.checkForDuplicates;
   }

   public void setCheckForDuplicates(boolean checkForDuplicates) {
      this.checkForDuplicates = checkForDuplicates;
   }

   public boolean isTransactedIndividualAck() {
      return transactedIndividualAck;
   }

   public void setTransactedIndividualAck(boolean transactedIndividualAck) {
      this.transactedIndividualAck = transactedIndividualAck;
   }

   public boolean isNonBlockingRedelivery() {
      return nonBlockingRedelivery;
   }

   public void setNonBlockingRedelivery(boolean nonBlockingRedelivery) {
      this.nonBlockingRedelivery = nonBlockingRedelivery;
   }

   public int getMaxThreadPoolSize() {
      return maxThreadPoolSize;
   }

   public void setMaxThreadPoolSize(int maxThreadPoolSize) {
      this.maxThreadPoolSize = maxThreadPoolSize;
   }

   public TaskRunnerFactory getSessionTaskRunner() {
      return sessionTaskRunner;
   }

   public void setSessionTaskRunner(TaskRunnerFactory sessionTaskRunner) {
      this.sessionTaskRunner = sessionTaskRunner;
   }

   public RejectedExecutionHandler getRejectedTaskHandler() {
      return rejectedTaskHandler;
   }

   public void setRejectedTaskHandler(RejectedExecutionHandler rejectedTaskHandler) {
      this.rejectedTaskHandler = rejectedTaskHandler;
   }

   public long getOptimizedAckScheduledAckInterval() {
      return optimizedAckScheduledAckInterval;
   }

   public void setOptimizedAckScheduledAckInterval(long optimizedAckScheduledAckInterval) {
      this.optimizedAckScheduledAckInterval = optimizedAckScheduledAckInterval;
   }

   public boolean isRmIdFromConnectionId() {
      return rmIdFromConnectionId;
   }

   public void setRmIdFromConnectionId(boolean rmIdFromConnectionId) {
      this.rmIdFromConnectionId = rmIdFromConnectionId;
   }

   public boolean isConsumerExpiryCheckEnabled() {
      return consumerExpiryCheckEnabled;
   }

   public void setConsumerExpiryCheckEnabled(boolean consumerExpiryCheckEnabled) {
      this.consumerExpiryCheckEnabled = consumerExpiryCheckEnabled;
   }

   public List<String> getTrustedPackages() {
      return trustedPackages;
   }

   public boolean isTrustAllPackages() {
      return trustAllPackages;
   }

   @Override
   public JMSContext createContext() {
      throw new UnsupportedOperationException("OpenWire test wrapper factory does not implement createContext");
   }

   @Override
   public JMSContext createContext(String userName, String password) {
      throw new UnsupportedOperationException("OpenWire test wrapper factory does not implement createContext");
   }

   @Override
   public JMSContext createContext(String userName, String password, int sessionMode) {
      throw new UnsupportedOperationException("OpenWire test wrapper factory does not implement createContext");
   }

   @Override
   public JMSContext createContext(int sessionMode) {
      throw new UnsupportedOperationException("OpenWire test wrapper factory does not implement createContext");
   }
}
