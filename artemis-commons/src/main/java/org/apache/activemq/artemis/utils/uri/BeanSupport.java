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
package org.apache.activemq.artemis.utils.uri;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.Converter;

public class BeanSupport {

   private static final BeanUtilsBean beanUtils = new BeanUtilsBean();
   private static final Map<Converter, Class> customConverters = new HashMap<>();

   static {
      // This is to customize the BeanUtils to use Fluent Properties as well
      beanUtils.getPropertyUtils().addBeanIntrospector(new FluentPropertyBeanIntrospectorWithIgnores());

      // generic converter from String to enum type via valueOf with no default
      registerConverter(new Converter() {
         @Override
         public <T> T convert(Class<T> type, Object value) {
            if (value == null) {
               return null;
            }

            if (String.class.equals(type) || Object.class.equals(type)) {
               return type.cast(value.toString());
            }
            if (type.isEnum()) {
               return (T) Enum.valueOf((Class<Enum>)type, value.toString());
            }
            throw new ConversionException("Can't convert value '" + value
                    + "' to type " + type);
         }
      }, String.class);
   }

   public static void registerConverter(Converter converter, Class type) {
      synchronized (beanUtils) {
         beanUtils.getConvertUtils().register(converter, type);
         customConverters.put(converter, type);
      }
   }

   public static void customise(BeanUtilsBean bean) {
      synchronized (beanUtils) {
         customConverters.forEach((key, value) -> bean.getConvertUtils().register(key, value));
      }
      bean.getPropertyUtils().addBeanIntrospector(new FluentPropertyBeanIntrospectorWithIgnores());
   }

   public static <P> P copyData(P source, P target) throws Exception {
      synchronized (beanUtils) {
         beanUtils.copyProperties(source, target);
      }
      return target;
   }

   public static <P> P setData(URI uri, P obj, Map<String, String> query) throws Exception {
      synchronized (beanUtils) {
         beanUtils.setProperty(obj, "host", uri.getHost());
         beanUtils.setProperty(obj, "port", uri.getPort());
         beanUtils.setProperty(obj, "userInfo", uri.getUserInfo());
         beanUtils.populate(obj, query);
      }
      return obj;
   }

   public static <P> P setData(P obj, Map<String, Object> data) throws Exception {
      synchronized (beanUtils) {
         beanUtils.populate(obj, data);
      }
      return obj;
   }

   public static <T> void stripPasswords(Map<String, T> properties) {
      properties.entrySet().removeIf(entry -> entry.getKey().toLowerCase().contains("password"));
   }

   public static <P> P setProperties(P bean, Properties properties)
      throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
      synchronized (beanUtils) {
         PropertyDescriptor[] descriptors = beanUtils.getPropertyUtils().getPropertyDescriptors(bean);
         for (PropertyDescriptor descriptor : descriptors) {
            if (descriptor.getReadMethod() != null && isWriteable(descriptor, null)) {
               String value = properties.getProperty(descriptor.getName());
               if (value != null) {
                  beanUtils.setProperty(bean, descriptor.getName(), value);
               }
            }
         }
      }
      return bean;
   }

   public static <P> Properties getProperties(P bean, Properties properties)
      throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
      synchronized (beanUtils) {
         PropertyDescriptor[] descriptors = beanUtils.getPropertyUtils().getPropertyDescriptors(bean);
         for (PropertyDescriptor descriptor : descriptors) {
            if (descriptor.getReadMethod() != null && isWriteable(descriptor, null)) {
               String value = beanUtils.getProperty(bean, descriptor.getName());
               if (value != null) {
                  properties.put(descriptor.getName(), value);
               }
            }
         }
      }
      return properties;
   }

   public static void setData(URI uri,
                              Map<String, Object> properties,
                              Set<String> allowableProperties,
                              Map<String, String> query,
                              Map<String, Object> extraProps) {
      if (allowableProperties.contains("scheme")) {
         properties.put("scheme", "" + uri.getScheme());
      }
      if (allowableProperties.contains("host")) {
         properties.put("host", "" + uri.getHost());
      }
      if (allowableProperties.contains("port")) {
         properties.put("port", "" + uri.getPort());
      }
      if (allowableProperties.contains("userInfo")) {
         properties.put("userInfo", "" + uri.getUserInfo());
      }
      for (Map.Entry<String, String> entry : query.entrySet()) {
         if (allowableProperties.contains(entry.getKey())) {
            properties.put(entry.getKey(), entry.getValue());
         } else {
            extraProps.put(entry.getKey(), entry.getValue());
         }
      }
   }

   public static String getData(List<String> ignored, Object... beans) throws Exception {
      StringBuilder sb = new StringBuilder();
      boolean empty = true;
      synchronized (beanUtils) {
         for (Object bean : beans) {
            if (bean != null) {
               PropertyDescriptor[] descriptors = beanUtils.getPropertyUtils().getPropertyDescriptors(bean);
               for (PropertyDescriptor descriptor : descriptors) {
                  if (descriptor.getReadMethod() != null && isWriteable(descriptor, ignored)) {
                     String value = beanUtils.getProperty(bean, descriptor.getName());
                     if (value != null) {
                        if (!empty) {
                           sb.append("&");
                        }
                        empty = false;
                        sb.append(descriptor.getName()).append("=").append(encodeURI(value));
                     }
                  }
               }
            }
         }
      }
      return sb.toString();
   }

   private static boolean isWriteable(PropertyDescriptor descriptor, List<String> ignored) {
      if (ignored != null && ignored.contains(descriptor.getName())) {
         return false;
      }
      Class<?> type = descriptor.getPropertyType();
      return (type == Double.class) ||
         (type == double.class) ||
         (type == Long.class) ||
         (type == long.class) ||
         (type == Integer.class) ||
         (type == int.class) ||
         (type == Float.class) ||
         (type == float.class) ||
         (type == Boolean.class) ||
         (type == boolean.class) ||
         (type == String.class);
   }

   public static String decodeURI(String value) {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
   }

   public static String encodeURI(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
   }

}
