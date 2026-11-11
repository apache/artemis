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
package org.apache.activemq.artemis.core.settings.impl;

import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;

/**
 * a Match is the holder for the match string and the object to hold against it.
 */
public class Match<T> {

   private static final String WILDCARD_REPLACEMENT = ".*";

   private static final String WORD_WILDCARD_REPLACEMENT_FORMAT = "[^%s]+";

   private static final String WILDCARD_CHILD_REPLACEMENT_FORMAT = "(%s.+)*";

   private static final String DOT = ".";

   private static final String DOT_REPLACEMENT = "\\.";

   private static final char DOLLAR_CHAR = '$';

   private static final String DOLLAR = String.valueOf(DOLLAR_CHAR);

   private static final String DOLLAR_REPLACEMENT = "\\" + DOLLAR;

   private final String match;

   private final Pattern pattern;

   private final T value;

   private final boolean literal;

   public Match(final String match, final T value, final WildcardConfiguration wildcardConfiguration) {
      this(match, value, wildcardConfiguration, false);
   }

   public Match(final String match, final T value, final WildcardConfiguration wildcardConfiguration, final boolean literal) {
      this.match = match;
      this.value = value;
      String actMatch = match;

      if (wildcardConfiguration.getAnyWordsString().equals(match)) {
         // replace any regex characters
         actMatch = WILDCARD_REPLACEMENT;
      } else {
         // this is to match with what's documented
         actMatch = actMatch.replace(wildcardConfiguration.getDelimiterString() + wildcardConfiguration.getAnyWordsString(), wildcardConfiguration.getAnyWordsString());
         actMatch = actMatch.replace(DOT, DOT_REPLACEMENT);
         if (!wildcardConfiguration.contains(DOLLAR_CHAR)) {
            actMatch = actMatch.replace(DOLLAR, DOLLAR_REPLACEMENT);
         }
         actMatch = actMatch.replace(wildcardConfiguration.getSingleWordString(), String.format(WORD_WILDCARD_REPLACEMENT_FORMAT, Pattern.quote(wildcardConfiguration.getDelimiterString())));

         // this one has to be done by last as we are using .* and it could be replaced wrongly if delimiter is '.'
         actMatch = actMatch.replace(wildcardConfiguration.getAnyWordsString(), String.format(WILDCARD_CHILD_REPLACEMENT_FORMAT, Pattern.quote(wildcardConfiguration.getDelimiterString())));
      }
      // we need to anchor with eot to ensure we have a full match
      pattern = Pattern.compile(actMatch + DOLLAR);
      this.literal = literal;
   }

   public final String getMatch() {
      return match;
   }

   public final Pattern getPattern() {
      return pattern;
   }

   public final T getValue() {
      return value;
   }

   public final boolean isLiteral() {
      return literal;
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof Match other)) {
         return false;
      }

      return Objects.equals(match, other.match);

   }

   @Override
   public int hashCode() {
      return Objects.hashCode(match);
   }

   /**
    * utility method to verify consistency of match
    *
    * @param match the match to validate
    * @throws IllegalArgumentException if a match isn't valid
    */
   public static void verify(final String match, final WildcardConfiguration wildcardConfiguration) throws IllegalArgumentException {
      if (match == null) {
         throw ActiveMQMessageBundle.BUNDLE.nullMatch();
      }
      final String anyWords = wildcardConfiguration.getAnyWordsString();
      if (match.contains(anyWords) && match.indexOf(anyWords) < match.length() - 1) {
         throw ActiveMQMessageBundle.BUNDLE.invalidMatch();
      }
   }

   @Override
   public String toString() {
      return value.toString();
   }
}
