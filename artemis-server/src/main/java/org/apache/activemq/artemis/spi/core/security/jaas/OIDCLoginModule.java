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
package org.apache.activemq.artemis.spi.core.security.jaas;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSecurityContextJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWKSecurityContext;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import org.apache.activemq.artemis.spi.core.security.jaas.oidc.OIDCSupport;
import org.apache.activemq.artemis.spi.core.security.jaas.oidc.OIDCSupport.ConfigKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OIDCLoginModule implements AuditLoginModule {

   public static final Logger logger = LoggerFactory.getLogger(OIDCLoginModule.class);

   // static configuration

   /**
    * JWT claims (fields) that must be present in JWT token
    */
   private static final Set<String> defaultRequiredClaims = Set.of(
         JWTClaimNames.AUDIENCE,
         JWTClaimNames.ISSUER,
         JWTClaimNames.SUBJECT,
         "azp",
         JWTClaimNames.EXPIRATION_TIME
   );

   /**
    * JWT claims (fields) that must not be present in JWT token
    */
   private static final Set<String> prohibitedClaims = Collections.emptySet();

   /**
    * JWT claims with specific values that must be present in JWT token
    */
   private static final JWTClaimsSet exactMatchClaims = new JWTClaimsSet.Builder().build();

   /**
    * Set of JWT signature algorithms we support
    */
   private static final Set<JWSAlgorithm> supportedJWSAlgorithms = new HashSet<>();

   /**
    * Key selector for JWT signature validation - crated once, because keys are fetched from the context
    */
   private static final JWSKeySelector<JWKSecurityContext> jwsKeySelector;

   // options from the configuration

   /**
    * Well known {@code debug} flag for the login module
    */
   private boolean debug;

   // JAAS state from initialization

   /**
    * Helper object instantiated in each {@link #initialize} to support with the OpenID Connect/OAuth2 login
    * process according to JAAS lifecycle
    */
   private OIDCSupport oidcSupport;

   /**
    * Discovered constructor to create instances of {@link Principal} representing user "identities"
    */
   private Constructor<Principal> userPrincipalConstructor;

   /**
    * Discovered constructor to create instances of {@link Principal} representing user "roles" (or "groups")
    */
   private Constructor<Principal> rolePrincipalConstructor;

   // Nimbus JOSE + JWT state and config from initialization

   private Subject subject;
   private CallbackHandler handler;

   /**
    * {@link JWTProcessor} created for each login process reusing some "services" for key and claim management
    */
   private ConfigurableDefaultJWTProcessor processor = null;

   /**
    * Set of required JWT claims that should be present (with any value - to be validated by different means)
    * in each processed JWT token.
    */
   private final Set<String> requiredClaims;

   /**
    * "JSON paths" to claims (possibly nested) which should point to JSON strings or JSON string arrays, which
    * contain user "identities"
    */
   private String[] identityPaths;

   /**
    * "JSON paths" to claims (possibly nested) which should point to JSON strings or JSON string arrays, which
    * contain user "roles" (or "groups")
    */
   private String[] rolesPaths;

   /**
    * <p>Flag which turns on OAuth 2.0 Mutual-TLS Client Authentication and Certificate-Bound Access Tokens
    * (RFC 8705).</p>
    * <p>{@code cnf} claim itself comes from RFC 7800 (Proof-of-Possession Key Semantics for JSON Web Tokens (JWTs))
    * and represents a proof that the token was issued for the actual sender (and was not stolen). {@code x5t#256}
    * is a specific type of proof from RFC 7515 (JSON Web Signature (JWS)) and represents an SHA-256 digest
    * of DER encoded certificate ("x5" = X.509, "t" = thumbprint).</p>
    */
   private boolean requireOAuth2MTLS;

   // state for the authentication (between login() and commit())

   /**
    * The JWT token as it arrived by the wire - which is dot-separated JTW {@code header.claims.signature}.
    */
   private String token;

   /**
    * The actual parsed (if it can be parsed) {@link JWT} to be processed during login
    */
   private JWT jwt;

   private final List<Principal> principals = new ArrayList<>();

   static {
      // don't add JWSAlgorithm.Family.HMAC_SHA - these are for symmetric keys
      supportedJWSAlgorithms.addAll(JWSAlgorithm.Family.RSA);
      supportedJWSAlgorithms.addAll(JWSAlgorithm.Family.EC);

      jwsKeySelector = new JWSVerificationKeySelector<>(supportedJWSAlgorithms, new JWKSecurityContextJWKSet());
   }

   /**
    * Public constructor to be used by {@link LoginContext}
    */
   public OIDCLoginModule() {
      this.requiredClaims = defaultRequiredClaims;
   }

   /**
    * Constructor for tests, where required claims may be configured
    *
    * @param requiredClaims tests may configure non-default required JWT claims here
    */
   OIDCLoginModule(Set<String> requiredClaims) {
      this.requiredClaims = requiredClaims == null ? defaultRequiredClaims : requiredClaims;
   }

   @Override
   public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
      this.subject = subject;
      this.handler = callbackHandler;

      debug = OIDCSupport.booleanOption(ConfigKey.DEBUG, options);
      if (debug) {
         logger.debug("OIDCLoginModule initialized with debug information");
      }

      userPrincipalConstructor = determinePrincipalConstructor(ConfigKey.USER_CLASS, options);
      rolePrincipalConstructor = determinePrincipalConstructor(ConfigKey.ROLE_CLASS, options);

      requireOAuth2MTLS = OIDCSupport.booleanOption(ConfigKey.REQUIRE_OAUTH_MTLS, options);

      // non-static helper which uses state, can be mocked, but uses static caches underneath for
      // keys and http client (see also https://issues.apache.org/jira/browse/ARTEMIS-5700)
      if (oidcSupport == null) {
         oidcSupport = new OIDCSupport(options, debug);
         oidcSupport.initialize();
      }

      boolean allowPlainJWT = OIDCSupport.booleanOption(ConfigKey.ALLOW_PLAIN_JWT, options);
      String[] audiences = OIDCSupport.stringArrayOption(ConfigKey.AUDIENCE, options);
      Set<String> audience = audiences == null ? null : new HashSet<>(Arrays.asList(audiences));
      int maxClockSkew = OIDCSupport.intOption(ConfigKey.MAX_CLOCK_SKEW_SECONDS, options);

      DefaultJWTClaimsVerifier<JWKSecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>(
            audience, exactMatchClaims, requiredClaims, prohibitedClaims
      );
      claimsVerifier.setMaxClockSkew(maxClockSkew);

      // processor is created for each login, because audience is configured from the options
      processor = new ConfigurableDefaultJWTProcessor(allowPlainJWT);
      processor.setJWSKeySelector(jwsKeySelector);
      processor.setJWTClaimsSetVerifier(claimsVerifier);

      // configuration for what to extract from the token
      identityPaths = OIDCSupport.stringArrayOption(ConfigKey.IDENTITY_PATHS, options);
      rolesPaths = OIDCSupport.stringArrayOption(ConfigKey.ROLES_PATHS, options);
   }

   @Override
   public boolean login() throws LoginException {
      if (handler == null) {
         throw new LoginException("No callback handler available to retrieve the JWT token");
      }
      JWT jwt;
      JWTClaimsSet claims = null;
      try {
         CertificateCallback x509Callback = null;
         JwtCallback jwtCallback = new JwtCallback();
         if (requireOAuth2MTLS) {
            x509Callback = new CertificateCallback();
            handler.handle(new Callback[] {x509Callback, jwtCallback});
         } else {
            handler.handle(new Callback[] {jwtCallback});
         }

         String token = jwtCallback.getJwtToken();

         if (token == null) {
            // no token at all. returning false here will allow other modules to check other credentials
            // which may have arrived
            return false;
         }

         // first parse
         jwt = JWTParser.parse(token);

         // get the claims before validating (in case we want to add some info to the logs)
         claims = jwt.getJWTClaimsSet();

         // then validate
         validateToken(jwt);

         // keep only if parsed & validated
         this.token = token;
         this.jwt = jwt;

         // we may want to verify the proof of possession even if it's not required, but the claim is
         // present in the token
         String thumbprint = OIDCSupport.getThumbprint(claims);
         X509Certificate[] certificates = x509Callback == null ? new X509Certificate[0] : x509Callback.getCertificates();

         if (requireOAuth2MTLS || thumbprint != null) {
            String msg = null;
            if (certificates == null || certificates.length == 0) {
               if (thumbprint == null) {
                  msg = "OAuth2 mTLS failed - no certificates found in transport layer";
               } else {
                  msg = "OAuth2 mTLS failed - cnf/x5t#256 present, but no certificates found in transport layer";
               }
            }
            if (!OIDCSupport.tlsCertificateMatching(certificates, claims, debug)) {
               String ref = OIDCSupport.getTokenSummary(claims);
               if (certificates != null && certificates.length > 0) {
                  msg = "OAuth2 mTLS failed - certificate from the transport layer doesn't match cnf/x5t#256 thumbprint"
                        + (ref == null ? "" : " (" + ref + ")");
               }
               if (debug) {
                  logger.error(msg);
               }
               throw new LoginException(msg);
            }
         }

         if (debug) {
            if (requireOAuth2MTLS) {
               logger.debug("JAAS login successful for JWT token with {} and X.509 thumbprint {}",
                     OIDCSupport.getTokenSummary(claims),
                     OIDCSupport.stringArrayForPath(claims, "cnf.x5t#256").value()[0]);
            } else {
               logger.debug("JAAS login successful for JWT token with {}", OIDCSupport.getTokenSummary(claims));
            }
         }

         return true;
      } catch (IOException | UnsupportedCallbackException e) {
         throw new LoginException("Can't obtain the JWT token: " + e.getMessage());
      } catch (ParseException e) {
         // invalid token - base64 error, JSON error or similar
         if (debug) {
            logger.error("JWT parsing error: {}", e.getMessage());
         }
         throw new LoginException("JWT parsing error: " + e.getMessage());
      } catch (BadJOSEException | JOSEException e) {
         String ref = OIDCSupport.getTokenSummary(claims);
         String msg = e.getMessage() + (ref == null ? "" : " (" + ref + ")");
         // invalid token - for example decryption error or claim validation error
         if (debug) {
            logger.error("JWT processing error: {}", msg);
         }
         throw new LoginException("JWT processing error: " + msg);
      }
   }

   @Override
   public boolean commit() throws LoginException {
      if (this.jwt != null) {
         try {
            JWTClaimsSet claims = this.jwt.getJWTClaimsSet();
            // let's extract anything we can from the token we got in login()
            this.subject.getPrivateCredentials().add(jwt);
            this.subject.getPrivateCredentials().add(token);

            if (identityPaths != null) {
               Set<String> userPrincipalNames = new LinkedHashSet<>();
               for (String identityPath : this.identityPaths) {
                  OIDCSupport.JWTStringArray users = OIDCSupport.stringArrayForPath(claims, identityPath);
                  if (!users.valid()) {
                     throw new LoginException("Can't determine user identity from JWT using \"" + identityPath + "\" path");
                  }
                  userPrincipalNames.addAll(Arrays.asList(users.value()));
               }
               if (debug) {
                  logger.debug("Found identities: {}", String.join(", ", userPrincipalNames));
               }
               for (String n : userPrincipalNames) {
                  Principal user = userPrincipalConstructor.newInstance(n);
                  principals.add(user);
                  this.subject.getPrincipals().add(user);
               }
            }

            if (rolesPaths != null) {
               Set<String> rolePrincipalNames = new LinkedHashSet<>();
               for (String rolePath : this.rolesPaths) {
                  OIDCSupport.JWTStringArray roles = OIDCSupport.stringArrayForPath(claims, rolePath);
                  if (!roles.valid()) {
                     throw new LoginException("Can't determine user role from JWT using \"" + rolePath + "\" path");
                  }
                  rolePrincipalNames.addAll(Arrays.asList(roles.value()));
               }
               if (debug) {
                  logger.debug("Found roles: {}", String.join(", ", rolePrincipalNames));
               }
               for (String n : rolePrincipalNames) {
                  Principal role = rolePrincipalConstructor.newInstance(n);
                  principals.add(role);
                  this.subject.getPrincipals().add(role);
               }
            }

            return true;
         } catch (ParseException e) {
            throw new LoginException("Can't process the JWT token: " + e.getMessage());
         } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new LoginException("Can't create subject's principal: " + e.getMessage());
         }
      }

      return false;
   }

   @Override
   public boolean abort() throws LoginException {
      boolean result = this.jwt != null;
      if (result) {
         this.token = null;
         this.jwt = null;
      }
      return result;
   }

   @Override
   public boolean logout() throws LoginException {
      boolean result = this.jwt != null;
      if (result) {
         // this will work only when LoginContext.logout() is called on the same instance of LoginContext
         // which was used for login() - as the LoginModule instances are stored in LoginContext
         subject.getPrivateCredentials().remove(jwt);
         subject.getPrivateCredentials().remove(token);
         principals.forEach(subject.getPrincipals()::remove);
         principals.clear();
         this.token = null;
         this.jwt = null;
      }
      return result;
   }

   void validateToken(JWT jwt) throws BadJOSEException, JOSEException {

      // See https://www.iana.org/assignments/jwt/jwt.xhtml#claims for known claims
      // Which claims do we want?
      // OAuth2 / JWT (RFC 7519):
      //  - nbf <= iat <= exp - timestamps ("not before" <= "issued at" <= "expiration")
      //     - Keycloak doesn't have a mapper for "nbf", doesn't set nbf in org.keycloak.protocol.oidc.TokenManager#initToken()
      //  - jti "JWT ID" - not needed, but one of the "Registered Claim Names" from RFC 6749 (OAuth2)
      //  - sub "Subject Identifier" - the principal that is the subject of the JWT
      //     - in client credentials grant type, this represents the client itself
      //     - in authorization code grant type, this represents the resource owner
      //  - iss "Issuer" - the principal that issued the JWT. like Keycloak realm URL
      // OpenID Connect Core 1.0:
      //  - azp "Authorized party" - the OAuth 2.0 Client ID of the party to which the token was issued (client id in Keycloak)
      //  - aud "Audience(s)" - the _target_ of the token - should be the broker to which the messages are sent
      //     - in Keycloak, with default configuration the "aud" is set to "account" and the roles are available in resource_access.account.roles
      //     - if the user (on behalf of which the token is issued) has additional "roles", related clients are added to "aud"
      // RFC 7800 (Proof-of-Possession Key Semantics for JWT): https://datatracker.ietf.org/doc/html/rfc7800#section-3.1
      //  - cnf "Confirmation"
      //     - cnf/x5t#S256 - Certificate Thumbprint - https://datatracker.ietf.org/doc/html/rfc8705#section-3.1

      processor.process(jwt, oidcSupport.currentContext());
   }

   /**
    * Set custom {@link OIDCSupport} to not use default version prepared in {@link #initialize}
    *
    * @param oidcSupport {@link OIDCSupport} class to configure this login module
    */
   void setOidcSupport(OIDCSupport oidcSupport) {
      this.oidcSupport = oidcSupport;
   }

   /**
    * Normally Artemis uses two default, {@link Principal} class implementations for "user" and "role". But
    * we are open for configuring different implementations as long as there's one-String-arg constructor.
    * @param configKey configuration key
    * @param options available options
    * @return a {@link Constructor} for creating the principal
    */
   @SuppressWarnings("unchecked")
   private Constructor<Principal> determinePrincipalConstructor(ConfigKey configKey, Map<String, ?> options) {
      String principalClass = OIDCSupport.stringOption(configKey, options);
      ClassLoader[] loaders = new ClassLoader[] {
            OIDCSupport.class.getClassLoader(),
            Thread.currentThread().getContextClassLoader()
      };
      Constructor<Principal> constructor = null;
      for (ClassLoader classLoader : loaders) {
         try {
            Class<Principal> cls = (Class<Principal>) classLoader.loadClass(principalClass);
            if (Principal.class.isAssignableFrom(cls)) {
               constructor = cls.getConstructor(String.class);
               break;
            }
         } catch (ClassNotFoundException | NoSuchMethodException ignore) {
         }
      }

      if (constructor == null) {
         throw new IllegalArgumentException("Principal class not available, incorrect or missing 1-arg constructor: " + principalClass);
      }

      return constructor;
   }

   /**
    * Extension of the only implementation of {@link JWTProcessor}, so we can configure
    * it a bit.
    */
   static class ConfigurableDefaultJWTProcessor extends DefaultJWTProcessor<JWKSecurityContext> {

      private final boolean allowPlainJWT;

      ConfigurableDefaultJWTProcessor(boolean allowPlainJWT) {
         this.allowPlainJWT = allowPlainJWT;
      }

      @Override
      public JWTClaimsSet process(PlainJWT plainJWT, JWKSecurityContext context) throws BadJOSEException, JOSEException {
         if (!allowPlainJWT) {
            return super.process(plainJWT, context);
         }

         if (getJWSTypeVerifier() == null) {
            throw new BadJOSEException("Plain JWT rejected: No JWS header typ (type) verifier is configured");
         }
         getJWSTypeVerifier().verify(plainJWT.getHeader().getType(), context);

         JWTClaimsSet claimsSet = extractJWTClaimsSet(plainJWT);
         return verifyJWTClaimsSet(claimsSet, context);
      }
   }

}
