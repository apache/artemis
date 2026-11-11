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
package org.apache.activemq.artemis.tests.unit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.junit.jupiter.api.Test;

public class VersionLoaderTest extends ActiveMQTestBase {



   @Test
   public void testLoadVersion() throws Exception {
      Version version = VersionLoader.getVersion();
      Properties props = new Properties();
      props.load(ClassLoader.getSystemResourceAsStream(VersionLoader.DEFAULT_PROP_FILE_NAME));

      assertEquals(props.get("activemq.version.versionName"), version.getVersionName());

      assertEquals(Integer.parseInt(props.getProperty("activemq.version.majorVersion")), version.getMajorVersion());
      assertEquals(Integer.parseInt(props.getProperty("activemq.version.minorVersion")), version.getMinorVersion());
      assertEquals(Integer.parseInt(props.getProperty("activemq.version.microVersion")), version.getMicroVersion());
      assertEquals(Integer.parseInt(new StringTokenizer(props.getProperty("activemq.version.incrementingVersion"), ",").nextToken()), version.getIncrementingVersion());
   }

}
