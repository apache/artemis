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

package org.apache.artemis.lock.kube.client;

import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.kubernetes.KubernetesApiException;
import org.apache.activemq.artemis.utils.kubernetes.KubernetesClient;

public class MutableLongClient {
   private static final String VALUE_KEY = "mutablelong";

   private static String getResourcePath(String namespace, String config) {
      return getRootPath(namespace) + "/" + config;
   }

   private static String getRootPath(String namespace) {
      return "/api/v1/namespaces/" + namespace + "/configmaps";
   }

   public static long getLong(String namespace, String config) throws KubernetesApiException {
      String resource = getResourcePath(namespace, config);

      JsonObject configMap = KubernetesClient.get(resource);

      if (configMap == null) {
         return 0L;
      }

      JsonObject data = configMap.getJsonObject("data");
      if (data == null) {
         return 0L;
      }

      String valueStr = data.getString(VALUE_KEY, "0");
      return Long.parseLong(valueStr);
   }

   public static void setLong(String namespace, String config, long value) throws KubernetesApiException {
      String resource = getResourcePath(namespace, config);

      JsonObject existingData = KubernetesClient.get(resource);

      if (existingData == null) {
         // Create new resource - POST to collection endpoint
         String configJson = buildConfig(namespace, config, null, value);
         KubernetesClient.post(getRootPath(namespace), configJson);
      } else {
         // Update existing resource - extract resourceVersion and rebuild with new value
         String resourceVersion = KubernetesClient.getResourceVersion(existingData);
         String updatedConfigJson = buildConfig(namespace, config, resourceVersion, value);
         KubernetesClient.put(resource, updatedConfigJson);
      }
   }

   public static String buildConfig(String namespace, String id, String resourceVersion, long value) {
      JsonObjectBuilder metadataBuilder = JsonLoader.createObjectBuilder()
         .add("name", id)
         .add("namespace", namespace);

      if (resourceVersion != null) {
         metadataBuilder.add("resourceVersion", resourceVersion);
      }

      JsonObjectBuilder dataBuilder = JsonLoader.createObjectBuilder()
         .add(VALUE_KEY, String.valueOf(value));

      JsonObjectBuilder configMapBuilder = JsonLoader.createObjectBuilder()
         .add("apiVersion", "v1")
         .add("kind", "ConfigMap")
         .add("metadata", metadataBuilder)
         .add("data", dataBuilder);

      return configMapBuilder.build().toString();
   }

}
