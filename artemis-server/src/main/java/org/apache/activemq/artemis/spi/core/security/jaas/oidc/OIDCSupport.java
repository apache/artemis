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
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.security.auth.login.LoginContext;

import com.nimbusds.jose.proc.JWKSecurityContext;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import org.apache.activemq.artemis.spi.core.security.jaas.OIDCLoginModule;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Services and helpers used by {@link OIDCLoginModule}.</p>
 *
 * <p>{@link javax.security.auth.spi.LoginModule login modules} are instantiated on each {@link LoginContext#login()},
 * so this class delegates to {@code XXXAccess} interfaces which may provide caching for better performance.</p>
 */
public class OIDCSupport {

   public static final Logger LOG = LoggerFactory.getLogger(OIDCSupport.class);

   private static final String[] idClaims = new String[] {"jti", "sid", "iat", "sub"};

   private final String providerURL;
   private final boolean debug;

   private final Map<String, ?> options;

   private URI providerBaseURI;

   /**
    * Delegated access to {@link HttpClient JDK HTTP Client} per provider URI. Optional
    * if {@link #oidcMetadataAccess} is configured externally.
    */
   private HttpClientAccess httpClientAccess = null;

   /**
    * Delegated access to OIDC metadata per provider URI. Optional - default will be provided if missing
    * during {@link #initialize initialization}.
    */
   private OIDCMetadataAccess oidcMetadataAccess = null;

   private JWKSecurityContext jwkSecurityContext = null;

   /**
    * Construct a helper object to support single {@link LoginContext#login()}. Scoped to the lifetime of
    * a single JAAS login operation.
    *
    * @param options options passed to {@link javax.security.auth.spi.LoginModule#initialize}
    * @param debug verbosity flag
    */
   public OIDCSupport(Map<String, ?> options, boolean debug) {
      String providerURL = stringOption(ConfigKey.PROVIDER_URL, options);
      if (providerURL == null) {
         throw new IllegalArgumentException("Missing OpenID Connect provider URL");
      }
      while (providerURL.endsWith("/")) {
         providerURL = providerURL.substring(providerURL.length() - 1);
      }

      this.providerURL = providerURL;
      this.debug = debug;

      this.options = options;
   }

   public static String stringOption(ConfigKey configKey, Map<String, ?> options) {
      Object v = options != null ? options.get(configKey.name) : null;

      String vs = configKey.defaultValue;
      if (v instanceof String s) {
         vs = s;
      }
      return vs;
   }

   public static boolean booleanOption(ConfigKey configKey, Map<String, ?> options) {
      Object v = options != null ? options.get(configKey.name) : null;

      if (v instanceof Boolean b) {
         return b;
      }
      if (v instanceof String s) {
         return Boolean.parseBoolean(s);
      }
      return Boolean.parseBoolean(configKey.defaultValue);
   }

   public static int intOption(ConfigKey configKey, Map<String, ?> options) {
      Object v = options != null ? options.get(configKey.name) : null;

      if (v instanceof Number n) {
         return n.intValue();
      }
      if (v instanceof String s) {
         return Integer.parseInt(s);
      }
      return Integer.parseInt(configKey.defaultValue);
   }

   public static String[] stringArrayOption(ConfigKey configKey, Map<String, ?> options) {
      Object v = options != null ? options.get(configKey.name) : null;

      String vs = configKey.defaultValue;
      if (v instanceof String s) {
         vs = s;
      }
      return vs == null ? null : vs.split("\\s*,\\s*");
   }

   /**
    * Initialize the {@link OIDCSupport}, so we can do more configuration after calling the constructor
    */
   public void initialize() {
      if (this.providerURL == null) {
         throw new IllegalArgumentException("OpenID Connect provider URL cannot be null");
      }

      if (this.oidcMetadataAccess == null) {
         if (this.httpClientAccess == null) {
            this.httpClientAccess = new SharedHttpClientAccess(options, debug);
         }
         this.oidcMetadataAccess = new SharedOIDCMetadataAccess(this.httpClientAccess, options, debug);
      }

      try {
         providerBaseURI = new URI(providerURL);
      } catch (URISyntaxException e) {
         throw new IllegalArgumentException("OpenID Connect provider URL is invalid: " + this.providerURL, e);
      }

      // this may use cached version
      initializeOIDCMetadata();
   }

   // ---- Utilities for JSON access (could be extracted when needed)

   /**
    * Handy utility method to extract String array values (single or multi element) from the "claim set"
    * of a JWT token. Used after basic token validation - mostly to extract roles for fields like
    * {@code realm_access.roles}. The extracted value should be a String or String array.
    *
    * @param claims {@link JWTClaimsSet} for a JWT
    * @param path <em>JSON path</em> for extracting specific values from the token
    * @return {@link JWTStringArray} with the extracted value and the flag for marking if the value was found
    */
   public static JWTStringArray stringArrayForPath(JWTClaimsSet claims, String path) {
      if (claims == null || claims.getClaims() == null || path == null || path.trim().isEmpty()) {
         return new JWTStringArray(null, false);
      }

      String[] segments = path.split("\\.");
      Map<?, ?> current = claims.getClaims();
      for  (int i = 0; i < segments.length; i++) {
         String segment = segments[i];
         Object v = current.get(segment);
         if (i < segments.length - 1) {
            // not the last one - should be a map
            if (!(v instanceof Map<?, ?> m)) {
               return new JWTStringArray(null, false);
            }
            current = m;
         } else {
            // the last one
            if (v instanceof String s) {
               // single String is split by whitespace
               return new JWTStringArray(createValidResult(s.trim()), true);
            } else if (v instanceof List<?> l) {
               // but String array elements are not further split by whitespace
               List<String> result = new ArrayList<>();
               int j = 0;
               for (Object v2 : l) {
                  if (v2 instanceof String s2) {
                     result.add(s2.trim());
                  } else {
                     return new JWTStringArray(null, false);
                  }
               }
               return new JWTStringArray(result.toArray(String[]::new), true);
            }
         }
      }

      return new JWTStringArray(null, false);
   }

   private static String[] createValidResult(String s) {
      return s.split("\\s+");
   }

   /**
    * Method which implements RFC 8705 OAuth 2.0 Mutual-TLS Client Authentication and Certificate-Bound Access Tokens.
    * The {@link JWT} should contain {@code cnf/x5t#256} field matching the SHA-256 of a certificate from the
    * transport layer.
    *
    * @param peerCertificates X.509 certificate for (client) peer certificates
    * @param claims {@link JWTClaimsSet} from the token which doesn't have to be fully validated (yet)
    * @param debug verbose flag
    * @return whether the {@code cnf/x5t#256} matches SHA256 digest of the peer certificate
    */
   public static boolean tlsCertificateMatching(X509Certificate[] peerCertificates, JWTClaimsSet claims, boolean debug) {
      if (claims == null || peerCertificates == null) {
         return false;
      }

      String thumbprint = getThumbprint(claims);
      if (thumbprint == null) {
         return false;
      }

      for (X509Certificate cert : peerCertificates) {
         try {
            MessageDigest md = MessageDigest.getInstance("SHA256");
            byte[] digest = md.digest(cert.getEncoded());
            String base64sha256 = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return thumbprint.equals(base64sha256);
         } catch (NoSuchAlgorithmException unexpected) {
            return false;
         } catch (CertificateEncodingException e) {
            if (debug) {
               String ref = OIDCSupport.getTokenSummary(claims);
               LOG.warn("OAuth2 mTLS failed - can't get encoded X.509 certificate{}", (ref == null ? "" : " (" + ref + ")"));
            }
            return false;
         }
      }

      return false;
   }

   public static String getThumbprint(JWTClaimsSet claims) {
      Object cnf = claims.getClaim("cnf");
      if (!(cnf instanceof Map<?, ?> confirmation)) {
         return null;
      }
      Object v = confirmation.get("x5t#256");
      if (!(v instanceof String thumbprint)) {
         return null;
      }
      return thumbprint;
   }

   /**
    * Having parsed, but not necessarily validated token, this method returns some String reference to identify
    * the token for logging purpose
    * @param claims token claims to investigate
    * @return some information from the token for logging purpose
    */
   public static String getTokenSummary(JWTClaimsSet claims) {
      if (claims != null) {
         String msg = null;
         for (String claim : idClaims) {
            Object v = claims.getClaim(claim);
            if (v != null) {
               if (v instanceof String s) {
                  msg = claim + "=" + s;
                  break;
               }
               if (v instanceof Number n) {
                  msg = claim + "=" + n.longValue();
                  break;
               }
            }
         }
         if (msg != null) {
            Object audV = claims.getClaim("aud");
            if (audV instanceof String s) {
               msg += ", aud=" + s;
            } else if (audV instanceof List<?> l) {
               msg += ", aud=[" + l.stream().map(e -> e == null ? "<null>" : e.toString()).collect(Collectors.joining(",")) + "]";
            }
         }
         return msg;
      }
      return null;
   }

   // ---- Utilities for option parsing

   private void initializeOIDCMetadata() {
      OIDCMetadata metadata = oidcMetadataAccess.getMetadata(providerBaseURI);

      // metadata can never be null, but can be invalid (no keys, no data when /.well-known/openid-configuration
      // returned 404, ...). Whatever we got, the metadata will have some context - even without any keys
      jwkSecurityContext = metadata.currentSecurityContext();
   }

   public void setHttpClientAccess(HttpClientAccess httpClientAccess) {
      this.httpClientAccess = httpClientAccess;
   }

   public void setOidcMetadataAccess(OIDCMetadataAccess oidcMetadataAccess) {
      this.oidcMetadataAccess = oidcMetadataAccess;
   }

   /**
    * Return a {@link com.nimbusds.jose.proc.SecurityContext} with keys recently synchronized with the provider.
    *
    * @return {@link JWKSecurityContext} with currently known public keys
    */
   public JWKSecurityContext currentContext() {
      return jwkSecurityContext;
   }

   /**
    * Record for returning values from {@link #stringArrayForPath} with indication if the path was correct.
    * @param value value extracted from JWT. Strings are converted to one-element String arrays. Strings with whitespace
    *              characters are first converted to multi-value tokens (but not Strings in actual arrays).
    *              Null values are always treated as invalid.
    * @param value an extracted value (can be null)
    * @param valid whether the JSON path successfully lead to actual value (String or String array)
    */
   public record JWTStringArray(String[] value, boolean valid) {
   }

   public enum ConfigKey {

      // ---- Login module configuration

      // debug level for the login module
      DEBUG("debug", "false"),

      // java.security.Principal implementation to be used for user identities
      USER_CLASS("userPrincipalClass", UserPrincipal.class.getName()),

      // java.security.Principal implementation to be used for user roles
      ROLE_CLASS("rolePrincipalClass", RolePrincipal.class.getName()),

      // ---- OIDC configuration (including HTTP Client config to access it)

      // the provider URL - something we can append /.well-known/openid-configuration to
      PROVIDER_URL("provider", null),

      // time in seconds for caching they keys from the keys endpoint of the provider
      CACHE_KEYS_TIME_SECONDS("cacheKeysSeconds", "3600"),

      // time in seconds to wait before fetching /.well-known/openid-configuration again in case of errors.
      // When initial fetch was successful, there won't be any reattempted fetching (keys are still refetched
      // periodically)
      METADATA_RETRY_TIME_SECONDS("metadataRetrySeconds", "30"),

      // TLS version to use with http client when using https protocol
      TLS_VERSION("tlsVersion", "TLSv1.3"),

      // CA certificate (PEM (single or multiple) or DER format, X.509) for building TLS context for HTTP Client
      CA_CERTIFICATE("caCertificate", null),

      // connection & read timeout for HTTP Client
      HTTP_TIMEOUT_MILLISECONDS("httpTimeout", "5000"),

      // ---- JWT configuration (fields, claims, verification)

      // whether plain JWTs are allowed ({"alg":"none"})
      ALLOW_PLAIN_JWT("allowPlainJWT", "false"),

      // time skew in seconds for nbf/exp validation
      // see com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier.maxClockSkew
      MAX_CLOCK_SKEW_SECONDS("maxClockSkew", "60"),

      // comma-separated required/expected audience ("aud" string/string[] claim)
      AUDIENCE("audience", null),

      // comma-separated "json paths" to fields (could be nested using "." separator, but no complex array navigation.
      // just field1.field2.xxx) with the identity of the caller. For Keycloak it could be:
      // "preferred_username": from "profile" client scope  -> "User Attribute" mapper, "username" field
      // "sub":                from "basic" client scope    -> "Subject (sub)" mapper
      // "client_id":          from "service_account" scope -> "User Session Note" mapper, "client_id" User Session Note
      //                       only for grant_type=client_credentials
      // "azp":                hardcoded in org.keycloak.protocol.oidc.TokenManager#initToken()
      //
      // each value referred will be added as JAAS subject "user" principal
      IDENTITY_PATHS("identityPaths", "sub"),

      // comma-separated "json paths" to JWT fields representing "roles" of the caller (subject of the JWT token).
      // In Keycloak we have "roles" scope with 3 mappers adding these 3 claims:
      // "aud" - "Audience Resolve" mapper:
      //         Adds all client_ids of "allowed" clients to the audience field of the token. Allowed client means the client
      //         for which user has at least one client role
      //         This is an "indirect" role (user -> realm/client permissions -> actual clients) representing a client
      //         for which the subject has permissions
      // "aud" - "Audience" mapper (alternative/complementary to the above):
      //         Add specified audience to the audience (aud) field of token
      //         When this mapper is added to a custom scope and this scope is added (explicitly or implicitly)
      //         we can use one of the:
      //          - "Included Client Audience" - refer to different client within Keycloak's realm
      //          - "Included Custom Audience" - just specify a value for "aud" which will be used/added to "aud" claim
      // "realm_access.roles" - "User Realm Role" mapper
      // "resource_access.${client_id}.roles" - "User Client Role" mapper
      // We could also use:
      // "scope" - whitespace-separated "scopes" which some OpenID Connect providers may interpret as roles/permissions.
      //           this claim most probably contain "openid profile email" _scopes_, but can also include other values.
      //           For example in Keycloak we can set "Include in token scope" option for each Client Scope
      //
      // There's no default, because JWT should be used to identify a subject and the roles may be loaded by
      // different login module (like LDAP).
      // Each value referred will be added as JAAS subject "role" principal
      ROLES_PATHS("rolesPaths", null),

      // Whether the token should contain cnf/x5t#256 claim according to https://datatracker.ietf.org/doc/html/rfc8705
      // When enabled, the field contains a base64url(sha256(der(client certificate))) value which SHOULD
      // match the certificate from actual mTLS (as handled by
      // org.apache.activemq.artemis.spi.core.security.jaas.CertificateLoginModule - but this module is not
      // required as a prerequisite of OIDCLoginModule, as it doesn't put the certificate into "public credentials").
      // This flag defaults to false, but when cnf/x5t#256 is present in the token, the proof-of-possession
      // validation is performed regardless.
      REQUIRE_OAUTH_MTLS("requireOAuth2MTLS", "false");

      private final String name;
      private final String defaultValue;

      ConfigKey(String name, String defaultValue) {
         this.name = name;
         this.defaultValue = defaultValue;
      }

      static ConfigKey from(String name) {
         for (ConfigKey k : values()) {
            if (k.name.equals(name)) {
               return k;
            }
         }
         return null;
      }

      public String getName() {
         return name;
      }

      public String getDefaultValue() {
         return defaultValue;
      }
   }

}
