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
package org.apache.activemq.artemis.core.server.group.impl;

import java.io.Serializable;
import java.util.Objects;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * A remote Grouping handler configuration
 */
public final class GroupingHandlerConfiguration implements Serializable {

   public static final long serialVersionUID = -4600283023652477326L;

   public static final String GROUP_TIMEOUT_PROP_NAME = "org.apache.activemq.GroupingHandlerConfiguration.groupTimeout";

   public static final String REAPER_PERIOD_PROP_NAME = "org.apache.activemq.GroupingHandlerConfiguration.reaperPeriod";

   private SimpleString name = null;

   private TYPE type = null;

   private SimpleString address = null;

   private long timeout = ActiveMQDefaultConfiguration.getDefaultGroupingHandlerTimeout();

   private long groupTimeout = ActiveMQDefaultConfiguration.getDefaultGroupingHandlerGroupTimeout();

   private long reaperPeriod = ActiveMQDefaultConfiguration.getDefaultGroupingHandlerReaperPeriod();

   public GroupingHandlerConfiguration() {
      if (System.getProperty(GROUP_TIMEOUT_PROP_NAME) != null) {
         this.groupTimeout = Long.parseLong(System.getProperty(GROUP_TIMEOUT_PROP_NAME));
      }

      if (System.getProperty(REAPER_PERIOD_PROP_NAME) != null) {
         this.reaperPeriod = Long.parseLong(System.getProperty(REAPER_PERIOD_PROP_NAME));
      }
   }

   public SimpleString getName() {
      return name;
   }

   public TYPE getType() {
      return type;
   }

   public SimpleString getAddress() {
      return address;
   }

   public long getTimeout() {
      return timeout;
   }

   public long getGroupTimeout() {
      return groupTimeout;
   }

   public long getReaperPeriod() {
      return reaperPeriod;
   }

   public GroupingHandlerConfiguration setName(SimpleString name) {
      this.name = name;
      return this;
   }

   public GroupingHandlerConfiguration setType(TYPE type) {
      this.type = type;
      return this;
   }

   public GroupingHandlerConfiguration setAddress(SimpleString address) {
      this.address = address;
      return this;
   }

   public GroupingHandlerConfiguration setTimeout(long timeout) {
      this.timeout = timeout;
      return this;
   }

   public GroupingHandlerConfiguration setGroupTimeout(long groupTimeout) {
      this.groupTimeout = groupTimeout;
      return this;
   }

   public GroupingHandlerConfiguration setReaperPeriod(long reaperPeriod) {
      this.reaperPeriod = reaperPeriod;
      return this;
   }

   public enum TYPE {
      LOCAL("LOCAL"), REMOTE("REMOTE");

      private String type;

      TYPE(final String type) {
         this.type = type;
      }

      public String getType() {
         return type;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(address, name, timeout, type);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof GroupingHandlerConfiguration other)) {
         return false;
      }

      return Objects.equals(address, other.address) &&
             Objects.equals(name, other.name) &&
             timeout == other.timeout &&
             type == other.type;
   }
}
