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

import java.util.ArrayList;
import java.util.List;
import org.apache.artemis.jsonschema.annotation.Heuristic;
import org.apache.artemis.jsonschema.ir.SchemaIR;

/**
 * Builds oneOf variants for TransportConfiguration (acceptors/connectors). Discriminator: {@code
 * factoryClassName}. Params field: {@code params}.
 */
public class TransportFactoryVariantBuilder extends FactoryVariantBuilder {

   public TransportFactoryVariantBuilder(SchemaIR ir, FactoryParameterRegistry factoryRegistry) {
      super(ir, factoryRegistry);
   }

   @Override
   protected String getTargetClassName() {
      return "org.apache.activemq.artemis.api.core.TransportConfiguration";
   }

   @Override
   protected String getDiscriminatorField() {
      return "factoryClassName";
   }

   @Override
   protected String getParamsField() {
      return "params";
   }

   @Override
   @Heuristic("Relies on 'acceptor'/'connector' appearing in property names")
   protected List<String> filterFactories(String propertyName) {
      List<String> allFactories = factoryRegistry.getFactoriesForProperty("factoryClassName");
      List<String> filtered = new ArrayList<>();

      String lower = propertyName.toLowerCase();

      for (String factoryClass : allFactories) {
         String simple = getSimpleClassName(factoryClass);

         if (lower.contains("acceptor") && !lower.contains("connector")) {
            if (simple.contains("Acceptor") && !simple.contains("Connector")) {
               filtered.add(factoryClass);
            }
         } else if (lower.contains("connector") && !lower.contains("acceptor")) {
            if (simple.contains("Connector") && !simple.contains("Acceptor")) {
               filtered.add(factoryClass);
            }
         } else {
            filtered.add(factoryClass);
         }
      }

      return filtered;
   }
}
