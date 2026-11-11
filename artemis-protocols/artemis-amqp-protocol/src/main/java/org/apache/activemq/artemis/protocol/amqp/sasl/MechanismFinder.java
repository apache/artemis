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
package org.apache.activemq.artemis.protocol.amqp.sasl;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class MechanismFinder {

   private static final Map<String, ServerSASLFactory> FACTORY_MAP = new HashMap<>();
   private static final Comparator<? super ServerSASLFactory> PRECEDENCE_COMPARATOR =
      (f1, f2) -> Integer.compare(f1.getPrecedence(), f2.getPrecedence());
   private static final String[] DEFAULT_MECHANISMS;

   static {
      ServiceLoader<ServerSASLFactory> serviceLoader =
               ServiceLoader.load(ServerSASLFactory.class, MechanismFinder.class.getClassLoader());
      for (ServerSASLFactory factory : serviceLoader) {
         FACTORY_MAP.merge(factory.getMechanism(), factory, (f1, f2) -> {
            if (f2.getPrecedence() > f1.getPrecedence()) {
               return f2;
            } else {
               return f1;
            }
         });
      }
      DEFAULT_MECHANISMS = FACTORY_MAP.values()
                                      .stream()
                                      .filter(ServerSASLFactory::isDefaultPermitted)
                                      .sorted(PRECEDENCE_COMPARATOR.reversed())
                                      .map(ServerSASLFactory::getMechanism)
                                      .toArray(String[]::new);
   }

   public static String[] getDefaultMechanisms() {
      return DEFAULT_MECHANISMS;
   }

   public static ServerSASLFactory getFactory(String mechanism) {
      return FACTORY_MAP.get(mechanism);
   }
}
