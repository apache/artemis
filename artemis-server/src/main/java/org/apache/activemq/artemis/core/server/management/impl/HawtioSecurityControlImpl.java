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
package org.apache.activemq.artemis.core.server.management.impl;

import org.apache.activemq.artemis.core.management.impl.AbstractControl;
import org.apache.activemq.artemis.core.management.impl.MBeanInfoHelper;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.management.GuardInvocationHandler;
import org.apache.activemq.artemis.core.server.management.HawtioSecurityControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.NotCompliantMBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.KeyAlreadyExistsException;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class HawtioSecurityControlImpl extends AbstractControl implements HawtioSecurityControl {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * The Tabular Type returned by the {@link #canInvoke(Map)} operation. The rows consist of
    * {@link #CAN_INVOKE_RESULT_ROW_TYPE} entries. It has a composite key with consists of the "ObjectName" and "Method"
    * columns.
    */
   static final TabularType CAN_INVOKE_TABULAR_TYPE = SecurityMBeanOpenTypeInitializer.TABULAR_TYPE;

   /**
    * A row as returned by the {@link #CAN_INVOKE_TABULAR_TYPE}. The columns of the row are defined by
    * {@link #CAN_INVOKE_RESULT_COLUMNS}.
    */
   static final CompositeType CAN_INVOKE_RESULT_ROW_TYPE = SecurityMBeanOpenTypeInitializer.ROW_TYPE;

   /**
    * The columns contained in a {@link #CAN_INVOKE_RESULT_ROW_TYPE}. The data types for these columns are as follows:
    * <ul>
    * <li>"ObjectName" : {@link SimpleType#STRING}</li>
    * <li>"Method" : {@link SimpleType#STRING}</li>
    * <li>"CanInvoke" : {@link SimpleType#BOOLEAN}</li>
    * </ul>
    */
   static final String[] CAN_INVOKE_RESULT_COLUMNS = SecurityMBeanOpenTypeInitializer.COLUMNS;
   private final GuardInvocationHandler mBeanServerGuard;

   public HawtioSecurityControlImpl(GuardInvocationHandler mBeanServerGuard, StorageManager storageManager) throws NotCompliantMBeanException {
      super(HawtioSecurityControl.class, storageManager);
      this.mBeanServerGuard = mBeanServerGuard;
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(HawtioSecurityControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(HawtioSecurityControl.class);
   }

   @Override
   public boolean canInvoke(String objectName) throws Exception {
      return mBeanServerGuard == null || mBeanServerGuard.canInvoke(objectName, null);
   }

   @Override
   public boolean canInvoke(String objectName, String methodName) throws Exception {
      return mBeanServerGuard == null || mBeanServerGuard.canInvoke(objectName, methodName);
   }

   @Override
   public boolean canInvoke(String objectName, String methodName, String[] argumentTypes) throws Exception {
      return mBeanServerGuard == null || mBeanServerGuard.canInvoke(objectName, methodName);
   }

   @Override
   public TabularData canInvoke(Map<String, List<String>> bulkQuery) throws Exception {
      TabularData table = new TabularDataSupport(CAN_INVOKE_TABULAR_TYPE);

      for (Map.Entry<String, List<String>> entry : bulkQuery.entrySet()) {
         String objectName = entry.getKey();
         List<String> methods = entry.getValue();
         if (methods.isEmpty()) {
            boolean res = canInvoke(objectName);
            CompositeData data = new CompositeDataSupport(CAN_INVOKE_RESULT_ROW_TYPE,
                  CAN_INVOKE_RESULT_COLUMNS,
                  new Object[]{objectName, "", res});
            table.put(data);
         } else {
            for (String method : methods) {
               List<String> argTypes = new ArrayList<>();
               String name = parseMethodName(method, argTypes);

               boolean res;
               if (name.equals(method)) {
                  res = canInvoke(objectName, name);
               } else {
                  res = canInvoke(objectName, name, argTypes.toArray(new String[]{}));
               }
               CompositeData data = new CompositeDataSupport(CAN_INVOKE_RESULT_ROW_TYPE,
                     CAN_INVOKE_RESULT_COLUMNS,
                     new Object[]{objectName, method, res});
               try {
                  table.put(data);
               } catch (KeyAlreadyExistsException e) {
                  logger.debug("Key already exists: {} (objectName = \"{}\", method = \"{}\")", e.getMessage(), objectName, method);
               }
            }
         }
      }

      return table;
   }

   private String parseMethodName(String method, List<String> argTypes) {
      method = method.trim();
      int index = method.indexOf('(');
      if (index < 0) {
         return method;
      }

      String args = method.substring(index + 1, method.length() - 1);
      argTypes.addAll(Arrays.asList(args.split(",")));

      return method.substring(0, index);
   }

   // A member class is used to initialize final fields, as this needs to do some exception handling...
   static class SecurityMBeanOpenTypeInitializer {
      private static final String[] COLUMNS = new String[]{"ObjectName", "Method", "CanInvoke"};
      private static final CompositeType ROW_TYPE;

      static {
         try {
            ROW_TYPE = new CompositeType("CanInvokeRowType",
                  "The rows of a CanInvokeTabularType table.",
                  COLUMNS,
                  new String[]{
                     "The ObjectName of the MBean checked",
                     "The Method to checked. This can either be a bare method name which means 'any method with this name' " +
                           "or a specific overload such as foo(java.lang.String). If an empty String is returned this means 'any' method.",
                     "true if the method or mbean can potentially be invoked by the current user."},
                  new OpenType[]{SimpleType.STRING, SimpleType.STRING, SimpleType.BOOLEAN}
            );
         } catch (OpenDataException e) {
            throw new RuntimeException(e);
         }
      }

      private static final TabularType TABULAR_TYPE;

      static {
         try {
            TABULAR_TYPE = new TabularType("CanInvokeTabularType", "Result of canInvoke() bulk operation", ROW_TYPE,
                  new String[]{"ObjectName", "Method"});
         } catch (OpenDataException e) {
            throw new RuntimeException(e);
         }
      }
   }
}
