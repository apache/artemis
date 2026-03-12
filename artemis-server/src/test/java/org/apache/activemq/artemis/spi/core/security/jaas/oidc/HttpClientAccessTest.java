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
package org.apache.activemq.artemis.spi.core.security.jaas.oidc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HttpClientAccess} - we don't have to test much, because we'll be mocking {@link HttpClient}
 * anyway. So HTTP issues should actually be covered in {@link OIDCMetadataAccessTest}.
 */
public class HttpClientAccessTest {

   @Test
   @SuppressWarnings("unchecked")
   public void justMockingHttpClient() throws IOException, InterruptedException {
      HttpClient client = mock(HttpClient.class);

      HttpResponse<String> notFoundResponse = mock(HttpResponse.class);
      when(notFoundResponse.body()).thenReturn("Not Found");
      when(notFoundResponse.statusCode()).thenReturn(404);

      HttpResponse<String> helloResponse = mock(HttpResponse.class);
      when(helloResponse.body()).thenReturn("Hello");
      when(helloResponse.statusCode()).thenReturn(200);

      HttpRequest req = any(HttpRequest.class);
      HttpResponse.BodyHandler<String> res = any(HttpResponse.BodyHandler.class);
      when(client.send(req, res)).thenAnswer(inv -> {
         HttpRequest r = inv.getArgument(0, HttpRequest.class);
         if (r.uri().getPath().equals("/index.txt")) {
            return helloResponse;
         }

         return notFoundResponse;
      });

      HttpRequest indexHtmlReq = HttpRequest.newBuilder(URI.create("http://localhost/index.html")).GET().build();
      HttpResponse<String> response = client.send(indexHtmlReq, HttpResponse.BodyHandlers.ofString());
      assertEquals(404, response.statusCode());
      assertEquals("Not Found", response.body());

      HttpRequest indexTxtReq = HttpRequest.newBuilder(URI.create("http://localhost/index.txt")).GET().build();
      response = client.send(indexTxtReq, HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode());
      assertEquals("Hello", response.body());
   }

   @Test
   public void sharedHttpClientAccess() {
      SharedHttpClientAccess access1 = new SharedHttpClientAccess(null, true);
      SharedHttpClientAccess access2 = new SharedHttpClientAccess(null, true);

      HttpClient client1 = access1.getClient(URI.create("http://localhost:8080"));
      HttpClient client2 = access1.getClient(URI.create("https://localhost:8443"));
      HttpClient client3 = access2.getClient(URI.create("http://localhost:8080"));
      HttpClient client4 = access2.getClient(URI.create("https://localhost:8443"));

      assertNotNull(client1);
      assertNotNull(client3);
      assertSame(client1, client3);
      assertSame(client2, client4);
   }

   @Test
   public void defaultClient() {
      SharedHttpClientAccess access = new SharedHttpClientAccess(null, true);

      HttpClient client = access.getClient(URI.create("http://localhost:8081"));

      assertNotNull(client);
      assertNotNull(client.sslContext());
   }

}
