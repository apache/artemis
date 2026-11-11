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
package org.apache.activemq.transport.amqp.client.util;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.engine.Transport;

/**
 * Utility that creates proxy objects for the Proton objects which won't allow any mutating operations to be applied so
 * that the test code does not interact with the proton engine outside the client serialization thread.
 */
public final class UnmodifiableProxy {

   private static List<String> denylist = new ArrayList<>();

   // These methods are mutating but don't take an arguments so they
   // aren't automatically filtered out. We will have to keep an eye
   // on proton API in the future and modify this list as it evolves.
   static {
      denylist.add("close");
      denylist.add("free");
      denylist.add("open");
      denylist.add("sasl");
      denylist.add("session");
      denylist.add("close_head");
      denylist.add("close_tail");
      denylist.add("outputConsumed");
      denylist.add("process");
      denylist.add("processInput");
      denylist.add("unbind");
      denylist.add("settle");
      denylist.add("clear");
      denylist.add("detach");
      denylist.add("abort");
   }

   private UnmodifiableProxy() {
   }

   public static Transport transportProxy(final Transport target) {
      Transport wrap = wrap(Transport.class, target);
      return wrap;
   }

   public static Sasl saslProxy(final Sasl target) {
      return wrap(Sasl.class, target);
   }

   public static Connection connectionProxy(final Connection target) {
      return wrap(Connection.class, target);
   }

   public static Session sessionProxy(final Session target) {
      return wrap(Session.class, target);
   }

   public static Delivery deliveryProxy(final Delivery target) {
      return wrap(Delivery.class, target);
   }

   public static Link linkProxy(final Link target) {
      return wrap(Link.class, target);
   }

   public static Receiver receiverProxy(final Receiver target) {
      return wrap(Receiver.class, target);
   }

   public static Sender senderProxy(final Sender target) {
      return wrap(Sender.class, target);
   }

   private static boolean isProtonType(Class<?> clazz) {
      String packageName = clazz.getPackage().getName();

      if (packageName.startsWith("org.apache.qpid.proton.")) {
         return true;
      }

      return false;
   }

   private static <T> T wrap(Class<T> type, final Object target) {
      return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class[] {type}, (o, method, objects) -> {
         if ("toString".equals(method.getName()) && method.getParameterTypes().length == 0) {
            return "Unmodifiable proxy -> (" + method.invoke(target, objects) + ")";
         }

         // Don't let methods that mutate be invoked.
         if (method.getParameterTypes().length > 0) {
            throw new UnsupportedOperationException("Cannot mutate outside the Client work thread");
         }

         if (denylist.contains(method.getName())) {
            throw new UnsupportedOperationException("Cannot mutate outside the Client work thread");
         }

         Class<?> returnType = method.getReturnType();

         try {
            Object result = method.invoke(target, objects);
            if (result == null) {
               return null;
            }

            if (returnType.isPrimitive() || returnType.isArray() || Object.class.equals(returnType)) {
               // Skip any other checks
            } else if (returnType.isAssignableFrom(ByteBuffer.class)) {
               // Buffers are modifiable but we can just return null to indicate
               // there's nothing there to access.
               result = null;
            } else if (returnType.isAssignableFrom(Map.class)) {
               // Prevent return of modifiable maps
               result = Collections.unmodifiableMap((Map<?, ?>) result);
            } else if (isProtonType(returnType) && returnType.isInterface()) {

               // Can't handle the crazy Source / Target types yet as there's two
               // different types for Source and Target the result can't be cast to
               // the one people actually want to use.
               if (!returnType.getName().equals("org.apache.qpid.proton.amqp.transport.Source")
                  && !returnType.getName().equals("org.apache.qpid.proton.amqp.messaging.Source")
                  && !returnType.getName().equals("org.apache.qpid.proton.amqp.transport.Target")
                  && !returnType.getName().equals("org.apache.qpid.proton.amqp.messaging.Target")) {

                  result = wrap(returnType, result);
               }
            }

            return result;
         } catch (InvocationTargetException e) {
            throw e.getTargetException();
         }
      }));
   }
}
