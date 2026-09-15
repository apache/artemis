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
package org.apache.activemq.artemis.api.core.management;

/**
 * A ResourceQuotaControl is used to manage and monitor a resource quota.
 * <p>
 * Resource quotas track and enforce limits on addresses, queues, and message bytes
 * across groups of addresses. They support hierarchical organization where child
 * quotas count toward parent limits.
 */
public interface ResourceQuotaControl {

   /**
    * {@return the name of this resource quota}
    */
   @Attribute(desc = "the name of this resource quota")
   String getName();

   /**
    * {@return the name of the parent quota this quota is part of, or null if this is a root quota}
    */
   @Attribute(desc = "the name of the parent quota this quota is part of")
   String getPartOf();

   /**
    * {@return the maximum number of addresses allowed by this quota, or -1 if unlimited}
    */
   @Attribute(desc = "the maximum number of addresses allowed by this quota")
   int getMaxAddresses();

   /**
    * {@return the current number of addresses tracked by this quota}
    */
   @Attribute(desc = "the current number of addresses tracked by this quota")
   int getCurrentAddressCount();

   /**
    * {@return the percentage of the address limit currently in use (0-100), or -1 if no limit configured}
    */
   @Attribute(desc = "the percentage of the address limit currently in use")
   double getAddressUtilizationPercent();

   /**
    * {@return the maximum number of queues allowed by this quota, or -1 if unlimited}
    */
   @Attribute(desc = "the maximum number of queues allowed by this quota")
   int getMaxQueues();

   /**
    * {@return the current number of queues tracked by this quota}
    */
   @Attribute(desc = "the current number of queues tracked by this quota")
   int getCurrentQueueCount();

   /**
    * {@return the percentage of the queue limit currently in use (0-100), or -1 if no limit configured}
    */
   @Attribute(desc = "the percentage of the queue limit currently in use")
   double getQueueUtilizationPercent();

   /**
    * {@return the maximum number of bytes allowed for messages in this quota, or -1 if unlimited}
    */
   @Attribute(desc = "the maximum number of bytes allowed for messages in this quota")
   long getMaxMessageBytes();

   /**
    * {@return the current number of bytes used by messages in this quota}
    */
   @Attribute(desc = "the current number of bytes used by messages in this quota")
   long getCurrentMessageBytes();

   /**
    * {@return the percentage of the message bytes limit currently in use (0-100), or -1 if no limit configured}
    */
   @Attribute(desc = "the percentage of the message bytes limit currently in use")
   double getMessageBytesUtilizationPercent();

   /**
    * {@return true if this quota has at least one limit configured (addresses, queues, or bytes)}
    */
   @Attribute(desc = "whether this quota has any limits configured")
   boolean hasLimits();

   /**
    * {@return true if this quota has reached any of its limits}
    */
   @Attribute(desc = "whether this quota has reached any of its limits")
   boolean isLimitReached();
}
