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

package org.apache.activemq.artemis.api.core.management;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class OperationAnnotationTest {

   @Parameters(name = "class=({0})")
   public static Collection<Object[]> getTestParameters() {
      return Arrays.asList(new Object[][]{{ActiveMQServerControl.class},
                                          {AddressControl.class},
                                          {QueueControl.class},
                                          {BridgeControl.class},
                                          {DivertControl.class},
                                          {AcceptorControl.class},
                                          {ClusterConnectionControl.class},
                                          {BroadcastGroupControl.class},
                                          {ConnectionRouterControl.class}});
   }

   private Class<?> managementClass;

   public OperationAnnotationTest(Class<?> managementClass) {
      this.managementClass = managementClass;
   }

   @TestTemplate
   public void testEachParameterAnnotated() throws Exception {
      checkControlInterface(managementClass);
   }

   private void checkControlInterface(Class controlInterface) {

      Method[] methods = controlInterface.getMethods();
      for (Method m : methods) {
         Annotation annotation = m.getAnnotation(Operation.class);
         if (annotation != null) {
            //each arguments must have a Parameter annotation
            Class<?>[] paramTypes = m.getParameterTypes();
            if (paramTypes.length > 0) {
               Annotation[][] paramAnnotations = m.getParameterAnnotations();
               for (int i = 0; i < paramTypes.length; i++) {
                  //one of them must be Parameter
                  boolean hasParameterAnnotation = false;
                  for (Annotation panno : paramAnnotations[i]) {
                     if (panno.annotationType() == Parameter.class) {
                        hasParameterAnnotation = true;
                        break;
                     }
                  }
                  assertTrue(hasParameterAnnotation, "method " + m + " has parameters with no Parameter annotation");
               }
            }
         }
      }
   }
}
