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
package org.apache.activemq.artemis.tests.unit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@ExtendWith(ParameterizedTestExtension.class)
public class AllClassesTest {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Parameters(name = "classInfo={0}")
   public static Collection getParameters() {
      List<Class> parameters = new ArrayList<>();
      ClassLoader classLoader = AllClassesTest.class.getClassLoader();

      try {
         ClassPath classPath = ClassPath.from(classLoader);
         ImmutableSet<ClassPath.ClassInfo> classInfos = classPath.getTopLevelClassesRecursive("org.apache.activemq.artemis");

         for (ClassPath.ClassInfo classInfo : classInfos) {
            if (!classInfo.getPackageName().contains("tests")) {
               try {
                  Class loadedClass = classInfo.load();
                  if (!loadedClass.isEnum() && !loadedClass.isInterface() && !Modifier.isAbstract(loadedClass.getModifiers())) {
                     parameters.add(loadedClass);
                  }
               } catch (Throwable loadThrowable) {
                  logger.debug("cannot load {} : {}", classInfo.getName(), loadThrowable);
               }
            }
         }

         parameters.sort(Comparator.comparing(Class::getName));

         return parameters;
      } catch (Exception e) {
         logger.warn("Exception on loading all classes: {}", e.toString());
      }

      return parameters;
   }

   private Class targetClass;

   public AllClassesTest(Class targetClass) {
      this.targetClass = targetClass;
   }


   @TestTemplate
   @Timeout(3)
   public void testToString() {
      Object targetInstance = null;

      try {
         targetInstance = newInstance(targetClass);
      } catch (Throwable t) {
         logger.debug("Error creating a new instance of {}: {}", targetClass.getName(), t);
      }

      assumeTrue(targetInstance != null, "Cannot create " + targetClass.getName());

      try {
         String targetOutput = targetInstance.toString();
         logger.debug("targetOutput: {}", targetOutput);
      } finally {
         if (targetInstance instanceof AutoCloseable closeable) {
            try {
               closeable.close();
            } catch (Throwable t) {
               logger.debug("Error closing the instance of {}: {}", targetClass.getName(), t);
            }
         }
      }
   }

   private Object newInstance(Class targetClass) {
      Constructor[] targetConstructors = targetClass.getDeclaredConstructors();
      Arrays.sort(targetConstructors, (c1, c2) -> c2.getParameterCount() - c1.getParameterCount());
      for (Constructor targetConstructor : targetConstructors) {
         List<Object> initArgs = new ArrayList<>();
         Parameter[] constructorParameters = targetConstructor.getParameters();

         for (Parameter constructorParameter : constructorParameters) {
            Object initArg;
            if (constructorParameter.getType().isAssignableFrom(byte.class)) {
               initArg = RandomUtil.randomByte();
            } else if (constructorParameter.getType().isAssignableFrom(byte[].class)) {
               initArg = RandomUtil.randomBytes();
            } else if (constructorParameter.getType().isAssignableFrom(boolean.class)) {
               initArg = RandomUtil.randomBoolean();
            } else if (constructorParameter.getType().isAssignableFrom(char.class)) {
               initArg = RandomUtil.randomChar();
            } else if (constructorParameter.getType().isAssignableFrom(double.class)) {
               initArg = RandomUtil.randomDouble();
            } else if (constructorParameter.getType().isAssignableFrom(float.class)) {
               initArg = RandomUtil.randomFloat();
            } else if (constructorParameter.getType().isAssignableFrom(int.class)) {
               initArg = RandomUtil.randomInt() / 1024;
            } else if (constructorParameter.getType().isAssignableFrom(long.class)) {
               initArg = RandomUtil.randomLong();
            } else if (constructorParameter.getType().isAssignableFrom(String.class)) {
               initArg = RandomUtil.randomUUIDString();
            } else {
               initArg = null;
            }
            initArgs.add(initArg);
         }

         try {
            return targetConstructor.newInstance(initArgs.toArray());
         } catch (Throwable t) {
            if (logger.isDebugEnabled()) {
               logger.debug("Cannot construct {}: {}", targetClass.getName(), t);
            }
         }
      }

      return null;
   }
}
