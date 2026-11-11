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
package org.apache.activemq.artemis.core.protocol.openwire.amq;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireProtocolManager;
import org.apache.activemq.command.Command;
import org.apache.activemq.command.ConnectionId;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.WireFormatInfo;
import org.apache.activemq.command.XATransactionId;
import org.apache.activemq.filter.MessageEvaluationContext;
import org.apache.activemq.state.ConnectionState;

public class AMQConnectionContext {

   private OpenWireConnection connection;
   private OpenWireProtocolManager broker; //use protocol manager to represent the broker
   private boolean inRecoveryMode;
   private ConnectionId connectionId;
   private String clientId;
   private String userName;
   private boolean reconnect;
   private WireFormatInfo wireFormatInfo;
   private Object longTermStoreContext;
   private boolean producerFlowControl = true;
   private boolean networkConnection;
   private boolean faultTolerant;
   private final AtomicBoolean stopping = new AtomicBoolean();
   private final MessageEvaluationContext messageEvaluationContext;
   private boolean dontSendResponse;
   private boolean clientMaster = true;
   private ConnectionState connectionState;
   private XATransactionId xid;
   private Command lastCommand;

   public AMQConnectionContext() {
      this.messageEvaluationContext = new MessageEvaluationContext();
   }

   public AMQConnectionContext(MessageEvaluationContext messageEvaluationContext) {
      this.messageEvaluationContext = messageEvaluationContext;
   }

   public AMQConnectionContext(ConnectionInfo info) {
      this();
      setClientId(info.getClientId());
      setUserName(info.getUserName());
      setConnectionId(info.getConnectionId());
   }

   public AMQConnectionContext copy() {
      AMQConnectionContext rc = new AMQConnectionContext(this.messageEvaluationContext);
      rc.connection = this.connection;
      rc.broker = this.broker;
      rc.inRecoveryMode = this.inRecoveryMode;
      rc.connectionId = this.connectionId;
      rc.clientId = this.clientId;
      rc.userName = this.userName;
      rc.reconnect = this.reconnect;
      rc.wireFormatInfo = this.wireFormatInfo;
      rc.longTermStoreContext = this.longTermStoreContext;
      rc.producerFlowControl = this.producerFlowControl;
      rc.networkConnection = this.networkConnection;
      rc.faultTolerant = this.faultTolerant;
      rc.stopping.set(this.stopping.get());
      rc.dontSendResponse = this.dontSendResponse;
      rc.clientMaster = this.clientMaster;
      return rc;
   }

   public OpenWireProtocolManager getBroker() {
      return broker;
   }

   public void setBroker(OpenWireProtocolManager broker) {
      this.broker = broker;
   }

   public OpenWireConnection getConnection() {
      return connection;
   }

   public void setConnection(OpenWireConnection connection) {
      this.connection = connection;
   }

   public boolean isInRecoveryMode() {
      return inRecoveryMode;
   }

   public void setInRecoveryMode(boolean inRecoveryMode) {
      this.inRecoveryMode = inRecoveryMode;
   }

   public String getClientId() {
      return clientId;
   }

   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   public boolean isReconnect() {
      return reconnect;
   }

   public void setReconnect(boolean reconnect) {
      this.reconnect = reconnect;
   }

   public WireFormatInfo getWireFormatInfo() {
      return wireFormatInfo;
   }

   public void setWireFormatInfo(WireFormatInfo wireFormatInfo) {
      this.wireFormatInfo = wireFormatInfo;
   }

   public ConnectionId getConnectionId() {
      return connectionId;
   }

   public void setConnectionId(ConnectionId connectionId) {
      this.connectionId = connectionId;
   }

   public String getUserName() {
      return userName;
   }

   public void setUserName(String userName) {
      this.userName = userName;
   }

   public MessageEvaluationContext getMessageEvaluationContext() {
      return messageEvaluationContext;
   }

   public Object getLongTermStoreContext() {
      return longTermStoreContext;
   }

   public void setLongTermStoreContext(Object longTermStoreContext) {
      this.longTermStoreContext = longTermStoreContext;
   }

   public boolean isProducerFlowControl() {
      return producerFlowControl;
   }

   public void setProducerFlowControl(boolean disableProducerFlowControl) {
      this.producerFlowControl = disableProducerFlowControl;
   }

   public synchronized boolean isNetworkConnection() {
      return networkConnection;
   }

   public synchronized void setNetworkConnection(boolean networkConnection) {
      this.networkConnection = networkConnection;
   }

   public AtomicBoolean getStopping() {
      return stopping;
   }

   public void setDontSendReponse(boolean b) {
      this.dontSendResponse = b;
   }

   public boolean isDontSendReponse() {
      return dontSendResponse;
   }

   public boolean isClientMaster() {
      return this.clientMaster;
   }

   public void setClientMaster(boolean clientMaster) {
      this.clientMaster = clientMaster;
   }

   public boolean isFaultTolerant() {
      return faultTolerant;
   }

   public void setFaultTolerant(boolean faultTolerant) {
      this.faultTolerant = faultTolerant;
   }

   public void setConnectionState(ConnectionState connectionState) {
      this.connectionState = connectionState;
   }

   public ConnectionState getConnectionState() {
      return this.connectionState;
   }

   public void setXid(XATransactionId id) {
      this.xid = id;
   }

   public XATransactionId getXid() {
      return xid;
   }

   public boolean isAllowLinkStealing() {
      // TODO: check what this means,
      //       on the activemq implementation this used to check on
      //       the connector, so this looks like a configuration option
      // http://activemq.apache.org/configuring-transports.html
      return false;
   }

   public void setLastCommand(Command lastCommand) {
      this.lastCommand = lastCommand;
   }

   public Command getLastCommand() {
      return this.lastCommand;
   }
}
