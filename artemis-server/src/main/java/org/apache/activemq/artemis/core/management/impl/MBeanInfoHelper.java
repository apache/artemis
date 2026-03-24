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
package org.apache.activemq.artemis.core.management.impl;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.management.Attribute;
import org.apache.activemq.artemis.api.core.management.Operation;
import org.apache.activemq.artemis.api.core.management.Parameter;

public class MBeanInfoHelper {



   public static MBeanOperationInfo[] getMBeanOperationsInfo(final Class mbeanInterface) {
      List<MBeanOperationInfo> operations = new ArrayList<>();

      for (Method method : mbeanInterface.getMethods()) {
         if (!MBeanInfoHelper.isGetterMethod(method) && !MBeanInfoHelper.isSetterMethod(method) &&
            !MBeanInfoHelper.isIsBooleanMethod(method)) {
            operations.add(MBeanInfoHelper.getOperationInfo(method));
         }
      }

      return operations.toArray(new MBeanOperationInfo[operations.size()]);
   }

   public static MBeanAttributeInfo[] getMBeanAttributesInfo(final Class mbeanInterface) {
      List<MBeanAttributeInfo> tempAttributes = new ArrayList<>();
      List<MBeanAttributeInfo> finalAttributes = new ArrayList<>();
      List<String> alreadyAdded = new ArrayList<>();

      for (Method method : mbeanInterface.getMethods()) {
         if (MBeanInfoHelper.isGetterMethod(method) || MBeanInfoHelper.isSetterMethod(method) ||
            MBeanInfoHelper.isIsBooleanMethod(method)) {
            tempAttributes.add(MBeanInfoHelper.getAttributeInfo(method));
         }
      }

      // since getters and setters will each have an MBeanAttributeInfo we need to de-duplicate
      for (MBeanAttributeInfo info1 : tempAttributes) {
         MBeanAttributeInfo infoToCopy = info1;
         for (MBeanAttributeInfo info2 : tempAttributes) {
            if (info1.getName().equals(info2.getName()) && !info1.equals(info2)) {
               infoToCopy = new MBeanAttributeInfo(info1.getName(), info1.getType().equals("void") ? info2.getType() : info1.getType(), info1.getDescription(), (info1.isReadable() || info2.isReadable()), (info1.isWritable() || info2.isWritable()), (info1.isIs() || info2.isIs()));
            }
         }
         if (!alreadyAdded.contains(infoToCopy.getName())) {
            finalAttributes.add(infoToCopy);
            alreadyAdded.add(infoToCopy.getName());
         }
      }

      return finalAttributes.toArray(new MBeanAttributeInfo[finalAttributes.size()]);
   }




   private static boolean isGetterMethod(final Method method) {
      if (!method.getName().equals("get") && method.getName().startsWith("get") &&
         method.getParameterTypes().length == 0 &&
         !method.getReturnType().equals(void.class)) {
         return true;
      }

      return false;
   }

   private static boolean isSetterMethod(final Method method) {
      if (!method.getName().equals("set") && method.getName().startsWith("set") &&
         method.getParameterTypes().length == 1 &&
         method.getReturnType().equals(void.class)) {
         return true;
      } else {
         return false;
      }
   }

   private static boolean isIsBooleanMethod(final Method method) {
      if (!method.getName().equals("is") && method.getName().startsWith("is") &&
         method.getParameterTypes().length == 0 &&
         method.getReturnType().equals(boolean.class)) {
         return true;
      } else {
         return false;
      }
   }

   private static MBeanOperationInfo getOperationInfo(final Method operation) {
      MBeanOperationInfo info = null;
      Class<?> returnType = operation.getReturnType();

      MBeanParameterInfo[] paramsInfo = MBeanInfoHelper.getParametersInfo(operation.getParameterAnnotations(), operation.getParameterTypes());

      String description = operation.getName();
      int impact = MBeanOperationInfo.UNKNOWN;

      if (operation.getAnnotation(Operation.class) != null) {
         description = operation.getAnnotation(Operation.class).desc();
         impact = operation.getAnnotation(Operation.class).impact();
      }
      info = new MBeanOperationInfo(operation.getName(), description, paramsInfo, returnType.getName(), impact);

      return info;
   }

   private static MBeanAttributeInfo getAttributeInfo(final Method operation) {
      String description = "N/A";

      if (operation.getAnnotation(Attribute.class) != null) {
         description = operation.getAnnotation(Attribute.class).desc();
      }

      MBeanAttributeInfo info = new MBeanAttributeInfo(getAttributeName(operation), operation.getReturnType().getName(), description, (isGetterMethod(operation) || isIsBooleanMethod(operation)), isSetterMethod(operation), isIsBooleanMethod(operation));

      return info;
   }

   private static String getAttributeName(Method operation) {
      String name = operation.getName();

      if (isGetterMethod(operation) || isSetterMethod(operation))
         name = operation.getName().substring(3);
      else if (isIsBooleanMethod(operation))
         name = operation.getName().substring(2);

      return name;
   }

   private static MBeanParameterInfo[] getParametersInfo(final Annotation[][] params, final Class<?>[] paramTypes) {
      MBeanParameterInfo[] paramsInfo = new MBeanParameterInfo[params.length];

      for (int i = 0; i < params.length; i++) {
         MBeanParameterInfo paramInfo = null;
         String type = paramTypes[i].getName();
         for (Annotation anno : params[i]) {
            if (Parameter.class.isInstance(anno)) {
               String name = Parameter.class.cast(anno).name();
               String description = Parameter.class.cast(anno).desc();
               paramInfo = new MBeanParameterInfo(name, type, description);
            }
         }

         if (paramInfo == null) {
            paramInfo = new MBeanParameterInfo("p " + (i + 1), type, "parameter " + (i + 1));
         }

         paramsInfo[i] = paramInfo;
      }

      return paramsInfo;
   }

}
