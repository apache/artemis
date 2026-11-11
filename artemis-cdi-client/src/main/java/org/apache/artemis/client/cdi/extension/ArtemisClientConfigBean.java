/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.artemis.client.cdi.extension;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import org.apache.artemis.client.cdi.configuration.ArtemisClientConfiguration;
import org.apache.artemis.client.cdi.configuration.DefaultArtemisClientConfigurationImpl;

import static java.util.Collections.emptySet;

class ArtemisClientConfigBean implements Bean<ArtemisClientConfiguration> {

   @Override
   public Class<?> getBeanClass() {
      return DefaultArtemisClientConfigurationImpl.class;
   }

   @Override
   public Set<InjectionPoint> getInjectionPoints() {
      return emptySet();
   }

   // we cannot use @Override here because otherwise the build of artemis-jakarta-cdi-client fails since the method was removed in jakarta ee 9+
   @SuppressWarnings("MissingOverride")
   public boolean isNullable() {
      return false;
   }

   @Override
   public ArtemisClientConfiguration create(CreationalContext<ArtemisClientConfiguration> creationalContext) {
      return new DefaultArtemisClientConfigurationImpl();
   }

   @Override
   public void destroy(ArtemisClientConfiguration configuration,
                       CreationalContext<ArtemisClientConfiguration> creationalContext) {
   }

   @Override
   public Set<Type> getTypes() {
      Set<Type> types = new HashSet<>();
      types.add(DefaultArtemisClientConfigurationImpl.class);
      types.add(ArtemisClientConfiguration.class);
      return types;
   }

   @Override
   public Set<Annotation> getQualifiers() {
      Set<Annotation> qualifiers = new HashSet<>();
      qualifiers.add(AnyLiteral.INSTANCE);
      qualifiers.add(DefaultLiteral.INSTANCE);
      return qualifiers;

   }

   @Override
   public Class<? extends Annotation> getScope() {
      return ApplicationScoped.class;
   }

   @Override
   public String getName() {
      return null;
   }

   @Override
   public Set<Class<? extends Annotation>> getStereotypes() {
      return emptySet();
   }

   @Override
   public boolean isAlternative() {
      return false;
   }
}
