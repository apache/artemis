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
package org.apache.activemq.artemis.core.version.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionImplTest {


   @Test
   public void testVersionImpl() throws Exception {

      String versionName = "ACTIVEMQ";
      int majorVersion = 2;
      int minorVersion = 0;
      int microVersion = 1;
      int incrementingVersion = 10;
      int[] compatibleVersionList = {7, 8, 9, 10};
      VersionImpl version = new VersionImpl(versionName, majorVersion, minorVersion, microVersion, incrementingVersion, compatibleVersionList);

      assertEquals(versionName, version.getVersionName());
      assertEquals(majorVersion, version.getMajorVersion());
      assertEquals(minorVersion, version.getMinorVersion());
      assertEquals(microVersion, version.getMicroVersion());
      assertEquals(incrementingVersion, version.getIncrementingVersion());
   }

   @Test
   public void testEquals() throws Exception {
      VersionImpl version = new VersionImpl("ACTIVEMQ", 2, 0, 1, 10, new int[]{7, 8, 9, 10});
      VersionImpl sameVersion = new VersionImpl("ACTIVEMQ", 2, 0, 1, 10, new int[]{7, 8, 9, 10});
      VersionImpl differentVersion = new VersionImpl("ACTIVEMQ", 2, 0, 1, 11, new int[]{7, 8, 9, 10, 11});

      assertFalse(version.equals(new Object()));

      assertTrue(version.equals(version));
      assertTrue(version.equals(sameVersion));
      assertFalse(version.equals(differentVersion));
   }

   @Test
   public void testSerialize() throws Exception {
      VersionImpl version = new VersionImpl("uyiuy", 3, 7, 6, 12, new int[]{9, 10, 11, 12});
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(version);
      oos.flush();

      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bais);
      VersionImpl version2 = (VersionImpl) ois.readObject();

      assertEquals(version, version2);
   }


}
