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

import org.apache.qpid.proton.engine.Event;

public final class Events {

   public static void dispatch(Event event, EventHandler handler) throws Exception {
      switch (event.getType()) {
         case CONNECTION_INIT:
            handler.onInit(event.getConnection());
            break;
         case CONNECTION_LOCAL_OPEN:
            handler.onLocalOpen(event.getConnection());
            break;
         case CONNECTION_REMOTE_OPEN:
            handler.onRemoteOpen(event.getConnection());
            break;
         case CONNECTION_LOCAL_CLOSE:
            handler.onLocalClose(event.getConnection());
            break;
         case CONNECTION_REMOTE_CLOSE:
            handler.onRemoteClose(event.getConnection());
            break;
         case CONNECTION_FINAL:
            handler.onFinal(event.getConnection());
            break;
         case SESSION_INIT:
            handler.onInit(event.getSession());
            break;
         case SESSION_LOCAL_OPEN:
            handler.onLocalOpen(event.getSession());
            break;
         case SESSION_REMOTE_OPEN:
            handler.onRemoteOpen(event.getSession());
            break;
         case SESSION_LOCAL_CLOSE:
            handler.onLocalClose(event.getSession());
            break;
         case SESSION_REMOTE_CLOSE:
            handler.onRemoteClose(event.getSession());
            break;
         case SESSION_FINAL:
            handler.onFinal(event.getSession());
            break;
         case LINK_INIT:
            handler.onInit(event.getLink());
            break;
         case LINK_LOCAL_OPEN:
            handler.onLocalOpen(event.getLink());
            break;
         case LINK_REMOTE_OPEN:
            handler.onRemoteOpen(event.getLink());
            break;
         case LINK_LOCAL_CLOSE:
            handler.onLocalClose(event.getLink());
            break;
         case LINK_REMOTE_CLOSE:
            handler.onRemoteClose(event.getLink());
            break;
         case LINK_FLOW:
            handler.onFlow(event.getLink());
            break;
         case LINK_FINAL:
            handler.onFinal(event.getLink());
            break;
         case LINK_LOCAL_DETACH:
            handler.onLocalDetach(event.getLink());
            break;
         case LINK_REMOTE_DETACH:
            handler.onRemoteDetach(event.getLink());
            break;
         case TRANSPORT_ERROR:
            handler.onTransportError(event.getTransport());
            break;
         case TRANSPORT:
            handler.onTransport(event.getTransport());
            break;
         case DELIVERY:
            handler.onDelivery(event.getDelivery());
            break;
         default:
            break;
      }
   }
}
