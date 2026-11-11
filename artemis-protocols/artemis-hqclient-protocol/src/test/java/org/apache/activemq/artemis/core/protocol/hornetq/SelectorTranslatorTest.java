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
package org.apache.activemq.artemis.core.protocol.hornetq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class SelectorTranslatorTest {
   @Test
   public void testConvertHQFilterString() {
      String selector = "HQUserID = 'ID:AMQ-12435678'";

      assertEquals("AMQUserID = 'ID:AMQ-12435678'", HQFilterConversionInterceptor.convertHQToActiveMQFilterString(selector));

      selector = "HQUserID = 'HQUserID'";

      assertEquals("AMQUserID = 'HQUserID'", HQFilterConversionInterceptor.convertHQToActiveMQFilterString(selector));

      selector = "HQUserID = 'ID:AMQ-12435678'";

      assertEquals("AMQUserID = 'ID:AMQ-12435678'", HQFilterConversionInterceptor.convertHQToActiveMQFilterString(selector));

      selector = "HQDurable='NON_DURABLE'";

      assertEquals("AMQDurable='NON_DURABLE'", HQFilterConversionInterceptor.convertHQToActiveMQFilterString(selector));

      selector = "HQPriority=5";

      assertEquals("AMQPriority=5", HQFilterConversionInterceptor.convertHQToActiveMQFilterString(selector));

      selector = "HQTimestamp=12345678";

      assertEquals("AMQTimestamp=12345678", HQFilterConversionInterceptor.convertHQToActiveMQFilterString(selector));

      selector = "HQExpiration=12345678";

      assertEquals("AMQExpiration=12345678", HQFilterConversionInterceptor.convertHQToActiveMQFilterString(selector));
   }

}
