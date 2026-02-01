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
package org.apache.activemq.artemis.logs;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;

/**
 * This class contains a tool where programs could intercept for LogMessage
 * <p>
 * Be careful with this use as this is intended for testing only (such as testcases)
 */
public class AssertionLoggerHandler extends AbstractAppender implements Closeable {

   private final Deque<LogEntry> messages = new ConcurrentLinkedDeque<>();
   private final boolean captureStackTrace;

   public AssertionLoggerHandler() {
      this(false);
   }

   public AssertionLoggerHandler(boolean captureStackTrace) {
      super("AssertionLoggerHandler" + System.currentTimeMillis(), null, null, true, Property.EMPTY_ARRAY);
      this.captureStackTrace = captureStackTrace;
      org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
      rootLogger.addAppender(this);
      super.start();
   }

   @Override
   public void append(LogEvent event) {
      LogEntry logEntry = new LogEntry();
      logEntry.message = event.getMessage().getFormattedMessage();
      logEntry.level = event.getLevel();
      logEntry.loggerName = event.getLoggerName();

      if (captureStackTrace && event.getThrown() != null) {
         StringWriter stackOutput = new StringWriter();
         event.getThrown().printStackTrace(new PrintWriter(stackOutput));
         logEntry.stackTrace = stackOutput.toString();
      }

      messages.addFirst(logEntry);
   }

   @Override
   public void close() throws IOException {
      org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
      rootLogger.removeAppender(this);
   }

   /**
    * Determines whether there is any log entry matching the specified log level.
    *
    * @param level the log level to check for in the log entries
    * @return true if a log entry matches the specified log level; otherwise, false
    */
   public boolean hasLevel(LogLevel level) {
      return hasLevel(level, null);
   }

   /**
    * Determines whether there is any log entry matching the specified log level while optionally ignoring messages that
    * contain specified substrings.
    *
    * @param level   the log level to check for in the log entries
    * @param ignores a list of substrings; log messages containing any of these substrings will be ignored during the
    *                check
    * @return true if a log entry matches the specified log level and does not contain any of the ignored substrings;
    * otherwise, false
    */
   public boolean hasLevel(LogLevel level, List<String> ignores) {
      Level implLevel = level.toImplLevel();
      for (LogEntry logEntry : messages) {
         if (implLevel == logEntry.level) {
            if (ignores != null) {
               boolean ignoreFound = false;
               for (String ignore : ignores) {
                  if (logEntry.message.contains(ignore)) {
                     ignoreFound = true;
                     break;
                  }
               }
               if (!ignoreFound) {
                  return true;
               }
            } else {
               return true;
            }
         }
      }

      return false;
   }

   public static LogLevel setLevel(String loggerName, LogLevel level) {
      final Logger logger = LogManager.getLogger(loggerName);
      final Level existingLevel = logger.getLevel();

      final Level newLevel = level.toImplLevel();

      if (!existingLevel.equals(newLevel)) {
         Configurator.setLevel(logger, newLevel);
      }

      return LogLevel.fromImplLevel(existingLevel);
   }

   /**
    * Find a line that contains the parameters passed as an argument
    */
   public boolean findText(final String... text) {
      for (LogEntry logEntry : messages) {
         boolean found = false;

         for (String txtCheck : text) {
            found = logEntry.message.contains(txtCheck);
            if (!found) {
               break;
            }
         }

         if (found) {
            return true;
         }
      }

      return false;
   }

   /**
    * Find a stacktrace that contains the parameters passed as an argument
    */
   public boolean findTrace(final String trace) {
      if (!captureStackTrace) {
         throw new IllegalStateException("captureStackTrace must be enabled to use this method");
      }
      for (LogEntry logEntry : messages) {
         if (logEntry.stackTrace != null && logEntry.stackTrace.contains(trace)) {
            return true;
         }
      }

      return false;
   }

   public int countText(final String... text) {
      int found = 0;
      for (LogEntry logEntry : messages) {
         for (String txtCheck : text) {
            if (logEntry.message.contains(txtCheck)) {
               found++;
            }
         }
      }
      return found;
   }

   public boolean matchText(final String pattern) {
      Pattern r = Pattern.compile(pattern);

      for (LogEntry logEntry : messages) {
         if (r.matcher(logEntry.message).matches()) {
            return true;
         }
      }

      return false;
   }

   public enum LogLevel {
      OFF(Level.OFF),
      FATAL(Level.FATAL),
      ERROR(Level.ERROR),
      WARN(Level.WARN),
      INFO(Level.INFO),
      DEBUG(Level.DEBUG),
      TRACE(Level.TRACE);

      Level implLevel;

      LogLevel(Level implLevel) {
         this.implLevel = implLevel;
      }

      private Level toImplLevel() {
         return implLevel;
      }

      private static LogLevel fromImplLevel(Level implLevel) {
         for (LogLevel logLevel : LogLevel.values()) {
            if (logLevel.implLevel == implLevel) {
               return logLevel;
            }
         }
         throw new IllegalArgumentException("Unexpected level:" + implLevel);
      }
   }

   public void clear() {
      messages.clear();
   }

   public List<LogEntry> getLogEntries() {
      return messages.stream().collect(Collectors.toList());
   }

   public int getNumberOfMessages() {
      return messages.size();
   }

   public static class LogEntry {
      private String message;
      private String stackTrace;
      private Level level;
      private String loggerName;

      public String getMessage() {
         return message;
      }

      public LogLevel getLogLevel() {
         return LogLevel.fromImplLevel(level);
      }

      public String getLoggerName() {
         return loggerName;
      }

      /**
       * Only useful if {@link AssertionLoggerHandler} was created with
       * {@link AssertionLoggerHandler#AssertionLoggerHandler(boolean captureStackTrace)} to enable StackTrace
       * collection.
       */
      public String getStackTrace() {
         return stackTrace;
      }
   }
}
