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
package org.apache.activemq.artemis.selector.impl;

import java.io.StringReader;

import org.apache.activemq.artemis.selector.filter.BooleanExpression;
import org.apache.activemq.artemis.selector.filter.ComparisonExpression;
import org.apache.activemq.artemis.selector.filter.FilterException;
import org.apache.activemq.artemis.selector.hyphenated.HyphenatedParser;
import org.apache.activemq.artemis.selector.strict.StrictParser;

public class SelectorParser {

   private static final String CONVERT_STRING_EXPRESSIONS_PREFIX = "convert_string_expressions:";
   private static final String HYPHENATED_PROPS_PREFIX = "hyphenated_props:";
   private static final String NO_CONVERT_STRING_EXPRESSIONS_PREFIX = "no_convert_string_expressions:";
   private static final String NO_HYPHENATED_PROPS_PREFIX = "no_hyphenated_props:";

   public static BooleanExpression parse(String sql) throws FilterException {
      String actual = sql;
      boolean convertStringExpressions = false;
      boolean hyphenatedProps = false;
      while (true) {
         if (actual.startsWith(CONVERT_STRING_EXPRESSIONS_PREFIX)) {
            convertStringExpressions = true;
            actual = actual.substring(CONVERT_STRING_EXPRESSIONS_PREFIX.length());
            continue;
         }
         if (actual.startsWith(HYPHENATED_PROPS_PREFIX)) {
            hyphenatedProps = true;
            actual = actual.substring(HYPHENATED_PROPS_PREFIX.length());
            continue;
         }
         if (actual.startsWith(NO_CONVERT_STRING_EXPRESSIONS_PREFIX)) {
            convertStringExpressions = false;
            actual = actual.substring(NO_CONVERT_STRING_EXPRESSIONS_PREFIX.length());
            continue;
         }
         if (actual.startsWith(NO_HYPHENATED_PROPS_PREFIX)) {
            hyphenatedProps = false;
            actual = actual.substring(NO_HYPHENATED_PROPS_PREFIX.length());
            continue;
         }
         break;
      }

      if (convertStringExpressions) {
         ComparisonExpression.CONVERT_STRING_EXPRESSIONS.set(true);
      }
      try {
         BooleanExpression e = null;
         if (hyphenatedProps) {
            HyphenatedParser parser = new HyphenatedParser(new StringReader(actual));
            e = parser.JmsSelector();
         } else {
            StrictParser parser = new StrictParser(new StringReader(actual));
            e = parser.JmsSelector();
         }
         return e;
      } catch (Throwable e) {
         FilterException fe = new FilterException(actual, e);
         throw fe;
      } finally {
         if (convertStringExpressions) {
            ComparisonExpression.CONVERT_STRING_EXPRESSIONS.remove();
         }
      }
   }
}
