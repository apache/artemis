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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@ExtendWith(ParameterizedTestExtension.class)
public class MaskPasswordResolvingTest extends ArtemisTestCase {

   private static final String plainPassword = "password";
   private static final String defaultMaskPassword = "defaultmasked";
   private static final String customizedCodecPassword = "secret";
   private static final String oldDefaultMaskedPassword = "oldmasked";
   private static final String oldCustomizedCodecPassword = "secret";
   private static final String oldExplicitPlainPassword = "PASSWORD";

   @Parameters(name = "mask({0})password({1})codec({2})")
   public static Collection<Object[]> params() {
      return Arrays.asList(new Object[][]{{null, plainPassword, null},
                                          {null, "ENC(3bdfd94fe8cdf710e7fefa72f809ea90)", null},
                                          {null, "ENC(momsword)", "org.apache.activemq.artemis.utils.MaskPasswordResolvingTest$SimplePasswordCodec"},
                                          {true, "662d05f5a83f9e073af6b8dc081d34aa", null},
                                          {true, "momsword", "org.apache.activemq.artemis.utils.MaskPasswordResolvingTest$SimplePasswordCodec"},
                                          {false, oldExplicitPlainPassword, null},
                                          {false, oldExplicitPlainPassword, "org.apache.activemq.artemis.utils.MaskPasswordResolvingTest$SimplePasswordCodec"}});
   }

   private Boolean maskPassword;
   private String password;
   private String codec;

   public MaskPasswordResolvingTest(Boolean maskPassword, String password, String codec) {
      this.maskPassword = maskPassword;
      this.password = password;
      this.codec = codec;
   }

   @TestTemplate
   public void testPasswordResolving() throws Exception {
      String resolved = PasswordMaskingUtil.resolveMask(maskPassword, password, codec);
      checkResult(resolved);
   }

   private void checkResult(String resolved) throws Exception {
      if (this.maskPassword == null) {
         if (PasswordMaskingUtil.isEncMasked(this.password)) {
            if (this.codec != null) {
               assertEquals(customizedCodecPassword, resolved);
            } else {
               assertEquals(defaultMaskPassword, resolved);
            }
         } else {
            assertEquals(plainPassword, resolved);
         }
      } else {
         if (this.maskPassword) {
            if (this.codec != null) {
               assertEquals(oldCustomizedCodecPassword, resolved);
            } else {
               assertEquals(oldDefaultMaskedPassword, resolved);
            }
         } else {
            assertEquals(oldExplicitPlainPassword, resolved);
         }
      }
   }

   public static class SimplePasswordCodec implements SensitiveDataCodec<String> {

      private Map<String, String> passwordBook = new HashMap<>();

      public SimplePasswordCodec() {
         passwordBook.put("momsword", "secret");
         passwordBook.put("youneverknow", "keypass");
         passwordBook.put("youcanguess", "trustpass");
      }

      @Override
      public String decode(Object mask) throws Exception {
         String password = passwordBook.get(mask);
         if (password == null) {
            throw new IllegalArgumentException("I don't know the password " + mask);
         }
         return password;
      }

      @Override
      public String encode(Object secret) throws Exception {
         return null;
      }
   }
}
