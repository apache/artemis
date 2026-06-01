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

package org.apache.artemis.jsonschema.enrichment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.artemis.jsonschema.config.SchemaGeneratorConfig;
import org.apache.artemis.jsonschema.ir.PropertyDescriptor;
import org.apache.artemis.jsonschema.ir.PropertyMetadata;
import org.apache.artemis.jsonschema.ir.PropertySource;
import org.apache.artemis.jsonschema.ir.SchemaType;
import org.w3c.dom.*;

/**
 * Extracts metadata from artemis-configuration.xsd schema using DOM parsing.
 *
 * <p>Extracts the following metadata:
 *
 * <ul>
 *   <li>Element documentation (xsd:documentation tags)
 *   <li>Type constraints (from default attributes — types only, not values)
 *   <li>Type constraints (xsd:int, xsd:boolean, etc.)
 *   <li>Enumerations (xsd:restriction with xsd:enumeration)
 *   <li>Min/max constraints (xsd:minInclusive, xsd:maxInclusive)
 *   <li>ComplexType nested elements (divertType, federationType, etc.)
 * </ul>
 */
public class XsdExtractor implements Extractor {

   private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
   private static final Map<String, String> COMPLEX_TYPE_TO_PATTERN =
         new LinkedHashMap<>(SchemaGeneratorConfig.load().getXsdComplexTypeToPathPattern());

   /** {@inheritDoc} */
   @Override
   public List<PropertyDescriptor> extract(Path artemisRoot) throws ExtractionException {
      try {
         Path xsdPath = artemisRoot.resolve(SchemaGeneratorConfig.load().getXsdPath());
         if (!Files.exists(xsdPath)) {
            throw new ExtractionException(
                  getName(), "artemis-configuration.xsd not found at: " + xsdPath);
         }

         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         factory.setNamespaceAware(true);
         DocumentBuilder builder = factory.newDocumentBuilder();
         Document doc = builder.parse(xsdPath.toFile());

         List<PropertyDescriptor> descriptors = new ArrayList<>();

         // Extract from the root configurationType complexType — these map to top-level
         // broker properties (parentPath=""). We target this specific complexType rather
         // than using getElementsByTagNameNS("element") which would also return elements
         // nested inside OTHER complexTypes (e.g. groupingHandlerType's "type" element),
         // contaminating root-level paths with wrong enrichments.
         Element schemaRoot = doc.getDocumentElement();
         NodeList schemaChildren = schemaRoot.getChildNodes();
         for (int i = 0; i < schemaChildren.getLength(); i++) {
            if (schemaChildren.item(i) instanceof Element child
                  && XSD_NS.equals(child.getNamespaceURI())) {
               if ("complexType".equals(child.getLocalName())
                     && "configurationType".equals(child.getAttribute("name"))) {
                  // Root configurationType — extract its elements with empty prefix
                  extractNestedElements(child, "", descriptors);
               } else if ("element".equals(child.getLocalName())) {
                  // Direct top-level element (e.g. <xsd:element name="core">)
                  extractElement(child, "", descriptors);
               }
            }
         }

         // Extract from named complexTypes (path-qualified via xsdComplexTypeToPathPattern)
         for (int i = 0; i < schemaChildren.getLength(); i++) {
            if (schemaChildren.item(i) instanceof Element child
                  && "complexType".equals(child.getLocalName())
                  && XSD_NS.equals(child.getNamespaceURI())) {
               extractComplexType(child, descriptors);
            }
         }

         return descriptors;
      } catch (ExtractionException e) {
         throw e; // Re-throw without wrapping
      } catch (Exception e) {
         throw new ExtractionException(getName(), "Failed to parse XSD", e);
      }
   }

   /**
    * Extract property metadata from a single XSD element definition.
    *
    * @param element the {@code xsd:element} DOM node
    * @param parentPath dot-separated parent path prefix (empty for top-level)
    * @param descriptors accumulator for extracted descriptors
    */
   private void extractElement(
         Element element, String parentPath, List<PropertyDescriptor> descriptors) {
      String name = element.getAttribute("name");
      if (name.isEmpty()) {
         return;
      }

      // Convert kebab-case to camelCase (maxDiskUsage → maxDiskUsage)
      String propName = kebabToCamel(name);
      String currentPath = parentPath.isEmpty() ? propName : parentPath + "." + propName;

      PropertyDescriptor descriptor = new PropertyDescriptor(currentPath, PropertySource.XSD);
      PropertyMetadata metadata = descriptor.getMetadata();

      // Extract documentation
      String doc = extractDocumentation(element);
      if (doc != null) {
         metadata.setDescription(doc);
      }

      // Extract enums and constraints from inline simpleType
      NodeList simpleTypes = element.getElementsByTagNameNS(XSD_NS, "simpleType");
      if (simpleTypes.getLength() > 0) {
         extractSimpleTypeConstraints((Element) simpleTypes.item(0), metadata);
      }

      descriptors.add(descriptor);

      // Recurse into inline complexType
      NodeList inlineComplexTypes = element.getElementsByTagNameNS(XSD_NS, "complexType");
      if (inlineComplexTypes.getLength() > 0) {
         extractNestedElements((Element) inlineComplexTypes.item(0), currentPath, descriptors);
      }
   }

   /**
    * Extract nested element metadata from a named XSD complexType. Only processes types listed in
    * the config's {@code xsdComplexTypeToPathPattern} map.
    *
    * @param complexType the {@code xsd:complexType} DOM node
    * @param descriptors accumulator for extracted descriptors
    */
   private void extractComplexType(Element complexType, List<PropertyDescriptor> descriptors) {
      String typeName = complexType.getAttribute("name");
      if (typeName.isEmpty()) {
         return;
      }

      // Map complexType to path pattern
      String pathPrefix = COMPLEX_TYPE_TO_PATTERN.get(typeName);
      if (pathPrefix == null) {
         return; // Not a recognized complexType
      }

      // Extract nested elements
      extractNestedElements(complexType, pathPrefix, descriptors);
   }

   /**
    * Recurse into XSD container elements (all/sequence/choice) and attributes within a complexType.
    *
    * @param complexType the complexType DOM node containing the containers
    * @param parentPath dot-separated parent path prefix for child elements
    * @param descriptors accumulator for extracted descriptors
    */
   private void extractNestedElements(
         Element complexType, String parentPath, List<PropertyDescriptor> descriptors) {
      // Find direct child container elements (all, sequence, choice).
      // Only process direct child elements of the container — NOT deep descendants,
      // which would include elements inside inline complexTypes of nested elements.
      String[] containers = {"all", "sequence", "choice"};
      for (String containerName : containers) {
         NodeList children = complexType.getChildNodes();
         for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element child
                  && containerName.equals(child.getLocalName())
                  && XSD_NS.equals(child.getNamespaceURI()))) {
               continue;
            }
            // Iterate direct child elements of this container only
            NodeList containerChildren = child.getChildNodes();
            for (int j = 0; j < containerChildren.getLength(); j++) {
               if (containerChildren.item(j) instanceof Element elem
                     && "element".equals(elem.getLocalName())
                     && XSD_NS.equals(elem.getNamespaceURI())) {
                  extractElement(elem, parentPath, descriptors);
               }
            }
         }
      }

      // Extract direct child attributes (XML attributes become properties too).
      // Only direct children — not attributes inside nested inline complexTypes.
      NodeList attrChildren = complexType.getChildNodes();
      for (int i = 0; i < attrChildren.getLength(); i++) {
         if (attrChildren.item(i) instanceof Element attrElem
               && "attribute".equals(attrElem.getLocalName())
               && XSD_NS.equals(attrElem.getNamespaceURI())) {
            extractAttribute(attrElem, parentPath, descriptors);
         }
      }
   }

   /**
    * Extract metadata from an XSD attribute (XML attributes become properties). Example: {@code
    * <xsd:attribute name="auto-start" type="xsd:boolean" default="true"/>} Maps to:
    * AMQPConnections.*.autoStart
    *
    * @param attribute the {@code xsd:attribute} DOM node
    * @param parentPath dot-separated parent path prefix
    * @param descriptors accumulator for extracted descriptors
    */
   private void extractAttribute(
         Element attribute, String parentPath, List<PropertyDescriptor> descriptors) {
      String attrName = attribute.getAttribute("name");
      if (attrName.isEmpty()) {
         return;
      }

      // Convert XML attribute name to Java property name (kebab-case to camelCase)
      String propertyName = kebabToCamel(attrName);
      String currentPath = parentPath.isEmpty() ? propertyName : parentPath + "." + propertyName;

      PropertyDescriptor descriptor = new PropertyDescriptor(currentPath, PropertySource.XSD);
      PropertyMetadata metadata = descriptor.getMetadata();

      // Extract documentation
      String doc = extractDocumentation(attribute);
      if (doc != null) {
         metadata.setDescription(doc);
      }

      // Extract use="required" → mark as required
      String use = attribute.getAttribute("use");
      if ("required".equals(use)) {
         metadata.setRequired(true);
      }

      descriptors.add(descriptor);
   }

   /**
    * Extract enum, min/max constraints from an inline {@code xsd:simpleType} restriction.
    *
    * @param simpleType the {@code xsd:simpleType} DOM node
    * @param metadata target metadata to populate with enum values and numeric bounds
    */
   private void extractSimpleTypeConstraints(Element simpleType, PropertyMetadata metadata) {
      NodeList restrictions = simpleType.getElementsByTagNameNS(XSD_NS, "restriction");
      if (restrictions.getLength() == 0) {
         return;
      }

      Element restriction = (Element) restrictions.item(0);

      // Extract enums
      NodeList enumElements = restriction.getElementsByTagNameNS(XSD_NS, "enumeration");
      if (enumElements.getLength() > 0) {
         List<String> enumValues = new ArrayList<>();
         for (int i = 0; i < enumElements.getLength(); i++) {
            Element enumElement = (Element) enumElements.item(i);
            String value = enumElement.getAttribute("value");
            if (!value.isEmpty()) {
               enumValues.add(value);
            }
         }
         metadata.setEnumValues(enumValues);
         metadata.setType(new SchemaType(SchemaType.Kind.STRING)); // Enums are strings
      }

      // Extract min/max
      NodeList minInclusiveNodes = restriction.getElementsByTagNameNS(XSD_NS, "minInclusive");
      if (minInclusiveNodes.getLength() > 0) {
         String minValue = ((Element) minInclusiveNodes.item(0)).getAttribute("value");
         try {
            metadata.setMinimum(Integer.parseInt(minValue));
         } catch (NumberFormatException e) {
            // Ignore
         }
      }

      NodeList maxInclusiveNodes = restriction.getElementsByTagNameNS(XSD_NS, "maxInclusive");
      if (maxInclusiveNodes.getLength() > 0) {
         String maxValue = ((Element) maxInclusiveNodes.item(0)).getAttribute("value");
         try {
            metadata.setMaximum(Integer.parseInt(maxValue));
         } catch (NumberFormatException e) {
            // Ignore
         }
      }
   }

   /**
    * Extract the {@code xsd:documentation} text from an element's annotation, if present.
    *
    * @param element the XSD element or attribute that may contain an annotation
    * @return trimmed documentation text, or {@code null} if absent
    */
   private String extractDocumentation(Element element) {
      NodeList annotations = element.getElementsByTagNameNS(XSD_NS, "annotation");
      if (annotations.getLength() == 0) {
         return null;
      }

      Element annotation = (Element) annotations.item(0);
      NodeList docs = annotation.getElementsByTagNameNS(XSD_NS, "documentation");
      if (docs.getLength() == 0) {
         return null;
      }

      Element documentation = (Element) docs.item(0);
      String text = documentation.getTextContent();
      if (text != null) {
         // Clean up whitespace
         text = text.trim().replaceAll("\\s+", " ");
      }
      return text;
   }

   /**
    * Convert a kebab-case XSD name to camelCase Java property name.
    *
    * @param kebab kebab-case string (e.g. "max-disk-usage")
    * @return camelCase equivalent (e.g. "maxDiskUsage"), or the input unchanged if no hyphens
    */
   private String kebabToCamel(String kebab) {
      if (!kebab.contains("-")) {
         return kebab;
      }

      String[] parts = kebab.split("-");
      StringBuilder sb = new StringBuilder(parts[0]);
      for (int i = 1; i < parts.length; i++) {
         sb.append(parts[i].substring(0, 1).toUpperCase());
         sb.append(parts[i].substring(1));
      }
      return sb.toString();
   }

   /**
    * Map an XSD type name to its JSON Schema equivalent.
    *
    * @param xsdType XSD type string, optionally namespace-prefixed (e.g. "xsd:int")
    * @return JSON Schema type string ("string", "integer", "boolean", or "number")
    */
   private String xsdTypeToJsonType(String xsdType) {
      // Strip namespace prefix if present
      if (xsdType.contains(":")) {
         xsdType = xsdType.substring(xsdType.indexOf(":") + 1);
      }

      switch (xsdType) {
         case "string":
            return "string";
         case "int":
         case "integer":
         case "long":
            return "integer";
         case "boolean":
            return "boolean";
         case "double":
         case "float":
            return "number";
         default:
            return "string";
      }
   }

   @Override
   public String getName() {
      return "XsdExtractor";
   }
}
