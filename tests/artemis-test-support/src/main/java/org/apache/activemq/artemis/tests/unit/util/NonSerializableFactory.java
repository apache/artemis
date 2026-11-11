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
package org.apache.activemq.artemis.tests.unit.util;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * used by the default context when running in embedded local configuration
 */
public class NonSerializableFactory implements ObjectFactory {

   public NonSerializableFactory() {
   }

   public static Object lookup(final String name) throws NamingException {
      if (NonSerializableFactory.getWrapperMap().get(name) == null) {
         throw new NamingException(name + " not found");
      }
      return NonSerializableFactory.getWrapperMap().get(name);
   }

   @Override
   public Object getObjectInstance(final Object obj,
                                   final Name name,
                                   final Context nameCtx,
                                   final Hashtable<?, ?> env) throws Exception {
      Reference ref = (Reference) obj;
      RefAddr addr = ref.get("nns");
      String key = (String) addr.getContent();
      return NonSerializableFactory.getWrapperMap().get(key);
   }

   public static Map<String, Object> getWrapperMap() {
      return NonSerializableFactory.wrapperMap;
   }

   private static Map<String, Object> wrapperMap = Collections.synchronizedMap(new HashMap<>());
}