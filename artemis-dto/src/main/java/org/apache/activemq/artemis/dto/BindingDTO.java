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
package org.apache.activemq.artemis.dto;

import org.apache.activemq.artemis.utils.PasswordMaskingUtil;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "binding")
@XmlAccessorType(XmlAccessType.FIELD)
public class BindingDTO {

   @XmlAttribute
   public String name;

   @XmlAttribute
   public String uri;

   @XmlElementRef
   public List<AppDTO> apps;

   @XmlAttribute
   public Boolean clientAuth;

   @XmlAttribute
   public String passwordCodec;

   @XmlAttribute
   public String keyStorePath;

   @XmlAttribute
   public String keyStoreType;

   @XmlAttribute
   public String trustStorePath;

   @XmlAttribute
   public String trustStoreType;

   @XmlAttribute
   private String includedTLSProtocols;

   @XmlAttribute
   private String excludedTLSProtocols;

   @XmlAttribute
   private String includedCipherSuites;

   @XmlAttribute
   private String excludedCipherSuites;

   @XmlAttribute
   private String keyStorePassword;

   @XmlAttribute
   private String trustStorePassword;

   @XmlAttribute
   private Boolean sniHostCheck;

   @XmlAttribute
   private Boolean sniRequired;

   @XmlAttribute
   private Boolean sslAutoReload;

   @XmlAttribute
   private Boolean http2;

   public String getKeyStorePassword() throws Exception {
      return getPassword(this.keyStorePassword);
   }

   private String getPassword(String password) throws Exception {
      return PasswordMaskingUtil.resolveMask(password, this.passwordCodec);
   }

   public void setKeyStorePassword(String keyStorePassword) {
      this.keyStorePassword = keyStorePassword;
   }

   public String getTrustStorePassword() throws Exception {
      return getPassword(this.trustStorePassword);
   }

   public void setTrustStorePassword(String trustStorePassword) {
      this.trustStorePassword = trustStorePassword;
   }

   public String[] getIncludedTLSProtocols() {
      return unmarshalArray(includedTLSProtocols);
   }

   public void setIncludedTLSProtocols(String... protocols) {
      includedTLSProtocols = marshalArray(protocols);
   }

   public String[] getExcludedTLSProtocols() {
      return unmarshalArray(excludedTLSProtocols);
   }

   public void setExcludedTLSProtocols(String... protocols) {
      excludedTLSProtocols = marshalArray(protocols);
   }

   public String[] getIncludedCipherSuites() {
      return unmarshalArray(includedCipherSuites);
   }

   public void setIncludedCipherSuites(String... cipherSuites) {
      includedCipherSuites = marshalArray(cipherSuites);
   }

   public String[] getExcludedCipherSuites() {
      return unmarshalArray(excludedCipherSuites);
   }

   public void setExcludedCipherSuites(String... cipherSuites) {
      excludedCipherSuites = marshalArray(cipherSuites);
   }

   public String getName() {
      if (name == null) {
         return uri;
      }

      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getUri() {
      return uri;
   }

   public void setUri(String uri) {
      this.uri = uri;
   }

   public Boolean getClientAuth() {
      return clientAuth;
   }

   public void setClientAuth(Boolean clientAuth) {
      this.clientAuth = clientAuth;
   }

   public String getPasswordCodec() {
      return passwordCodec;
   }

   public void setPasswordCodec(String passwordCodec) {
      this.passwordCodec = passwordCodec;
   }

   public String getKeyStorePath() {
      return keyStorePath;
   }

   public void setKeyStorePath(String keyStorePath) {
      this.keyStorePath = keyStorePath;
   }

   public String getKeyStoreType() {
      return keyStoreType;
   }

   public void setKeyStoreType(String keyStoreType) {
      this.keyStoreType = keyStoreType;
   }

   public String getTrustStorePath() {
      return trustStorePath;
   }

   public void setTrustStorePath(String trustStorePath) {
      this.trustStorePath = trustStorePath;
   }

   public String getTrustStoreType() {
      return trustStoreType;
   }

   public void setTrustStoreType(String trustStoreType) {
      this.trustStoreType = trustStoreType;
   }

   public List<AppDTO> getApps() {
      return apps;
   }

   public void addApp(AppDTO app) {
      apps.add(app);
   }

   public Boolean getSniHostCheck() {
      return sniHostCheck;
   }

   public void setSniHostCheck(Boolean sniHostCheck) {
      this.sniHostCheck = sniHostCheck;
   }

   public Boolean getSniRequired() {
      return sniRequired;
   }

   public void setSniRequired(Boolean sniRequired) {
      this.sniRequired = sniRequired;
   }

   public Boolean getSslAutoReload() {
      return sslAutoReload;
   }

   public void setSslAutoReload(Boolean sslAutoReload) {
      this.sslAutoReload = sslAutoReload;
   }

   public Boolean getHttp2() {
      return http2;
   }

   public void setHttp2(Boolean http2) {
      this.http2 = http2;
   }

   public BindingDTO() {
      apps = new ArrayList<>();
   }

   private String[] unmarshalArray(String text) {
      if (text == null) {
         return null;
      }

      return text.split(",");
   }

   private String marshalArray(String[] array) {
      if (array == null || (array.length == 1 && array[0] == null)) {
         return null;
      }

      return String.join(",", array);
   }
}
