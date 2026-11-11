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
package org.apache.activemq.artemis.core.management.impl.openmbean;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.reader.TextMessageUtil;
import org.junit.jupiter.api.Test;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenDataException;

public class OpenTypeSupportTest {

   @Test
   public void testTextBody() throws OpenDataException {
      final String bodyText = "TEST";
      CoreMessage coreMessage = new CoreMessage();
      coreMessage.initBuffer(1024);
      coreMessage.setType(Message.TEXT_TYPE);

      TextMessageUtil.writeBodyText(coreMessage.getBodyBuffer(), SimpleString.of(bodyText));

      CompositeData cd = coreMessage.toCompositeData(256, 1);

      assertEquals(bodyText, cd.get("text"));
   }
}
