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
package org.apache.activemq.artemis.spi.core.security.jaas.oidc;

import javax.security.auth.login.LoginContext;
import java.net.URI;
import java.net.http.HttpClient;

/**
 * Accessor for {@link HttpClient} used by login modules. The <em>default</em> implementation should return
 * cached instance, because {@link javax.security.auth.spi.LoginModule login modules} are instantiated on each
 * {@link LoginContext#login()}, but for test purposes we could return mocked instance.
 */
public interface HttpClientAccess {

   /**
    * Get an instance of {@link HttpClient JDK HTTP client} to be used for OIDC operations like getting keys or
    * OIDC metadata. Could be used by {@link org.apache.activemq.artemis.spi.core.security.jaas.KubernetesLoginModule}
    * too if needed. When {@code baseURI} is passed, we may get a cached/shared client instance configured for this
    * specific URI.
    *
    * @param baseURI URI for caching purpose
    * @return {@link HttpClient}
    */
   HttpClient getClient(URI baseURI);

}
