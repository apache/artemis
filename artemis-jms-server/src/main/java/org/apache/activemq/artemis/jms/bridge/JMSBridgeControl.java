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
package org.apache.activemq.artemis.jms.bridge;

import org.apache.activemq.artemis.api.core.management.ActiveMQComponentControl;

public interface JMSBridgeControl extends ActiveMQComponentControl {

   void pause() throws Exception;

   void resume() throws Exception;

   String getSourceUsername();

   void setSourceUsername(String name);

   String getSourcePassword();

   void setSourcePassword(String pwd);

   String getTargetUsername();

   void setTargetUsername(String name);

   String getTargetPassword();

   void setTargetPassword(String pwd);

   String getSelector();

   void setSelector(String selector);

   long getFailureRetryInterval();

   void setFailureRetryInterval(long interval);

   int getMaxRetries();

   void setMaxRetries(int retries);

   String getQualityOfServiceMode();

   void setQualityOfServiceMode(String mode);

   int getMaxBatchSize();

   void setMaxBatchSize(int size);

   long getMaxBatchTime();

   void setMaxBatchTime(long time);

   String getSubscriptionName();

   void setSubscriptionName(String subname);

   String getClientID();

   void setClientID(String clientID);

   boolean isAddMessageIDInHeader();

   void setAddMessageIDInHeader(boolean value);

   boolean isPaused();

   boolean isFailed();

}
