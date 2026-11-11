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
package org.apache.activemq.artemis.utils.uri;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class URISupportTest {

   @Test
   public void testBadParameterValues() throws Exception {
      try {
         URISupport.parseParameters(new URI("tcp://127.0.0.1:61616?foo=bar&KK%258K="));
         fail();
      } catch (URISyntaxException e) {
         // expected
      }

      try {
         URISupport.parseParameters(new URI("tcp://127.0.0.1:61616?bar=baz&,KK%25İK="));
         fail();
      } catch (URISyntaxException e) {
         // expected
      }

      try {
         URISupport.parseParameters(new URI("tcp://127.0.0.1:61616?KK%25-8K=&bee=boo"));
         fail();
      } catch (URISyntaxException e) {
         // expected
      }
   }

   @Test
   public void testBadParameterNames() throws Exception {
      try {
         URISupport.parseParameters(new URI("tcp://127.0.0.1:61616?foo=bar&K8K=K%258K="));
         fail();
      } catch (URISyntaxException e) {
         // expected
      }

      try {
         URISupport.parseParameters(new URI("tcp://127.0.0.1:61616?bar=baz&KKKÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆÆ=%25#¿8K="));
         fail();
      } catch (URISyntaxException e) {
         // expected
      }
   }
}
