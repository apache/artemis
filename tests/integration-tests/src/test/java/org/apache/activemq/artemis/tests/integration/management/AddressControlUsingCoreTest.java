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
package org.apache.activemq.artemis.tests.integration.management;

import java.util.Map;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;

public class AddressControlUsingCoreTest extends AddressControlTest {



   @Override
   protected AddressControl createManagementControl(final SimpleString name) throws Exception {
      return new AddressControl() {
         private final CoreMessagingProxy proxy = new CoreMessagingProxy(addServerLocator(createInVMNonHALocator()), ResourceNames.ADDRESS + name);

         @Override
         public String getAddress() {
            return (String) proxy.retrieveAttributeValue("address");
         }

         @Override
         public String[] getRoutingTypes() {
            return (String[]) proxy.retrieveAttributeValue("routingTypes", String.class);
         }

         @Override
         public String getRoutingTypesAsJSON() throws Exception {
            return (String) proxy.retrieveAttributeValue("routingTypesAsJSON");
         }

         @Override
         public Object[] getRoles() throws Exception {
            return (Object[]) proxy.retrieveAttributeValue("roles");
         }

         @Override
         public String getRolesAsJSON() throws Exception {
            return (String) proxy.retrieveAttributeValue("rolesAsJSON");
         }

         @Override
         public long getAddressSize() {
            return (long) proxy.retrieveAttributeValue("addressSize");
         }

         @Override
         public long getNumberOfMessages() throws Exception {
            return (long) proxy.retrieveAttributeValue("numberOfMessages");
         }

         @Override
         public String[] getRemoteQueueNames() throws Exception {
            return (String[]) proxy.retrieveAttributeValue("remoteQueueNames", String.class);
         }

         @Override
         public String[] getAllQueueNames() throws Exception {
            return (String[]) proxy.retrieveAttributeValue("allQueueNames", String.class);
         }

         @Override
         public String[] getQueueNames() throws Exception {
            return (String[]) proxy.retrieveAttributeValue("queueNames", String.class);
         }

         @Override
         public int getNumberOfPages() {
            return (int) proxy.retrieveAttributeValue("numberOfPages", Integer.class);
         }

         @Override
         public boolean isPaging() throws Exception {
            return (boolean) proxy.retrieveAttributeValue("paging");
         }

         @Override
         public long getNumberOfBytesPerPage() throws Exception {
            return (long) proxy.retrieveAttributeValue("numberOfBytesPerPage");
         }

         @Override
         public String[] getBindingNames() throws Exception {
            return (String[]) proxy.retrieveAttributeValue("bindingNames", String.class);
         }

         @Override
         public long getMessageCount() {
            return (long) proxy.retrieveAttributeValue("messageCount");
         }

         @Override
         public long getRoutedMessageCount() {
            return (long) proxy.retrieveAttributeValue("routedMessageCount");
         }

         @Override
         public long getUnRoutedMessageCount() {
            return (long) proxy.retrieveAttributeValue("unRoutedMessageCount");
         }

         @Override
         public void pause() throws Exception {
            proxy.invokeOperation("pause");
         }

         @Override
         public void pause(boolean persist) throws Exception {
            proxy.invokeOperation("pause", persist);
         }

         @Override
         public void resume() throws Exception {
            proxy.invokeOperation("resume");
         }

         @Override
         public boolean isPaused() {
            return (boolean) proxy.retrieveAttributeValue("paused");
         }

         @Override
         public boolean isRetroactiveResource() {
            return (boolean) proxy.retrieveAttributeValue("retroactiveResource");
         }

         @Override
         public long getCurrentDuplicateIdCacheSize() {
            return (long) proxy.retrieveAttributeValue("currentDuplicateIdCacheSize");
         }

         @Override
         public boolean clearDuplicateIdCache() throws Exception {
            return (boolean) proxy.invokeOperation("clearDuplicateIdCache");
         }

         @Override
         public boolean isAutoCreated() {
            return (boolean) proxy.retrieveAttributeValue("autoCreated");
         }

         @Override
         public boolean isInternal() {
            return (boolean) proxy.retrieveAttributeValue("internal");
         }

         @Override
         public boolean isTemporary() {
            return (boolean) proxy.retrieveAttributeValue("temporary");
         }

         @Override
         public long purge() throws Exception {
            return (long) proxy.invokeOperation("purge");
         }

         @Override
         public String sendMessage(Map<String, String> headers,
                                   int type,
                                   String body,
                                   boolean durable,
                                   String user,
                                   String password) throws Exception {
            return (String) proxy.invokeOperation("sendMessage", headers, type, body, durable, user, password);
         }

         @Override
         public String sendMessage(Map<String, String> headers,
                                   int type,
                                   String body,
                                   boolean durable,
                                   String user,
                                   String password,
                                   boolean createMessageId) throws Exception {
            return (String) proxy.invokeOperation("sendMessage", headers, type, body, durable, user, password, createMessageId);
         }
      };
   }


   @Override
   public boolean usingCore() {
      return true;
   }



}
