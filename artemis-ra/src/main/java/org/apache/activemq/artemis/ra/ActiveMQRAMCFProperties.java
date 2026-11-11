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

import javax.jms.Queue;
import javax.jms.Topic;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * The MCF default properties - these are set in the tx-connection-factory at the jms-ds.xml
 */
public class ActiveMQRAMCFProperties extends ConnectionFactoryProperties implements Serializable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static final long serialVersionUID = -5951352236582886862L;

   private static final String QUEUE_TYPE = Queue.class.getName();

   private static final String TOPIC_TYPE = Topic.class.getName();

   private boolean allowLocalTransactions;

   /**
    * If {@code true} then for outbound connections will always assume that they are part of a transaction. This is
    * helpful when running in containers where access to the Transaction manager can't be configured
    */
   private boolean inJtaTransaction;

   private String strConnectorClassName;

   public String strConnectionParameters;

   private int type = ActiveMQRAConnectionFactory.CONNECTION;

   private Integer useTryLock;

   public ActiveMQRAMCFProperties() {
      logger.trace("constructor()");

      useTryLock = null;
   }

   public int getType() {
      logger.trace("getType()");

      return type;
   }

   public String getConnectorClassName() {
      return strConnectorClassName;
   }

   public void setConnectorClassName(final String connectorClassName) {
      logger.trace("setConnectorClassName({})", connectorClassName);

      strConnectorClassName = connectorClassName;

      setParsedConnectorClassNames(ActiveMQRaUtils.parseConnectorConnectorConfig(connectorClassName));
   }

   public String getStrConnectionParameters() {
      return strConnectionParameters;
   }

   public void setConnectionParameters(final String configuration) {
      strConnectionParameters = configuration;
      setParsedConnectionParameters(ActiveMQRaUtils.parseConfig(configuration));
   }

   /**
    * Set the default session type.
    *
    * @param defaultType either {@literal javax.jms.Topic} or {@literal javax.jms.Queue}
    */
   public void setSessionDefaultType(final String defaultType) {
      logger.trace("setSessionDefaultType({})", type);

      if (defaultType.equals(ActiveMQRAMCFProperties.QUEUE_TYPE)) {
         type = ActiveMQRAConnectionFactory.QUEUE_CONNECTION;
      } else if (defaultType.equals(ActiveMQRAMCFProperties.TOPIC_TYPE)) {
         type = ActiveMQRAConnectionFactory.TOPIC_CONNECTION;
      } else {
         type = ActiveMQRAConnectionFactory.CONNECTION;
      }
   }

   public String getSessionDefaultType() {
      logger.trace("getSessionDefaultType()");

      if (type == ActiveMQRAConnectionFactory.CONNECTION) {
         return "BOTH";
      } else if (type == ActiveMQRAConnectionFactory.QUEUE_CONNECTION) {
         return ActiveMQRAMCFProperties.TOPIC_TYPE;
      } else {
         return ActiveMQRAMCFProperties.QUEUE_TYPE;
      }
   }

   public Integer getUseTryLock() {
      logger.trace("getUseTryLock()");

      return useTryLock;
   }

   public void setUseTryLock(final Integer useTryLock) {
      logger.trace("setUseTryLock({})", useTryLock);

      this.useTryLock = useTryLock;
   }

   public boolean isAllowLocalTransactions() {
      return allowLocalTransactions;
   }

   public void setAllowLocalTransactions(boolean allowLocalTransactions) {
      this.allowLocalTransactions = allowLocalTransactions;
   }

   public boolean isInJtaTransaction() {
      return inJtaTransaction;
   }

   public void setInJtaTransaction(boolean inJtaTransaction) {
      this.inJtaTransaction = inJtaTransaction;
   }
}
