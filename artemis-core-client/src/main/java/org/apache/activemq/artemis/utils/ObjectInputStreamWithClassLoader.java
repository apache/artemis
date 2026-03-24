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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;

public class ObjectInputStreamWithClassLoader extends ObjectInputStream {


   /**
    * Value used to indicate that all classes should be white or black listed,
    */
   public static final String CATCH_ALL_WILDCARD = "*";

   public static final String WHITELIST_PROPERTY = "org.apache.activemq.artemis.jms.deserialization.whitelist";
   public static final String BLACKLIST_PROPERTY = "org.apache.activemq.artemis.jms.deserialization.blacklist";


   private List<String> whiteList = new ArrayList<>();
   private List<String> blackList = new ArrayList<>();


   public ObjectInputStreamWithClassLoader(final InputStream in) throws IOException {
      super(in);
      String whiteList = System.getProperty(WHITELIST_PROPERTY, null);
      setWhiteList(whiteList);

      String blackList = System.getProperty(BLACKLIST_PROPERTY, null);
      setBlackList(blackList);
   }

   /**
    * @return the whiteList configured on this policy instance.
    */
   public String getWhiteList() {
      return StringUtil.joinStringList(whiteList, ",");
   }

   /**
    * @return the blackList configured on this policy instance.
    */
   public String getBlackList() {
      return StringUtil.joinStringList(blackList, ",");
   }

   /**
    * Replaces the currently configured whiteList with a comma separated
    * string containing the new whiteList. Null or empty string denotes
    * no whiteList entries, {@value #CATCH_ALL_WILDCARD} indicates that
    * all classes are whiteListed.
    *
    * @param whiteList the whiteList that this policy is configured to recognize.
    */
   public void setWhiteList(String whiteList) {
      this.whiteList = StringUtil.splitStringList(whiteList, ",");
   }

   /**
    * Replaces the currently configured blackList with a comma separated
    * string containing the new blackList. Null or empty string denotes
    * no blacklist entries, {@value #CATCH_ALL_WILDCARD} indicates that
    * all classes are blacklisted.
    *
    * @param blackList the blackList that this policy is configured to recognize.
    */
   public void setBlackList(String blackList) {
      this.blackList = StringUtil.splitStringList(blackList, ",");
   }

   @Override
   protected Class resolveClass(final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
      if (System.getSecurityManager() == null) {
         return resolveClass0(desc);
      } else {
         try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Class>() {
               @Override
               public Class run() throws Exception {
                  return resolveClass0(desc);
               }
            });
         } catch (PrivilegedActionException e) {
            throw unwrapException(e);
         }
      }
   }

   @Override
   protected Class resolveProxyClass(final String[] interfaces) throws IOException, ClassNotFoundException {
      if (System.getSecurityManager() == null) {
         return resolveProxyClass0(interfaces);
      } else {
         try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Class>() {
               @Override
               public Class run() throws Exception {
                  return resolveProxyClass0(interfaces);
               }
            });
         } catch (PrivilegedActionException e) {
            throw unwrapException(e);
         }
      }
   }


   private Class resolveClass0(final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
      String name = desc.getName();
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      try {
         // HORNETQ-747 https://issues.jboss.org/browse/HORNETQ-747
         // Use Class.forName instead of ClassLoader.loadClass to avoid issues with loading arrays
         Class clazz = Class.forName(name, false, loader);
         // sanity check only.. if a classLoader can't find a clazz, it will throw an exception
         if (clazz == null) {
            clazz = super.resolveClass(desc);
         }

         return checkSecurity(clazz);
      } catch (ClassNotFoundException e) {
         return checkSecurity(super.resolveClass(desc));
      }
   }

   private Class resolveProxyClass0(String[] interfaces) throws IOException, ClassNotFoundException {
      ClassLoader latestLoader = Thread.currentThread().getContextClassLoader();
      ClassLoader nonPublicLoader = null;
      boolean hasNonPublicInterface = false;
      // define proxy in class loader of non-public interface(s), if any
      Class[] classObjs = new Class[interfaces.length];
      for (int i = 0; i < interfaces.length; i++) {
         Class cl = Class.forName(interfaces[i], false, latestLoader);
         if ((cl.getModifiers() & Modifier.PUBLIC) == 0) {
            if (hasNonPublicInterface) {
               if (nonPublicLoader != cl.getClassLoader()) {
                  throw new IllegalAccessError("conflicting non-public interface class loaders");
               }
            } else {
               nonPublicLoader = cl.getClassLoader();
               hasNonPublicInterface = true;
            }
         }
         classObjs[i] = cl;
      }
      try {
         return checkSecurity(Proxy.getProxyClass(hasNonPublicInterface ? nonPublicLoader : latestLoader, classObjs));
      } catch (IllegalArgumentException e) {
         throw new ClassNotFoundException(null, e);
      }
   }

   private RuntimeException unwrapException(PrivilegedActionException e) throws IOException, ClassNotFoundException {
      Throwable c = e.getCause();
      if (c instanceof IOException) {
         throw (IOException) c;
      } else if (c instanceof ClassNotFoundException) {
         throw (ClassNotFoundException) c;
      } else if (c instanceof RuntimeException) {
         throw (RuntimeException) c;
      } else if (c instanceof Error) {
         throw (Error) c;
      } else {
         throw new RuntimeException(c);
      }
   }

   private Class<?> checkSecurity(Class<?> clazz) throws ClassNotFoundException {
      Class<?> target = clazz;

      while (target.isArray()) {
         target = target.getComponentType();
      }

      while (target.isAnonymousClass() || target.isLocalClass()) {
         target = target.getEnclosingClass();
      }

      if (!target.isPrimitive()) {
         if (!isTrustedType(target)) {
            throw new ClassNotFoundException("Forbidden " + clazz + "! " +
                                                "This class is not trusted to be deserialized under the current configuration. " +
                                                "Please refer to the documentation for more information on how to configure trusted classes.");
         }
      }

      return clazz;
   }

   private boolean isTrustedType(Class<?> clazz) {
      if (clazz == null) {
         return true;
      }

      String className = clazz.getCanonicalName();
      if (className == null) {
         // Shouldn't happen as we pre-processed things, but just in case..
         className = clazz.getName();
      }

      for (String blackListEntry : blackList) {
         if (CATCH_ALL_WILDCARD.equals(blackListEntry)) {
            return false;
         } else if (isClassOrPackageMatch(className, blackListEntry)) {
            return false;
         }
      }

      for (String whiteListEntry : whiteList) {
         if (CATCH_ALL_WILDCARD.equals(whiteListEntry)) {
            return true;
         } else if (isClassOrPackageMatch(className, whiteListEntry)) {
            return true;
         }
      }

      // Failing outright rejection or allow from above
      // reject only if the whiteList is not empty.
      return whiteList.size() == 0;
   }

   private boolean isClassOrPackageMatch(String className, String listEntry) {
      if (className == null) {
         return false;
      }

      // Check if class is an exact match of the entry
      if (className.equals(listEntry)) {
         return true;
      }

      // Check if class is from a [sub-]package matching the entry
      int entryLength = listEntry.length();
      if (className.length() > entryLength && className.startsWith(listEntry) && '.' == className.charAt(entryLength)) {
         return true;
      }

      return false;
   }

}
