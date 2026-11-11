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
package org.apache.activemq.artemis.cli.commands.util;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.utils.HashProcessor;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;

public class HashUtil {

   private static final HashProcessor HASH_PROCESSOR = PasswordMaskingUtil.getHashProcessor();

   //calculate the hash for plaintext.
   //any exception will cause plaintext returned unchanged.
   public static String tryHash(ActionContext context, String plaintext) {

      try {
         String hash = HASH_PROCESSOR.hash(plaintext);
         return hash;
      } catch (Exception e) {
         context.err.println("Warning: Failed to calculate hash value for password using " + HASH_PROCESSOR);
         context.err.println("Reason: " + e.getMessage());
         e.printStackTrace();
      }
      return plaintext;
   }
}
