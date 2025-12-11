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
package org.apache.activemq.artemis.core.security;

import org.apache.activemq.artemis.json.JsonObject;
import java.io.Serializable;
import java.util.Objects;

import org.apache.activemq.artemis.utils.JsonLoader;

/**
 * A role is used by the security store to define access rights and is configured on a connection factory or an address.
 */
public class Role implements Serializable {

   public static final String SEND_PERMISSION = "send";
   public static final String CONSUME_PERMISSION = "consume";
   public static final String CREATE_DURABLE_QUEUE_PERMISSION = "createDurableQueue";
   public static final String DELETE_DURABLE_QUEUE_PERMISSION = "deleteDurableQueue";
   public static final String CREATE_NONDURABLE_QUEUE_PERMISSION = "createNonDurableQueue";
   public static final String DELETE_NONDURABLE_QUEUE_PERMISSION = "deleteNonDurableQueue";
   public static final String MANAGE_PERMISSION = "manage";
   public static final String BROWSE_PERMISSION = "browse";
   public static final String CREATE_ADDRESS_PERMISSION = "createAddress";
   public static final String DELETE_ADDRESS_PERMISSION = "deleteAddress";
   public static final String VIEW_PERMISSION = "view";
   public static final String EDIT_PERMISSION = "edit";

   private static final long serialVersionUID = 3560097227776448872L;

   protected String name;

   protected boolean send;

   protected boolean consume;

   protected boolean createAddress;

   protected boolean deleteAddress;

   protected boolean createDurableQueue;

   protected boolean deleteDurableQueue;

   protected boolean createNonDurableQueue;

   protected boolean deleteNonDurableQueue;

   protected boolean manage;

   protected boolean browse;

   protected boolean view;

   protected boolean edit;

   public JsonObject toJson() {
      return JsonLoader.createObjectBuilder()
         .add("name", name)
         .add(SEND_PERMISSION, send)
         .add(CONSUME_PERMISSION, consume)
         .add(CREATE_DURABLE_QUEUE_PERMISSION, createDurableQueue)
         .add(DELETE_DURABLE_QUEUE_PERMISSION, deleteDurableQueue)
         .add(CREATE_NONDURABLE_QUEUE_PERMISSION, createNonDurableQueue)
         .add(DELETE_NONDURABLE_QUEUE_PERMISSION, deleteNonDurableQueue)
         .add(MANAGE_PERMISSION, manage)
         .add(BROWSE_PERMISSION, browse)
         .add(CREATE_ADDRESS_PERMISSION, createAddress)
         .add(DELETE_ADDRESS_PERMISSION, deleteAddress)
         .add(VIEW_PERMISSION, view)
         .add(EDIT_PERMISSION, edit)
         .build();
   }

   public Role() {
      // for properties config
   }

   /**
    * @deprecated Use {@link #Role(String, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean)}
    */
   @Deprecated(forRemoval = true)
   public Role(final String name,
               final boolean send,
               final boolean consume,
               final boolean createDurableQueue,
               final boolean deleteDurableQueue,
               final boolean createNonDurableQueue,
               final boolean deleteNonDurableQueue,
               final boolean manage) {
      // This constructor exists for version compatibility on the API.
      // it will pass consume as browse
      this(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue, deleteNonDurableQueue, manage, consume);
   }

   /**
    * @deprecated Use {@link #Role(String, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean)}
    */
   @Deprecated(forRemoval = true)
   public Role(final String name,
               final boolean send,
               final boolean consume,
               final boolean createDurableQueue,
               final boolean deleteDurableQueue,
               final boolean createNonDurableQueue,
               final boolean deleteNonDurableQueue,
               final boolean manage,
               final boolean browse) {
      // This constructor exists for version compatibility on the API. If either createDurableQueue or createNonDurableQueue
      // is true then createAddress will be true. If either deleteDurableQueue or deleteNonDurableQueue is true then deleteAddress will be true.
      this(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue, deleteNonDurableQueue, manage, browse, createDurableQueue || createNonDurableQueue, deleteDurableQueue || deleteNonDurableQueue, false, false);
   }

   /**
    * @deprecated Use {@link #Role(String, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean)}
    */
   @Deprecated(forRemoval = true)
   public Role(final String name,
               final boolean send,
               final boolean consume,
               final boolean createDurableQueue,
               final boolean deleteDurableQueue,
               final boolean createNonDurableQueue,
               final boolean deleteNonDurableQueue,
               final boolean manage,
               final boolean browse,
               final boolean createAddress,
               final boolean deleteAddress) {
      this(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue, deleteNonDurableQueue, manage, browse, createAddress, deleteAddress, false, false);
   }

   public Role(final String name,
               final boolean send,
               final boolean consume,
               final boolean createDurableQueue,
               final boolean deleteDurableQueue,
               final boolean createNonDurableQueue,
               final boolean deleteNonDurableQueue,
               final boolean manage,
               final boolean browse,
               final boolean createAddress,
               final boolean deleteAddress,
               final boolean view,
               final boolean edit) {
      this.name = Objects.requireNonNull(name, "name is null");
      this.send = send;
      this.consume = consume;
      this.createAddress = createAddress;
      this.deleteAddress = deleteAddress;
      this.createDurableQueue = createDurableQueue;
      this.deleteDurableQueue = deleteDurableQueue;
      this.createNonDurableQueue = createNonDurableQueue;
      this.deleteNonDurableQueue = deleteNonDurableQueue;
      this.manage = manage;
      this.browse = browse;
      this.view = view;
      this.edit = edit;
   }

   public String getName() {
      return name;
   }

   public boolean isSend() {
      return send;
   }

   public boolean isConsume() {
      return consume;
   }

   public boolean isCreateAddress() {
      return createAddress;
   }

   public boolean isDeleteAddress() {
      return deleteAddress;
   }

   public boolean isCreateDurableQueue() {
      return createDurableQueue;
   }

   public boolean isDeleteDurableQueue() {
      return deleteDurableQueue;
   }

   public boolean isCreateNonDurableQueue() {
      return createNonDurableQueue;
   }

   public boolean isDeleteNonDurableQueue() {
      return deleteNonDurableQueue;
   }

   public boolean isManage() {
      return manage;
   }

   public boolean isBrowse() {
      return browse;
   }

   public boolean isEdit() {
      return edit;
   }

   public boolean isView() {
      return view;
   }

   public void setName(String name) {
      this.name = name;
   }

   public void setSend(boolean send) {
      this.send = send;
   }

   public void setConsume(boolean consume) {
      this.consume = consume;
   }

   public void setCreateAddress(boolean createAddress) {
      this.createAddress = createAddress;
   }

   public void setDeleteAddress(boolean deleteAddress) {
      this.deleteAddress = deleteAddress;
   }

   public void setCreateDurableQueue(boolean createDurableQueue) {
      this.createDurableQueue = createDurableQueue;
   }

   public void setDeleteDurableQueue(boolean deleteDurableQueue) {
      this.deleteDurableQueue = deleteDurableQueue;
   }

   public void setCreateNonDurableQueue(boolean createNonDurableQueue) {
      this.createNonDurableQueue = createNonDurableQueue;
   }

   public void setDeleteNonDurableQueue(boolean deleteNonDurableQueue) {
      this.deleteNonDurableQueue = deleteNonDurableQueue;
   }

   public void setManage(boolean manage) {
      this.manage = manage;
   }

   public void setBrowse(boolean browse) {
      this.browse = browse;
   }

   public void setEdit(boolean edit) {
      this.edit = edit;
   }

   public void setView(boolean view) {
      this.view = view;
   }

   @Override
   public String toString() {
      StringBuilder stringReturn = new StringBuilder("Role {name=" + name + "; allows=[");

      if (send) {
         stringReturn.append(" " + SEND_PERMISSION + " ");
      }
      if (consume) {
         stringReturn.append(" " + CONSUME_PERMISSION + " ");
      }
      if (createDurableQueue) {
         stringReturn.append(" " + CREATE_DURABLE_QUEUE_PERMISSION + " ");
      }
      if (deleteDurableQueue) {
         stringReturn.append(" " + DELETE_DURABLE_QUEUE_PERMISSION + " ");
      }
      if (createNonDurableQueue) {
         stringReturn.append(" " + CREATE_NONDURABLE_QUEUE_PERMISSION + " ");
      }
      if (deleteNonDurableQueue) {
         stringReturn.append(" " + DELETE_NONDURABLE_QUEUE_PERMISSION + " ");
      }
      if (manage) {
         stringReturn.append(" " + MANAGE_PERMISSION + " ");
      }
      if (browse) {
         stringReturn.append(" " + BROWSE_PERMISSION + " ");
      }
      if (createAddress) {
         stringReturn.append(" " + CREATE_ADDRESS_PERMISSION + " ");
      }
      if (deleteAddress) {
         stringReturn.append(" " + DELETE_ADDRESS_PERMISSION + " ");
      }
      if (view) {
         stringReturn.append(" " + VIEW_PERMISSION + " ");
      }
      if (edit) {
         stringReturn.append(" " + EDIT_PERMISSION + " ");
      }
      stringReturn.append("]}");

      return stringReturn.toString();
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof Role other)) {
         return false;
      }

      return Objects.equals(name, other.name) &&
             send == other.send &&
             consume == other.consume &&
             createDurableQueue == other.createDurableQueue &&
             createNonDurableQueue == other.createNonDurableQueue &&
             deleteDurableQueue == other.deleteDurableQueue &&
             deleteNonDurableQueue == other.deleteNonDurableQueue &&
             manage == other.manage &&
             browse == other.browse &&
             createAddress == other.createAddress &&
             deleteAddress == other.deleteAddress &&
             view == other.view &&
             edit == other.edit;
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue,
                          deleteNonDurableQueue, manage, browse, createAddress, deleteAddress, view, edit);
   }

   public void merge(Role other) {
      send = send || other.send;
      consume = consume || other.consume;
      createDurableQueue = createDurableQueue || other.createDurableQueue;
      deleteDurableQueue = deleteDurableQueue || other.deleteDurableQueue;
      createNonDurableQueue = createNonDurableQueue || other.createNonDurableQueue;
      deleteNonDurableQueue = deleteNonDurableQueue || other.deleteNonDurableQueue;
      manage = manage || other.manage;
      browse = browse || other.browse;
      createAddress = createAddress || other.createAddress;
      deleteAddress = deleteAddress || other.deleteAddress;
      view = view || other.view;
      edit = edit || other.edit;
   }
}
