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
package org.apache.activemq.artemis.cli.commands.tools.xml;

import javax.xml.stream.XMLStreamWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Proxy to handle indenting the XML since {@code javax.xml.stream.XMLStreamWriter} doesn't support that.
 */
public class PrettyPrintHandler implements InvocationHandler {

   private final XMLStreamWriter target;

   private int depth = 0;

   private static final char INDENT_CHAR = ' ';

   private static final String LINE_SEPARATOR = System.lineSeparator();

   boolean wrap = true;

   public PrettyPrintHandler(XMLStreamWriter target) {
      this.target = target;
   }

   @Override
   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String m = method.getName();

      switch (m) {
         case "writeStartElement":
            target.writeCharacters(LINE_SEPARATOR);
            target.writeCharacters(indent(depth));

            depth++;
            break;
         case "writeEndElement":
            depth--;
            if (wrap) {
               target.writeCharacters(LINE_SEPARATOR);
               target.writeCharacters(indent(depth));
            }
            wrap = true;
            break;
         case "writeEmptyElement":
         case "writeCData":
            target.writeCharacters(LINE_SEPARATOR);
            target.writeCharacters(indent(depth));
            break;
         case "writeCharacters":
            wrap = false;
            break;
      }

      method.invoke(target, args);

      return null;
   }

   private String indent(int depth) {
      depth *= 3; // level of indentation
      char[] output = new char[depth];
      while (depth-- > 0) {
         output[depth] = INDENT_CHAR;
      }
      return new String(output);
   }
}
