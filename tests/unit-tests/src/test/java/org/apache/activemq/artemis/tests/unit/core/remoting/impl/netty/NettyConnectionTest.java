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
package org.apache.activemq.artemis.tests.unit.core.remoting.impl.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.remoting.ClientConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class NettyConnectionTest extends ActiveMQTestBase {

   private static final Map<String, Object> emptyMap = Collections.emptyMap();

   @Test
   public void testGetID() throws Exception {
      Channel channel = createChannel();
      NettyConnection conn = new NettyConnection(emptyMap, channel, new MyListener(), false, false);

      assertEquals(channel.id(), conn.getID());
   }

   @Test
   public void testWrite() throws Exception {
      ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(ByteBuffer.allocate(128));
      EmbeddedChannel channel = createChannel();

      assertEquals(0, channel.outboundMessages().size());

      NettyConnection conn = new NettyConnection(emptyMap, channel, new MyListener(), false, false);
      conn.write(buff);
      channel.runPendingTasks();
      assertEquals(1, channel.outboundMessages().size());
   }

   @Test
   public void testCreateBuffer() throws Exception {
      EmbeddedChannel channel = createChannel();
      NettyConnection conn = new NettyConnection(emptyMap, channel, new MyListener(), false, false);

      final int size = 1234;

      ActiveMQBuffer buff = conn.createTransportBuffer(size);
      buff.writeByte((byte) 0x00); // Netty buffer does lazy initialization.
      assertEquals(size, buff.capacity());

   }

   @Test
   public void throwsExceptionOnBlockUntilWritableIfClosed() {
      assertThrows(IllegalStateException.class, () -> {
         EmbeddedChannel channel = createChannel();
         NettyConnection conn = new NettyConnection(emptyMap, channel, new MyListener(), false, false);
         conn.close();
         //to make sure the channel is closed it needs to run the pending tasks
         channel.runPendingTasks();
         conn.blockUntilWritable(0, TimeUnit.NANOSECONDS);
      });
   }

   @Test
   public void testIsTargetNode() throws Exception {
      Map<String, Object> config = new HashMap<>();
      config.put("host", "localhost");
      config.put("port", "1234");

      Map<String, Object> config1 = new HashMap<>();
      config1.put("host", "localhost");
      config1.put("port", "1234");
      TransportConfiguration tf1 = new TransportConfiguration(NettyConnectorFactory.class.getName(), config1, "tf1");

      Map<String, Object> config2 = new HashMap<>();
      config2.put("host", "127.0.0.1");
      config2.put("port", "1234");
      TransportConfiguration tf2 = new TransportConfiguration(NettyConnectorFactory.class.getName(), config2, "tf2");

      Map<String, Object> config3 = new HashMap<>();
      config3.put("host", "otherhost");
      config3.put("port", "1234");
      TransportConfiguration tf3 = new TransportConfiguration(NettyConnectorFactory.class.getName(), config3, "tf3");

      Map<String, Object> config4 = new HashMap<>();
      config4.put("host", "127.0.0.1");
      config4.put("port", "9999");
      TransportConfiguration tf4 = new TransportConfiguration(NettyConnectorFactory.class.getName(), config4, "tf4");

      Map<String, Object> config5 = new HashMap<>();
      config5.put("host", "127.0.0.2");
      config5.put("port", "1234");
      TransportConfiguration tf5 = new TransportConfiguration(NettyConnectorFactory.class.getName(), config5, "tf5");

      Map<String, Object> config6 = new HashMap<>();
      config6.put("host", "127.0.0.2");
      config6.put("port", "1234");
      TransportConfiguration tf6 = new TransportConfiguration("some.other.FactoryClass", config6, "tf6");

      Channel channel = createChannel();
      NettyConnection conn = new NettyConnection(config, channel, new MyListener(), false, false);

      assertTrue(conn.isSameTarget(tf1));
      assertTrue(conn.isSameTarget(tf2));
      assertTrue(conn.isSameTarget(tf1, tf2));
      assertFalse(conn.isSameTarget(tf3));
      assertTrue(conn.isSameTarget(tf3, tf1));
      assertTrue(conn.isSameTarget(tf3, tf2));
      assertTrue(conn.isSameTarget(tf1, tf3));
      assertFalse(conn.isSameTarget(tf4));
      assertFalse(conn.isSameTarget(tf5));
      assertFalse(conn.isSameTarget(tf4, tf5));
      assertFalse(conn.isSameTarget(tf6));
      assertTrue(conn.isSameTarget(tf1, tf6));
      assertTrue(conn.isSameTarget(tf6, tf2));
   }

   private static EmbeddedChannel createChannel() {
      return new EmbeddedChannel(new ChannelInboundHandlerAdapter());
   }

   class MyListener implements ClientConnectionLifeCycleListener {

      @Override
      public void connectionCreated(final ActiveMQComponent component,
                                    final Connection connection,
                                    final ClientProtocolManager protocol) {

      }

      @Override
      public void connectionDestroyed(final Object connectionID, boolean failed) {

      }

      @Override
      public void connectionException(final Object connectionID, final ActiveMQException me) {

      }

      @Override
      public void connectionReadyForWrites(Object connectionID, boolean ready) {
      }

   }
}
