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

import java.net.URI;
import javax.security.auth.login.LoginContext;

/**
 * Accessor for {@link OIDCMetadata} used by login modules. Helps with performance, because each
 * {@link LoginContext#login()} creates new instances of each involved
 * {@link javax.security.auth.spi.LoginModule JAAS login modules}.
 */
public interface OIDCMetadataAccess {

   /**
    * Get access to {@link OIDCMetadata} for a given OIDC provider base URI. The result may be retrieved
    * from cache.
    *
    * @param providerBaseURI URI of the OpenID Connect provider
    * @return {@link OIDCMetadata} for given OpenID Connect provider
    */
   OIDCMetadata getMetadata(URI providerBaseURI);

}
