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

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.apache.activemq.artemis.utils.kubernetes.KubernetesApiException;
import org.apache.artemis.lock.kube.client.MutableLongClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubeMutableLong implements MutableLong {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String namespace;
   private final String id;

   public KubeMutableLong(String namespace, String id) {
      this.namespace = namespace;
      // Kubernetes resource names must be lowercase RFC 1123 subdomain
      this.id = id.toLowerCase();
   }


   @Override
   public String getMutableLongId() {
      return id;
   }

   @Override
   public long get() throws UnavailableStateException {
      try {
         return MutableLongClient.getLong(namespace, id);
      } catch (KubernetesApiException e) {
         throw new UnavailableStateException("Failed to get mutable long value: " + e.getMessage(), e);
      }
   }

   @Override
   public void set(long value) throws UnavailableStateException {

      try {
         MutableLongClient.setLong(namespace, id, value);
      } catch (KubernetesApiException e) {
         throw new UnavailableStateException("Failed to set mutable long value: " + e.getMessage(), e);
      }
   }

   @Override
   public void close() {
      // No cleanup needed for now
   }
}
