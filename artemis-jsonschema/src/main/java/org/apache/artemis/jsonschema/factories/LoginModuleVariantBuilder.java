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

package org.apache.artemis.jsonschema.factories;

import java.util.List;
import org.apache.artemis.jsonschema.ir.SchemaIR;

/**
 * Builds oneOf variants for JaasAppConfigurationEntry (login modules). Discriminator: {@code
 * loginModuleClass}. Params field: {@code params}.
 *
 * <p>Each LoginModule implementation (PropertiesLoginModule, LDAPLoginModule, GuestLoginModule,
 * etc.) accepts different params. This builder creates a $def per module with the specific params
 * documented, and a oneOf on the modules map so the schema expresses which params are valid for
 * which module.
 */
public class LoginModuleVariantBuilder extends FactoryVariantBuilder {

   public LoginModuleVariantBuilder(SchemaIR ir, FactoryParameterRegistry factoryRegistry) {
      super(ir, factoryRegistry);
   }

   @Override
   protected String getTargetClassName() {
      return "org.apache.activemq.artemis.core.config.JaasAppConfigurationEntry";
   }

   @Override
   protected String getDiscriminatorField() {
      return "loginModuleClass";
   }

   @Override
   protected String getParamsField() {
      return "params";
   }

   @Override
   protected List<String> filterFactories(String propertyName) {
      // All LoginModule implementations are relevant regardless of context.
      // Unlike transport factories, there's no acceptor/connector split.
      List<String> all = factoryRegistry.getFactoriesForProperty("loginModuleClass");
      if (all != null && !all.isEmpty()) {
         return all;
      }
      // Fallback: get all LoginModule implementations from the registry
      return factoryRegistry.getFactoriesByInterface("LoginModule");
   }
}
