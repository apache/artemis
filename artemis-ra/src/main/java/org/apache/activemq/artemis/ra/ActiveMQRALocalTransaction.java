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
package org.apache.activemq.artemis.ra;

import javax.jms.JMSException;
import javax.resource.ResourceException;
import javax.resource.spi.LocalTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * JMS Local transaction
 */
public class ActiveMQRALocalTransaction implements LocalTransaction {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ActiveMQRAManagedConnection mc;

   public ActiveMQRALocalTransaction(final ActiveMQRAManagedConnection mc) {
      logger.trace("constructor({})", mc);

      this.mc = mc;
   }

   @Override
   public void begin() throws ResourceException {
      logger.trace("begin()");

      // mc.setInManagedTx(true);
   }

   @Override
   public void commit() throws ResourceException {
      logger.trace("commit()");

      mc.lock();
      try {
         if (mc.getSession().getTransacted()) {
            mc.getSession().commit();
         }
      } catch (JMSException e) {
         throw new ResourceException("Could not commit LocalTransaction", e);
      } finally {
         //mc.setInManagedTx(false);
         mc.unlock();
      }
   }

   @Override
   public void rollback() throws ResourceException {
      logger.trace("rollback()");

      mc.lock();
      try {
         if (mc.getSession().getTransacted()) {
            mc.getSession().rollback();
         }
      } catch (JMSException ex) {
         throw new ResourceException("Could not rollback LocalTransaction", ex);
      } finally {
         //mc.setInManagedTx(false);
         mc.unlock();
      }
   }
}
