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

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Hashtable;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RA default properties - these are set in the ra.xml file
 */
public class ActiveMQRAProperties extends ConnectionFactoryProperties implements Serializable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static final long serialVersionUID = -2772367477755473248L;

   protected boolean allowLocalTransactions;

   protected boolean useTopologyForLoadBalancing = ActiveMQClient.DEFAULT_USE_TOPOLOGY_FOR_LOADBALANCING;

   private String userName;

   private String password = null;

   /**
    * Use Local TX instead of XA
    */
   private Boolean localTx = false;

   private static final int DEFAULT_SETUP_ATTEMPTS = -1;

   private static final long DEFAULT_SETUP_INTERVAL = 2 * 1000;

   private int setupAttempts = DEFAULT_SETUP_ATTEMPTS;

   private long setupInterval = DEFAULT_SETUP_INTERVAL;

   private Hashtable<?, ?> jndiParams;

   private boolean useJNDI;

   private Boolean useMaskedPassword = null;

   private String passwordCodec;

   private boolean initialized = false;

   /**
    * Class used to get a JChannel
    */
   private String jgroupsChannelLocatorClass;

   /**
    * Name used to locate a JChannel
    */
   private String jgroupsChannelRefName;

   public ActiveMQRAProperties() {
      logger.trace("constructor()");
   }

   public String getUserName() {
      logger.trace("getUserName()");

      return userName;
   }

   public void setUserName(final String userName) {
      logger.trace("setUserName({})", userName);

      this.userName = userName;
   }

   public String getPassword() {
      logger.trace("getPassword()");

      return password;
   }

   /**
    * Set the password Based on UseMaskedPassword property, the password can be plain text or encoded string. However we
    * cannot decide which is the case at this moment, because we don't know when the UseMaskedPassword and PasswordCodec
    * are loaded. So for the moment we just save the password.
    *
    * @param password The value
    */
   public void setPassword(final String password) {
      logger.trace("setPassword(****)");

      this.password = password;
   }

   public boolean isUseJNDI() {
      return useJNDI;
   }

   public void setUseJNDI(final Boolean value) {
      useJNDI = value;
   }

   public Hashtable<?, ?> getParsedJndiParams() {
      return jndiParams;
   }

   public void setParsedJndiParams(Hashtable<?, ?> params) {
      jndiParams = params;
   }

   public Boolean getUseLocalTx() {
      logger.trace("getUseLocalTx()");

      return localTx;
   }

   public void setUseLocalTx(final Boolean localTx) {
      logger.trace("setUseLocalTx({})", localTx);

      this.localTx = localTx;
   }

   public int getSetupAttempts() {
      return setupAttempts;
   }

   public void setSetupAttempts(Integer setupAttempts) {
      this.setupAttempts = setupAttempts;
   }

   public long getSetupInterval() {
      return setupInterval;
   }

   public void setSetupInterval(Long setupInterval) {
      this.setupInterval = setupInterval;
   }

   public Boolean isUseMaskedPassword() {
      return useMaskedPassword;
   }

   public void setUseMaskedPassword(Boolean useMaskedPassword) {
      this.useMaskedPassword = useMaskedPassword;
   }

   public String getPasswordCodec() {
      return passwordCodec;
   }

   public void setPasswordCodec(String codecs) {
      passwordCodec = codecs;
   }

   @Override
   public String toString() {
      return "ActiveMQRAProperties[localTx=" + localTx +
         ", userName=" + userName + ", password=****]";
   }

   public synchronized void init() throws ActiveMQException {
      if (initialized) {
         return;
      }

      if (password != null) {
         try {
            password = PasswordMaskingUtil.resolveMask(useMaskedPassword, password, passwordCodec);
         } catch (Exception e) {
            throw ActiveMQRABundle.BUNDLE.errorDecodingPassword(e);
         }
      }

      initialized = true;
   }

   public void setUseTopologyForLoadBalancing(boolean useTopologyForLoadBalancing) {
      this.useTopologyForLoadBalancing = useTopologyForLoadBalancing;
   }

   public boolean isUseTopologyForLoadBalancing() {
      return useTopologyForLoadBalancing;
   }

   public String getCodec() {
      return passwordCodec;
   }

   public String getJgroupsChannelLocatorClass() {
      return jgroupsChannelLocatorClass;
   }

   public void setJgroupsChannelLocatorClass(String jgroupsChannelLocatorClass) {
      this.jgroupsChannelLocatorClass = jgroupsChannelLocatorClass;
   }

   public String getJgroupsChannelRefName() {
      return jgroupsChannelRefName;
   }

   public void setJgroupsChannelRefName(String jgroupsChannelRefName) {
      this.jgroupsChannelRefName = jgroupsChannelRefName;
   }

   public boolean isAllowLocalTransactions() {
      return allowLocalTransactions;
   }

   public void setAllowLocalTransactions(boolean allowLocalTransactions) {
      this.allowLocalTransactions = allowLocalTransactions;
   }
}
