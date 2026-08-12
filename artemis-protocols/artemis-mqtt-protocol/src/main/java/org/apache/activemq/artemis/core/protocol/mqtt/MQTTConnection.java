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
package org.apache.activemq.artemis.core.protocol.mqtt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.spi.core.protocol.AbstractRemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;

public class MQTTConnection extends AbstractRemotingConnection {

   private boolean destroyed;

   private boolean connected;

   private int receiveMaximum = -1;

   private String protocolVersion;

   private boolean clientIdAssignedByBroker = false;

   public MQTTConnection(Connection transportConnection) {
      super(transportConnection, null);
      this.destroyed = false;
   }

   @Override
   public void fail(ActiveMQException me) {
      List<FailureListener> copy = new ArrayList<>(failureListeners);
      for (FailureListener listener : copy) {
         listener.connectionFailed(me, false);
      }
      transportConnection.close();
   }

   @Override
   public void fail(ActiveMQException me, String scaleDownTargetNodeID) {
      fail(me);
   }

   @Override
   public Future asyncFail(ActiveMQException me) {
      FutureTask<Void> task = new FutureTask(() -> {
         fail(me);
         return null;
      });


      // I don't expect asyncFail happening on MQTT, in case of happens this is semantically correct
      Thread t = new Thread(task);

      t.start();

      return task;
   }

   @Override
   public void destroy() {
      destroyed = true;
      disconnect(false);
   }

   @Override
   public void disconnect(boolean criticalError) {
      transportConnection.forceClose();
   }

   @Override
   public void disconnect(String scaleDownNodeID, boolean criticalError) {
      transportConnection.forceClose();
   }

   protected void dataReceived() {
      dataReceived = true;
   }

   @Override
   public void flush() {
      transportConnection.checkFlushBatchBuffer();
   }

   @Override
   public void bufferReceived(Object connectionID, ActiveMQBuffer buffer) {
   }

   public void setConnected(boolean connected) {
      this.connected = connected;
   }

   public boolean getConnected() {
      return connected;
   }

   @Override
   public String getProtocolName() {
      return MQTTProtocolManagerFactory.MQTT_PROTOCOL_NAME + Objects.requireNonNullElse(protocolVersion, "");
   }

   public int getReceiveMaximum() {
      return receiveMaximum;
   }

   public void setReceiveMaximum(int maxReceive) {
      this.receiveMaximum = maxReceive;
   }

   public void setProtocolVersion(String protocolVersion) {
      this.protocolVersion = protocolVersion;
   }

   public void setClientIdAssignedByBroker(boolean clientIdAssignedByBroker) {
      this.clientIdAssignedByBroker = clientIdAssignedByBroker;
   }

   public boolean isClientIdAssignedByBroker() {
      return clientIdAssignedByBroker;
   }
}
