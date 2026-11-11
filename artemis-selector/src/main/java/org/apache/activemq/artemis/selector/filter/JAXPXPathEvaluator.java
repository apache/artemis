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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import org.xml.sax.InputSource;

public class JAXPXPathEvaluator implements XPathExpression.XPathEvaluator {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // this is not thread-safe https://docs.oracle.com/javase/8/docs/api/javax/xml/xpath/XPathFactory.html
   private static final XPathFactory FACTORY = XPathFactory.newInstance();

   private final String xpathExpression;
   private final XPath xpath;
   private final DocumentBuilder builder;
   private final javax.xml.xpath.XPathExpression compiledXPathExpression;

   public JAXPXPathEvaluator(String xpathExpression, DocumentBuilder builder) {
      this.xpathExpression = xpathExpression;
      this.builder = builder;
      synchronized (FACTORY) {
         this.xpath = FACTORY.newXPath();
      }

      try {
         this.compiledXPathExpression = xpath.compile(xpathExpression);
      } catch (XPathExpressionException e) {
         throw new IllegalArgumentException(e);
      }
   }

   @Override
   public boolean evaluate(Filterable m) throws FilterException {
      String stringBody = m.getBodyAs(String.class);
      if (stringBody != null) {
         return evaluate(stringBody);
      }
      return false;
   }

   protected boolean evaluate(String text) {
      return evaluate(new InputSource(new StringReader(text)));
   }

   protected boolean evaluate(InputSource inputSource) {
      try {
         synchronized (builder) {
            return ((Boolean)compiledXPathExpression.evaluate(builder.parse(inputSource), XPathConstants.BOOLEAN)).booleanValue();
         }
      } catch (Exception e) {
         logger.debug("Failed to evaluate XPath expression {}", xpathExpression, inputSource, e);
         return false;
      }
   }

   @Override
   public String toString() {
      return xpathExpression;
   }
}
