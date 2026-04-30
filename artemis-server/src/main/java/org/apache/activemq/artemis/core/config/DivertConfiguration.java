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
package org.apache.activemq.artemis.core.config;

import java.io.Serializable;
import java.io.StringReader;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonString;
import org.apache.activemq.artemis.json.JsonValue;
import org.apache.activemq.artemis.utils.BufferHelper;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.UUIDGenerator;

public class DivertConfiguration implements Serializable, EncodingSupport {

   private static final long serialVersionUID = 6910543740464269629L;

   public static String NAME = "name";
   public static String ROUTING_NAME = "routing-name";
   public static String ADDRESS = "address";
   public static String FORWARDING_ADDRESS = "forwarding-address";
   public static String EXCLUSIVE = "exclusive";
   public static String FILTER_STRING = "filter-string";
   public static String TRANSFORMER_CONFIGURATION = "transformer-configuration";
   public static String ROUTING_TYPE = "routing-type";

   private String name = null;

   private String routingName = UUIDGenerator.getInstance().generateStringUUID();

   private String address = null;

   private String forwardingAddress = null;

   private boolean exclusive = ActiveMQDefaultConfiguration.isDefaultDivertExclusive();

   private String filterString = null;

   private TransformerConfiguration transformerConfiguration = null;

   private ComponentConfigurationRoutingType routingType = ComponentConfigurationRoutingType.valueOf(ActiveMQDefaultConfiguration.getDefaultDivertRoutingType());

   public DivertConfiguration() {
   }

   public DivertConfiguration(DivertConfiguration config) {
      this.name = config.getName();
      this.routingName = config.getRoutingName();
      this.address = config.getAddress();
      this.forwardingAddress = config.getForwardingAddress();
      this.exclusive = config.isExclusive();
      this.filterString = config.getFilterString();
      this.transformerConfiguration = config.getTransformerConfiguration();
      this.routingType = config.getRoutingType();
   }

   /**
    * Set the value of a parameter based on its "key" {@code String}. Valid key names and corresponding {@code static}
    * {@code final} are:
    * <ul>
    * <li>name: {@link #NAME}
    * <li>routing-name: {@link #ROUTING_NAME}
    * <li>address: {@link #ADDRESS}
    * <li>forwarding-address: {@link #FORWARDING_ADDRESS}
    * <li>exclusive: {@link #EXCLUSIVE}
    * <li>filter-string: {@link #FILTER_STRING}
    * <li>transformer-configuration: {@link #TRANSFORMER_CONFIGURATION}
    * <li>routing-type: {@link #ROUTING_TYPE}
    * </ul>
    * The {@code String}-based values will be converted to the proper value types based on the underlying property. For
    * example, if you pass the value "TRUE" for the key "exclusive" the {@code String} "TRUE" will be converted to
    * the {@code Boolean} {@code true}.
    *
    * @param key   the key to set to the value
    * @param value the value to set for the key
    * @return this {@code DivertConfiguration}
    */
   public DivertConfiguration set(String key, String value) {
      if (key != null) {
         if (key.equals(NAME)) {
            setName(value);
         } else if (key.equals(ROUTING_NAME)) {
            setRoutingName(value);
         } else if (key.equals(ADDRESS)) {
            setAddress(value);
         } else if (key.equals(FORWARDING_ADDRESS)) {
            setForwardingAddress(value);
         } else if (key.equals(EXCLUSIVE)) {
            setExclusive(Boolean.parseBoolean(value));
         } else if (key.equals(FILTER_STRING)) {
            setFilterString(value);
         } else if (key.equals(TRANSFORMER_CONFIGURATION)) {
            // create a transformer instance from a JSON string
            TransformerConfiguration transformerConfiguration = TransformerConfiguration.fromJSON(value);
            if (transformerConfiguration != null) {
               setTransformerConfiguration(transformerConfiguration);
            }
         } else if (key.equals(ROUTING_TYPE)) {
            setRoutingType(value == null ? null : ComponentConfigurationRoutingType.valueOf(value));
         }
      }
      return this;
   }

   public String getName() {
      return name;
   }

   public String getRoutingName() {
      return routingName;
   }

   public String getAddress() {
      return address;
   }

   public String getForwardingAddress() {
      return forwardingAddress;
   }

   public boolean isExclusive() {
      return exclusive;
   }

   public String getFilterString() {
      return filterString;
   }

   public TransformerConfiguration getTransformerConfiguration() {
      return transformerConfiguration;
   }

   public ComponentConfigurationRoutingType getRoutingType() {
      return routingType;
   }

   public DivertConfiguration setName(final String name) {
      this.name = name;
      return this;
   }

   /**
    * Sets the {@code routingName}. If the input is {@code null} then a random {@code routingName} will be generated.
    *
    * @param routingName the routingName to set
    */
   public DivertConfiguration setRoutingName(final String routingName) {
      if (routingName == null) {
         this.routingName = UUIDGenerator.getInstance().generateStringUUID();
      } else {
         this.routingName = routingName;
      }
      return this;
   }
   public DivertConfiguration setAddress(final String address) {
      this.address = address;
      return this;
   }

   public DivertConfiguration setForwardingAddress(final String forwardingAddress) {
      this.forwardingAddress = forwardingAddress;
      return this;
   }

   public DivertConfiguration setExclusive(final boolean exclusive) {
      this.exclusive = exclusive;
      return this;
   }

   public DivertConfiguration setFilterString(final String filterString) {
      this.filterString = filterString;
      return this;
   }

   public DivertConfiguration setTransformerConfiguration(final TransformerConfiguration transformerConfiguration) {
      this.transformerConfiguration = transformerConfiguration;
      return this;
   }

   public DivertConfiguration setRoutingType(final ComponentConfigurationRoutingType routingType) {
      this.routingType = routingType;
      return this;
   }

   /**
    * This method returns a JSON-formatted {@code String} representation of this {@code DivertConfiguration}. It is a
    * simple collection of key/value pairs. The keys used are referenced in {@link #set(String, String)}.
    *
    * @return a JSON-formatted {@code String} representation of this {@code DivertConfiguration}
    */
   public String toJSON() {
      JsonObjectBuilder builder = JsonLoader.createObjectBuilder();

      if (getName() != null) {
         builder.add(NAME, getName());
      }
      if (getRoutingName() != null) {
         builder.add(ROUTING_NAME, getRoutingName());
      }
      if (getAddress() != null) {
         builder.add(ADDRESS, getAddress());
      }
      if (getForwardingAddress() != null) {
         builder.add(FORWARDING_ADDRESS, getForwardingAddress());
      }

      builder.add(EXCLUSIVE, isExclusive());

      if (getFilterString() != null) {
         builder.add(FILTER_STRING, getFilterString());
      }

      TransformerConfiguration tc = getTransformerConfiguration();
      if (tc != null) {
         builder.add(TRANSFORMER_CONFIGURATION, tc.createJsonObjectBuilder());
      }

      if (getRoutingType() == null) {
         builder.add(ROUTING_TYPE, JsonValue.NULL);
      } else {
         builder.add(ROUTING_TYPE, getRoutingType().name());
      }

      return builder.build().toString();
   }

   /**
    * This method returns a {@code DivertConfiguration} created from the JSON-formatted input {@code String}. The input
    * should be a simple object of key/value pairs. Valid keys are referenced in {@link #set(String, String)}.
    *
    * @param jsonString json string
    * @return the {@code DivertConfiguration} created from the JSON-formatted input {@code String}
    */
   public static DivertConfiguration fromJSON(String jsonString) {
      JsonObject json = JsonLoader.readObject(new StringReader(jsonString));

      DivertConfiguration result = new DivertConfiguration();

      for (Map.Entry<String, JsonValue> entry : json.entrySet()) {
         if (entry.getValue().getValueType() == JsonValue.ValueType.STRING) {
            result.set(entry.getKey(), ((JsonString) entry.getValue()).getString());
         } else if (entry.getValue().getValueType() == JsonValue.ValueType.NULL) {
            result.set(entry.getKey(), null);
         } else {
            result.set(entry.getKey(), entry.getValue().toString());
         }
      }

      return result;
   }

   @Override
   public int hashCode() {
      return Objects.hash(address, exclusive, filterString, forwardingAddress, name, routingName,
                          transformerConfiguration, routingType);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof DivertConfiguration other)) {
         return false;
      }

      return Objects.equals(address, other.address) &&
             exclusive == other.exclusive &&
             Objects.equals(filterString, other.filterString) &&
             Objects.equals(forwardingAddress, other.forwardingAddress) &&
             Objects.equals(name, other.name) &&
             Objects.equals(routingName, other.routingName) &&
             Objects.equals(transformerConfiguration, other.transformerConfiguration) &&
             Objects.equals(routingType, other.routingType);
   }

   @Override
   public int getEncodeSize() {
      int transformerSize;
      if (transformerConfiguration != null) {
         transformerSize = BufferHelper.sizeOfNullableString(transformerConfiguration.getClassName());
         transformerSize += DataConstants.SIZE_INT;
         for (Map.Entry<String, String> entry : transformerConfiguration.getProperties().entrySet()) {
            transformerSize += BufferHelper.sizeOfNullableString(entry.getKey());
            transformerSize += BufferHelper.sizeOfNullableString(entry.getValue());
         }
      } else {
         transformerSize = DataConstants.SIZE_NULL;
      }
      int size =  BufferHelper.sizeOfNullableString(name) +
            BufferHelper.sizeOfNullableString(address) +
            BufferHelper.sizeOfNullableString(forwardingAddress) +
            BufferHelper.sizeOfNullableString(routingName) +
            DataConstants.SIZE_BOOLEAN +
            BufferHelper.sizeOfNullableString(filterString) +
            DataConstants.SIZE_BYTE + transformerSize;
      return size;
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      buffer.writeNullableString(name);
      buffer.writeNullableString(address);
      buffer.writeNullableString(forwardingAddress);
      buffer.writeNullableString(routingName);
      buffer.writeBoolean(exclusive);
      buffer.writeNullableString(filterString);
      buffer.writeByte(routingType != null ? routingType.getType() : ComponentConfigurationRoutingType.valueOf(ActiveMQDefaultConfiguration.getDefaultDivertRoutingType()).getType());
      if (transformerConfiguration != null) {
         buffer.writeNullableString(transformerConfiguration.getClassName());
         Map<String, String> properties = transformerConfiguration.getProperties();
         buffer.writeInt(properties.size());
         for (Map.Entry<String, String> entry : properties.entrySet()) {
            buffer.writeNullableString(entry.getKey());
            buffer.writeNullableString(entry.getValue());
         }
      } else {
         buffer.writeNullableString(null);
      }
   }

   @Override
   public String toString() {
      return "DivertConfiguration [" +
         "name=" + name +
         ", routingName=" + routingName +
         ", address=" + address +
         ", forwardingAddress=" + forwardingAddress +
         ", exclusive=" + exclusive +
         ", filterString=" + filterString +
         ", routing-type=" + routingType +
         ", transformerConfiguration=" + transformerConfiguration + "]";
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      name = buffer.readNullableString();
      address = buffer.readNullableString();
      forwardingAddress = buffer.readNullableString();
      routingName = buffer.readNullableString();
      exclusive = buffer.readBoolean();
      filterString = buffer.readNullableString();
      routingType = ComponentConfigurationRoutingType.getType(buffer.readByte());
      String transformerClassName = buffer.readNullableString();
      if (transformerClassName != null) {
         transformerConfiguration = new TransformerConfiguration(transformerClassName);
         int propsSize = buffer.readInt();
         for (int i = 0; i < propsSize; i++) {
            transformerConfiguration.getProperties().put(buffer.readNullableString(), buffer.readNullableString());
         }
      }
   }
}
