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
package org.apache.activemq.artemis.jms.client;


import java.io.IOException;
import java.io.InputStream;
import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;

import org.apache.activemq.artemis.core.version.Version;

/**
 * Apache Artemis implementation of a JMS ConnectionMetaData.
 */
public class ActiveMQConnectionMetaData implements ConnectionMetaData {
   public static final String DEFAULT_PROP_FILE_NAME = "jms-version.properties";

   private static final String JMS_VERSION_NAME;
   private static final int JMS_MAJOR_VERSION;
   private static final int JMS_MINOR_VERSION;
   static {
      Properties versionProps = new Properties();
      try (InputStream in = ActiveMQConnectionMetaData.class.getClassLoader().getResourceAsStream(DEFAULT_PROP_FILE_NAME)) {
         if (in != null) {
            versionProps.load(in);
         }
      } catch (IOException e) {
      }
      JMS_VERSION_NAME = versionProps.getProperty("activemq.version.implementation.versionName", "2.0");
      JMS_MAJOR_VERSION = Integer.parseInt(versionProps.getProperty("activemq.version.implementation.majorVersion", "2"));
      JMS_MINOR_VERSION = Integer.parseInt(versionProps.getProperty("activemq.version.implementation.minorVersion", "0"));
   }


   private static final String ACTIVEMQ = "ActiveMQ";


   private final Version serverVersion;



   /**
    * Create a new ActiveMQConnectionMetaData object.
    */
   public ActiveMQConnectionMetaData(final Version serverVersion) {
      this.serverVersion = serverVersion;
   }

   // ConnectionMetaData implementation -----------------------------

   @Override
   public String getJMSVersion() throws JMSException {
      return JMS_VERSION_NAME;
   }

   @Override
   public int getJMSMajorVersion() throws JMSException {
      return JMS_MAJOR_VERSION;
   }

   @Override
   public int getJMSMinorVersion() throws JMSException {
      return JMS_MINOR_VERSION;
   }

   @Override
   public String getJMSProviderName() throws JMSException {
      return ActiveMQConnectionMetaData.ACTIVEMQ;
   }

   @Override
   public String getProviderVersion() throws JMSException {
      return serverVersion.getFullVersion();
   }

   @Override
   public int getProviderMajorVersion() throws JMSException {
      return serverVersion.getMajorVersion();
   }

   @Override
   public int getProviderMinorVersion() throws JMSException {
      return serverVersion.getMinorVersion();
   }

   @Override
   public Enumeration getJMSXPropertyNames() throws JMSException {
      Vector<Object> v = new Vector<>();
      v.add("JMSXGroupID");
      v.add("JMSXGroupSeq");
      v.add("JMSXDeliveryCount");
      return v.elements();
   }
}
