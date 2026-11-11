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
package org.apache.activemq.artemis.tests.util;

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
public final class NonSerializableFactory implements ObjectFactory {

   private NonSerializableFactory() {
      // Utility
   }

   //   public static void unbind(final Context ctx, final String strName) throws NamingException
   //   {
   //      Name name = ctx.getNameParser("").parse(strName);
   //      int size = name.size();
   //      String atom = name.get(size - 1);
   //      Context parentCtx = Util.createSubcontext(ctx, name.getPrefix(size - 1));
   //      String key = new StringBuilder().append(parentCtx.getNameInNamespace()).append("/").append(atom).toString();
   //      NonSerializableFactory.getWrapperMap().remove(key);
   //      Util.unbind(ctx, strName);
   //   }
   //
   //   public static void rebind(final Context ctx, final String strName, final Object value) throws NamingException
   //   {
   //      Name name = ctx.getNameParser("").parse(strName);
   //      int size = name.size();
   //      String atom = name.get(size - 1);
   //      Context parentCtx = Util.createSubcontext(ctx, name.getPrefix(size - 1));
   //      String key = new StringBuilder().append(parentCtx.getNameInNamespace()).append("/").append(atom).toString();
   //      NonSerializableFactory.getWrapperMap().put(key, value);
   //      String className = value.getClass().getName();
   //      String factory = NonSerializableFactory.class.getName();
   //      StringRefAddr addr = new StringRefAddr("nns", key);
   //      Reference memoryRef = new Reference(className, addr, factory, null);
   //      parentCtx.rebind(atom, memoryRef);
   //   }
   //
   //   public static void bind(final Context ctx, final String strName, final Object value) throws NamingException
   //   {
   //      Name name = ctx.getNameParser("").parse(strName);
   //      int size = name.size();
   //      String atom = name.get(size - 1);
   //      Context parentCtx = Util.createSubcontext(ctx, name.getPrefix(size - 1));
   //      String key = new StringBuilder().append(parentCtx.getNameInNamespace()).append("/").append(atom).toString();
   //      NonSerializableFactory.getWrapperMap().put(key, value);
   //      String className = value.getClass().getName();
   //      String factory = NonSerializableFactory.class.getName();
   //      StringRefAddr addr = new StringRefAddr("nns", key);
   //      Reference memoryRef = new Reference(className, addr, factory, null);
   //
   //      parentCtx.bind(atom, memoryRef);
   //   }

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