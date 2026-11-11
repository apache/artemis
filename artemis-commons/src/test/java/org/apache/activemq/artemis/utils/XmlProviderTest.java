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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@ExtendWith(ParameterizedTestExtension.class)
public class XmlProviderTest extends ArtemisTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static Boolean origXxeEnabled;

   protected boolean xxeEnabled;

   @Parameters(name = "xxeEnabled={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Boolean[]{true, false});
   }

   @BeforeAll
   public static void beforeAll() {
      if (origXxeEnabled == null) {
         origXxeEnabled = XmlProvider.isXxeEnabled();
      }

      logger.trace("BeforeAll - origXxeEnabled={}, isXxeEnabled={}", origXxeEnabled, XmlProvider.isXxeEnabled());
   }

   @AfterAll
   public static void afterAll() {
      logger.trace("AfterAll - origXxeEnabled={}, isXxeEnabled={} ", origXxeEnabled, XmlProvider.isXxeEnabled());
      if (origXxeEnabled != null) {
         logger.trace("AfterAll - Resetting XxeEnabled={}", origXxeEnabled);
         XmlProvider.setXxeEnabled(origXxeEnabled);
      }
   }

   @BeforeEach
   public void setUp() {
      logger.trace("Running setUp - xxeEnabled={}", xxeEnabled);
      XmlProvider.setXxeEnabled(xxeEnabled);
   }

   public XmlProviderTest(boolean xxeEnabled) {
      this.xxeEnabled = xxeEnabled;
   }

   @TestTemplate
   public void testDocument() throws Exception {
      DocumentBuilder documentBuilder = XmlProvider.newDocumentBuilder();
      Document document = documentBuilder.parse(new File(getClass().getResource("/document.xml").toURI()));
      Element documentElement = document.getDocumentElement();

      assertEquals("t:book", documentElement.getTagName());
      assertEquals(1, documentElement.getElementsByTagName("title").getLength());
   }

   @TestTemplate
   public void testDocumentWithXmlInclude() throws Exception {
      Map<String, Boolean> properties = new HashMap<>();
      properties.put(XmlProvider.XINCLUDE_AWARE_PROPERTY, true);
      properties.put(XmlProvider.NAMESPACE_AWARE_PROPERTY, true);
      DocumentBuilder documentBuilder = XmlProvider.newDocumentBuilder(null, properties);
      Document document = documentBuilder.parse(new File(XmlProviderTest.class.getResource("/document-with-xinclude.xml").toURI()));
      Element documentElement = document.getDocumentElement();

      assertEquals("t:book", documentElement.getTagName());

      if (XmlProvider.isXxeEnabled()) {
         assertEquals(1, documentElement.getElementsByTagName("title").getLength());
      } else {
         assertEquals(0, documentElement.getElementsByTagName("title").getLength());
      }
   }

   @TestTemplate
   public void testSchema() throws Exception {
      StreamSource streamSource = new StreamSource(XmlProviderTest.class.getResourceAsStream("/schema.xsd"));
      XmlProvider.newSchema(streamSource, null);
   }

   @TestTemplate
   public void testSchemaWithImport() {
      StreamSource streamSource = new StreamSource(XmlProviderTest.class.getResourceAsStream("/schema-with-import.xsd"));

      Exception newSchemaException = null;
      try {
         XmlProvider.newSchema(streamSource, null);
      } catch (Exception e) {
         newSchemaException = e;
      }

      if (XmlProvider.isXxeEnabled()) {
         assertNull(newSchemaException);
      } else {
         assertNotNull(newSchemaException);
      }
   }

   @TestTemplate
   public void testValidator() throws Exception {
      Map<String, Boolean> properties = new HashMap<>();
      properties.put(XmlProvider.NAMESPACE_AWARE_PROPERTY, true);
      DocumentBuilder documentBuilder = XmlProvider.newDocumentBuilder(null, properties);
      Document document = documentBuilder.parse(new File(getClass().getResource("/document.xml").toURI()));
      Element documentElement = document.getDocumentElement();

      Validator validator = XmlProvider.newValidator(XmlProviderTest.class.getResource("/schema.xsd"));
      validator.validate(new DOMSource(documentElement));
   }

   @TestTemplate
   public void testValidatorWithImport() throws Exception {
      Map<String, Boolean> properties = new HashMap<>();
      properties.put(XmlProvider.NAMESPACE_AWARE_PROPERTY, true);
      DocumentBuilder documentBuilder = XmlProvider.newDocumentBuilder(null, properties);
      Document document = documentBuilder.parse(new File(getClass().getResource("/document.xml").toURI()));
      Element documentElement = document.getDocumentElement();

      Exception validateException = null;
      try {
         Validator validator = XmlProvider.newValidator(XmlProviderTest.class.getResource("/schema-with-import.xsd"));
         validator.validate(new DOMSource(documentElement));
      } catch (Exception e) {
         validateException = e;
      }

      if (XmlProvider.isXxeEnabled()) {
         assertNull(validateException);
      } else {
         assertNotNull(validateException);
      }
   }
}
