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
package org.apache.activemq.artemis.protocol.amqp.proton.handler;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.engine.Transport;

public interface EventHandler {

   default void onAuthInit(ProtonHandler handler, Connection connection, boolean sasl) {
   }

   default void onSaslRemoteMechanismChosen(ProtonHandler handler, String mech) {
   }

   default void onAuthFailed(ProtonHandler protonHandler, Connection connection) {
   }

   default void onAuthSuccess(ProtonHandler protonHandler, Connection connection) {
   }

   default void onSaslMechanismsOffered(ProtonHandler handler, String[] mechanisms) {
   }

   default void onInit(Connection connection) throws Exception {
   }

   default void onLocalOpen(Connection connection) throws Exception {
   }

   default void onRemoteOpen(Connection connection) throws Exception {
   }

   default void onLocalClose(Connection connection) throws Exception {
   }

   default void onRemoteClose(Connection connection) throws Exception {
   }

   default void onFinal(Connection connection) throws Exception {
   }

   default void onInit(Session session) throws Exception {
   }

   default void onLocalOpen(Session session) throws Exception {
   }

   default void onRemoteOpen(Session session) throws Exception {
   }

   default void onLocalClose(Session session) throws Exception {
   }

   default void onRemoteClose(Session session) throws Exception {
   }

   default void onFinal(Session session) throws Exception {
   }

   default void onInit(Link link) throws Exception {
   }

   default void onLocalOpen(Link link) throws Exception {
   }

   default void onRemoteOpen(Link link) throws Exception {
   }

   default void onLocalClose(Link link) throws Exception {
   }

   default void onRemoteClose(Link link) throws Exception {
   }

   default void onFlow(Link link) throws Exception {
   }

   default void onFinal(Link link) throws Exception {
   }

   default void onRemoteDetach(Link link) throws Exception {
   }

   default void onLocalDetach(Link link) throws Exception {
   }

   default void onDelivery(Delivery delivery) throws Exception {
   }

   default void onTransportError(Transport transport) throws Exception {
   }

   default void onTransport(Transport transport) throws Exception {
   }

   default void pushBytes(ByteBuf bytes) {
   }

   default boolean flowControl(ReadyListener readyListener) {
      return true;
   }

   default String getRemoteAddress() {
      return "";
   }
}
