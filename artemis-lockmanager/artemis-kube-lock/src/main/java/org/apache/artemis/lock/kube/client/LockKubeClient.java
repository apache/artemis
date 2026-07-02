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

public class LockKubeClient {

   private static String getResourcePath(String namespace, String id) {
      return getRootPath(namespace) + "/" + id;
   }

   private static String getRootPath(String namespace) {
      return "/apis/coordination.k8s.io/v1/namespaces/" + namespace + "/leases";
   }

   public static JsonObject getLease(String namespace, String id) throws KubernetesApiException {
      return KubernetesClient.get(getResourcePath(namespace, id));
   }

   public static JsonObject renewLease(String namespace, String id, String resourceVersion, String holderIdentity, String acquireTime, String renewTime, int leaseDurationSeconds, int leaseTransitions) throws KubernetesApiException {
      String renewLeaseJson = buildLease(id, namespace, resourceVersion, holderIdentity, acquireTime, renewTime, leaseDurationSeconds, leaseTransitions);
      return KubernetesClient.put(getResourcePath(namespace, id), renewLeaseJson);
   }

   public static JsonObject createLease(String namespace, String id, String holderIdentity, String acquireTime, String renewTime, int leaseDurationSeconds, int leaseTransitions) throws KubernetesApiException {
      String leaseJson = buildLease(id, namespace, null, holderIdentity, acquireTime, renewTime, leaseDurationSeconds, leaseTransitions);
      return KubernetesClient.post(getRootPath(namespace), leaseJson);
   }

   public static void deleteLease(String namespace, String id) throws KubernetesApiException {
      KubernetesClient.delete(getResourcePath(namespace, id));
   }

   public static String buildLease(String id, String namespace, String resourceVersion, String holderIdentity, String acquireTime, String renewTime, int leaseDurationSeconds, int leaseTransitions) {
      JsonObjectBuilder metadataBuilder = JsonLoader.createObjectBuilder()
         .add("name", id)
         .add("namespace", namespace);

      if (resourceVersion != null) {
         metadataBuilder.add("resourceVersion", resourceVersion);
      }

      JsonObjectBuilder specBuilder = JsonLoader.createObjectBuilder()
         .add("holderIdentity", holderIdentity)
         .add("acquireTime", acquireTime)
         .add("renewTime", renewTime)
         .add("leaseDurationSeconds", leaseDurationSeconds)
         .add("leaseTransitions", leaseTransitions);

      JsonObjectBuilder leaseBuilder = JsonLoader.createObjectBuilder()
         .add("apiVersion", "coordination.k8s.io/v1")
         .add("kind", "Lease")
         .add("metadata", metadataBuilder)
         .add("spec", specBuilder);

      return leaseBuilder.build().toString();
   }
}
