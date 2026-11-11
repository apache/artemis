/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.security.auth.Subject;

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.commons.collections.set.ListOrderedSet;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class KeyResolverTest {
   private static final String UNMATCHED_FILTER = "ARTEMIS";

   @Test
   public void testClientIDKey() {
      testClientIDKey("TEST", "TEST", null);
   }

   @Test
   public void testClientIDKeyWithFilter() {
      testClientIDKey("TEST", "TEST1234", "^.{4}");
   }

   @Test
   public void testClientIDKeyWithUnmatchedFilter() {
      testClientIDKey(KeyResolver.NULL_KEY_VALUE, "TEST1234", UNMATCHED_FILTER);
   }

   private void testClientIDKey(String expected, String clientID, String filter) {
      KeyResolver keyResolver = new KeyResolver(KeyType.CLIENT_ID, filter);

      assertEquals(expected, keyResolver.resolve(null, clientID, null));

      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(null, null, null));
   }

   @Test
   public void testSNIHostKey() {
      testSNIHostKey("TEST", "TEST", null);
   }

   @Test
   public void testSNIHostKeyWithFilter() {
      testSNIHostKey("TEST", "TEST1234", "^.{4}");
   }

   @Test
   public void testSNIHostKeyWithUnmatchedFilter() {
      testSNIHostKey(KeyResolver.NULL_KEY_VALUE, null, UNMATCHED_FILTER);
   }

   private void testSNIHostKey(String expected, String sniHost, String filter) {
      Connection connection = Mockito.mock(Connection.class);

      KeyResolver keyResolver = new KeyResolver(KeyType.SNI_HOST, filter);

      Mockito.when(connection.getSNIHostName()).thenReturn(sniHost);
      assertEquals(expected, keyResolver.resolve(connection, null, null));

      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(null, null, null));

      Mockito.when(connection.getSNIHostName()).thenReturn(null);
      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(null, null, null));
   }

   @Test
   public void testSourceIPKey() {
      testSourceIPKey("10.0.0.1", "10.0.0.1:12345", null);
   }

   @Test
   public void testSourceIPKeyWithFilter() {
      testSourceIPKey("10", "10.0.0.1:12345", "^[^.]+");
   }

   @Test
   public void testSourceIPKeyWithUnmatchedFilter() {
      testSourceIPKey(KeyResolver.NULL_KEY_VALUE, "10.0.0.1:12345", UNMATCHED_FILTER);
   }

   private void testSourceIPKey(String expected, String remoteAddress, String filter) {
      Connection connection = Mockito.mock(Connection.class);

      KeyResolver keyResolver = new KeyResolver(KeyType.SOURCE_IP, filter);

      Mockito.when(connection.getRemoteAddress()).thenReturn(remoteAddress);
      assertEquals(expected, keyResolver.resolve(connection, null, null));

      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(null, null, null));

      Mockito.when(connection.getRemoteAddress()).thenReturn(null);
      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(null, null, null));
   }

   @Test
   public void testUserNameKey() {
      testUserNameKey("TEST", "TEST", null);
   }

   @Test
   public void testUserNameKeyWithFilter() {
      testUserNameKey("TEST", "TEST1234", "^.{4}");
   }

   @Test
   public void testUserNameKeyWithUnmatchedFilter() {
      testUserNameKey(KeyResolver.NULL_KEY_VALUE, "TEST1234", UNMATCHED_FILTER);
   }

   private void testUserNameKey(String expected, String username, String filter) {
      KeyResolver keyResolver = new KeyResolver(KeyType.USER_NAME, filter);

      assertEquals(expected, keyResolver.resolve(null, null, username));

      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(null, null, null));
   }

   @Test
   public void testRoleNameKeyWithFilter() throws Exception {
      KeyResolver keyResolver = new KeyResolver(KeyType.ROLE_NAME, "B");

      Connection connection = Mockito.mock(Connection.class);
      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(connection, null, null));

      RemotingConnection protocolConnection = Mockito.mock(RemotingConnection.class);
      Mockito.when(connection.getProtocolConnection()).thenReturn(protocolConnection);
      Subject subject = Mockito.mock(Subject.class);
      Mockito.when(protocolConnection.getSubject()).thenReturn(subject);

      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(connection, null, null));

      Set<RolePrincipal> rolePrincipals = new HashSet<>();
      Mockito.when(subject.getPrincipals(RolePrincipal.class)).thenReturn(rolePrincipals);

      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(connection, null, null));

      rolePrincipals.add(new RolePrincipal("A"));

      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(connection, null, null));

      rolePrincipals.add(new RolePrincipal("B"));

      assertEquals("B", keyResolver.resolve(connection, null, null));
   }

   @Test
   public void testRoleNameKeyWithoutFilter() throws Exception {
      KeyResolver keyResolver = new KeyResolver(KeyType.ROLE_NAME, null);

      Connection connection = Mockito.mock(Connection.class);
      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(connection, null, null));

      RemotingConnection protocolConnection = Mockito.mock(RemotingConnection.class);
      Mockito.when(connection.getProtocolConnection()).thenReturn(protocolConnection);
      Subject subject = Mockito.mock(Subject.class);
      Mockito.when(protocolConnection.getSubject()).thenReturn(subject);

      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(connection, null, null));

      Set<RolePrincipal> rolePrincipals = new ListOrderedSet();
      Mockito.when(subject.getPrincipals(RolePrincipal.class)).thenReturn(rolePrincipals);

      assertEquals(KeyResolver.NULL_KEY_VALUE, keyResolver.resolve(connection, null, null));

      final RolePrincipal roleA = new RolePrincipal("A");
      rolePrincipals.add(roleA);

      assertEquals("A", keyResolver.resolve(connection, null, null));

      rolePrincipals.add(new RolePrincipal("B"));

      assertEquals("A", keyResolver.resolve(connection, null, null));

      rolePrincipals.remove(roleA);
      // with no filter, the first entry matches
      assertEquals("B", keyResolver.resolve(connection, null, null));
   }
}
