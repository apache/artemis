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
package org.apache.activemq.artemis.jms.bridge.impl;

import javax.management.StandardMBean;

import org.apache.activemq.artemis.jms.bridge.JMSBridge;
import org.apache.activemq.artemis.jms.bridge.JMSBridgeControl;
import org.apache.activemq.artemis.jms.bridge.QualityOfServiceMode;

public class JMSBridgeControlImpl extends StandardMBean implements JMSBridgeControl {

   private final JMSBridge bridge;


   public JMSBridgeControlImpl(final JMSBridge bridge) throws Exception {
      super(JMSBridgeControl.class);
      this.bridge = bridge;
   }


   @Override
   public void pause() throws Exception {
      bridge.pause();
   }

   @Override
   public void resume() throws Exception {
      bridge.resume();
   }

   @Override
   public boolean isStarted() {
      return bridge.isStarted();
   }

   @Override
   public void start() throws Exception {
      bridge.start();
   }

   @Override
   public void stop() throws Exception {
      bridge.stop();
   }

   @Override
   public String getClientID() {
      return bridge.getClientID();
   }

   @Override
   public long getFailureRetryInterval() {
      return bridge.getFailureRetryInterval();
   }

   @Override
   public int getMaxBatchSize() {
      return bridge.getMaxBatchSize();
   }

   @Override
   public long getMaxBatchTime() {
      return bridge.getMaxBatchTime();
   }

   @Override
   public int getMaxRetries() {
      return bridge.getMaxRetries();
   }

   @Override
   public String getQualityOfServiceMode() {
      QualityOfServiceMode mode = bridge.getQualityOfServiceMode();
      if (mode != null) {
         return mode.name();
      } else {
         return null;
      }
   }

   @Override
   public String getSelector() {
      return bridge.getSelector();
   }

   @Override
   public String getSourcePassword() {
      return bridge.getSourcePassword();
   }

   @Override
   public String getSourceUsername() {
      return bridge.getSourceUsername();
   }

   @Override
   public String getSubscriptionName() {
      return bridge.getSubscriptionName();
   }

   @Override
   public String getTargetPassword() {
      return bridge.getTargetPassword();
   }

   @Override
   public String getTargetUsername() {
      return bridge.getTargetUsername();
   }

   @Override
   public boolean isAddMessageIDInHeader() {
      return bridge.isAddMessageIDInHeader();
   }

   @Override
   public boolean isFailed() {
      return bridge.isFailed();
   }

   @Override
   public boolean isPaused() {
      return bridge.isPaused();
   }

   @Override
   public void setAddMessageIDInHeader(final boolean value) {
      bridge.setAddMessageIDInHeader(value);
   }

   @Override
   public void setClientID(final String clientID) {
      bridge.setClientID(clientID);
   }

   @Override
   public void setFailureRetryInterval(final long interval) {
      bridge.setFailureRetryInterval(interval);
   }

   @Override
   public void setMaxBatchSize(final int size) {
      bridge.setMaxBatchSize(size);
   }

   @Override
   public void setMaxBatchTime(final long time) {
      bridge.setMaxBatchTime(time);
   }

   @Override
   public void setMaxRetries(final int retries) {
      bridge.setMaxRetries(retries);
   }

   @Override
   public void setQualityOfServiceMode(String mode) {
      if (mode != null) {
         bridge.setQualityOfServiceMode(QualityOfServiceMode.valueOf(mode));
      }
   }

   @Override
   public void setSelector(final String selector) {
      bridge.setSelector(selector);
   }

   @Override
   public void setSourcePassword(final String pwd) {
      bridge.setSourcePassword(pwd);
   }

   @Override
   public void setSourceUsername(final String name) {
      bridge.setSourceUsername(name);
   }

   @Override
   public void setSubscriptionName(final String subname) {
      bridge.setSubscriptionName(subname);
   }

   @Override
   public void setTargetPassword(final String pwd) {
      bridge.setTargetPassword(pwd);
   }

   @Override
   public void setTargetUsername(final String name) {
      bridge.setTargetUsername(name);
   }


}
