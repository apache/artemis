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
package org.apache.activemq.artemis.dto;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.activemq.artemis.utils.PasswordMaskingUtil;

@XmlRootElement(name = "connector")
@XmlAccessorType(XmlAccessType.FIELD)
public class JMXConnectorDTO {

   @XmlAttribute (name = "connector-host")
   String connectorHost;

   @XmlAttribute (name = "connector-port", required = true)
   Integer connectorPort;

   @XmlAttribute (name = "rmi-registry-port")
   Integer rmiRegistryPort;

   @XmlAttribute (name = "jmx-realm")
   String jmxRealm;

   @XmlAttribute (name = "object-name")
   String objectName;

   @XmlAttribute (name = "authenticator-type")
   String authenticatorType;

   @XmlAttribute (name = "secured")
   Boolean secured;

   @XmlAttribute (name = "key-store-provider")
   String keyStoreProvider;

   @XmlAttribute (name = "key-store-type")
   String keyStoreType;

   @XmlAttribute (name = "key-store-path")
   String keyStorePath;

   @XmlAttribute (name = "key-store-password")
   String keyStorePassword;

   @XmlAttribute (name = "trust-store-provider")
   String trustStoreProvider;

   @XmlAttribute (name = "trust-store-type")
   String trustStoreType;

   @XmlAttribute (name = "trust-store-path")
   String trustStorePath;

   @XmlAttribute (name = "trust-store-password")
   String trustStorePassword;

   @XmlAttribute (name = "password-codec")
   String passwordCodec;

   public String getConnectorHost() {
      return connectorHost;
   }

   public int getConnectorPort() {
      return connectorPort;
   }

   public Integer getRmiRegistryPort() {
      return rmiRegistryPort;
   }

   public String getJmxRealm() {
      return jmxRealm;
   }

   public String getObjectName() {
      return objectName;
   }

   public String getAuthenticatorType() {
      return authenticatorType;
   }

   public Boolean isSecured() {
      return secured;
   }

   public String getKeyStoreProvider() {
      return keyStoreProvider;
   }

   public String getKeyStoreType() {
      return keyStoreType;
   }

   public String getKeyStorePath() {
      return keyStorePath;
   }

   public String getKeyStorePassword() throws Exception {
      return getPassword(keyStorePassword);
   }

   public String getTrustStoreProvider() {
      return trustStoreProvider;
   }

   public String getTrustStoreType() {
      return trustStoreType;
   }

   public String getTrustStorePath() {
      return trustStorePath;
   }

   public String getTrustStorePassword() throws Exception {
      return getPassword(trustStorePassword);
   }

   private String getPassword(String password) throws Exception {
      return PasswordMaskingUtil.resolveMask(password, this.passwordCodec);
   }
}
