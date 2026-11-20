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
package org.apache.activemq.artemis.logs.log4j;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.status.StatusLogger;

/**
 * A Log4J 2 filter which suppresses log entries by their broker log code.
 * <p>
 * @see <a href="https://logging.apache.org/log4j/2.x/manual/filters.html">Log4J 2 filters</a>
 */
@Plugin(name = ArtemisLogCodeFilter.PLUGIN_NAME, category = Node.CATEGORY, elementType = Filter.ELEMENT_TYPE, printObject = true)
public final class ArtemisLogCodeFilter extends AbstractFilter {

   public static final String PLUGIN_NAME = "ArtemisLogCodeFilter";

   /**
    * The {@code projectCode} declared by every {@code @LogBundle} in the broker.
    */
   private static final String PROJECT_CODE = "AMQ";

   /**
    * Generous upper bound on the length of a code, purely to bound the scan and avoid numeric overflow.
    */
   private static final int MAX_CODE_DIGITS = 9;

   private static final int NO_CODE = -1;

   /**
    * Sorted, to allow a binary search which stays cheap no matter how many codes are suppressed.
    */
   private final int[] codes;

   /**
    * The context this filter was configured in, or {@code null} if it was built outside one.
    */
   private final LoggerContext loggerContext;

   private ArtemisLogCodeFilter(final int[] sortedCodes, final Result onMatch, final Result onMismatch,
                                final Configuration configuration) {
      super(onMatch, onMismatch);
      this.codes = sortedCodes;
      this.loggerContext = configuration == null ? null : configuration.getLoggerContext();
   }

   @Override
   public Result filter(final LogEvent event) {
      return event == null ? onMismatch : filterMessage(event.getMessage());
   }

   @Override
   public Result filter(final Logger logger, final Level level, final Marker marker, final Message msg, final Throwable t) {
      return filterMessage(msg);
   }

   @Override
   public Result filter(final Logger logger, final Level level, final Marker marker, final String msg, final Object... params) {
      return filterFormat(msg);
   }

   @Override
   public Result filter(final Logger logger, final Level level, final Marker marker, final Object msg, final Throwable t) {
      // only a String can carry a code as its format; anything else is matched against nothing rather than
      // paying for a toString() on a hot path
      return msg instanceof String ? filterFormat((String) msg) : onMismatch;
   }

   private Result filterMessage(final Message msg) {
      return msg == null ? onMismatch : filterFormat(msg.getFormat());
   }

   private Result filterFormat(final String format) {
      final int code = parseCode(format);
      return code != NO_CODE && Arrays.binarySearch(codes, code) >= 0 ? onMatch : onMismatch;
   }

   /**
    * Arranges for the suppressed codes to be announced, so that an entry which never appears can be accounted
    * for.
    * <p>
    * The announcement cannot be made from here. A filter is started while its configuration is still being
    * assembled, before the appenders are able to accept anything, so a message logged at this point is
    * silently discarded. Instead a listener is registered which fires once the context has finished switching
    * to the new configuration, at which point logging works normally.
    * <p>
    * Nothing is announced when {@code onMatch} is not {@link Result#DENY}, since the filter is then not
    * suppressing anything.
    */
   @Override
   public void start() {
      if (onMatch == Result.DENY && loggerContext != null) {
         loggerContext.addPropertyChangeListener(new AnnouncementListener());
      }
      super.start();
   }

   /**
    * Announces the suppressed codes once the configuration this filter belongs to has become the active one, then
    * removes itself so that later reconfigurations are left to their own filters.
    */
   private final class AnnouncementListener implements PropertyChangeListener {

      @Override
      public void propertyChange(final PropertyChangeEvent event) {
         if (!LoggerContext.PROPERTY_CONFIG.equals(event.getPropertyName())) {
            return;
         }
         loggerContext.removePropertyChangeListener(this);
         try {
            Logger logger = loggerContext.getLogger(String.valueOf(MethodHandles.lookup().lookupClass()));
            logger.info("Log entries with the following codes are being suppressed: {}", codeList());
         } catch (Exception e) {
            StatusLogger.getLogger().warn("{} could not announce the codes it is suppressing", PLUGIN_NAME, e);
         }
      }
   }

   /**
    * Extracts the numeric part of the leading {@code AMQ<code>:} token.
    *
    * @param format the message format, before parameter substitution
    * @return the code, or {@link #NO_CODE} if {@code format} does not begin with a well-formed code token
    */
   static int parseCode(final String format) {
      if (format == null || !format.startsWith(PROJECT_CODE)) {
         return NO_CODE;
      }
      final int length = format.length();
      int code = 0;
      int digits = 0;
      for (int i = PROJECT_CODE.length(); i < length; i++) {
         final char c = format.charAt(i);
         if (c == ':') {
            return digits == 0 ? NO_CODE : code;
         }
         if (c < '0' || c > '9' || ++digits > MAX_CODE_DIGITS) {
            return NO_CODE;
         }
         code = (code * 10) + (c - '0');
      }
      // ran off the end without the ':' terminator
      return NO_CODE;
   }

   /**
    * @return the configured codes, e.g. {@code 222061, 224000}
    */
   private String codeList() {
      final StringBuilder sb = new StringBuilder();
      for (int i = 0; i < codes.length; i++) {
         if (i > 0) {
            sb.append(", ");
         }
         sb.append(codes[i]);
      }
      return sb.toString();
   }

   @Override
   public String toString() {
      return PLUGIN_NAME + '[' + codeList() + ']';
   }

   @PluginBuilderFactory
   public static Builder newBuilder() {
      return new Builder();
   }

   public static final class Builder implements org.apache.logging.log4j.core.util.Builder<ArtemisLogCodeFilter> {

      /**
       * Comma-separated list of codes to act on, each without the {@code AMQ} prefix.
       */
      @PluginConfiguration
      private Configuration configuration;

      @PluginBuilderAttribute
      private String codes;

      /**
       * Defaults to {@link Result#DENY}, since suppression is the reason this filter exists.
       */
      @PluginBuilderAttribute
      private Result onMatch = Result.DENY;

      /**
       * Defaults to {@link Result#NEUTRAL} so that non-matching events are left to the rest of the configuration
       * rather than being force-accepted past the level check.
       */
      @PluginBuilderAttribute
      private Result onMismatch = Result.NEUTRAL;

      public Builder setConfiguration(final Configuration configuration) {
         this.configuration = configuration;
         return this;
      }

      public Builder setCodes(final String codes) {
         this.codes = codes;
         return this;
      }

      public Builder setOnMatch(final Result onMatch) {
         this.onMatch = onMatch;
         return this;
      }

      public Builder setOnMismatch(final Result onMismatch) {
         this.onMismatch = onMismatch;
         return this;
      }

      @Override
      public ArtemisLogCodeFilter build() {
         if (codes == null || codes.isBlank()) {
            StatusLogger.getLogger().error("{} requires a non-empty 'codes' attribute", PLUGIN_NAME);
            return null;
         }
         // -1 so that trailing empty tokens are kept and rejected rather than silently dropped
         final String[] tokens = codes.split(",", -1);
         final int[] parsed = new int[tokens.length];
         for (int i = 0; i < tokens.length; i++) {
            final String token = tokens[i].trim();
            if (token.isEmpty()) {
               StatusLogger.getLogger().error("{} found an empty log code in '{}'", PLUGIN_NAME, codes);
               return null;
            }
            final int code;
            try {
               code = Integer.parseInt(token);
            } catch (NumberFormatException e) {
               StatusLogger.getLogger().error("{} cannot parse '{}' as a log code; specify codes without the '{}' " +
                                                 "prefix, e.g. 222061 rather than {}222061", PLUGIN_NAME, token,
                                              PROJECT_CODE, PROJECT_CODE);
               return null;
            }
            if (code < 0) {
               StatusLogger.getLogger().error("{} does not accept the negative log code '{}'", PLUGIN_NAME, token);
               return null;
            }
            parsed[i] = code;
         }
         Arrays.sort(parsed);
         return new ArtemisLogCodeFilter(parsed, onMatch, onMismatch, configuration);
      }
   }
}
