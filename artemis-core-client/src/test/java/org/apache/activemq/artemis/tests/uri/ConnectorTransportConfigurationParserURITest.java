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
package org.apache.activemq.artemis.tests.uri;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.List;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.uri.ConnectorTransportConfigurationParser;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorTransportConfigurationParserURITest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testParse() throws Exception {
      ConnectorTransportConfigurationParser parser = new ConnectorTransportConfigurationParser(false);

      URI transportURI = parser.expandURI("tcp://live:1#tcp://backupA:2,tcp://backupB:3");
      System.out.println(transportURI);
      List<TransportConfiguration> objects = parser.newObject(transportURI, "test");
      if (logger.isInfoEnabled()) {
         objects.forEach(t -> logger.info("transportConfig: {}", t));
      }

      assertEquals(3, objects.size());
      assertEquals("live", objects.get(0).getParams().get("host"));
      assertEquals("1", objects.get(0).getParams().get("port"));
      assertEquals("backupA", objects.get(1).getParams().get("host"));
      assertEquals("2", objects.get(1).getParams().get("port"));
      assertEquals("backupB", objects.get(2).getParams().get("host"));
      assertEquals("3", objects.get(2).getParams().get("port"));
   }

   @Test
   public void testParseMultipleConnectorWithName() throws Exception {
      ConnectorTransportConfigurationParser parser = new ConnectorTransportConfigurationParser(false);

      URI transportURI = parser.expandURI("(tcp://live:1?name=live1,tcp://backupA:2?name=backupA2,tcp://backupB:3?name=backupB3");
      System.out.println(transportURI);
      List<TransportConfiguration> objects = parser.newObject(transportURI, null);
      if (logger.isInfoEnabled()) {
         objects.forEach(t -> logger.info("transportConfig: {}", t));
      }

      assertEquals(3, objects.size());
      assertEquals("live1", objects.get(0).getName());
      assertEquals("live", objects.get(0).getParams().get("host"));
      assertEquals("1", objects.get(0).getParams().get("port"));
      assertEquals("backupA2", objects.get(1).getName());
      assertEquals("backupA", objects.get(1).getParams().get("host"));
      assertEquals("2", objects.get(1).getParams().get("port"));
      assertEquals("backupB3", objects.get(2).getName());
      assertEquals("backupB", objects.get(2).getParams().get("host"));
      assertEquals("3", objects.get(2).getParams().get("port"));
   }
}
