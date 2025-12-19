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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@XmlRootElement(name = "web")
@XmlAccessorType(XmlAccessType.FIELD)
public class WebServerDTO extends ComponentDTO {

   @Deprecated
   @XmlAttribute
   public String bind;

   @XmlAttribute(required = true)
   public String path;

   @Deprecated
   @XmlAttribute
   public Boolean clientAuth;

   @Deprecated
   @XmlAttribute
   public String passwordCodec;

   @Deprecated
   @XmlAttribute
   public String keyStorePath;

   @Deprecated
   @XmlAttribute
   public String trustStorePath;

   @XmlAttribute
   public String customizer;

   @XmlElementRef
   private List<BindingDTO> bindings;

   @Deprecated
   @XmlElementRef
   public List<AppDTO> apps;

   @XmlElementRef(required = false)
   public RequestLogDTO requestLog;

   @Deprecated
   @XmlAttribute
   private String keyStorePassword;

   @Deprecated
   @XmlAttribute
   private String trustStorePassword;

   @Deprecated
   @XmlAttribute
   private String includedTLSProtocols;

   @Deprecated
   @XmlAttribute
   private String excludedTLSProtocols;

   @Deprecated
   @XmlAttribute
   private String includedCipherSuites;

   @Deprecated
   @XmlAttribute
   private String excludedCipherSuites;

   @XmlAttribute
   public String rootRedirectLocation;

   @XmlAttribute
   public Boolean webContentEnabled;

   @XmlAttribute
   public Integer maxThreads = 200;

   @XmlAttribute
   public Integer minThreads = Math.min(8, maxThreads);

   @XmlAttribute
   public Integer idleThreadTimeout = 60000;

   @XmlAttribute
   public Integer scanPeriod;

   @XmlAttribute
   public Integer maxRequestHeaderSize;

   @XmlAttribute
   public Integer maxResponseHeaderSize;

   @XmlAttribute
   public Boolean compressionEnabled;

   @XmlAttribute
   public Integer compressionLevel;

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   public String getCustomizer() {
      return customizer;
   }

   public void setCustomizer(String customizer) {
      this.customizer = customizer;
   }

   public RequestLogDTO getRequestLog() {
      return requestLog;
   }

   public void setRequestLog(RequestLogDTO requestLog) {
      this.requestLog = requestLog;
   }

   public String getRootRedirectLocation() {
      return rootRedirectLocation;
   }

   public void setRootRedirectLocation(String rootRedirectLocation) {
      this.rootRedirectLocation = rootRedirectLocation;
   }

   public Boolean getWebContentEnabled() {
      return webContentEnabled;
   }

   public void setWebContentEnabled(Boolean webContentEnabled) {
      this.webContentEnabled = webContentEnabled;
   }

   public Integer getMaxThreads() {
      return maxThreads;
   }

   public void setMaxThreads(Integer maxThreads) {
      this.maxThreads = maxThreads;
   }

   public Integer getMinThreads() {
      return minThreads;
   }

   public void setMinThreads(Integer minThreads) {
      this.minThreads = minThreads;
   }

   public Integer getIdleThreadTimeout() {
      return idleThreadTimeout;
   }

   public void setIdleThreadTimeout(Integer idleThreadTimeout) {
      this.idleThreadTimeout = idleThreadTimeout;
   }

   public Integer getScanPeriod() {
      return scanPeriod;
   }

   public void setScanPeriod(Integer scanPeriod) {
      this.scanPeriod = scanPeriod;
   }

   public List<BindingDTO> getBindings() {
      return bindings;
   }

   public void addBinding(BindingDTO binding) {
      bindings.add(binding);
   }


   public WebServerDTO() {
      componentClassName = "org.apache.activemq.artemis.component.WebServerComponent";
      bindings = new ArrayList<>();
      requestLog = new RequestLogDTO();
   }

   public List<BindingDTO> getAllBindings() {
      if (bindings == null || bindings.isEmpty()) {
         return Collections.singletonList(convertToBindingDTO());
      }
      return bindings;
   }

   public void setBindings(List<BindingDTO> bindings) {
      this.bindings = bindings;
   }

   private BindingDTO convertToBindingDTO() {
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = bind;
      bindingDTO.apps = apps;
      bindingDTO.clientAuth = clientAuth;
      bindingDTO.passwordCodec = passwordCodec;
      bindingDTO.keyStorePath = keyStorePath;
      bindingDTO.setKeyStorePassword(keyStorePassword);
      bindingDTO.trustStorePath = trustStorePath;
      bindingDTO.setTrustStorePassword(trustStorePassword);
      bindingDTO.setIncludedTLSProtocols(includedTLSProtocols);
      bindingDTO.setExcludedTLSProtocols(excludedTLSProtocols);
      bindingDTO.setIncludedCipherSuites(includedCipherSuites);
      bindingDTO.setExcludedCipherSuites(excludedCipherSuites);
      return bindingDTO;
   }

   public BindingDTO getDefaultBinding() {
      return getAllBindings().get(0);
   }
}
