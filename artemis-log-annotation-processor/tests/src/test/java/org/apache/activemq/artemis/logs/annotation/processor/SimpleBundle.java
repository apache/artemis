/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.logs.annotation.processor;

import java.io.IOException;

import org.apache.activemq.artemis.logs.annotation.GetLogger;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.annotation.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LogBundle(projectCode = "TST")
public interface SimpleBundle {

   static SimpleBundle init() {
      try {
         Logger logger = LoggerFactory.getLogger(SimpleBundle.class.getName());
         return (SimpleBundle) Class.forName(SimpleBundle.class.getName() + "_impl").getConstructor(Logger.class).newInstance(logger);
      } catch (Exception e) {
         LoggerFactory.getLogger(SimpleBundle.class).error(e.getMessage(), e);
      }
      return null;
   }

   SimpleBundle MESSAGES = init();

   @Message(id = 1, value = "Test")
   String simpleTest();

   @Message(id = 2, value = "V{}-{}")
   String parameters(int value, String value2);

   @Message(id = 3, value = "EX")
   Exception someException();

   @Message(id = 4, value = "EX-{}")
   Exception someExceptionParameter(String parameter);

   @LogMessage(id = 5, value = "This is a print!!!", level = LogMessage.Level.WARN)
   void printMessage();

   @LogMessage(id = 6, value = "This is a print!!! {}", level = LogMessage.Level.WARN)
   void printMessage(int nr);

   @LogMessage(id = 7, value = "multi\nLine\nMessage", level = LogMessage.Level.WARN)
   void multiLines();

   @Message(id = 8, value = "EX{}")
   MyException someExceptionWithCause(String message, Exception myCause);

   @Message(id = 9, value = "{} {} {} {}")
   String abcd(String a, String b, String c, String d);

   @Message(id = 10, value = "{} {} {} {}")
   String objectsAbcd(MyObject a, MyObject b, MyObject c, MyObject d);

   @LogMessage(id = 11, value = "This message has the following parameter:: {}", level = LogMessage.Level.WARN)
   void parameterException(String parameter, IOException e);

   @LogMessage(id = 12, value = "This message has the following parameter:: {}", level = LogMessage.Level.WARN)
   void myExceptionLogger(String parameter, MyException e);

   @LogMessage(id = 13, value = "Long with 5 parameters p{} p{} p{} p{} p{}", level = LogMessage.Level.WARN)
   void longParameters(String p1, String p2, String p3, String p4, String p5);

   @LogMessage(id = 14, value = "An Exceptional example", level = LogMessage.Level.WARN)
   void onlyException(MyException e);

   @GetLogger
   Logger getLogger();

   String LOGGER_NAME_OVERRIDE_PARAMS = "org.apache.activemq.artemis.logs.annotation.processor.SimpleBundle.OVERRIDE.PARAMS";

   @LogMessage(id = 15, value = "Logger name overridden to add .OVERRIDE.PARAMS suffix. {}", loggerName = LOGGER_NAME_OVERRIDE_PARAMS, level = LogMessage.Level.WARN)
   void overrideLoggerNameWithParameter(String parameter);

   String LOGGER_NAME_OVERRIDE_NO_PARAMS = "org.apache.activemq.artemis.logs.annotation.processor.SimpleBundle.OVERRIDE.NO_PARAMS";

   @LogMessage(id = 16, value = "Logger name overridden to add .OVERRIDE.NO_PARAMS suffix.", loggerName = LOGGER_NAME_OVERRIDE_NO_PARAMS, level = LogMessage.Level.WARN)
   void overrideLoggerNameWithoutParameter();
}
