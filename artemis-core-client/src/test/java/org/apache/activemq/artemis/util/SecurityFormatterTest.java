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
package org.apache.activemq.artemis.util;

import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.SecurityFormatter;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.core.security.Role.BROWSE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CONSUME_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CREATE_ADDRESS_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CREATE_DURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CREATE_NONDURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.DELETE_ADDRESS_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.DELETE_DURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.DELETE_NONDURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.EDIT_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.MANAGE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.SEND_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.VIEW_PERMISSION;
import static org.apache.activemq.artemis.utils.RandomUtil.randomAlphaNumericString;
import static org.apache.activemq.artemis.utils.RandomUtil.randomBoolean;
import static org.apache.activemq.artemis.utils.RandomUtil.randomPositiveInt;
import static org.apache.activemq.artemis.utils.RandomUtil.randomWords;
import static org.apache.activemq.artemis.utils.SecurityFormatter.fromJSON;
import static org.apache.activemq.artemis.utils.SecurityFormatter.getListOfRoles;
import static org.apache.activemq.artemis.utils.SecurityFormatter.toJSON;
import static org.apache.activemq.artemis.utils.SecurityFormatter.toListOfRoles;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SecurityFormatterTest {

   @Test
   public void testToJsonFromSet() {
      Set<Role> originalRoles = new HashSet<>();

      // create several test roles
      for (int i = 0; i < 10; i++) {
         boolean[] booleans = new boolean[12];
         boolean atLeastOneTrue = false;

         // generate a set of permissions that has at least one true value
         while (!atLeastOneTrue) {
            for (int j = 0; j < booleans.length; j++) {
               booleans[j] = randomBoolean();
               if (booleans[j]) {
                  atLeastOneTrue = true;
               }
            }
         }
         originalRoles.add(new Role("role-" + randomAlphaNumericString(6),
                                    booleans[0],
                                    booleans[1],
                                    booleans[2],
                                    booleans[3],
                                    booleans[4],
                                    booleans[5],
                                    booleans[6],
                                    booleans[7],
                                    booleans[8],
                                    booleans[9],
                                    booleans[10],
                                    booleans[11]));
      }

      assertEquals(originalRoles, fromJSON(JsonUtil.readJsonObject(toJSON(originalRoles))));
   }

   @Test
   public void testToJsonFromStrings() {
      String[] roles = new String[12];
      for (int i = 0; i < roles.length; i++) {
         roles[i] = String.join(", ", randomWords(randomPositiveInt() % 5 + 1));
      }

      String json = toJSON(roles[0],
                           roles[1],
                           roles[2],
                           roles[3],
                           roles[4],
                           roles[5],
                           roles[6],
                           roles[7],
                           roles[8],
                           roles[9],
                           roles[10],
                           roles[11]);

      JsonObject o = JsonLoader.readObject(new StringReader(json));
      assertEquals(toListOfRoles(roles[0]), getListOfRoles(o, SEND_PERMISSION));
      assertEquals(toListOfRoles(roles[1]), getListOfRoles(o, CONSUME_PERMISSION));
      assertEquals(toListOfRoles(roles[2]), getListOfRoles(o, CREATE_DURABLE_QUEUE_PERMISSION));
      assertEquals(toListOfRoles(roles[3]), getListOfRoles(o, DELETE_DURABLE_QUEUE_PERMISSION));
      assertEquals(toListOfRoles(roles[4]), getListOfRoles(o, CREATE_NONDURABLE_QUEUE_PERMISSION));
      assertEquals(toListOfRoles(roles[5]), getListOfRoles(o, DELETE_NONDURABLE_QUEUE_PERMISSION));
      assertEquals(toListOfRoles(roles[6]), getListOfRoles(o, MANAGE_PERMISSION));
      assertEquals(toListOfRoles(roles[7]), getListOfRoles(o, BROWSE_PERMISSION));
      assertEquals(toListOfRoles(roles[8]), getListOfRoles(o, CREATE_ADDRESS_PERMISSION));
      assertEquals(toListOfRoles(roles[9]), getListOfRoles(o, DELETE_ADDRESS_PERMISSION));
      assertEquals(toListOfRoles(roles[10]), getListOfRoles(o, VIEW_PERMISSION));
      assertEquals(toListOfRoles(roles[11]), getListOfRoles(o, EDIT_PERMISSION));
   }

   @Test
   public void testToJsonVarious() {
      final String emptyJson = """
                      {"send":[],"consume":[],"createDurableQueue":[],"deleteDurableQueue":[],"createNonDurableQueue":[],"deleteNonDurableQueue":[],"manage":[],"browse":[],"createAddress":[],"deleteAddress":[],"view":[],"edit":[]}""";
      assertEquals(emptyJson,
                   toJSON("",
                          "",
                          "",
                          "",
                          "",
                          "",
                          "",
                          "",
                          "",
                          "",
                          "",
                          ""));
      assertEquals(emptyJson,
                   toJSON(null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null));
   }

   @Test
   public void testGetListOfRoles() {
      String json = """
       {
         "send": ["a"],
         "consume": ["b", "c"],
         "createDurableQueue": ["d", "e", "f"],
         "deleteDurableQueue": []
       }""";
      JsonObject o = JsonLoader.readObject(new StringReader(json));
      assertEquals(List.of("a"), SecurityFormatter.getListOfRoles(o, SEND_PERMISSION));
      assertEquals(List.of("b", "c"), SecurityFormatter.getListOfRoles(o, CONSUME_PERMISSION));
      assertEquals(List.of("d", "e", "f"), SecurityFormatter.getListOfRoles(o, CREATE_DURABLE_QUEUE_PERMISSION));
      assertEquals(Collections.emptyList(), SecurityFormatter.getListOfRoles(o, DELETE_DURABLE_QUEUE_PERMISSION));

      // test something that doesn't exist
      assertEquals(Collections.emptyList(), SecurityFormatter.getListOfRoles(o, randomAlphaNumericString(4)));
   }

   @Test
   public void testToListOfRoles() {
      assertEquals(Collections.emptyList(), toListOfRoles(""));
      assertEquals(Collections.emptyList(), toListOfRoles(null));

      assertEquals(List.of("a"), toListOfRoles("a"));
      assertEquals(List.of("a"), toListOfRoles(" a"));
      assertEquals(List.of("a"), toListOfRoles("a "));
      assertEquals(List.of("a"), toListOfRoles(" a "));
      assertEquals(List.of("a", "b"), toListOfRoles("a,b"));
      assertEquals(List.of("a", "b"), toListOfRoles("a, b"));
      assertEquals(List.of("a", "b"), toListOfRoles(" a,  b "));
   }
}
