/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.tests.unit;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import org.apache.activemq.artemis.tests.util.RandomUtil;
import org.jboss.logging.Logger;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(value = Parameterized.class)
public class AllClassesTest {
   private static final Logger log = Logger.getLogger(AllClassesTest.class);

   private static ClassLoader classLoader = AllClassesTest.class.getClassLoader();

   @Parameterized.Parameters(name = "classInfo={0}")
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
                  log.debug("cannot load " + classInfo.getName() + ": " + loadThrowable);
               }
            }
         }
         return parameters;
      } catch (Exception e) {
         log.warn("Exception on loading all classes: " + e);
      }

      return parameters;
   }

   private Class targetClass;

   public AllClassesTest(Class targetClass) {
      this.targetClass = targetClass;
   }


   @Test
   public void testToString() {
      Object targetInstance = newInstance(targetClass);
      Assume.assumeTrue("cannot create " + targetClass.getName(), targetInstance != null);

      String targetOutput = targetInstance.toString();
      log.debug("targetOutput: " + targetOutput);
   }

   private Object newInstance(Class targetClass) {

      Constructor[] targetConstructors = targetClass.getDeclaredConstructors();
      Arrays.sort(targetConstructors, (c1, c2) -> c2.getParameterCount() - c1.getParameterCount());
      for (Constructor targetConstructor : targetConstructors) {
         List<Object> initArgs = new ArrayList<>();
         Parameter[] constructorParameters = targetConstructor.getParameters();

         for (Parameter constructorParameter : constructorParameters) {
            Object initArg = null;
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
               initArg = RandomUtil.randomString();
            } else {
               initArg = null;
            }
            initArgs.add(initArg);
         }

         try {
            return targetConstructor.newInstance(initArgs.toArray());
         } catch (Throwable createThrowable) {
            log.debug("cannot construct " + targetClass.getName() + ": " + createThrowable);
         }
      }

      try {
         return Mockito.spy(targetClass);
      } catch (Throwable spyThrowable) {
         log.debug("cannot spy " + targetClass.getName() + ": " + spyThrowable);
      }

      return null;
   }
}
