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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Map;

import org.w3c.dom.ls.LSInput;
import org.xml.sax.SAXException;

public class XmlProvider {
   public static final String ARTEMIS_DISABLE_XXE_PROPERTY = "artemis.disableXxe";

   public static final String XINCLUDE_AWARE_PROPERTY = "XINCLUDE_AWARE";
   public static final String NAMESPACE_AWARE_PROPERTY = "NAMESPACE_AWARE";
   public static final String IGNORE_COMMENTS_PROPERTY = "IGNORE_COMMENTS";
   public static final String IGNORE_ELEMENT_CONTENT_WHITESPACE_PROPERTY = "IGNORE_ELEMENT_CONTENT_WHITESPACE";

   private static final String ACTIVEMQ_CORE_NS = "urn:activemq:core";
   private static final String ACTIVEMQ_JMS_NS = "urn:activemq:jms";

   private static final String ARTEMIS_XML_SCHEMA_SID = "xml.xsd";
   private static final String ARTEMIS_CONFIGURATION_SCHEMA_SID = "artemis-configuration.xsd";
   private static final String ARTEMIS_JMS_SCHEMA_SID = "artemis-jms.xsd";

   private static final String ARTEMIS_SCHEMA_BASE_URL = "schema/";
   private static final String ARTEMIS_XML_SCHEMA_URL = ARTEMIS_SCHEMA_BASE_URL + ARTEMIS_XML_SCHEMA_SID;
   private static final String ARTEMIS_CONFIGURATION_SCHEMA_URL = ARTEMIS_SCHEMA_BASE_URL + ARTEMIS_CONFIGURATION_SCHEMA_SID;
   private static final String ARTEMIS_JMS_SCHEMA_URL = ARTEMIS_SCHEMA_BASE_URL + ARTEMIS_JMS_SCHEMA_SID;

   private static boolean xxeEnabled = !"".equals(System.getProperty(ARTEMIS_DISABLE_XXE_PROPERTY)) &&
      !Boolean.parseBoolean(System.getProperty(ARTEMIS_DISABLE_XXE_PROPERTY, Boolean.FALSE.toString()));

   public static boolean isXxeEnabled() {
      return xxeEnabled;
   }

   public static void setXxeEnabled(boolean enabled) {
      xxeEnabled = enabled;
   }

   public static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
      return newDocumentBuilder(null, null);
   }

   public static DocumentBuilder newDocumentBuilder(Map<String, Boolean> features, Map<String, Boolean> properties) throws ParserConfigurationException {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

      if (features != null) {
         for (Map.Entry<String, Boolean> feature : features.entrySet()) {
            factory.setFeature(feature.getKey(), feature.getValue());
         }
      }

      if (properties != null) {
         for (Map.Entry<String, Boolean> property : properties.entrySet()) {
            if (XINCLUDE_AWARE_PROPERTY.equals(property.getKey())) {
               factory.setXIncludeAware(property.getValue());
            } else if (NAMESPACE_AWARE_PROPERTY.equals(property.getKey())) {
               factory.setNamespaceAware(property.getValue());
            } else if (IGNORE_COMMENTS_PROPERTY.equals(property.getKey())) {
               factory.setIgnoringComments(property.getValue());
            } else if (IGNORE_ELEMENT_CONTENT_WHITESPACE_PROPERTY.equals(property.getKey())) {
               factory.setIgnoringElementContentWhitespace(property.getValue());
            } else {
               throw new IllegalArgumentException("Property not supported: " + property.getKey());
            }
         }
      }

      if (!isXxeEnabled()) {
         factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
         factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
         factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
         factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

         factory.setXIncludeAware(false);
         factory.setExpandEntityReferences(false);
      }

      return factory.newDocumentBuilder();
   }

   public static XMLStreamReader createXMLStreamReader(InputStream inputStream) throws XMLStreamException {
      XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

      if (!isXxeEnabled()) {
         // This disables DTDs entirely for that factory
         xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
         // disable external entities
         xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
      }

      return xmlInputFactory.createXMLStreamReader(inputStream);
   }

   public static Schema newSchema(Source schema, Map<String, Boolean> features) throws SAXException {
      return newSchemaFactory(features).newSchema(schema);
   }

   private static SchemaFactory newSchemaFactory(Map<String, Boolean> features) throws SAXException {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

      if (features != null) {
         for (Map.Entry<String, Boolean> feature : features.entrySet()) {
            factory.setFeature(feature.getKey(), feature.getValue());
         }
      }

      if (!isXxeEnabled()) {
         factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
         factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

         factory.setResourceResolver((type, namespaceURI, publicId, systemId, baseURI) -> {
            if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(type) && XMLConstants.XML_NS_URI.equals(namespaceURI) && ARTEMIS_XML_SCHEMA_SID.equals(systemId)) {
               return newLSInput(publicId, systemId, baseURI, Thread.currentThread().getContextClassLoader().getResourceAsStream(ARTEMIS_XML_SCHEMA_URL));
            } else if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(type) && ACTIVEMQ_CORE_NS.equals(namespaceURI) && ARTEMIS_CONFIGURATION_SCHEMA_SID.equals(systemId)) {
               return newLSInput(publicId, systemId, baseURI, Thread.currentThread().getContextClassLoader().getResourceAsStream(ARTEMIS_CONFIGURATION_SCHEMA_URL));
            } else if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(type) && ACTIVEMQ_JMS_NS.equals(namespaceURI) && ARTEMIS_JMS_SCHEMA_SID.equals(systemId)) {
               return newLSInput(publicId, systemId, baseURI, Thread.currentThread().getContextClassLoader().getResourceAsStream(ARTEMIS_JMS_SCHEMA_URL));
            }

            return null;
         });
      }

      return factory;
   }

   public static Transformer newTransformer() throws TransformerConfigurationException {
      TransformerFactory transformerFactory = TransformerFactory.newInstance();

      if (!isXxeEnabled()) {
         transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
         transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      }

      return transformerFactory.newTransformer();
   }

   public static Validator newValidator(URL schema) throws SAXException {
      Validator validator = newSchemaFactory(null).newSchema(schema).newValidator();

      if (!isXxeEnabled()) {
         validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
         validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      }

      return validator;
   }

   private static LSInput newLSInput(String publicId, String systemId, String baseURI, InputStream byteStream) {
      return new LSInput() {
         @Override
         public Reader getCharacterStream() {
            return null;
         }

         @Override
         public void setCharacterStream(Reader reader) {

         }

         @Override
         public InputStream getByteStream() {
            return byteStream;
         }

         @Override
         public void setByteStream(InputStream inputStream) {

         }

         @Override
         public String getStringData() {
            return null;
         }

         @Override
         public void setStringData(String s) {

         }

         @Override
         public String getSystemId() {
            return systemId;
         }

         @Override
         public void setSystemId(String s) {

         }

         @Override
         public String getPublicId() {
            return publicId;
         }

         @Override
         public void setPublicId(String s) {

         }

         @Override
         public String getBaseURI() {
            return baseURI;
         }

         @Override
         public void setBaseURI(String s) {

         }

         @Override
         public String getEncoding() {
            return null;
         }

         @Override
         public void setEncoding(String s) {

         }

         @Override
         public boolean getCertifiedText() {
            return false;
         }

         @Override
         public void setCertifiedText(boolean b) {

         }
      };
   }
}
