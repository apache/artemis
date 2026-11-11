/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.management.impl;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.ConnectionRouterControl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.routing.ConnectionRouter;
import org.apache.activemq.artemis.core.server.routing.targets.Target;
import org.apache.activemq.artemis.core.server.routing.targets.TargetResult;
import org.apache.activemq.artemis.utils.JsonLoader;

import org.apache.activemq.artemis.json.JsonObjectBuilder;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.NotCompliantMBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import java.util.Map;
import java.util.Objects;

public class ConnectionRouterControlImpl extends AbstractControl implements ConnectionRouterControl {
   private final ConnectionRouter connectionRouter;


   private static CompositeType parameterType;

   private static TabularType parametersType;

   private static CompositeType transportConfigurationType;

   private static CompositeType targetType;


   public ConnectionRouterControlImpl(final ConnectionRouter connectionRouter, final StorageManager storageManager) throws NotCompliantMBeanException {
      super(ConnectionRouterControl.class, storageManager);
      this.connectionRouter = connectionRouter;
   }

   @Override
   public CompositeData getTarget(String key) throws Exception {
      TargetResult result = connectionRouter.getTarget(key);
      if (TargetResult.Status.OK == result.getStatus()) {
         CompositeData connectorData = null;
         TransportConfiguration connector = result.getTarget().getConnector();

         if (connector != null) {
            TabularData paramsData = new TabularDataSupport(getParametersType());
            for (Map.Entry<String, Object> param : connector.getParams().entrySet()) {
               paramsData.put(new CompositeDataSupport(getParameterType(), new String[]{"key", "value"},
                  new Object[]{param.getKey(), Objects.toString(param.getValue(), null)}));
            }

            connectorData = new CompositeDataSupport(getTransportConfigurationType(),
               new String[]{"name", "factoryClassName", "params"},
               new Object[]{connector.getName(), connector.getFactoryClassName(), paramsData});
         }

         return new CompositeDataSupport(getTargetCompositeType(),
                                         new String[]{"nodeID", "local", "connector"},
                                         new Object[]{result.getTarget().getNodeID(), result.getTarget().isLocal(), connectorData});
      }

      return null;
   }

   @Override
   public String getTargetAsJSON(String key) {
      TargetResult result = connectionRouter.getTarget(key);
      if (TargetResult.Status.OK == result.getStatus()) {
         TransportConfiguration connector = result.getTarget().getConnector();

         JsonObjectBuilder targetDataBuilder = JsonLoader.createObjectBuilder()
            .add("nodeID", result.getTarget().getNodeID())
            .add("local", result.getTarget().isLocal());

         if (connector == null) {
            targetDataBuilder.addNull("connector");
         } else {
            targetDataBuilder.add("connector", connector.toJson());
         }

         return targetDataBuilder.build().toString();
      }

      return null;
   }

   @Override
   public void setLocalTargetFilter(String regExp) {
      connectionRouter.setLocalTargetFilter(regExp);
   }

   @Override
   public String getLocalTargetFilter() {
      return connectionRouter.getLocalTargetFilter();
   }

   @Override
   public void setTargetKeyFilter(String regExp) {
      connectionRouter.getKeyResolver().setKeyFilter(regExp);
   }

   @Override
   public String getTargetKeyFilter() {
      return connectionRouter.getKeyResolver().getKeyFilter();
   }

   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(ConnectionRouterControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(ConnectionRouterControl.class);
   }


   private CompositeType getParameterType() throws OpenDataException {
      if (parameterType == null) {
         parameterType = new CompositeType("java.util.Map.Entry<java.lang.String, java.lang.String>",
            "Parameter", new String[]{"key", "value"}, new String[]{"Parameter key", "Parameter value"},
            new OpenType[]{SimpleType.STRING, SimpleType.STRING});
      }
      return parameterType;
   }

   private TabularType getParametersType() throws OpenDataException {
      if (parametersType == null) {
         parametersType = new TabularType("java.util.Map<java.lang.String, java.lang.String>",
            "Parameters", getParameterType(), new String[]{"key"});
      }
      return parametersType;
   }

   private CompositeType getTransportConfigurationType() throws OpenDataException {
      if (transportConfigurationType == null) {
         transportConfigurationType = new CompositeType(TransportConfiguration.class.getName(),
            "TransportConfiguration", new String[]{"name", "factoryClassName", "params"},
            new String[]{"TransportConfiguration name", "TransportConfiguration factoryClassName", "TransportConfiguration params"},
            new OpenType[]{SimpleType.STRING, SimpleType.STRING, getParametersType()});
      }
      return transportConfigurationType;
   }

   private CompositeType getTargetCompositeType() throws OpenDataException {
      if (targetType == null) {
         targetType = new CompositeType(Target.class.getName(),
            "Target", new String[]{"nodeID", "local", "connector"},
            new String[]{"Target nodeID", "Target local", "Target connector"},
            new OpenType[]{SimpleType.STRING, SimpleType.BOOLEAN, getTransportConfigurationType()});
      }
      return targetType;
   }
}
