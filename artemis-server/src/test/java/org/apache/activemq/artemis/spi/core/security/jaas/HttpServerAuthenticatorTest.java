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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpsExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import static org.apache.activemq.artemis.spi.core.security.jaas.HttpServerAuthenticator.AUTHORIZATION_HEADER_NAME;
import static org.apache.activemq.artemis.spi.core.security.jaas.HttpServerAuthenticator.DEFAULT_SUBJECT_ATTRIBUTE;
import static org.apache.activemq.artemis.spi.core.security.jaas.HttpServerAuthenticator.REALM_PROPERTY_NAME;
import static org.apache.activemq.artemis.spi.core.security.jaas.HttpServerAuthenticator.REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HttpServerAuthenticatorTest {

   private final HttpsExchange httpsExchange = mock(HttpsExchange.class);

   static final String loginConfigSystemPropName = "java.security.auth.login.config";

   @BeforeAll
   public static void setSystemProps() {
      URL url = HttpServerAuthenticatorTest.class.getClassLoader().getResource("login.config");
      if (url != null) {
         String val = url.getFile();
         if (val != null) {
            System.setProperty(loginConfigSystemPropName, val);
         }
      }
   }

   @AfterAll
   public static void unsetSystemProps() {
      System.clearProperty(loginConfigSystemPropName);
      System.clearProperty(REALM_PROPERTY_NAME);
      System.clearProperty(REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME);
   }

   @Test
   public void testGuestLogin() {

      System.setProperty(REALM_PROPERTY_NAME, "GuestLogin");
      System.clearProperty(REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME);

      Object[] tracked = new Object[1];
      doAnswer((Answer<Void>) invocationOnMock -> {
         tracked[0] = invocationOnMock.getArgument(1);
         return null;
      }).when(httpsExchange).setAttribute(any(String.class), any(Object.class));
      when(httpsExchange.getAttribute(DEFAULT_SUBJECT_ATTRIBUTE)).then(invocationOnMock -> tracked[0]);

      HttpServerAuthenticator underTest = new HttpServerAuthenticator();
      Authenticator.Result result = underTest.authenticate(httpsExchange);

      assertInstanceOf(Authenticator.Success.class, result);
      assertEquals("foo", ((Authenticator.Success) result).getPrincipal().getUsername());

      Subject subject = (Subject) httpsExchange.getAttribute(DEFAULT_SUBJECT_ATTRIBUTE);

      assertEquals(1, subject.getPrincipals(UserPrincipal.class).size());
      subject.getPrincipals(UserPrincipal.class).forEach(p -> assertEquals("foo", p.getName()));
      Set<RolePrincipal> roles = subject.getPrincipals(RolePrincipal.class);
      assertEquals(1, roles.size());
   }

   @Test
   public void testBasicLogin() {

      System.setProperty(REALM_PROPERTY_NAME, "PropertiesLogin");
      System.clearProperty(REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME);

      Headers headers = new Headers();
      headers.add(AUTHORIZATION_HEADER_NAME, "Basic " + Base64.getEncoder().encodeToString("first:secret".getBytes(StandardCharsets.UTF_8)));
      when(httpsExchange.getRequestHeaders()).thenReturn(headers);

      Object[] tracked = new Object[1];
      doAnswer((Answer<Void>) invocationOnMock -> {
         tracked[0] = invocationOnMock.getArgument(1);
         return null;
      }).when(httpsExchange).setAttribute(any(String.class), any(Object.class));
      when(httpsExchange.getAttribute(DEFAULT_SUBJECT_ATTRIBUTE)).then(invocationOnMock -> tracked[0]);

      HttpServerAuthenticator underTest = new HttpServerAuthenticator();
      Authenticator.Result result = underTest.authenticate(httpsExchange);

      assertInstanceOf(Authenticator.Success.class, result);
      assertEquals("first", ((Authenticator.Success) result).getPrincipal().getUsername());

      Subject subject = (Subject) httpsExchange.getAttribute(DEFAULT_SUBJECT_ATTRIBUTE);

      assertEquals(1, subject.getPrincipals(UserPrincipal.class).size());
      subject.getPrincipals(UserPrincipal.class).forEach(p -> assertEquals("first", p.getName()));

      Set<RolePrincipal> roles = subject.getPrincipals(RolePrincipal.class);
      assertEquals(2, roles.size());
   }


   @Test
   public void testNonBasic() {

      System.setProperty(REALM_PROPERTY_NAME, "HttpServerAuthenticator");
      System.clearProperty(REQUEST_SUBJECT_ATTRIBUTE_PROPERTY_NAME);

      Headers headers = new Headers();
      headers.add(AUTHORIZATION_HEADER_NAME, "Bearer " + Base64.getEncoder().encodeToString("some-random-string".getBytes(StandardCharsets.UTF_8)));
      when(httpsExchange.getRequestHeaders()).thenReturn(headers);


      HttpServerAuthenticator underTest = new HttpServerAuthenticator();
      Authenticator.Result result = underTest.authenticate(httpsExchange);

      assertInstanceOf(Authenticator.Failure.class, result);
      assertNull(httpsExchange.getAttribute(DEFAULT_SUBJECT_ATTRIBUTE), "no subject");

      // kube login attempt
      verify(httpsExchange, times(1)).getRequestHeaders();

      // cert attempt
      verify(httpsExchange, times(1)).getSSLSession();
   }
}
