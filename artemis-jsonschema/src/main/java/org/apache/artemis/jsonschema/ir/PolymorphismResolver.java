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

package org.apache.artemis.jsonschema.ir;

import java.lang.reflect.Modifier;
import java.util.*;
import org.apache.artemis.jsonschema.annotation.Heuristic;

/**
 * Discovers concrete subclasses of abstract config classes via Reflections classpath scanning. Used
 * during IR construction to detect class-based polymorphism (e.g., AMQPBrokerConnectionElement →
 * Bridge, Mirror, Federated variants).
 */
public class PolymorphismResolver {

   private final SchemaIR ir;

   /**
    * @param ir the IR graph that will receive discovered polymorphic class registrations
    */
   public PolymorphismResolver(SchemaIR ir) {
      this.ir = ir;
   }

   /**
    * Discover concrete subclasses of a base class via classpath scanning. Only scans classes under
    * org.apache.activemq.artemis.core.config to avoid expensive full-classpath scans for JDK/library
    * types encountered during traversal.
    *
    * @param baseClass the abstract/interface class to find subtypes of
    * @return sorted list of concrete subclasses, or empty if none found or class is outside scope
    */
   @Heuristic("Assumes polymorphic config classes live under core.config package")
   public List<Class<?>> findSubclasses(Class<?> baseClass) {
      if (!baseClass.getName().startsWith("org.apache.activemq.artemis.core.config")) {
         return new ArrayList<>();
      }

      try {
         String packageName = baseClass.getPackage().getName();
         org.reflections.Reflections reflections =
               new org.reflections.Reflections(
                     new org.reflections.util.ConfigurationBuilder()
                           .forPackages(packageName)
                           .setScanners(org.reflections.scanners.Scanners.SubTypes));

         Set<? extends Class<?>> subtypes = getSubTypes(reflections, baseClass);

         List<Class<?>> subclasses = new ArrayList<>();
         for (Class<?> subtype : subtypes) {
            if (!subtype.equals(baseClass)
                  && !Modifier.isAbstract(subtype.getModifiers())
                  && !subtype.isInterface()) {
               subclasses.add(subtype);
            }
         }

         subclasses.sort(Comparator.comparing(Class::getSimpleName));
         return subclasses;
      } catch (Exception e) {
         return new ArrayList<>();
      }
   }

   /**
    * Bridge for Reflections generic erasure — unavoidable cast isolated here.
    *
    * @param reflections configured Reflections instance
    * @param baseClass base class to query subtypes for
    * @param <T> base type parameter
    * @return set of discovered subtypes
    */
   @SuppressWarnings("unchecked")
   private static <T> Set<? extends Class<?>> getSubTypes(
         org.reflections.Reflections reflections, Class<T> baseClass) {
      return (Set<? extends Class<?>>) (Set<?>) reflections.getSubTypesOf(baseClass);
   }
}
