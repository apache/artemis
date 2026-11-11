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
package org.apache.activemq.artemis.tests.unit.jms.client;

import static org.apache.activemq.artemis.api.core.ActiveMQExceptionType.CONNECTION_TIMEDOUT;
import static org.apache.activemq.artemis.api.core.ActiveMQExceptionType.GENERIC_EXCEPTION;
import static org.apache.activemq.artemis.api.core.ActiveMQExceptionType.INVALID_FILTER_EXPRESSION;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.jms.client.JMSExceptionHelper;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class JMSExceptionHelperTest extends ActiveMQTestBase {

   @Test
   public void testCONNECTION_TIMEDOUT() throws Exception {
      doConvertException(CONNECTION_TIMEDOUT, JMSException.class);
   }

   @Test
   public void testILLEGAL_STATE() throws Exception {
      doConvertException(ActiveMQExceptionType.ILLEGAL_STATE, IllegalStateException.class);
   }

   @Test
   public void testINTERNAL_ERROR() throws Exception {
      doConvertException(ActiveMQExceptionType.INTERNAL_ERROR, JMSException.class);
   }

   @Test
   public void testINVALID_FILTER_EXPRESSION() throws Exception {
      doConvertException(INVALID_FILTER_EXPRESSION, InvalidSelectorException.class);
   }

   @Test
   public void testNOT_CONNECTED() throws Exception {
      doConvertException(ActiveMQExceptionType.NOT_CONNECTED, JMSException.class);
   }

   @Test
   public void testOBJECT_CLOSED() throws Exception {
      doConvertException(ActiveMQExceptionType.OBJECT_CLOSED, IllegalStateException.class);
   }

   @Test
   public void testQUEUE_DOES_NOT_EXIST() throws Exception {
      doConvertException(ActiveMQExceptionType.QUEUE_DOES_NOT_EXIST, InvalidDestinationException.class);
   }

   @Test
   public void testQUEUE_EXISTS() throws Exception {
      doConvertException(ActiveMQExceptionType.QUEUE_EXISTS, InvalidDestinationException.class);
   }

   @Test
   public void testSECURITY_EXCEPTION() throws Exception {
      doConvertException(ActiveMQExceptionType.SECURITY_EXCEPTION, JMSSecurityException.class);
   }

   @Test
   public void testUNSUPPORTED_PACKET() throws Exception {
      doConvertException(ActiveMQExceptionType.UNSUPPORTED_PACKET, IllegalStateException.class);
   }

   @Test
   public void testDefault() throws Exception {
      doConvertException(GENERIC_EXCEPTION, JMSException.class);
   }

   private void doConvertException(final ActiveMQExceptionType errorCode,
                                   final Class<? extends Throwable> expectedException) {
      ActiveMQException me = new ActiveMQException(errorCode);
      Exception e = JMSExceptionHelper.convertFromActiveMQException(me);
      assertNotNull(e);
      assertTrue(e.getClass().isAssignableFrom(expectedException));
   }
}
