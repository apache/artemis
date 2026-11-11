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

package org.apache.activemq.artemis.spi.core.security.jaas;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.StringTokenizer;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpsExchange;

/**
 * delegate to our JAAS login modules by adapting our handlers to httpserver.httpExchange
 */
public class HttpServerAuthenticator extends Authenticator {

   static final String REALM_PROPERTY_NAME = "httpServerAuthenticator.realm";
   static final String REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME = "httpServerAuthenticator.requestSubjectAttribute";
   static String DEFAULT_SUBJECT_ATTRIBUTE = "org.apache.activemq.artemis.jaasSubject";
   static final String DEFAULT_REALM = "http_server_authenticator";
   static final String AUTHORIZATION_HEADER_NAME = "Authorization";

   final String realm = System.getProperty(REALM_PROPERTY_NAME, DEFAULT_REALM);
   final String subjectRequestAttribute = System.getProperty(REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME, DEFAULT_SUBJECT_ATTRIBUTE);

   @Override
   public Result authenticate(HttpExchange httpExchange) {
      ClassLoader currentTCCL = Thread.currentThread().getContextClassLoader();
      try {
         Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
         LoginContext loginContext = new LoginContext(realm, callbacks -> {
            for (Callback callback : callbacks) {
               if (callback instanceof PasswordCallback passwordCallback) {

                  StringTokenizer stringTokenizer = new StringTokenizer(extractAuthHeader(httpExchange));
                  String method = stringTokenizer.nextToken();
                  if ("Basic".equalsIgnoreCase(method)) {
                     byte[] authHeaderBytes = Base64.getDecoder().decode(stringTokenizer.nextToken().getBytes(StandardCharsets.UTF_8));

                     // :pass
                     byte[] password = Arrays.copyOfRange(authHeaderBytes, Arrays.binarySearch(authHeaderBytes, (byte) ':') + 1, authHeaderBytes.length);
                     passwordCallback.setPassword(new String(password, StandardCharsets.UTF_8).toCharArray());
                  } else if ("Bearer".equalsIgnoreCase(method)) {
                     passwordCallback.setPassword(stringTokenizer.nextToken().toCharArray());
                  }
               } else if (callback instanceof NameCallback nameCallback) {

                  StringTokenizer stringTokenizer = new StringTokenizer(extractAuthHeader(httpExchange));
                  String method = stringTokenizer.nextToken();
                  if ("Basic".equalsIgnoreCase(method)) {
                     byte[] authHeaderBytes = Base64.getDecoder().decode(stringTokenizer.nextToken().getBytes(StandardCharsets.UTF_8));

                     // user:
                     byte[] user = Arrays.copyOfRange(authHeaderBytes, 0, Arrays.binarySearch(authHeaderBytes, (byte) ':'));
                     nameCallback.setName(new String(user, StandardCharsets.UTF_8));
                  }
               } else if (callback instanceof CertificateCallback certCallback) {

                  if (httpExchange instanceof HttpsExchange httpsExchange) {
                     Certificate[] peerCerts = httpsExchange.getSSLSession().getPeerCertificates();
                     if (peerCerts != null && peerCerts.length > 0) {
                        certCallback.setCertificates(new X509Certificate[]{(X509Certificate) peerCerts[0]});
                     }
                  }
               } else if (callback instanceof PrincipalsCallback principalsCallback) {

                  Principal principal = httpExchange.getPrincipal();
                  if (principal == null && httpExchange instanceof HttpsExchange httpsExchange) {
                     principal = httpsExchange.getSSLSession().getPeerPrincipal();
                  }
                  if (principal != null) {
                     principalsCallback.setPeerPrincipals(new Principal[]{principal});
                  }
               } else {
                  throw new UnsupportedCallbackException(callback);
               }
            }
         });
         loginContext.login();
         httpExchange.setAttribute(subjectRequestAttribute, loginContext.getSubject());
         return new Authenticator.Success(new HttpPrincipal(nameFromAuthSubject(loginContext.getSubject()), realm));
      } catch (Exception e) {
         return new Authenticator.Failure(401);
      } finally {
         Thread.currentThread().setContextClassLoader(currentTCCL);
      }
   }

   protected String extractAuthHeader(HttpExchange httpExchange) {
      return httpExchange.getRequestHeaders().getFirst(AUTHORIZATION_HEADER_NAME);
   }

   protected String nameFromAuthSubject(Subject subject) {
      for (Principal p : subject.getPrincipals(UserPrincipal.class)) {
         return p.getName();
      }
      return "";
   }
}
