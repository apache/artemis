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
package org.apache.activemq.artemis.core.security;

import java.util.Objects;

import org.apache.activemq.artemis.utils.PasswordMaskingUtil;

public class User {

   final String user;

   String password;

   public User(final String user, final String password) {
      this.user = user;
      this.password = password;
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof User other)) {
         return false;
      }

      return Objects.equals(user, other.user);
   }

   @Override
   public int hashCode() {
      return user.hashCode();
   }

   public boolean isValid(final String user, final String password) {
      if (user == null) {
         return false;
      }

      return this.user.equals(user) && PasswordMaskingUtil.getHashProcessor(this.password).compare(password != null ? password.toCharArray() : null, this.password);
   }

   public String getUser() {
      return user;
   }

   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }
}
