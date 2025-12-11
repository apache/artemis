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
package org.apache.activemq.artemis.core.persistence.config;

import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.json.JsonObject;

import static org.apache.activemq.artemis.core.security.Role.BROWSE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CONSUME_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CREATE_ADDRESS_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CREATE_DURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CREATE_NONDURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.DELETE_ADDRESS_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.DELETE_DURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.DELETE_NONDURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.EDIT_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.MANAGE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.SEND_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.VIEW_PERMISSION;
import static org.apache.activemq.artemis.utils.DataConstants.SIZE_INT;
import static org.apache.activemq.artemis.utils.DataConstants.SIZE_NULL;

public class PersistedSecuritySetting implements EncodingSupport {

   private long storeId;

   private SimpleString addressMatch;

   private SimpleString sendRoles;

   private SimpleString consumeRoles;

   private SimpleString createDurableQueueRoles;

   private SimpleString deleteDurableQueueRoles;

   private SimpleString createNonDurableQueueRoles;

   private SimpleString deleteNonDurableQueueRoles;

   private SimpleString manageRoles;

   private SimpleString browseRoles;

   private SimpleString createAddressRoles;

   private SimpleString deleteAddressRoles;

   private SimpleString viewRoles;

   private SimpleString editRoles;


   public PersistedSecuritySetting() {
   }

   public PersistedSecuritySetting(final String addressMatch,
                                   final String sendRoles,
                                   final String consumeRoles,
                                   final String createDurableQueueRoles,
                                   final String deleteDurableQueueRoles,
                                   final String createNonDurableQueueRoles,
                                   final String deleteNonDurableQueueRoles,
                                   final String manageRoles,
                                   final String browseRoles,
                                   final String createAddressRoles,
                                   final String deleteAddressRoles,
                                   final String viewRoles,
                                   final String editRoles) {
      super();
      this.addressMatch = SimpleString.of(addressMatch);
      this.sendRoles = SimpleString.of(sendRoles);
      this.consumeRoles = SimpleString.of(consumeRoles);
      this.createDurableQueueRoles = SimpleString.of(createDurableQueueRoles);
      this.deleteDurableQueueRoles = SimpleString.of(deleteDurableQueueRoles);
      this.createNonDurableQueueRoles = SimpleString.of(createNonDurableQueueRoles);
      this.deleteNonDurableQueueRoles = SimpleString.of(deleteNonDurableQueueRoles);
      this.manageRoles = SimpleString.of(manageRoles);
      this.browseRoles = SimpleString.of(browseRoles);
      this.createAddressRoles = SimpleString.of(createAddressRoles);
      this.deleteAddressRoles = SimpleString.of(deleteAddressRoles);
      this.viewRoles = SimpleString.of(viewRoles);
      this.editRoles = SimpleString.of(editRoles);
   }

   public PersistedSecuritySetting(final String addressMatch, JsonObject o) {
      this(addressMatch,
           JsonUtil.arrayToString(o, SEND_PERMISSION),
           JsonUtil.arrayToString(o, CONSUME_PERMISSION),
           JsonUtil.arrayToString(o, CREATE_DURABLE_QUEUE_PERMISSION),
           JsonUtil.arrayToString(o, DELETE_DURABLE_QUEUE_PERMISSION),
           JsonUtil.arrayToString(o, CREATE_NONDURABLE_QUEUE_PERMISSION),
           JsonUtil.arrayToString(o, DELETE_NONDURABLE_QUEUE_PERMISSION),
           JsonUtil.arrayToString(o, MANAGE_PERMISSION),
           JsonUtil.arrayToString(o, BROWSE_PERMISSION),
           JsonUtil.arrayToString(o, CREATE_ADDRESS_PERMISSION),
           JsonUtil.arrayToString(o, DELETE_ADDRESS_PERMISSION),
           JsonUtil.arrayToString(o, VIEW_PERMISSION),
           JsonUtil.arrayToString(o, EDIT_PERMISSION));
   }


   public long getStoreId() {
      return storeId;
   }

   public void setStoreId(final long id) {
      storeId = id;
   }

   public SimpleString getAddressMatch() {
      return addressMatch;
   }

   public String getSendRoles() {
      return Objects.toString(sendRoles, null);
   }

   public String getConsumeRoles() {
      return Objects.toString(consumeRoles, null);
   }

   public String getCreateDurableQueueRoles() {
      return Objects.toString(createDurableQueueRoles, null);
   }

   public String getDeleteDurableQueueRoles() {
      return Objects.toString(deleteDurableQueueRoles, null);
   }

   public String getCreateNonDurableQueueRoles() {
      return Objects.toString(createNonDurableQueueRoles, null);
   }

   public String getDeleteNonDurableQueueRoles() {
      return Objects.toString(deleteNonDurableQueueRoles, null);
   }

   public String getManageRoles() {
      return Objects.toString(manageRoles, null);
   }

   public String getBrowseRoles() {
      return Objects.toString(browseRoles, null);
   }

   public String getCreateAddressRoles() {
      return Objects.toString(createAddressRoles, null);
   }

   public String getDeleteAddressRoles() {
      return Objects.toString(deleteAddressRoles, null);
   }

   public String getViewRoles() {
      return Objects.toString(viewRoles, null);
   }

   public String getEditRoles() {
      return Objects.toString(editRoles, null);
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      if (addressMatch == null) {
         addressMatch = SimpleString.of("");
      }
      buffer.writeSimpleString(addressMatch);
      buffer.writeNullableSimpleString(sendRoles);
      buffer.writeNullableSimpleString(consumeRoles);
      buffer.writeNullableSimpleString(createDurableQueueRoles);
      buffer.writeNullableSimpleString(deleteDurableQueueRoles);
      buffer.writeNullableSimpleString(createNonDurableQueueRoles);
      buffer.writeNullableSimpleString(deleteNonDurableQueueRoles);
      buffer.writeNullableSimpleString(manageRoles);
      buffer.writeNullableSimpleString(browseRoles);
      buffer.writeNullableSimpleString(createAddressRoles);
      buffer.writeNullableSimpleString(deleteAddressRoles);
      buffer.writeNullableSimpleString(viewRoles);
      buffer.writeNullableSimpleString(editRoles);
   }

   @Override
   public int getEncodeSize() {
      return
         (addressMatch == null ? SIZE_INT : addressMatch.sizeof()) +
         (sendRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(sendRoles)) +
         (consumeRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(consumeRoles)) +
         (createDurableQueueRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(createDurableQueueRoles)) +
         (deleteDurableQueueRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(deleteDurableQueueRoles)) +
         (createNonDurableQueueRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(createNonDurableQueueRoles)) +
         (deleteNonDurableQueueRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(deleteNonDurableQueueRoles)) +
         (manageRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(manageRoles)) +
         (browseRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(browseRoles)) +
         (createAddressRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(createAddressRoles)) +
         (deleteAddressRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(deleteAddressRoles)) +
         (viewRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(viewRoles)) +
         (editRoles == null ? SIZE_NULL : SimpleString.sizeofNullableString(editRoles));
   }

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      addressMatch = buffer.readSimpleString();
      sendRoles = buffer.readNullableSimpleString();
      consumeRoles = buffer.readNullableSimpleString();
      createDurableQueueRoles = buffer.readNullableSimpleString();
      deleteDurableQueueRoles = buffer.readNullableSimpleString();
      createNonDurableQueueRoles = buffer.readNullableSimpleString();
      deleteNonDurableQueueRoles = buffer.readNullableSimpleString();
      manageRoles = buffer.readNullableSimpleString();
      browseRoles = buffer.readNullableSimpleString();
      createAddressRoles = buffer.readNullableSimpleString();
      deleteAddressRoles = buffer.readNullableSimpleString();
      if (buffer.readableBytes() > 0) {
         viewRoles = buffer.readNullableSimpleString();
      }
      if (buffer.readableBytes() > 0) {
         editRoles = buffer.readNullableSimpleString();
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(addressMatch, consumeRoles, createDurableQueueRoles, createNonDurableQueueRoles,
                          deleteDurableQueueRoles, deleteNonDurableQueueRoles, manageRoles, browseRoles,
                          createAddressRoles, deleteAddressRoles, sendRoles, viewRoles, editRoles, storeId);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof PersistedSecuritySetting other)) {
         return false;
      }

      return Objects.equals(addressMatch, other.addressMatch) &&
             Objects.equals(consumeRoles, other.consumeRoles) &&
             Objects.equals(createDurableQueueRoles, other.createDurableQueueRoles) &&
             Objects.equals(createNonDurableQueueRoles, other.createNonDurableQueueRoles) &&
             Objects.equals(deleteDurableQueueRoles, other.deleteDurableQueueRoles) &&
             Objects.equals(deleteNonDurableQueueRoles, other.deleteNonDurableQueueRoles) &&
             Objects.equals(manageRoles, other.manageRoles) &&
             Objects.equals(browseRoles, other.browseRoles) &&
             Objects.equals(createAddressRoles, other.createAddressRoles) &&
             Objects.equals(deleteAddressRoles, other.deleteAddressRoles) &&
             Objects.equals(sendRoles, other.sendRoles) &&
             Objects.equals(viewRoles, other.viewRoles) &&
             Objects.equals(editRoles, other.editRoles) &&
             storeId == other.storeId;
   }

   @Override
   public String toString() {
      return "PersistedSecuritySetting [storeId=" + storeId +
         ", addressMatch=" + addressMatch +
         ", sendRoles=" + sendRoles +
         ", consumeRoles=" + consumeRoles +
         ", createDurableQueueRoles=" + createDurableQueueRoles +
         ", deleteDurableQueueRoles=" + deleteDurableQueueRoles +
         ", createNonDurableQueueRoles=" + createNonDurableQueueRoles +
         ", deleteNonDurableQueueRoles=" + deleteNonDurableQueueRoles +
         ", manageRoles=" + manageRoles +
         ", browseRoles=" + browseRoles +
         ", createAddressRoles=" + createAddressRoles +
         ", deleteAddressRoles=" + deleteAddressRoles +
         ", viewRoles=" + viewRoles +
         ", editRoles=" + editRoles +
         "]";
   }

}
