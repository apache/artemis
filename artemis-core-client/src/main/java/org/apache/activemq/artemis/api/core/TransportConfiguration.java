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
package org.apache.activemq.artemis.api.core;

import javax.json.JsonObject;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.remoting.impl.TransportConfigurationUtil;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.UUIDGenerator;

/**
 * A TransportConfiguration is used by a client to specify connections to a server and its backup if
 * one exists.
 * <p>
 * Typically the constructors take the class name and parameters for needed to create the
 * connection. These will be different dependent on which connector is being used, i.e. Netty or
 * InVM etc. For example:
 *
 * <pre>
 * HashMap&lt;String, Object&gt; map = new HashMap&lt;String, Object&gt;();
 * map.put(&quot;host&quot;, &quot;localhost&quot;);
 * map.put(&quot;port&quot;, 61616);
 * TransportConfiguration config = new TransportConfiguration(InVMConnectorFactory.class.getName(), map);
 * ClientSessionFactory sf = new ClientSessionFactoryImpl(config);
 * </pre>
 */
public class TransportConfiguration implements Serializable {

   private static final long serialVersionUID = -3994528421527392679L;

   public static final String EXTRA_PROPERTY_PREFIX = "$.EP.";

   private String name;

   private String factoryClassName;

   private Map<String, Object> params;

   private Map<String, Object> extraProps;

   private static final byte TYPE_BOOLEAN = 0;

   private static final byte TYPE_INT = 1;

   private static final byte TYPE_LONG = 2;

   private static final byte TYPE_STRING = 3;

   public JsonObject toJson() {
      return JsonLoader.createObjectBuilder().add("name", name).add("factoryClassName", factoryClassName).add("params", JsonUtil.toJsonObject(params)).add("extraProps", JsonUtil.toJsonObject(extraProps)).build();
   }

   /**
    * Utility method for splitting a comma separated list of hosts
    *
    * @param commaSeparatedHosts the comma separated host string
    * @return the hosts
    */
   public static String[] splitHosts(final String commaSeparatedHosts) {
      if (commaSeparatedHosts == null) {
         return new String[0];
      }
      String[] hosts = commaSeparatedHosts.split(",");

      for (int i = 0; i < hosts.length; i++) {
         hosts[i] = hosts[i].trim();
      }
      return hosts;
   }

   /**
    * Creates a default TransportConfiguration with no configured transport.
    */
   public TransportConfiguration() {
      this.params = new HashMap<>();
   }

   /**
    * Creates a TransportConfiguration with a specific name providing the class name of the {@link org.apache.activemq.artemis.spi.core.remoting.ConnectorFactory}
    * and any parameters needed.
    *
    * @param className The class name of the ConnectorFactory
    * @param params    The parameters needed by the ConnectorFactory
    * @param name      The name of this TransportConfiguration
    */
   public TransportConfiguration(final String className, final Map<String, Object> params, final String name) {
      this(className, params, name, null);
   }

   /**
    * Creates a TransportConfiguration with a specific name providing the class name of the {@link org.apache.activemq.artemis.spi.core.remoting.ConnectorFactory}
    * and any parameters needed.
    *
    * @param className  The class name of the ConnectorFactory
    * @param params     The parameters needed by the ConnectorFactory
    * @param name       The name of this TransportConfiguration
    * @param extraProps The extra properties that specific to protocols
    */
   public TransportConfiguration(final String className,
                                 final Map<String, Object> params,
                                 final String name,
                                 final Map<String, Object> extraProps) {
      factoryClassName = className;

      if (params == null || params.isEmpty()) {
         this.params = TransportConfigurationUtil.getDefaults(className);
      } else {
         this.params = params;
      }

      this.name = name;
      this.extraProps = extraProps;
   }

   public TransportConfiguration newTransportConfig(String newName) {
      return new TransportConfiguration(factoryClassName, params, newName);
   }

   /**
    * Creates a TransportConfiguration providing the class name of the {@link org.apache.activemq.artemis.spi.core.remoting.ConnectorFactory}
    * and any parameters needed.
    *
    * @param className The class name of the ConnectorFactory
    * @param params    The parameters needed by the ConnectorFactory
    */
   public TransportConfiguration(final String className, final Map<String, Object> params) {
      this(className, params, UUIDGenerator.getInstance().generateStringUUID());
   }

   /**
    * Creates a TransportConfiguration providing the class name of the {@link org.apache.activemq.artemis.spi.core.remoting.ConnectorFactory}
    *
    * @param className The class name of the ConnectorFactory
    */
   public TransportConfiguration(final String className) {
      this(className, new HashMap<String, Object>(), UUIDGenerator.getInstance().generateStringUUID());
   }

   /**
    * Returns the name of this TransportConfiguration.
    *
    * @return the name
    */
   public String getName() {
      return name;
   }

   /**
    * Returns the class name of ConnectorFactory being used by this TransportConfiguration
    *
    * @return The factory's class name
    */
   public String getFactoryClassName() {
      return factoryClassName;
   }

   /**
    * Returns any parameters set for this TransportConfiguration
    *
    * @return the parameters
    */
   public Map<String, Object> getParams() {
      return params;
   }

   public Map<String, Object> getExtraParams() {
      return extraProps;
   }

   public Map<String, Object> getCombinedParams() {
      Map<String, Object> combined = new HashMap<>();
      if (params != null) {
         combined.putAll(params);
      }
      if (extraProps != null) {
         combined.putAll(extraProps);
      }
      return combined;
   }

   @Override
   public int hashCode() {
      int result = name != null ? name.hashCode() : 0;
      result = 31 * result + factoryClassName.hashCode();
      result = 31 * result + (params != null ? params.hashCode() : 0);
      return result;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;

      TransportConfiguration that = (TransportConfiguration) o;

      if (!isSameParams(that)) {
         return false;
      }

      if (!Objects.equals(name, that.name)) {
         return false;
      }

      // Empty and null extraProps maps are equivalent so the condition to check if two extraProps maps are not equal is:
      if ((extraProps != that.extraProps) && (extraProps != null || !that.extraProps.isEmpty()) && (that.extraProps != null || !extraProps.isEmpty()) && (extraProps == null || that.extraProps == null || !extraProps.equals(that.extraProps))) {
         return false;
      }

      return true;
   }

   public boolean isSameParams(TransportConfiguration that) {
      if (!factoryClassName.equals(that.factoryClassName))
         return false;
      if (!Objects.equals(params, that.params))
         return false;

      return true;
   }

   /**
    * There's a case on ClusterConnections that we need to find an equivalent Connector and we can't
    * use a Netty Cluster Connection on an InVM ClusterConnection (inVM used on tests) for that
    * reason I need to test if the two instances of the TransportConfiguration are equivalent while
    * a test a connector against an acceptor
    *
    * @param otherConfig
    * @return {@code true} if the factory class names are equivalents
    */
   public boolean isEquivalent(TransportConfiguration otherConfig) {
      if (this.getFactoryClassName().equals(otherConfig.getFactoryClassName())) {
         return true;
      } else if (this.getFactoryClassName().contains("Netty") && otherConfig.getFactoryClassName().contains("Netty")) {
         return true;
      } else if (this.getFactoryClassName().contains("InVM") && otherConfig.getFactoryClassName().contains("InVM")) {
         return true;
      } else {
         return false;
      }
   }

   @Override
   public String toString() {
      StringBuilder str = new StringBuilder(TransportConfiguration.class.getSimpleName());
      str.append("(name=" + name + ", ");
      str.append("factory=" + (factoryClassName == null ? "null" : replaceWildcardChars(factoryClassName)));
      str.append(") ");
      str.append(toStringParameters(params, extraProps));
      return str.toString();
   }

   public static String toStringParameters(Map<String, Object> params, Map<String, Object> extraProps) {
      StringBuilder str = new StringBuilder();
      if (params != null) {
         if (!params.isEmpty()) {
            str.append("?");
         }

         boolean first = true;
         for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) {
               str.append("&");
            }

            String key = entry.getKey();

            // HORNETQ-1281 - don't log passwords
            String val;
            if (key.toLowerCase().contains("password")) {
               val = "****";
            } else {
               val = entry.getValue() == null ? "null" : entry.getValue().toString();
            }

            str.append(replaceWildcardChars(key)).append('=').append(replaceWildcardChars(val));

            first = false;
         }
         if (extraProps != null) {
            for (Map.Entry<String, Object> entry : extraProps.entrySet()) {
               if (!first) {
                  str.append("&");
               }

               String key = entry.getKey();
               String val = entry.getValue() == null ? "null" : entry.getValue().toString();

               str.append(replaceWildcardChars(key)).append('=').append(replaceWildcardChars(val));

               first = false;
            }
         }
      }
      return str.toString();
   }

   private void encodeMap(final ActiveMQBuffer buffer, final Map<String, Object> map, final String prefix) {
      for (Map.Entry<String, Object> entry : map.entrySet()) {
         buffer.writeString(prefix != null ? prefix + entry.getKey() : entry.getKey());

         Object val = entry.getValue();

         if (val instanceof Boolean) {
            buffer.writeByte(TransportConfiguration.TYPE_BOOLEAN);
            buffer.writeBoolean((Boolean) val);
         } else if (val instanceof Integer) {
            buffer.writeByte(TransportConfiguration.TYPE_INT);
            buffer.writeInt((Integer) val);
         } else if (val instanceof Long) {
            buffer.writeByte(TransportConfiguration.TYPE_LONG);
            buffer.writeLong((Long) val);
         } else if (val instanceof String) {
            buffer.writeByte(TransportConfiguration.TYPE_STRING);
            buffer.writeString((String) val);
         } else {
            throw ActiveMQClientMessageBundle.BUNDLE.invalidEncodeType(val);
         }
      }
   }

   /**
    * Encodes this TransportConfiguration into a buffer.
    * <p>
    * Note that this is only used internally ActiveMQ Artemis.
    *
    * @param buffer the buffer to encode into
    */
   public void encode(final ActiveMQBuffer buffer) {
      buffer.writeString(name);
      buffer.writeString(factoryClassName);

      buffer.writeInt((params == null ? 0 : params.size()) + (extraProps == null ? 0 : extraProps.size()));

      if (params != null) {
         encodeMap(buffer, params, null);
      }
      if (extraProps != null) {
         encodeMap(buffer, extraProps, EXTRA_PROPERTY_PREFIX);
      }
   }

   /**
    * Decodes this TransportConfiguration from a buffer.
    * <p>
    * Note this is only used internally by ActiveMQ
    *
    * @param buffer the buffer to decode from
    */
   public void decode(final ActiveMQBuffer buffer) {
      name = buffer.readString();
      factoryClassName = buffer.readString();

      int num = buffer.readInt();

      if (params == null) {
         if (num > 0) {
            params = new HashMap<>();
         }
      } else {
         params.clear();
      }

      for (int i = 0; i < num; i++) {
         String key = buffer.readString();

         byte type = buffer.readByte();

         Object val;

         switch (type) {
            case TYPE_BOOLEAN: {
               val = buffer.readBoolean();

               break;
            }
            case TYPE_INT: {
               val = buffer.readInt();

               break;
            }
            case TYPE_LONG: {
               val = buffer.readLong();

               break;
            }
            case TYPE_STRING: {
               val = buffer.readString();

               break;
            }
            default: {
               throw ActiveMQClientMessageBundle.BUNDLE.invalidType(type);
            }
         }

         if (key.startsWith(EXTRA_PROPERTY_PREFIX)) {
            if (extraProps == null) {
               extraProps = new HashMap<>();
            }
            extraProps.put(key.substring(EXTRA_PROPERTY_PREFIX.length()), val);
         } else {
            params.put(key, val);
         }
      }
   }

   private static String replaceWildcardChars(final String str) {
      return str.replace('.', '-');
   }

   public void setFactoryClassName(String factoryClassName) {
      this.factoryClassName = factoryClassName;
   }
}
