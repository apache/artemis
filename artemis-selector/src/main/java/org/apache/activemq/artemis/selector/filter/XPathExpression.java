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
package org.apache.activemq.artemis.selector.filter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.activemq.artemis.utils.XmlProvider;

/**
 * Used to evaluate an XPath Expression in a JMS selector.
 */
public final class XPathExpression implements BooleanExpression {

   private final String xpath;
   private final XPathEvaluator evaluator;

   private static DocumentBuilder builder;

   public static XPathEvaluatorFactory XPATH_EVALUATOR_FACTORY = null;
   public static final String DOCUMENT_BUILDER_FACTORY_FEATURE_PREFIX = "org.apache.activemq.documentBuilderFactory.feature:";

   static {
      try {
         // get features from system properties (if any)
         Map<String, Boolean> features = getFeatures();
         Map<String, Boolean> properties = new HashMap<>();
         properties.put(XmlProvider.NAMESPACE_AWARE_PROPERTY, true);
         properties.put(XmlProvider.IGNORE_COMMENTS_PROPERTY, true);
         properties.put(XmlProvider.IGNORE_ELEMENT_CONTENT_WHITESPACE_PROPERTY, true);
         builder = XmlProvider.newDocumentBuilder(features, properties);
      } catch (ParserConfigurationException e) {
         throw new RuntimeException(e);
      }

      new JAXPXPathEvaluator("//root", builder).evaluate("<root></root>");
      try {
         XPATH_EVALUATOR_FACTORY = xpath -> new JAXPXPathEvaluator(xpath, builder);
      } catch (Throwable e) {
      }
   }

   public interface XPathEvaluatorFactory {
      XPathEvaluator create(String xpath);
   }

   public interface XPathEvaluator {
      boolean evaluate(Filterable message) throws FilterException;
   }

   XPathExpression(String xpath) {
      if (XPATH_EVALUATOR_FACTORY == null) {
         throw new IllegalArgumentException("XPATH support not enabled.");
      }
      this.xpath = xpath;
      this.evaluator = XPATH_EVALUATOR_FACTORY.create(xpath);
   }

   @Override
   public Object evaluate(Filterable message) throws FilterException {
      return evaluator.evaluate(message) ? Boolean.TRUE : Boolean.FALSE;
   }

   @Override
   public String toString() {
      return "XPATH " + ConstantExpression.encodeString(xpath);
   }

   /**
    * {@return true if the expression evaluates to Boolean.TRUE}
    */
   @Override
   public boolean matches(Filterable message) throws FilterException {
      Object object = evaluate(message);
      return object == Boolean.TRUE;
   }

   protected static Map<String, Boolean> getFeatures() throws ParserConfigurationException {
      Map<String, Boolean> features = new HashMap<>();
      Properties properties = System.getProperties();
      for (Map.Entry<Object, Object> prop : properties.entrySet()) {
         String key = (String) prop.getKey();
         if (key.startsWith(DOCUMENT_BUILDER_FACTORY_FEATURE_PREFIX)) {
            Boolean value = Boolean.valueOf((String)prop.getValue());
            features.put(key.substring(DOCUMENT_BUILDER_FACTORY_FEATURE_PREFIX.length()), value);
         }
      }
      return features;
   }
}
