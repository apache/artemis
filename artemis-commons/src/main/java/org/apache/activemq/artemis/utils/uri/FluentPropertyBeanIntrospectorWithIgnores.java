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
package org.apache.activemq.artemis.utils.uri;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.commons.beanutils.FluentPropertyBeanIntrospector;
import org.apache.commons.beanutils.IntrospectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class FluentPropertyBeanIntrospectorWithIgnores extends FluentPropertyBeanIntrospector {

   static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static Set<Pair<String, String>> ignores = ConcurrentHashMap.newKeySet();

   public static void addIgnore(String className, String methodName) {
      logger.trace("Adding ignore on {}/{}", className, methodName);
      ignores.add(new Pair<>(className, methodName));
   }

   public static boolean isIgnored(String className, String methodName) {
      return ignores.contains(new Pair<>(className, methodName));
   }

   @Override
   public void introspect(IntrospectionContext icontext) throws IntrospectionException {
      for (Method m : icontext.getTargetClass().getMethods()) {
         final String methodName = m.getName();
         if (methodName.startsWith(getWriteMethodPrefix()) && !methodName.equals(getWriteMethodPrefix())) {
            if (isIgnored(icontext.getTargetClass().getName(), methodName)) {
               logger.trace("{} Ignored for {}", methodName, icontext.getTargetClass().getName());
               continue;
            }

            final String propertyName = propertyName(methodName);
            introspect(icontext, m, propertyName);
            final String defaultPropertyName = defaultPropertyName(methodName);
            if (!defaultPropertyName.equals(propertyName)) {
               introspect(icontext, m, defaultPropertyName);
            }
         }
      }
   }

   private void introspect(IntrospectionContext icontext, Method writeMethod, String propertyName) {
      PropertyDescriptor pd = icontext.getPropertyDescriptor(propertyName);

      Method readMethod = null;
      if (pd != null) {
         readMethod = pd.getReadMethod();
      }
      if (readMethod == null) {
         try {
            if (writeMethod != null && writeMethod.getParameterTypes().length == 1 && writeMethod.getParameterTypes()[0].equals(Boolean.class)) {
               // is methods with Boolean return are not valid bean accessors but our fluent classes use them
               readMethod = icontext.getTargetClass().getMethod("is" + capitalise(propertyName), null);
            }
         } catch (NoSuchMethodException ignored) {
         }
      }
      try {
         PropertyDescriptor withFluentWrite = createFluentPropertyDescriptor(readMethod, writeMethod, propertyName);
         icontext.addPropertyDescriptor(withFluentWrite);
      } catch (IntrospectionException e) {
         logger.trace("error on add fluent descriptor for property named {}", propertyName, e);
      }
   }

   private String capitalise(String name) {
      if (name.length() > 1) {
         return name.substring(0, 1).toUpperCase() + name.substring(1);
      }
      return name;
   }

   private PropertyDescriptor createFluentPropertyDescriptor(Method readMethod, Method writeMethod, String propertyName) throws IntrospectionException {
      return new PropertyDescriptor(propertyName, readMethod, writeMethod);
   }

   private String propertyName(final String methodName) {
      String propName = methodName.substring(getWriteMethodPrefix().length());
      return (propName.length() > 1) ? Character.toLowerCase(propName.charAt(0)) +
         propName.substring(1) : propName.toLowerCase(Locale.ENGLISH);
   }

   private String defaultPropertyName(final String methodName) {
      final String propertyName = methodName.substring(
         getWriteMethodPrefix().length());
      return (propertyName.length() > 1) ? Introspector.decapitalize(propertyName) : propertyName
         .toLowerCase(Locale.ENGLISH);
   }
}
