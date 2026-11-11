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

import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class HashProcessorTest {

   private static final String USER1_PASSWORD = "password";
   private static final String USER1_HASHED_PASSWORD = "ENC(1024:973A466A489ABFDED3D4B3D181DC77F410F2FC6E87432809A46B72B294147D76:C999ECA8A85387E1FFB14E4FE5CECD17948BA80BA04318A9BE4C3E34B7FE2925F43AB6BC9DFE0D9855DA67439AEEB9850351BC4D5D3AEC6A6903C42B8EB4ED1E)";

   private static final String USER2_PASSWORD = "manager";
   private static final String USER2_HASHED_PASSWORD = "ENC(1024:48018CDB1B5925DA2CC51DBD6F7E8C5FF156C22C03C6C69720C56F8BE76A1D48:0A0F68C2C01F46D347C6C51D641291A4608EDA50A873ED122909D9134B7A757C14176F0C033F0BD3CE35B3C373D5B652650CDE5FFBBB0F286D4495CEFEEDB166)";

   private static final String USER3_PASSWORD = "artemis000";

   @Parameters(name = "{index}: testing password {0}")
   public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][] {
         {USER1_PASSWORD, USER1_HASHED_PASSWORD, true},
         {USER2_PASSWORD, USER2_HASHED_PASSWORD, true},
         {USER3_PASSWORD, USER3_PASSWORD, true},
         {USER1_PASSWORD, USER2_PASSWORD, false},
         {USER3_PASSWORD, USER2_HASHED_PASSWORD, false}
      });
   }

   private String password;
   private String storedPassword;
   private boolean match;

   public HashProcessorTest(String password, String storedPassword, boolean match) {
      this.password = password;
      this.storedPassword = storedPassword;
      this.match = match;
   }

   @TestTemplate
   public void testPasswordVerification() throws Exception {
      HashProcessor processor = PasswordMaskingUtil.getHashProcessor(storedPassword);
      boolean result = processor.compare(password.toCharArray(), storedPassword);
      assertEquals(match, result);
   }
}
