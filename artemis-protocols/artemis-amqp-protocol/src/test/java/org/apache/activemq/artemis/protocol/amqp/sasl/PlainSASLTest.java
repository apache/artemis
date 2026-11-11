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
package org.apache.activemq.artemis.protocol.amqp.sasl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class PlainSASLTest {

   @Test
   public void testPlain() {
      ClientSASLPlain plainSASL = new ClientSASLPlain("user-me", "password-secret");
      byte[] bytesResult = plainSASL.getBytes();

      ServerSASLPlain serverSASLPlain = new ServerSASLPlain();
      serverSASLPlain.processSASL(bytesResult);
      PlainSASLResult result = (PlainSASLResult) serverSASLPlain.result();
      assertEquals("user-me", result.getUser());
      assertEquals("password-secret", result.getPassword());
   }
}
