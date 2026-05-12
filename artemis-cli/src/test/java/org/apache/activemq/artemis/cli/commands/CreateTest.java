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
package org.apache.activemq.artemis.cli.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.XmlProvider;
import org.apache.activemq.cli.test.CliTestBase;
import org.apache.activemq.cli.test.TestActionContext;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xml.sax.SAXException;

@ExtendWith(ParameterizedTestExtension.class)
public class CreateTest extends CliTestBase {

   private final String testName;
   private String httpHost;
   private boolean relaxJolokia;

   public CreateTest(String testName, String httpHost, boolean relaxJolokia) throws IOException {
      this.testName = testName;
      this.httpHost = httpHost;
      this.relaxJolokia = relaxJolokia;
   }

   @Parameters(name = "{0}")
   public static Collection<Object[]> testData() {
      return Arrays.asList(new Object[][]{
         {"Happy path + relaxJolokia", "sampledomain.com", true},
         {"Happy path - relaxJolokia", "sampledomain.net", false},
         {"Domain with dash + relaxJolokia", "sample-domain.co", true},
         {"Domain with dash - relaxJolokia", "sample-domain.co.uk", false},
         {"Domain with double dashes + relaxJolokia", "sample--domain.name", true},
         {"Domain with double dashes - relaxJolokia", "sample--domain.biz", false},
         {"Domain with leading dashes + relaxJolokia", "--sampledomain.company", true},
         {"Domain with leading dashes - relaxJolokia", "--sampledomain.email", false},
         {"Domain with trailing dashes + relaxJolokia", "sampledomain--.shop", true},
         {"Domain with trailing dashes - relaxJolokia", "sampledomain--.java", false},
      });
   }

   @TestTemplate
   public void testWriteJolokiaAccessXmlCreatesValidXml() throws Exception {
      TestActionContext context = new TestActionContext();
      File testInstance = new File(temporaryFolder, "test-instance");

      Create c = new Create();
      c.setNoAutoTune(true);
      c.setInstance(testInstance);
      c.setHttpHost(httpHost);
      c.setRelaxJolokia(relaxJolokia);
      c.setPassword(RandomUtil.randomUUIDString());
      c.execute(context);

      assertTrue(isXmlValid(new File(testInstance, "etc/" + Create.ETC_JOLOKIA_ACCESS_XML)));
   }

   /**
    * IsXmlValid will check if a given xml file is valid by parsing the xml to create a Document.
    * <p>
    * If it parses, the xml is assumed to be valid. If any exceptions occur, the xml is not valid.
    *
    * @param xml The xml file to check for validity.
    * @return whether the xml file represents a valid xml document
    */
   private boolean isXmlValid(File xml) {
      try {
         DocumentBuilder dbuilder = XmlProvider.newDocumentBuilder();
         dbuilder.parse(xml);

      } catch (ParserConfigurationException e) {
         return false;
      } catch (IOException e) {
         return false;
      } catch (SAXException e) {
         return false;
      }
      return true;
   }
}