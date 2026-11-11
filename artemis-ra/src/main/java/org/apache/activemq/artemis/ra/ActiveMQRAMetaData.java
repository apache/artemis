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

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnectionMetaData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * {@inheritDoc}
 */
public class ActiveMQRAMetaData implements ManagedConnectionMetaData {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ActiveMQRAManagedConnection mc;

   public ActiveMQRAMetaData(final ActiveMQRAManagedConnection mc) {
      logger.trace("constructor({})", mc);

      this.mc = mc;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getEISProductName() throws ResourceException {
      logger.trace("getEISProductName()");

      return "ActiveMQ Artemis";
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public String getEISProductVersion() throws ResourceException {
      logger.trace("getEISProductVersion()");

      return "2.0";
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public String getUserName() throws ResourceException {
      logger.trace("getUserName()");

      return mc.getUserName();
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public int getMaxConnections() throws ResourceException {
      logger.trace("getMaxConnections()");

      return 0;
   }

}
