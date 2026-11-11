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
package org.apache.activemq.artemis.ra;

import javax.jms.JMSException;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.XAQueueConnection;
import javax.jms.XATopicConnection;

/**
 * A joint interface for {@link XATopicConnection} and {@link XAQueueConnection}.
 */
public interface ActiveMQRASessionFactory extends XATopicConnection, XAQueueConnection {

   /**
    * Error message for strict behaviour
    */
   String ISE = "This method is not applicable inside the application server. See the J2EE spec, e.g. J2EE1.4 Section 6.6";

   void addTemporaryQueue(TemporaryQueue temp);

   void addTemporaryTopic(TemporaryTopic temp);

   void closeSession(ActiveMQRASession session) throws JMSException;
}
