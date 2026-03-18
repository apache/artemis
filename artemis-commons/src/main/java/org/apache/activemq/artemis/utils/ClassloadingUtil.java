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
package org.apache.activemq.artemis.utils;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * This class will be used to perform generic class-loader operations, such as load a class first using TCCL, and then
 * the classLoader used by Apache Artemis (ClassloadingUtil.getClass().getClassLoader()).
 * <p>
 * Is't required to use a Security Block on any calls to this class.
 */
public final class ClassloadingUtil {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String INSTANTIATION_EXCEPTION_MESSAGE = "Your class must have a constructor without arguments. If it is an inner class, it must be static!";

   public static Object newInstanceFromClassLoader(final String className, Class<?> expectedType) {
      return newInstanceFromClassLoader(ClassloadingUtil.class, className, expectedType);
   }

   public static Object newInstanceFromClassLoader(final Class<?> classOwner,
                                                   final String className,
                                                   Class<?> expectedType) {
      ClassLoader loader = classOwner.getClassLoader();
      try {
         return getInstanceWithTypeCheck(className, expectedType, loader);
      } catch (Throwable t) {
         if (t instanceof InstantiationException) {
            System.out.println(INSTANTIATION_EXCEPTION_MESSAGE);
         }
         loader = Thread.currentThread().getContextClassLoader();
         if (loader == null) {
            throw new RuntimeException("No local context classloader", t);
         }
         try {
            return getInstanceWithTypeCheck(className, expectedType, loader);
         } catch (InstantiationException e) {
            throw new RuntimeException(INSTANTIATION_EXCEPTION_MESSAGE + " " + className, e);
         } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
         } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
         }
      }
   }

   public static Object getInstanceWithTypeCheck(String className,
                                   Class<?> expectedType,
                                   ClassLoader loader) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
      final Class<?> clazz = loadWithCheck(className, expectedType, loader);
      return clazz.getDeclaredConstructor().newInstance();
   }

   public static Object getInstanceForParamsWithTypeCheck(String className,
                                                 Class<?> expectedType,
                                                 ClassLoader loader,  Class<?>[] parameterTypes, Object... params) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
      final Class<?> clazz = loadWithCheck(className, expectedType, loader);
      return clazz.getDeclaredConstructor(parameterTypes).newInstance(params);
   }

   private static Class<?> loadWithCheck(String className,
                                         Class<?> expectedType,
                                         ClassLoader loader) throws ClassNotFoundException {
      Class<?> clazz = loader.loadClass(className);
      if (!expectedType.isAssignableFrom(clazz)) {
         throw new IllegalStateException("clazz [" + className + "] is not assignable from expected type: " + expectedType);
      }
      return clazz;
   }

   public static URL findResource(final String resourceName) {
      return findResource(ClassloadingUtil.class.getClassLoader(), resourceName);
   }

   public static URL findResource(ClassLoader loader, final String resourceName) {
      try {
         URL resource = loader.getResource(resourceName);
         if (resource != null) {
            return resource;
         }
      } catch (Throwable t) {
      }

      loader = Thread.currentThread().getContextClassLoader();
      if (loader == null) {
         return null;
      }

      return loader.getResource(resourceName);
   }


   public static String loadProperty(ClassLoader loader, String propertiesFile, String name) {
      Properties properties = loadProperties(loader, propertiesFile);

      return (String)properties.get(name);
   }

   public static Properties loadProperties(String propertiesFile) {
      return loadProperties(ClassloadingUtil.class.getClassLoader(), propertiesFile);
   }

   public static Properties loadProperties(ClassLoader loader, String propertiesFile) {
      Properties properties = new Properties();

      try {
         URL url = findResource(loader, propertiesFile);
         if (url != null) {
            properties.load(url.openStream());
         }
      } catch (Throwable ignored) {
         logger.warn(ignored.getMessage(), ignored);
      }
      return properties;
   }

}
