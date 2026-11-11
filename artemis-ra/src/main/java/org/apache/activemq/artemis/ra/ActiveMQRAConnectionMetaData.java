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

import java.io.IOException;
import java.io.InputStream;
import javax.jms.ConnectionMetaData;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * This class implements {@link javax.jms.ConnectionMetaData}
 *
 * {@inheritDoc}
 */
public class ActiveMQRAConnectionMetaData implements ConnectionMetaData {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String DEFAULT_PROP_FILE_NAME = "jms-version.properties";

   private static final String JMS_VERSION_NAME;
   private static final int JMS_MAJOR_VERSION;
   private static final int JMS_MINOR_VERSION;
   static {
      Properties versionProps = new Properties();
      try (InputStream in = ActiveMQRAConnectionMetaData.class.getClassLoader().getResourceAsStream(DEFAULT_PROP_FILE_NAME)) {
         if (in != null) {
            versionProps.load(in);
         }
      } catch (IOException e) {
      }
      JMS_VERSION_NAME = versionProps.getProperty("activemq.version.implementation.versionName", "2.0");
      JMS_MAJOR_VERSION = Integer.parseInt(versionProps.getProperty("activemq.version.implementation.majorVersion", "2"));
      JMS_MINOR_VERSION = Integer.parseInt(versionProps.getProperty("activemq.version.implementation.minorVersion", "0"));
   }

   public ActiveMQRAConnectionMetaData() {
      logger.trace("constructor()");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getJMSVersion() {
      logger.trace("getJMSVersion()");
      return JMS_VERSION_NAME;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getJMSMajorVersion() {
      logger.trace("getJMSMajorVersion()");
      return JMS_MAJOR_VERSION;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getJMSMinorVersion() {
      logger.trace("getJMSMinorVersion()");
      return JMS_MINOR_VERSION;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getJMSProviderName() {
      logger.trace("getJMSProviderName()");

      return "ActiveMQ Artemis";
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getProviderVersion() {
      logger.trace("getJMSProviderName()");

      return "2.4";
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getProviderMajorVersion() {
      logger.trace("getProviderMajorVersion()");

      return 2;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getProviderMinorVersion() {
      logger.trace("getProviderMinorVersion()");

      return 4;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Enumeration<Object> getJMSXPropertyNames() {
      Vector<Object> v = new Vector<>();
      v.add("JMSXGroupID");
      v.add("JMSXGroupSeq");
      v.add("JMSXDeliveryCount");
      return v.elements();
   }
}
