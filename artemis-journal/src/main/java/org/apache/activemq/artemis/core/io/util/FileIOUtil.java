/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.io.util;

import java.nio.ByteBuffer;

import org.apache.activemq.artemis.core.io.SequentialFile;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;

public class FileIOUtil {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static void copyData(SequentialFile from, SequentialFile to, ByteBuffer buffer) throws Exception {

      boolean fromIsOpen = from.isOpen();
      boolean toIsOpen = to.isOpen();

      from.close();
      from.open();

      if (!toIsOpen) {
         to.open();
      }

      to.position(to.size());

      from.position(0);

      try {
         for (;;) {
            // The buffer is reused...
            // We need to make sure we clear the limits and the buffer before reusing it
            buffer.clear();
            int bytesRead = from.read(buffer);

            if (logger.isTraceEnabled()) {
               logger.trace("appending {} bytes on {}", bytesRead, to.getFileName());
            }

            if (bytesRead > 0) {
               to.writeDirect(buffer, false);
            }

            if (bytesRead < buffer.capacity()) {
               logger.trace("Interrupting reading as the whole thing was sent on {}", to.getFileName());
               break;
            }
         }
      } finally {
         if (!fromIsOpen) {
            from.close();
         } else {
            from.position(from.size());
         }
         if (!toIsOpen) {
            to.close();
         } else {
            to.position(to.size());
         }
      }

   }

}
