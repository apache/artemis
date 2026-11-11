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
package org.apache.activemq.artemis.jms.server;

import java.io.InputStream;

import org.apache.activemq.artemis.jms.server.config.JMSConfiguration;
import org.apache.activemq.artemis.jms.server.config.JMSQueueConfiguration;
import org.apache.activemq.artemis.jms.server.config.TopicConfiguration;
import org.w3c.dom.Node;

public interface JMSServerConfigParser {

   /**
    * Parse the JMS Configuration XML as a JMSConfiguration object
    */
   JMSConfiguration parseConfiguration(InputStream stream) throws Exception;

   /**
    * Parse the JMS Configuration XML as a JMSConfiguration object
    */
   JMSConfiguration parseConfiguration(Node rootnode) throws Exception;

   /**
    * Parse the topic node as a TopicConfiguration object
    *
    * @return {@link TopicConfiguration} parsed from the node
    */
   TopicConfiguration parseTopicConfiguration(Node node) throws Exception;

   /**
    * Parse the Queue Configuration node as a QueueConfiguration object
    *
    * @return {@link JMSQueueConfiguration} parsed from the node
    */
   JMSQueueConfiguration parseQueueConfiguration(Node node) throws Exception;
}
