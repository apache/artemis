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

package org.apache.activemq.artemis.uri;

import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.uri.schema.serverLocator.ConnectionOptions;

/**
 * This will represent all the possible options you could setup on URLs When parsing the URL this will serve as an
 * intermediate object And it could also be a pl
 */
public class JMSConnectionOptions extends ConnectionOptions {

   private JMSFactoryType factoryType = JMSFactoryType.CF;

   public JMSFactoryType getFactoryTypeEnum() {
      return factoryType;
   }

   public String getType() {
      return factoryType.toString();
   }

   public void setType(final String type) {
      this.factoryType = convertCFType(type);
      if (factoryType == null) {
         factoryType = JMSFactoryType.CF;
      }
   }

   public static JMSFactoryType convertCFType(String type) {
      try {
         if (type == null) {
            return JMSFactoryType.CF;
         } else {
            return Enum.valueOf(JMSFactoryType.class, type);
         }
      } catch (Exception e) {
         return null;
      }
   }

   @Override
   public String toString() {
      return "JMSConnectionOptions{" +
         ", factoryType=" + factoryType +
         '}';
   }
}
