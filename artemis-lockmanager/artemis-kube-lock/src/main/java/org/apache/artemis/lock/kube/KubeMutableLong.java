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

package org.apache.artemis.lock.kube;

import java.io.FileReader;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubeMutableLong implements MutableLong {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private static final String VALUE_KEY = "value";

   private final String namespace;
   private final String id;
   private final String configMapName;
   private final ApiClient kubeClient;
   private final CoreV1Api coreV1Api;

   public KubeMutableLong(String namespace, String id) throws Exception {
      this.namespace = namespace;
      this.id = id;
      this.configMapName = sanitizeConfigMapName(id);
      String kubeconfigPath = System.getenv("KUBECONFIG");
      if (kubeconfigPath == null) {
         kubeconfigPath = System.getProperty("user.home") + "/.kube/config";
      }
      kubeClient = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeconfigPath))).build();
      coreV1Api = new CoreV1Api(kubeClient);
   }

   private static String sanitizeConfigMapName(String name) {
      return name.toLowerCase().replaceAll("[^a-z0-9.-]", "-");
   }

   @Override
   public String getMutableLongId() {
      return id;
   }

   @Override
   public long get() throws UnavailableStateException {
      try {
         V1ConfigMap configMap = coreV1Api.readNamespacedConfigMap(configMapName, namespace).execute();
         Map<String, String> data = configMap.getData();
         if (data == null || !data.containsKey(VALUE_KEY)) {
            return 0L;
         }
         return Long.parseLong(data.get(VALUE_KEY));
      } catch (ApiException e) {
         if (e.getCode() == 404) {
            return 0L;
         }
         throw new UnavailableStateException("Failed to get mutable long value: " + e.getResponseBody(), e);
      } catch (NumberFormatException e) {
         throw new UnavailableStateException("Invalid long value stored in ConfigMap", e);
      }
   }

   @Override
   public void set(long value) throws UnavailableStateException {
      try {
         V1ConfigMap existingConfigMap;
         boolean exists = true;

         try {
            existingConfigMap = coreV1Api.readNamespacedConfigMap(configMapName, namespace).execute();
         } catch (ApiException e) {
            if (e.getCode() == 404) {
               exists = false;
               existingConfigMap = null;
            } else {
               throw e;
            }
         }

         if (exists) {
            Map<String, String> data = existingConfigMap.getData();
            if (data == null) {
               data = new HashMap<>();
               existingConfigMap.setData(data);
            }
            data.put(VALUE_KEY, String.valueOf(value));
            coreV1Api.replaceNamespacedConfigMap(configMapName, namespace, existingConfigMap).execute();
         } else {
            V1ConfigMap newConfigMap = new V1ConfigMap();
            newConfigMap.setApiVersion("v1");
            newConfigMap.setKind("ConfigMap");

            V1ObjectMeta metadata = new V1ObjectMeta();
            metadata.setName(configMapName);
            metadata.setNamespace(namespace);
            newConfigMap.setMetadata(metadata);

            Map<String, String> data = new HashMap<>();
            data.put(VALUE_KEY, String.valueOf(value));
            newConfigMap.setData(data);

            coreV1Api.createNamespacedConfigMap(namespace, newConfigMap).execute();
         }
      } catch (ApiException e) {
         throw new UnavailableStateException("Failed to set mutable long value: " + e.getResponseBody(), e);
      }
   }

   @Override
   public void close() {
      // No cleanup needed for now
   }
}
