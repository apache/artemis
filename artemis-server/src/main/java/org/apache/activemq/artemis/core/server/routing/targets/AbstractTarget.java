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
package org.apache.activemq.artemis.core.server.routing.targets;

import org.apache.activemq.artemis.api.core.TransportConfiguration;

public abstract class AbstractTarget implements Target {
   private final TransportConfiguration connector;

   private String nodeID;

   private String username;

   private String password;

   private int checkPeriod;

   private TargetListener listener;

   @Override
   public String getNodeID() {
      return nodeID;
   }

   protected void setNodeID(String nodeID) {
      this.nodeID = nodeID;
   }

   @Override
   public String getUsername() {
      return username;
   }

   @Override
   public void setUsername(String username) {
      this.username = username;
   }

   @Override
   public String getPassword() {
      return password;
   }

   @Override
   public void setPassword(String password) {
      this.password = password;
   }

   @Override
   public int getCheckPeriod() {
      return checkPeriod;
   }

   @Override
   public void setCheckPeriod(int checkPeriod) {
      this.checkPeriod = checkPeriod;
   }

   @Override
   public TargetListener getListener() {
      return listener;
   }

   @Override
   public void setListener(TargetListener listener) {
      this.listener = listener;
   }

   @Override
   public TransportConfiguration getConnector() {
      return connector;
   }


   public AbstractTarget(TransportConfiguration connector, String nodeID) {
      this.connector = connector;
      this.nodeID = nodeID;
   }


   protected void fireConnectedEvent() {
      if (listener != null) {
         listener.targetConnected();
      }
   }

   protected void fireDisconnectedEvent() {
      if (listener != null) {
         listener.targetDisconnected();
      }
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + " [connector=" + connector + ", nodeID=" + nodeID + "]";
   }
}
