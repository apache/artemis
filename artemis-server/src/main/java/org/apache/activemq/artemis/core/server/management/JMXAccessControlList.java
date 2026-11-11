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
package org.apache.activemq.artemis.core.server.management;

import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

public class JMXAccessControlList {
   private static final String WILDCARD = "*";

   private Access defaultAccess = new Access(WILDCARD);
   private ConcurrentMap<String, TreeMap<String, Access>> domainAccess = new ConcurrentHashMap<>();
   private ConcurrentMap<String, TreeMap<String, Access>> allowList = new ConcurrentHashMap<>();
   private Comparator<String> keyComparator = (key1, key2) -> {
      boolean key1ContainsWildCard = key1.contains(WILDCARD);
      boolean key2ContainsWildcard = key2.contains(WILDCARD);
      if (key1ContainsWildCard && !key2ContainsWildcard) {
         return +1;
      } else if (!key1ContainsWildCard && key2ContainsWildcard) {
         return -1;
      } else if (key1.length() == key2.length()) {
         return key1.compareTo(key2);
      }

      return key2.length() - key1.length();
   };

   public void addToAllowList(String domain, String key) {
      TreeMap<String, Access> domainMap = new TreeMap<>(keyComparator);
      domainMap = allowList.putIfAbsent(domain, domainMap);
      if (domainMap == null) {
         domainMap = allowList.get(domain);
      }
      Access access = new Access(domain, normalizeKey(key));
      domainMap.putIfAbsent(access.getKey(), access);
   }


   public List<String> getRolesForObject(ObjectName objectName, String methodName) {
      TreeMap<String, Access> domainMap = domainAccess.get(objectName.getDomain());
      if (domainMap != null) {
         Map<String, String> keyPropertyList = objectName.getKeyPropertyList();
         for (Map.Entry<String, String> keyEntry : keyPropertyList.entrySet()) {
            String key = normalizeKey(keyEntry.getKey() + "=" + keyEntry.getValue());
            for (Access accessEntry : domainMap.values()) {
               if (accessEntry.getKeyPattern().matcher(key).matches()) {
                  return accessEntry.getMatchingRolesForMethod(methodName);
               }
            }
         }

         Access access = domainMap.get("");
         if (access != null) {
            return access.getMatchingRolesForMethod(methodName);
         }
      }

      return defaultAccess.getMatchingRolesForMethod(methodName);
   }

   public boolean isInAllowList(ObjectName objectName) {
      TreeMap<String, Access> domainMap = allowList.get(objectName.getDomain());

      if (domainMap == null) {
         domainMap = allowList.get(WILDCARD);
      }

      if (domainMap != null) {
         if (domainMap.containsKey("")) {
            return true;
         }

         Map<String, String> keyPropertyList = objectName.getKeyPropertyList();
         for (Map.Entry<String, String> keyEntry : keyPropertyList.entrySet()) {
            String key = normalizeKey(keyEntry.getKey() + "=" + keyEntry.getValue());
            for (Access accessEntry : domainMap.values()) {
               if (accessEntry.getKeyPattern().matcher(key).matches()) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public void addToDefaultAccess(String method, String... roles) {
      if (roles != null) {
         if (method.equals(WILDCARD)) {
            defaultAccess.addCatchAll(roles);
         } else if (method.endsWith(WILDCARD)) {
            String prefix = method.replace(WILDCARD, "");
            defaultAccess.addMethodsPrefixes(prefix, roles);
         } else {
            defaultAccess.addMethods(method, roles);
         }
      }
   }

   public void addToRoleAccess(String domain, String key, String method, String... roles) {
      TreeMap<String, Access> domainMap = new TreeMap<>(keyComparator);
      domainMap = domainAccess.putIfAbsent(domain, domainMap);
      if (domainMap == null) {
         domainMap = domainAccess.get(domain);
      }

      String accessKey = normalizeKey(key);
      Access access = domainMap.get(accessKey);
      if (access == null) {
         access = new Access(domain, accessKey);
         domainMap.put(accessKey, access);
      }

      if (method.equals(WILDCARD)) {
         access.addCatchAll(roles);
      } else if (method.endsWith(WILDCARD)) {
         String prefix = method.replace(WILDCARD, "");
         access.addMethodsPrefixes(prefix, roles);
      } else {
         access.addMethods(method, roles);
      }
   }

   private String normalizeKey(final String key) {
      if (key == null) {
         return "";
      } else if (key.endsWith("\"")) {
         return key.replace("\"", "");
      }
      return key;
   }

   static class Access {
      private final String id;
      private final String key;
      private final Pattern keyPattern;
      List<String> catchAllRoles = new ArrayList<>();
      Map<String, List<String>> methodRoles = new HashMap<>();
      Map<String, List<String>> methodPrefixRoles = new LinkedHashMap<>();

      Access(String id) {
         this(id, "");
      }

      Access(String id, String key) {
         this.id = id;
         this.key = key;
         this.keyPattern = Pattern.compile(key.replace(WILDCARD, ".*"));
      }

      public synchronized void addMethods(String prefix, String... roles) {
         List<String> rolesList = methodRoles.get(prefix);
         if (rolesList == null) {
            rolesList = new ArrayList<>();
            methodRoles.put(prefix, rolesList);
         }
         for (String role : roles) {
            rolesList.add(role);
         }
      }

      public synchronized void addMethodsPrefixes(String prefix, String... roles) {
         List<String> rolesList = methodPrefixRoles.get(prefix);
         if (rolesList == null) {
            rolesList = new ArrayList<>();
            methodPrefixRoles.put(prefix, rolesList);
         }
         for (String role : roles) {
            rolesList.add(role);
         }
      }

      public void addCatchAll(String... roles) {
         for (String role : roles) {
            catchAllRoles.add(role);
         }
      }

      public String getId() {
         return id;
      }

      public String getKey() {
         return key;
      }

      public Pattern getKeyPattern() {
         return keyPattern;
      }

      public List<String> getMatchingRolesForMethod(String methodName) {
         List<String> roles = methodRoles.get(methodName);
         if (roles != null) {
            return roles;
         }
         for (Map.Entry<String, List<String>> entry : methodPrefixRoles.entrySet()) {
            if (methodName.startsWith(entry.getKey())) {
               return entry.getValue();
            }
         }
         return catchAllRoles;
      }
   }

   public static JMXAccessControlList createDefaultList() {
      JMXAccessControlList accessControlList = new JMXAccessControlList();

      accessControlList.addToAllowList("hawtio", "type=*");

      accessControlList.addToRoleAccess("org.apache.activemq.artemis", null, "list*", "view", "update", "amq");
      accessControlList.addToRoleAccess("org.apache.activemq.artemis", null, "get*", "view", "update", "amq");
      accessControlList.addToRoleAccess("org.apache.activemq.artemis", null, "is*", "view", "update", "amq");
      accessControlList.addToRoleAccess("org.apache.activemq.artemis", null, "set*", "update", "amq");
      accessControlList.addToRoleAccess("org.apache.activemq.artemis", null, WILDCARD, "amq");

      accessControlList.addToDefaultAccess("list*", "view", "update", "amq");
      accessControlList.addToDefaultAccess("get*", "view", "update", "amq");
      accessControlList.addToDefaultAccess("is*", "view", "update", "amq");
      accessControlList.addToDefaultAccess("set*", "update", "amq");
      accessControlList.addToDefaultAccess(WILDCARD, "amq");

      return accessControlList;
   }
}
