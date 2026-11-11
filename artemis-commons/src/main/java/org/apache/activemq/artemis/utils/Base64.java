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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.activemq.artemis.logs.ActiveMQUtilLogger;

public class Base64 {

   public static String encodeObject(final Serializable serializableObject) {
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
           ObjectOutputStream oos = new ObjectOutputStream(baos)) {
         oos.writeObject(serializableObject);
         return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
      } catch (Exception e) {
         ActiveMQUtilLogger.LOGGER.failedToSerializeObject(e);
         return null;
      }
   }

   public static String encodeBytes(final byte[] s) {
      return encodeBytes(s, false);
   }

   public static String encodeBytes(final byte[] s, boolean urlSafe) {
      if (urlSafe) {
         return java.util.Base64.getUrlEncoder().encodeToString(s);
      } else {
         return java.util.Base64.getEncoder().encodeToString(s);
      }
   }

   public static byte[] decode(final String s) {
      return decode(s, false);
   }

   public static byte[] decode(final String s, boolean urlSafe) {
      if (urlSafe) {
         return java.util.Base64.getUrlDecoder().decode(s);
      } else {
         return java.util.Base64.getDecoder().decode(s);
      }
   }
}
