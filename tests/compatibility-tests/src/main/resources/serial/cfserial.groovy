package serial

import io.netty.buffer.Unpooled
import org.apache.activemq.artemis.api.core.ActiveMQBuffer
import org.apache.activemq.artemis.api.core.ActiveMQBuffers
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper
import org.apache.activemq.artemis.jms.server.config.impl.ConnectionFactoryConfigurationImpl

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

// Create a client connection factory

import org.apache.activemq.artemis.tests.compatibility.GroovyRun;
import javax.jms.*;
import org.apache.activemq.artemis.jms.client.*

import java.nio.ByteBuffer

file = arg[0]
method = arg[1]
version = arg[2]

if (method.equals("write")) {
    List<String> transportConfigurations = new ArrayList<>();
    transportConfigurations.add("tst");    cfConfiguration = new ConnectionFactoryConfigurationImpl();
    cfConfiguration.setName("np").setConnectorNames(transportConfigurations);
    ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
    cfConfiguration.encode(buffer);
    byte[] bytes = new byte[buffer.readableBytes()];
    buffer.readBytes(bytes);



    ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(file));
    objectOutputStream.write(bytes);
    objectOutputStream.close();
} else {
    ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file))
    ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
    while (true) {
        int byteRead = inputStream.read()
        if (byteRead < 0) {
            break;
        }

        buffer.writeByte((byte)byteRead);
    }
    cfConfiguration = new ConnectionFactoryConfigurationImpl();
    cfConfiguration.decode(buffer);

    inputStream.close();
}

GroovyRun.assertEquals("np", cfConfiguration.getName())



