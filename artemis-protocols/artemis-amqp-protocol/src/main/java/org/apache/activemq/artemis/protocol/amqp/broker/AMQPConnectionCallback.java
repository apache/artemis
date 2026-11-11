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
package org.apache.activemq.artemis.protocol.amqp.broker;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.ExtCapability;
import org.apache.activemq.artemis.protocol.amqp.proton.transaction.ProtonTransactionImpl;
import org.apache.activemq.artemis.protocol.amqp.sasl.AnonymousServerSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.MechanismFinder;
import org.apache.activemq.artemis.protocol.amqp.sasl.SASLResult;
import org.apache.activemq.artemis.protocol.amqp.sasl.ServerSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.ServerSASLFactory;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import io.netty.buffer.ByteBuf;

public class AMQPConnectionCallback implements FailureListener, CloseListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ConcurrentMap<Binary, Transaction> transactions = new ConcurrentHashMap<>();

   private final ProtonProtocolManager manager;

   private final Connection connection;

   protected ActiveMQProtonRemotingConnection protonConnectionDelegate;

   protected AMQPConnectionContext amqpConnection;

   private final Executor sessionExecutor;

   private String remoteContainerId;

   private AtomicBoolean registeredConnectionId = new AtomicBoolean(false);

   private ActiveMQServer server;

   private final String[] saslMechanisms;

   public AMQPConnectionCallback(ProtonProtocolManager manager,
                                 Connection connection,
                                 Executor sessionExecutor,
                                 ActiveMQServer server) {
      this.manager = manager;
      this.connection = connection;
      this.sessionExecutor = sessionExecutor;
      this.server = server;
      saslMechanisms = manager.getSaslMechanisms();
   }

   public Connection getTransportConnection() {
      return connection;
   }

   public String[] getSaslMechanisms() {
      return saslMechanisms;
   }

   public ServerSASL getServerSASL(final String mechanism) {
      ServerSASL result = null;
      if (isPermittedMechanism(mechanism)) {
         ServerSASLFactory factory = MechanismFinder.getFactory(mechanism);
         if (factory != null) {
            result = factory.create(server, manager, connection, protonConnectionDelegate);
         } else {
            logger.debug("Mo matching mechanism found for: {}", mechanism);
         }
      }
      return result;
   }

   private boolean isPermittedMechanism(String mechanism) {
      if (saslMechanisms == null || saslMechanisms.length == 0) {
         return AnonymousServerSASL.NAME.equals(mechanism);
      } else {
         for (String candidate : saslMechanisms) {
            if (candidate.equals(mechanism)) {
               return true;
            }
         }
      }
      return false;
   }

   public boolean isSupportsAnonymous() {
      boolean supportsAnonymous = false;
      try {
         server.getSecurityStore().authenticate(null, null, protonConnectionDelegate);
         supportsAnonymous = true;
      } catch (Exception e) {
         // authentication failed so no anonymous support
      }
      return supportsAnonymous;
   }

   public void close() {
      try {
         if (registeredConnectionId.getAndSet(false)) {
            server.removeClientConnection(remoteContainerId);
         }
         connection.close();
         amqpConnection.close(null);
      } finally {
         for (Transaction tx : transactions.values()) {
            try {
               tx.rollback();
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }
         }
      }
   }

   public void setConnection(AMQPConnectionContext connection) {
      this.amqpConnection = connection;
   }

   public AMQPConnectionContext getConnection() {
      return amqpConnection;
   }

   public ActiveMQProtonRemotingConnection getProtonConnectionDelegate() {
      return protonConnectionDelegate;
   }

   public void setProtonConnectionDelegate(ActiveMQProtonRemotingConnection protonConnectionDelegate) {

      this.protonConnectionDelegate = protonConnectionDelegate;
   }

   public void onTransport(ByteBuf byteBuf, AMQPConnectionContext amqpConnection) {
      connection.write(new ChannelBufferWrapper(byteBuf, true));
   }

   public boolean isWritable(ReadyListener readyListener) {
      return connection.isWritable(readyListener);
   }

   public boolean isLargeMessageSync() {
      return server.getConfiguration().isLargeMessageSync();
   }

   public AMQPSessionCallback createSessionCallback(AMQPConnectionContext connection) {
      return new AMQPSessionCallback(this, manager, connection, this.connection, sessionExecutor, server.newOperationContext());
   }

   public void sendSASLSupported() {
      connection.write(ActiveMQBuffers.wrappedBuffer(new byte[]{'A', 'M', 'Q', 'P', 3, 1, 0, 0}));
   }

   public boolean validateConnection(org.apache.qpid.proton.engine.Connection connection, SASLResult saslResult) {
      remoteContainerId = connection.getRemoteContainer();
      boolean idOK = server.addClientConnection(remoteContainerId, ExtCapability.needUniqueConnection(connection));
      if (!idOK) {
         //https://issues.apache.org/jira/browse/ARTEMIS-728
         Map<Symbol, Object> connProp = new HashMap<>();
         connProp.put(AmqpSupport.CONNECTION_OPEN_FAILED, "true");
         connection.setProperties(connProp);
         connection.getCondition().setCondition(AmqpError.INVALID_FIELD);
         Map<Symbol, Symbol> info = new HashMap<>();
         info.put(AmqpSupport.INVALID_FIELD, AmqpSupport.CONTAINER_ID);
         connection.getCondition().setInfo(info);
         return false;
      }
      registeredConnectionId.set(true);
      return true;
   }

   @Override
   public void connectionClosed() {
      close();
   }

   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver) {
      close();
   }

   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
      close();
   }

   public Binary newTransaction() {
      XidImpl xid = newXID();
      Binary binary = new Binary(xid.getGlobalTransactionId());
      Transaction transaction = new ProtonTransactionImpl(xid, server.getStorageManager(), -1, amqpConnection);
      transactions.put(binary, transaction);
      return binary;
   }

   public Transaction getTransaction(Binary txid, boolean remove) throws ActiveMQAMQPException {
      Transaction tx;

      if (remove) {
         tx = transactions.remove(txid);
      } else {
         tx = transactions.get(txid);
      }

      if (tx == null) {
         logger.warn("Couldn't find txid = {}", txid);
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.txNotFound(txid.toString());
      }

      return tx;
   }

   protected XidImpl newXID() {
      return newXID(UUIDGenerator.getInstance().generateStringUUID().getBytes());
   }

   protected XidImpl newXID(byte[] bytes) {
      return new XidImpl("amqp".getBytes(), 1, bytes);
   }

   public URI getFailoverList() {
      ClusterManager clusterManager = server.getClusterManager();
      ClusterConnection clusterConnection = clusterManager.getDefaultConnection(null);
      if (clusterConnection != null) {
         TopologyMemberImpl member = clusterConnection.getTopology().getMember(server.getNodeID().toString());
         if (member != null) {
            return member.toBackupURI();
         }
      }
      return null;
   }

   public String invokeIncomingInterceptors(Message message, ActiveMQProtonRemotingConnection connection) {
      return manager.invokeIncoming(message, connection);
   }

   public String invokeOutgoingInterceptors(Message message, ActiveMQProtonRemotingConnection connection) {
      return manager.invokeOutgoing(message, connection);
   }
}
