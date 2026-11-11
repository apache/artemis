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
package org.apache.activemq.artemis.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonString;

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

/**
 * A utility class mainly for converting back and forth between JSON and a {@code Set} of {@link Role} objects.
 * <p>
 * This is necessary because while security-settings are configured via XML and JSON as well as stored in the journal as
 * a list of role names keyed by permission type, they are handled by the broker in a {@code HierarchicalRepository} as
 * a {@code Set} of {@link Role} objects where each {@link Role} object contains the role name and a boolean for each
 * permission type. Therefore, we need methods to convert between the two representations.
 */
public class SecurityFormatter {

   /**
    * Converts a {@code Set} of {@link Role} objects into a JSON string representation.
    *
    * @param roles the {@code Set} of {@link Role} objects to be converted into JSON.
    * @return JSON representing the permission types and their associated role names. See
    * {@link ActiveMQServerControl#addSecuritySettings(String, String)} for details about the JSON structure.
    */
   public static String toJSON(Set<Role> roles) {
      JsonObjectBuilder builder = JsonLoader.createObjectBuilder();
      JsonArrayBuilder sendRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder consumeRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder createDurableQueueRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder deleteDurableQueueRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder createNonDurableQueueRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder deleteNonDurableQueueRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder manageRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder browseRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder createAddressRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder deleteAddressRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder viewRoles = JsonLoader.createArrayBuilder();
      JsonArrayBuilder editRoles = JsonLoader.createArrayBuilder();

      for (Role role : roles) {
         if (role.isSend()) {
            sendRoles.add(role.getName());
         }
         if (role.isConsume()) {
            consumeRoles.add(role.getName());
         }
         if (role.isCreateDurableQueue()) {
            createDurableQueueRoles.add(role.getName());
         }
         if (role.isDeleteDurableQueue()) {
            deleteDurableQueueRoles.add(role.getName());
         }
         if (role.isCreateNonDurableQueue()) {
            createNonDurableQueueRoles.add(role.getName());
         }
         if (role.isDeleteNonDurableQueue()) {
            deleteNonDurableQueueRoles.add(role.getName());
         }
         if (role.isManage()) {
            manageRoles.add(role.getName());
         }
         if (role.isBrowse()) {
            browseRoles.add(role.getName());
         }
         if (role.isCreateAddress()) {
            createAddressRoles.add(role.getName());
         }
         if (role.isDeleteAddress()) {
            deleteAddressRoles.add(role.getName());
         }
         if (role.isView()) {
            viewRoles.add(role.getName());
         }
         if (role.isEdit()) {
            editRoles.add(role.getName());
         }
      }
      return builder
         .add(SEND_PERMISSION, sendRoles)
         .add(CONSUME_PERMISSION, consumeRoles)
         .add(CREATE_DURABLE_QUEUE_PERMISSION, createDurableQueueRoles)
         .add(DELETE_DURABLE_QUEUE_PERMISSION, deleteDurableQueueRoles)
         .add(CREATE_NONDURABLE_QUEUE_PERMISSION, createNonDurableQueueRoles)
         .add(DELETE_NONDURABLE_QUEUE_PERMISSION, deleteNonDurableQueueRoles)
         .add(MANAGE_PERMISSION, manageRoles)
         .add(BROWSE_PERMISSION, browseRoles)
         .add(CREATE_ADDRESS_PERMISSION, createAddressRoles)
         .add(DELETE_ADDRESS_PERMISSION, deleteAddressRoles)
         .add(VIEW_PERMISSION, viewRoles)
         .add(EDIT_PERMISSION, editRoles)
         .build().toString();
   }

   /**
    * Converts the specified role permissions into a JSON string representation. Each permission category maps to an
    * array of role names.
    *
    * @param sendRoles                  a comma-separated string of role names allowed to send messages
    * @param consumeRoles               a comma-separated string of role names allowed to consume messages
    * @param createDurableQueueRoles    a comma-separated string of role names allowed to create durable queues
    * @param deleteDurableQueueRoles    a comma-separated string of role names allowed to delete durable queues
    * @param createNonDurableQueueRoles a comma-separated string of role names allowed to create non-durable queues
    * @param deleteNonDurableQueueRoles a comma-separated string of role names allowed to delete non-durable queues
    * @param manageRoles                a comma-separated string of role names allowed to manage resources
    * @param browseRoles                a comma-separated string of role names allowed to browse messages
    * @param createAddressRoles         a comma-separated string of role names allowed to create addresses
    * @param deleteAddressRoles         a comma-separated string of role names allowed to delete addresses
    * @param viewRoles                  a comma-separated string of role names allowed to view resources
    * @param editRoles                  a comma-separated string of role names allowed to edit resources
    * @return JSON representing the permission types and their associated role names. See
    * {@link ActiveMQServerControl#addSecuritySettings(String, String)} for details about the JSON structure.
    */
   public static String toJSON(final String sendRoles,
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
      return JsonLoader.createObjectBuilder()
         .add(SEND_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(sendRoles)))
         .add(CONSUME_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(consumeRoles)))
         .add(CREATE_DURABLE_QUEUE_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(createDurableQueueRoles)))
         .add(DELETE_DURABLE_QUEUE_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(deleteDurableQueueRoles)))
         .add(CREATE_NONDURABLE_QUEUE_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(createNonDurableQueueRoles)))
         .add(DELETE_NONDURABLE_QUEUE_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(deleteNonDurableQueueRoles)))
         .add(MANAGE_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(manageRoles)))
         .add(BROWSE_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(browseRoles)))
         .add(CREATE_ADDRESS_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(createAddressRoles)))
         .add(DELETE_ADDRESS_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(deleteAddressRoles)))
         .add(VIEW_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(viewRoles)))
         .add(EDIT_PERMISSION, JsonUtil.toJsonArray(toListOfRoles(editRoles)))
         .build().toString();
   }

   /**
    * Parses a JSON String to create a {@code Set} of {@link Role} objects, where each role is assigned specific
    * permissions based on the data provided in the JSON structure.
    *
    * @param json JSON representing the permission types and their associated role names. See
    *             {@link ActiveMQServerControl#addSecuritySettings(String, String)} for details about the JSON
    *             structure.
    * @return a {@code Set} of {@link Role} objects with permissions assigned according to the parsed JSON.
    */
   public static Set<Role> fromJSON(JsonObject json) {
      List<String> sendRoles = getListOfRoles(json, SEND_PERMISSION);
      List<String> consumeRoles = getListOfRoles(json, CONSUME_PERMISSION);
      List<String> createDurableQueueRoles = getListOfRoles(json, CREATE_DURABLE_QUEUE_PERMISSION);
      List<String> deleteDurableQueueRoles = getListOfRoles(json, DELETE_DURABLE_QUEUE_PERMISSION);
      List<String> createNonDurableQueueRoles = getListOfRoles(json, CREATE_NONDURABLE_QUEUE_PERMISSION);
      List<String> deleteNonDurableQueueRoles = getListOfRoles(json, DELETE_NONDURABLE_QUEUE_PERMISSION);
      List<String> manageRoles = getListOfRoles(json, MANAGE_PERMISSION);
      List<String> browseRoles = getListOfRoles(json, BROWSE_PERMISSION);
      List<String> createAddressRoles = getListOfRoles(json, CREATE_ADDRESS_PERMISSION);
      List<String> deleteAddressRoles = getListOfRoles(json, DELETE_ADDRESS_PERMISSION);
      List<String> viewRoles = getListOfRoles(json, VIEW_PERMISSION);
      List<String> editRoles = getListOfRoles(json, EDIT_PERMISSION);

      Set<String> allRoles = new HashSet<>();
      allRoles.addAll(sendRoles);
      allRoles.addAll(consumeRoles);
      allRoles.addAll(createDurableQueueRoles);
      allRoles.addAll(deleteDurableQueueRoles);
      allRoles.addAll(createNonDurableQueueRoles);
      allRoles.addAll(deleteNonDurableQueueRoles);
      allRoles.addAll(manageRoles);
      allRoles.addAll(browseRoles);
      allRoles.addAll(createAddressRoles);
      allRoles.addAll(deleteAddressRoles);
      allRoles.addAll(viewRoles);
      allRoles.addAll(editRoles);

      Set<Role> roles = new HashSet<>(allRoles.size());
      for (String role : allRoles) {
         roles.add(new Role(role,
                            sendRoles.contains(role),
                            consumeRoles.contains(role),
                            createDurableQueueRoles.contains(role),
                            deleteDurableQueueRoles.contains(role),
                            createNonDurableQueueRoles.contains(role),
                            deleteNonDurableQueueRoles.contains(role),
                            manageRoles.contains(role),
                            browseRoles.contains(role),
                            createAddressRoles.contains(role),
                            deleteAddressRoles.contains(role),
                            viewRoles.contains(role),
                            editRoles.contains(role)));
      }
      return roles;
   }

   /**
    * Creates a {@code Set} of {@link Role} objects with specific permissions based on the provided input role names.
    *
    * @param sendRoles                  a comma-separated string of role names allowed to send messages
    * @param consumeRoles               a comma-separated string of role names allowed to consume messages
    * @param createDurableQueueRoles    a comma-separated string of role names allowed to create durable queues
    * @param deleteDurableQueueRoles    a comma-separated string of role names allowed to delete durable queues
    * @param createNonDurableQueueRoles a comma-separated string of roles allowed to create non-durable queues
    * @param deleteNonDurableQueueRoles a comma-separated string of role names allowed to delete non-durable queues
    * @param manageRoles                a comma-separated string of role names with manage-level access permissions
    * @param browseRoles                a comma-separated string of role names allowed to browse messages
    * @param createAddressRoles         a comma-separated string of role names allowed to create addresses
    * @param deleteAddressRoles         a comma-separated string of role names allowed to delete addresses
    * @return a {@code Set} of {@link Role} objects representing the permissions assigned to each role
    */
   public static Set<Role> createSecurity(String sendRoles,
                                          String consumeRoles,
                                          String createDurableQueueRoles,
                                          String deleteDurableQueueRoles,
                                          String createNonDurableQueueRoles,
                                          String deleteNonDurableQueueRoles,
                                          String manageRoles,
                                          String browseRoles,
                                          String createAddressRoles,
                                          String deleteAddressRoles) {
      List<String> createDurableQueue = toListOfRoles(createDurableQueueRoles);
      List<String> deleteDurableQueue = toListOfRoles(deleteDurableQueueRoles);
      List<String> createNonDurableQueue = toListOfRoles(createNonDurableQueueRoles);
      List<String> deleteNonDurableQueue = toListOfRoles(deleteNonDurableQueueRoles);
      List<String> send = toListOfRoles(sendRoles);
      List<String> consume = toListOfRoles(consumeRoles);
      List<String> manage = toListOfRoles(manageRoles);
      List<String> browse = toListOfRoles(browseRoles);
      List<String> createAddress = toListOfRoles(createAddressRoles);
      List<String> deleteAddress = toListOfRoles(deleteAddressRoles);

      Set<String> allRoles = new HashSet<>();
      allRoles.addAll(createDurableQueue);
      allRoles.addAll(deleteDurableQueue);
      allRoles.addAll(createNonDurableQueue);
      allRoles.addAll(deleteNonDurableQueue);
      allRoles.addAll(send);
      allRoles.addAll(consume);
      allRoles.addAll(manage);
      allRoles.addAll(browse);
      allRoles.addAll(createAddress);
      allRoles.addAll(deleteAddress);

      Set<Role> roles = new HashSet<>(allRoles.size());
      for (String role : allRoles) {
         roles.add(new Role(role,
                            send.contains(role),
                            consume.contains(role),
                            createDurableQueue.contains(role),
                            deleteDurableQueue.contains(role),
                            createNonDurableQueue.contains(role),
                            deleteNonDurableQueue.contains(role),
                            manageRoles.contains(role),
                            browse.contains(role),
                            createAddressRoles.contains(role),
                            deleteAddressRoles.contains(role),
                            false,
                            false));
      }
      return roles;
   }

   /**
    * Retrieves a list of role names from security-settings JSON based on the specified permission type.
    *
    * @param jsonObject     the {@code JsonObject} containing various permission types each mapped to an array of role
    *                       names.
    * @param permissionType the specific permission type key to retrieve the corresponding list of role names from the
    *                       JSON object.
    * @return a {@code List} of strings representing the role names associated with the given permission type in the
    * security-settings JSON.
    */
   public static List<String> getListOfRoles(JsonObject jsonObject, String permissionType) {
      return jsonObject.getJsonArray(permissionType) == null ? Collections.emptyList() : jsonObject.getJsonArray(permissionType).getValuesAs((JsonString v) -> v.getString());
   }

   /**
    * Converts a comma-separated string of role names into a list of Strings. If the input string is null or empty, an
    * empty list is returned.
    *
    * @param commaSeparatedRoles the input string containing role names separated by commas
    * @return a list of Strings populated with the values from the input string, or an empty list if the input is null
    * or empty
    */
   public static List<String> toListOfRoles(final String commaSeparatedRoles) {
      if (commaSeparatedRoles == null || commaSeparatedRoles.trim().isEmpty()) {
         return Collections.emptyList();
      }
      return List.class.cast(Collections.list(new StringTokenizer(commaSeparatedRoles, ", ")));
   }
}
