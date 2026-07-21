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

package org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.client;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.model.TokenReview;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.kubernetes.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenReviewKubeClientImpl implements TokenReviewKubeClient {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   String tokenReviewPath = "/apis/authentication.k8s.io/v1/tokenreviews";

   @Override
   public TokenReview getTokenReview(String token) {
      String jsonRequest = buildTokenReviewRequest(token);

      try {
         JsonObject response = KubernetesClient.post(tokenReviewPath, jsonRequest);
         return TokenReview.fromJson(response);
      } catch (Exception e) {
         logger.debug(e.getMessage(), e);
         return new TokenReview();
      }
   }

   public String buildTokenReviewRequest(String clientToken) {
      return JsonLoader.createObjectBuilder()
         .add("apiVersion", "authentication.k8s.io/v1")
         .add("kind", "TokenReview")
         .add("spec", JsonLoader.createObjectBuilder()
            .add("token", clientToken)
            .build())
         .build().toString();
   }

}
