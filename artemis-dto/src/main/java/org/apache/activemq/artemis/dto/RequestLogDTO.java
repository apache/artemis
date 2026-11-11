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
package org.apache.activemq.artemis.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "request-log")
@XmlAccessorType(XmlAccessType.FIELD)
public class RequestLogDTO {

   /**
    * the output file name of the request log
    */
   @XmlAttribute(required = true)
   public String filename;

   /**
    * append to log
    */
   @XmlAttribute
   public Boolean append;

   /**
    * the extended request log format flag
    */
   @XmlAttribute
   public Boolean extended;

   /**
    * logging of the request cookies
    */
   @Deprecated
   @XmlAttribute
   public Boolean logCookies;

   /**
    * the output file name of the request log
    */
   @Deprecated
   @XmlAttribute
   public String logTimeZone;

   /**
    * the log file name date format
    */
   @XmlAttribute
   public String filenameDateFormat;

   /**
    * the number of days before rotated log files are deleted
    */
   @XmlAttribute
   public Integer retainDays;

   /**
    * request paths that will not be logged
    */
   @XmlAttribute
   public String ignorePaths;

   /**
    * the timestamp format string for request log entries
    */
   @Deprecated
   @XmlAttribute
   public String logDateFormat;

   /**
    * the locale of the request log
    */
   @Deprecated
   @XmlAttribute
   public String logLocale;

   /**
    * logging of request processing time
    */
   @Deprecated
   @XmlAttribute
   public Boolean logLatency;

   /**
    * logging of the request hostname
    */
   @Deprecated
   @XmlAttribute
   public Boolean logServer;

   /**
    * whether the actual IP address of the connection or the IP address from the X-Forwarded-For header will be logged
    */
   @Deprecated
   @XmlAttribute
   public Boolean preferProxiedForAddress;

   /**
    * the format to use for logging; see
    * https://www.eclipse.org/jetty/javadoc/jetty-9/org/eclipse/jetty/server/CustomRequestLog.html
    */
   @XmlAttribute
   public String format;

   public String getFilename() {
      return filename;
   }

   public void setFilename(String filename) {
      this.filename = filename;
   }

   public Boolean getAppend() {
      return append;
   }

   public void setAppend(Boolean append) {
      this.append = append;
   }

   public Boolean getExtended() {
      return extended;
   }

   public void setExtended(Boolean extended) {
      this.extended = extended;
   }

   public String getFilenameDateFormat() {
      return filenameDateFormat;
   }

   public void setFilenameDateFormat(String filenameDateFormat) {
      this.filenameDateFormat = filenameDateFormat;
   }

   public Integer getRetainDays() {
      return retainDays;
   }

   public void setRetainDays(Integer retainDays) {
      this.retainDays = retainDays;
   }

   public String getIgnorePaths() {
      return ignorePaths;
   }

   public void setIgnorePaths(String ignorePaths) {
      this.ignorePaths = ignorePaths;
   }

   public String getFormat() {
      return format;
   }

   public void setFormat(String format) {
      this.format = format;
   }
}
