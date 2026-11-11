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
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;

public class ObjectInputStreamWithClassLoader extends ObjectInputStream {

   /**
    * Value used to indicate that all classes should be allowed or denied
    */
   public static final String CATCH_ALL_WILDCARD = "*";

   @Deprecated(forRemoval = true)
   public static final String WHITELIST_PROPERTY = "org.apache.activemq.artemis.jms.deserialization.whitelist";
   public static final String ALLOWLIST_PROPERTY = "org.apache.activemq.artemis.jms.deserialization.allowlist";
   @Deprecated(forRemoval = true)
   public static final String BLACKLIST_PROPERTY = "org.apache.activemq.artemis.jms.deserialization.blacklist";
   public static final String DENYLIST_PROPERTY = "org.apache.activemq.artemis.jms.deserialization.denylist";

   private List<String> allowList = new ArrayList<>();
   private List<String> denyList = new ArrayList<>();

   public ObjectInputStreamWithClassLoader(final InputStream in) throws IOException {
      super(in);
      addToAllowList(System.getProperty(WHITELIST_PROPERTY, null));
      addToAllowList(System.getProperty(ALLOWLIST_PROPERTY, null));

      addToDenyList(System.getProperty(BLACKLIST_PROPERTY, null));
      addToDenyList(System.getProperty(DENYLIST_PROPERTY, null));
   }

   /**
    * @return the allowList configured on this policy instance
    */
   public String getAllowList() {
      return StringUtil.joinStringList(allowList, ",");
   }

   /**
    * @return the denyList configured on this policy instance
    */
   public String getDenyList() {
      return StringUtil.joinStringList(denyList, ",");
   }

   /**
    * Replaces the currently configured allowList with a comma separated string containing the new allowList. Null or
    * empty string denotes no allowList entries, {@value #CATCH_ALL_WILDCARD} indicates that all classes are
    * allowListed.
    *
    * @param allowList the list that this policy is configured to recognize.
    */
   public void setAllowList(String allowList) {
      this.allowList = StringUtil.splitStringList(allowList, ",");
   }

   /**
    * Adds to the currently configured allowList with a comma separated string containing the additional allowList
    * entries. Null or empty string denotes no additional allowList entries.
    *
    * @param allowList the additional list entries that this policy is configured to recognize.
    */
   public void addToAllowList(String allowList) {
      this.allowList.addAll(StringUtil.splitStringList(allowList, ","));
   }

   /**
    * Replaces the currently configured denyList with a comma separated string containing the new denyList. Null or
    * empty string denotes no denylist entries, {@value #CATCH_ALL_WILDCARD} indicates that all classes are denylisted.
    *
    * @param denyList the list that this policy is configured to recognize.
    */
   public void setDenyList(String denyList) {
      this.denyList = StringUtil.splitStringList(denyList, ",");
   }

   /**
    * Adds to the currently configured denyList with a comma separated string containing the additional denyList
    * entries. Null or empty string denotes no additional denyList entries.
    *
    * @param denyList the additional list entries that this policy is configured to recognize.
    */
   public void addToDenyList(String denyList) {
      this.denyList.addAll(StringUtil.splitStringList(denyList, ","));
   }

   @Override
   protected Class resolveClass(final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
      if (!SecurityManagerShim.isSecurityManagerEnabled()) {
         return resolveClass0(desc);
      } else {
         try {
            return SecurityManagerShim.doPrivileged((PrivilegedExceptionAction<Class>) () -> resolveClass0(desc));
         } catch (PrivilegedActionException e) {
            throw unwrapException(e);
         }
      }
   }

   @Override
   protected Class resolveProxyClass(final String[] interfaces) throws IOException, ClassNotFoundException {
      if (!SecurityManagerShim.isSecurityManagerEnabled()) {
         return resolveProxyClass0(interfaces);
      } else {
         try {
            return SecurityManagerShim.doPrivileged((PrivilegedExceptionAction<Class>) () -> resolveProxyClass0(interfaces));
         } catch (PrivilegedActionException e) {
            throw unwrapException(e);
         }
      }
   }

   private Class resolveClass0(final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
      String name = desc.getName();
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      try {
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
      if (c instanceof IOException ioException) {
         throw ioException;
      } else if (c instanceof ClassNotFoundException classNotFoundException) {
         throw classNotFoundException;
      } else if (c instanceof RuntimeException runtimeException) {
         throw runtimeException;
      } else if (c instanceof Error error) {
         throw error;
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

      for (String entry : denyList) {
         if (CATCH_ALL_WILDCARD.equals(entry)) {
            return false;
         } else if (isClassOrPackageMatch(className, entry)) {
            return false;
         }
      }

      for (String entry : allowList) {
         if (CATCH_ALL_WILDCARD.equals(entry)) {
            return true;
         } else if (isClassOrPackageMatch(className, entry)) {
            return true;
         }
      }

      // Failing outright rejection or allow from above
      // reject only if the allowList is not empty.
      return allowList.isEmpty();
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
