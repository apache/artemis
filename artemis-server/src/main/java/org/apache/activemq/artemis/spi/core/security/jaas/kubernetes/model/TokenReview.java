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
package org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.model;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonString;
import org.apache.activemq.artemis.utils.JsonLoader;

public class TokenReview {

   private boolean authenticated;
   private User user;
   private List<String> audiences;

   public boolean isAuthenticated() {
      return authenticated;
   }

   public User getUser() {
      return user;
   }

   public String getUsername() {
      if (user == null) {
         return null;
      }
      return user.getUsername();
   }

   public List<String> getAudiences() {
      return audiences;
   }

   public static TokenReview fromJsonString(String obj) {
      JsonObject json = JsonLoader.readObject(new StringReader(obj));
      JsonObject status = json.getJsonObject("status");
      return TokenReview.fromJson(status);
   }

   private static TokenReview fromJson(JsonObject obj) {
      TokenReview t = new TokenReview();
      if (obj == null) {
         return t;
      }
      t.authenticated = obj.getBoolean("authenticated", false);
      t.user = User.fromJson(obj.getJsonObject("user"));
      t.audiences = listFromJson(obj.getJsonArray("audiences"));
      return t;
   }

   private static List<String> listFromJson(JsonArray items) {
      if (items == null) {
         return Collections.emptyList();
      }
      return Collections.unmodifiableList(items.getValuesAs(JsonString::getString));
   }

   public static class User {

      private String username;
      private String uid;
      private List<String> groups;
      private Extra extra;

      public Extra getExtra() {
         return extra;
      }

      public List<String> getGroups() {
         return groups;
      }

      public String getUid() {
         return uid;
      }

      public String getUsername() {
         return username;
      }

      public static User fromJson(JsonObject obj) {
         if (obj == null) {
            return null;
         }
         User u = new User();
         u.username = obj.getString("username", null);
         u.uid = obj.getString("uid", null);
         u.groups = listFromJson(obj.getJsonArray("groups"));
         u.extra = Extra.fromJson(obj.getJsonObject("extra"));
         return u;
      }

   }

   public static class Extra {
      private List<String> podNames;
      private List<String> podUids;

      public List<String> getPodNames() {
         return podNames;
      }

      public List<String> getPodUids() {
         return podUids;
      }

      public static Extra fromJson(JsonObject obj) {
         if (obj == null) {
            return null;
         }
         Extra e = new Extra();
         e.podNames = listFromJson(obj.getJsonArray("authentication.kubernetes.io/pod-name"));
         e.podUids = listFromJson(obj.getJsonArray("authentication.kubernetes.io/pod-uid"));
         return e;
      }
   }
}
