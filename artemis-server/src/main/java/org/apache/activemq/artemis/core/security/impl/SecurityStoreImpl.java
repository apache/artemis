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
package org.apache.activemq.artemis.core.security.impl;

import javax.security.auth.Subject;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.management.impl.ManagementRemotingConnection;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.management.NotificationService;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.HierarchicalRepositoryChangeListener;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager2;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager3;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager4;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager5;
import org.apache.activemq.artemis.spi.core.security.jaas.NoCacheLoginException;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.CertificateUtil;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.utils.CertificateUtil.CERT_SUBJECT_DN_UNAVAILABLE;

/**
 * The Apache Artemis SecurityStore implementation
 */
public class SecurityStoreImpl implements SecurityStore, HierarchicalRepositoryChangeListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final byte[] CACHE_KEY_SEPARATOR = new byte[]{0};

   private final HierarchicalRepository<Set<Role>> securityRepository;

   private final ActiveMQSecurityManager securityManager;

   private final Cache<String, ConcurrentHashSet<SimpleString>> authorizationCache;

   private final Cache<String, Pair<Boolean, Subject>> authenticationCache;

   private boolean securityEnabled;

   private final String managementClusterUser;

   private final String managementClusterPassword;

   private final NotificationService notificationService;

   private static final AtomicLongFieldUpdater<SecurityStoreImpl> AUTHENTICATION_SUCCESS_COUNT_UPDATER = AtomicLongFieldUpdater.newUpdater(SecurityStoreImpl.class, "authenticationSuccessCount");
   private volatile long authenticationSuccessCount;
   private static final AtomicLongFieldUpdater<SecurityStoreImpl> AUTHENTICATION_FAILURE_COUNT_UPDATER = AtomicLongFieldUpdater.newUpdater(SecurityStoreImpl.class, "authenticationFailureCount");
   private volatile long authenticationFailureCount;
   private static final AtomicLongFieldUpdater<SecurityStoreImpl> AUTHORIZATION_SUCCESS_COUNT_UPDATER = AtomicLongFieldUpdater.newUpdater(SecurityStoreImpl.class, "authorizationSuccessCount");
   private volatile long authorizationSuccessCount;
   private static final AtomicLongFieldUpdater<SecurityStoreImpl> AUTHORIZATION_FAILURE_COUNT_UPDATER = AtomicLongFieldUpdater.newUpdater(SecurityStoreImpl.class, "authorizationFailureCount");
   private volatile long authorizationFailureCount;

   private static final MessageDigest SHA256 = getDigestInstance();
   private static volatile boolean messageDigestCnsLogged = false;

   /**
    * @param notificationService can be {@code null}
    */
   public SecurityStoreImpl(final HierarchicalRepository<Set<Role>> securityRepository,
                            final ActiveMQSecurityManager securityManager,
                            final long invalidationInterval,
                            final boolean securityEnabled,
                            final String managementClusterUser,
                            final String managementClusterPassword,
                            final NotificationService notificationService,
                            final long authenticationCacheSize,
                            final long authorizationCacheSize) throws NoSuchAlgorithmException {
      this.securityRepository = securityRepository;
      this.securityManager = securityManager;
      this.securityEnabled = securityEnabled;
      this.managementClusterUser = managementClusterUser;
      this.managementClusterPassword = managementClusterPassword;
      this.notificationService = notificationService;
      if (securityEnabled) {
         if (authenticationCacheSize == 0) {
            authenticationCache = null;
         } else {
            authenticationCache = Caffeine.newBuilder()
                                          .maximumSize(authenticationCacheSize)
                                          .expireAfterWrite(invalidationInterval, TimeUnit.MILLISECONDS)
                                          .recordStats()
                                          .build();
            logger.trace("Created authn cache: {}; maxSize: {}; invalidationInterval: {}", authenticationCache, authenticationCacheSize, invalidationInterval);
         }
         if (authorizationCacheSize == 0) {
            authorizationCache = null;
         } else {
            authorizationCache = Caffeine.newBuilder()
                                         .maximumSize(authorizationCacheSize)
                                         .expireAfterWrite(invalidationInterval, TimeUnit.MILLISECONDS)
                                         .recordStats()
                                         .build();
            logger.trace("Created authz cache: {}; maxSize: {}; invalidationInterval: {}", authorizationCache, authorizationCacheSize, invalidationInterval);
         }
         this.securityRepository.registerListener(this);
      } else {
         authenticationCache = null;
         authorizationCache = null;
      }
   }

   // SecurityManager implementation --------------------------------

   @Override
   public boolean isSecurityEnabled() {
      return securityEnabled;
   }

   @Override
   public void setSecurityEnabled(boolean securityEnabled) {
      this.securityEnabled = securityEnabled;
   }

   @Override
   public void stop() {
      securityRepository.unRegisterListener(this);
   }

   @Override
   public String authenticate(final String user,
                              final String password,
                              RemotingConnection connection) throws Exception {
      return authenticate(user, password, connection, null);
   }

   @Override
   public String authenticate(final String user,
                              final String password,
                              RemotingConnection connection,
                              String securityDomain) throws Exception {
      if (securityEnabled) {

         if (managementClusterUser.equals(user)) {
            logger.trace("Authenticating cluster admin user");

            /*
             * The special user cluster user is used for creating sessions that replicate management
             * operation between nodes
             */
            if (!managementClusterPassword.equals(password)) {
               AUTHENTICATION_FAILURE_COUNT_UPDATER.incrementAndGet(this);
               throw ActiveMQMessageBundle.BUNDLE.unableToValidateClusterUser(user);
            } else {
               AUTHENTICATION_SUCCESS_COUNT_UPDATER.incrementAndGet(this);
               return managementClusterUser;
            }
         }

         String validatedUser = null;
         boolean userIsValid = false;
         boolean check = true;

         Subject subject = null;
         String authnCacheKey = createAuthenticationCacheKey(user, password, connection);
         Pair<Boolean, Subject> cacheEntry = getAuthenticationCacheEntry(authnCacheKey);
         if (cacheEntry != null) {
            if (!cacheEntry.getA()) {
               // cached authentication failed previously so don't check again
               check = false;
            } else {
               // cached authentication succeeded previously so don't check again
               check = false;
               userIsValid = true;
               subject = cacheEntry.getB();
               validatedUser = getUserFromSubject(subject);
            }
         } else {
            if (user == null && password == null && connection instanceof ManagementRemotingConnection) {
               subject = SecurityManagerShim.currentSubject();
               if (subject != null) {
                  check = false;
                  userIsValid = true;
                  validatedUser = getUserFromSubject(subject);
               }
            }
         }
         if (check) {
            if (securityManager instanceof ActiveMQSecurityManager5 manager5) {
               try {
                  subject = manager5.authenticate(user, password, connection, securityDomain);
                  if (validateExpectedUserPrincipal(subject)) {
                     validatedUser = getUserFromSubject(subject);
                     putAuthenticationCacheEntry(authnCacheKey, subject);
                  }
               } catch (NoCacheLoginException e) {
                  handleNoCacheLoginException(e);
               }
            } else if (securityManager instanceof ActiveMQSecurityManager4 manager4) {
               validatedUser = manager4.validateUser(user, password, connection, securityDomain);
            } else if (securityManager instanceof ActiveMQSecurityManager3 manager3) {
               validatedUser = manager3.validateUser(user, password, connection);
            } else if (securityManager instanceof ActiveMQSecurityManager2 manager2) {
               userIsValid = manager2.validateUser(user, password, CertificateUtil.getCertsFromConnection(connection));
            } else {
               userIsValid = securityManager.validateUser(user, password);
            }
         }

         // authentication failed, send a notification & throw an exception
         if (!userIsValid && validatedUser == null) {
            authenticationFailed(user, connection);
         }

         if (connection != null) {
            connection.setSubject(subject);
         }
         if (AuditLogger.isResourceLoggingEnabled()) {
            if (connection != null) {
               AuditLogger.userSuccesfullyAuthenticatedInAudit(subject, connection.getRemoteAddress(), connection.getID().toString());
            } else {
               AuditLogger.userSuccesfullyAuthenticatedInAudit(subject, null, null);
            }
         }

         AUTHENTICATION_SUCCESS_COUNT_UPDATER.incrementAndGet(this);
         return validatedUser;
      }

      return null;
   }

   /*
    * Verify that the Subject (if not null) contains at least one instance of the expected java.security.Principal
    * implementation. This check is done before any caching because a failure here is considered an infrastructure
    * failure and not something which should be cached as opposed to a "normal" authentication failure (e.g. wrong
    * password) which should be cached.
    */
   private boolean validateExpectedUserPrincipal(Subject subject) throws ClassNotFoundException {
      if (subject != null) {
         Class expectedPrincipal = UserPrincipal.class;
         if (securityManager instanceof ActiveMQJAASSecurityManager jaasManager) {
            expectedPrincipal = Class.forName(jaasManager.getUserPrincipalClass());
         }

         if (subject.getPrincipals(expectedPrincipal).size() == 0) {
            ActiveMQServerLogger.LOGGER.illegalPrincipal(subject.getPrincipals().stream().map(p -> p.getClass().getName() + " (" + p.getName() + ")").collect(Collectors.joining(", ")));
            return false;
         }
      }
      return true;
   }

   @Override
   public void check(final SimpleString address,
                     final CheckType checkType,
                     final SecurityAuth session) throws Exception {
      check(address, null, checkType, session);
   }

   @Override
   public void check(final SimpleString address,
                     final SimpleString queue,
                     final CheckType checkType,
                     final SecurityAuth session) throws Exception {
      if (securityEnabled) {
         SimpleString bareAddress = CompositeAddress.extractAddressName(address);
         SimpleString bareQueue = CompositeAddress.extractQueueName(queue);

         logger.trace("checking access permissions to {}", bareAddress);

         // bypass permission checks for management cluster user
         String user = session.getUsername();
         if (managementClusterUser.equals(user) && session.getPassword().equals(managementClusterPassword)) {
            AUTHORIZATION_SUCCESS_COUNT_UPDATER.incrementAndGet(this);
            return;
         }

         Set<Role> roles = securityRepository.getMatch(bareAddress.toString());

         // If a valid queue is passed in and there's an exact match for the FQQN then use the FQQN instead of the address
         SimpleString fqqn = null;
         if (bareQueue != null) {
            fqqn = CompositeAddress.toFullyQualified(bareAddress, bareQueue);
            if (securityRepository.containsExactMatch(fqqn.toString())) {
               roles = securityRepository.getMatch(fqqn.toString());
            }
         }

         if (checkAuthorizationCache(fqqn != null  ? fqqn : bareAddress, user, checkType)) {
            AUTHORIZATION_SUCCESS_COUNT_UPDATER.incrementAndGet(this);
            return;
         }

         final Boolean validated;
         if (securityManager instanceof ActiveMQSecurityManager5 manager5) {
            Subject subject = getSubjectForAuthorization(session, manager5);

            /*
             * A user may authenticate successfully at first, but then later when their Subject is evicted from the
             * local cache re-authentication may fail. This could happen, for example, if the user was removed from LDAP
             * or the user's token expired.
             *
             * If the subject is null then authorization will *always* fail.
             */
            if (subject == null) {
               authenticationFailed(user, session.getRemotingConnection());
            }

            validated = manager5.authorize(subject, roles, checkType, fqqn != null ? fqqn.toString() : bareAddress.toString());
         } else if (securityManager instanceof ActiveMQSecurityManager4 manager4) {
            validated = manager4.validateUserAndRole(user, session.getPassword(), roles, checkType, bareAddress.toString(), session.getRemotingConnection(), session.getSecurityDomain()) != null;
         } else if (securityManager instanceof ActiveMQSecurityManager3 manager3) {
            validated = manager3.validateUserAndRole(user, session.getPassword(), roles, checkType, bareAddress.toString(), session.getRemotingConnection()) != null;
         } else if (securityManager instanceof ActiveMQSecurityManager2 manager2) {
            validated = manager2.validateUserAndRole(user, session.getPassword(), roles, checkType, bareAddress.toString(), session.getRemotingConnection());
         } else {
            validated = securityManager.validateUserAndRole(user, session.getPassword(), roles, checkType);
         }

         if (!validated) {
            if (notificationService != null) {
               TypedProperties props = new TypedProperties();

               props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, bareAddress);
               props.putSimpleStringProperty(ManagementHelper.HDR_CHECK_TYPE, SimpleString.of(checkType.toString()));
               props.putSimpleStringProperty(ManagementHelper.HDR_USER, SimpleString.of(getCaller(user, session.getRemotingConnection().getSubject())));

               Notification notification = new Notification(null, CoreNotificationType.SECURITY_PERMISSION_VIOLATION, props);

               notificationService.sendNotification(notification);
            }

            Exception ex;
            if (bareQueue == null) {
               ex = ActiveMQMessageBundle.BUNDLE.userNoPermissions(getCaller(user, session.getRemotingConnection().getSubject()), checkType, bareAddress);
            } else {
               ex = ActiveMQMessageBundle.BUNDLE.userNoPermissionsQueue(getCaller(user, session.getRemotingConnection().getSubject()), checkType, bareQueue, bareAddress);
            }
            AuditLogger.securityFailure(session.getRemotingConnection().getSubject(), session.getRemotingConnection().getRemoteAddress(), ex.getMessage(), ex);
            AUTHORIZATION_FAILURE_COUNT_UPDATER.incrementAndGet(this);
            throw ex;
         }

         // if we get here we're granted, add to the cache

         AUTHORIZATION_SUCCESS_COUNT_UPDATER.incrementAndGet(this);

         if (user == null) {
            // should get all user/pass into a subject and only cache subjects
            // till then when subject is in play, the user may be null and
            // we cannot cache as we don't have a unique key
            return;
         }

         ConcurrentHashSet<SimpleString> set;
         String key = createAuthorizationCacheKey(user, checkType);
         ConcurrentHashSet<SimpleString> act = getAuthorizationCacheEntry(key);
         if (act != null) {
            set = act;
         } else {
            set = new ConcurrentHashSet<>();
            putAuthorizationCacheEntry(set, key);
         }
         set.add(Objects.requireNonNullElse(fqqn, bareAddress));
      }
   }

   @Override
   public void onChange() {
      invalidateAuthorizationCache();
      // we don't invalidate the authentication cache here because it's not necessary
   }

   public String getUserFromSubject(Subject subject) {
      return securityManager.getUserFromSubject(subject);
   }

   public String getCaller(String user, Subject subject) {
      if (user != null) {
         return user;
      }
      return getUserFromSubject(subject);
   }

   /**
    * Get the cached Subject. If the Subject is not in the cache then authenticate again to retrieve it.
    *
    * @param session contains the authentication data
    * @return the authenticated Subject with all associated role principals or null if not authenticated or JAAS is not
    * supported by the SecurityManager.
    */
   @Override
   public Subject getSessionSubject(SecurityAuth session) {
      if (securityManager instanceof ActiveMQSecurityManager5 manager5) {
         return getSubjectForAuthorization(session, manager5);
      }
      return null;
   }

   private void authenticationFailed(String user, RemotingConnection connection) throws Exception {
      String certSubjectDN = CertificateUtil.getCertSubjectDN(connection);

      if (notificationService != null) {
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(ManagementHelper.HDR_USER, SimpleString.of(user));
         props.putSimpleStringProperty(ManagementHelper.HDR_CERT_SUBJECT_DN, SimpleString.of(certSubjectDN));
         props.putSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS, SimpleString.of(connection == null ? "null" : connection.getRemoteAddress()));

         Notification notification = new Notification(null, CoreNotificationType.SECURITY_AUTHENTICATION_VIOLATION, props);

         notificationService.sendNotification(notification);
      }

      Exception e = ActiveMQMessageBundle.BUNDLE.unableToValidateUser(connection == null ? "null" : connection.getRemoteAddress(), user, certSubjectDN);

      ActiveMQServerLogger.LOGGER.securityProblemWhileAuthenticating(e.getMessage());

      if (AuditLogger.isResourceLoggingEnabled()) {
         AuditLogger.userFailedAuthenticationInAudit(null, e.getMessage(), connection == null ? "null" : connection.getID().toString());
      }

      AUTHENTICATION_FAILURE_COUNT_UPDATER.incrementAndGet(this);
      throw e;
   }

   /**
    * Get the cached Subject. If the Subject is not in the cache then authenticate again to retrieve it.
    *
    * @param auth            contains the authentication data
    * @param securityManager used to authenticate the user if the Subject is not in the cache
    * @return the authenticated Subject with all associated role principals
    */
   private Subject getSubjectForAuthorization(SecurityAuth auth, ActiveMQSecurityManager5 securityManager) {
      if (auth.getUsername() == null && auth.getPassword() == null && auth.getRemotingConnection() instanceof ManagementRemotingConnection) {
         return SecurityManagerShim.currentSubject();
      }

      String authnCacheKey = createAuthenticationCacheKey(auth.getUsername(), auth.getPassword(), auth.getRemotingConnection());
      Pair<Boolean, Subject> cached = getAuthenticationCacheEntry(authnCacheKey);

      /*
       * We don't need to worry about the cached boolean being false as users always have to
       * successfully authenticate before requesting authorization for anything.
       */
      if (cached == null) {
         try {
            Subject subject = securityManager.authenticate(auth.getUsername(), auth.getPassword(), auth.getRemotingConnection(), auth.getSecurityDomain());
            putAuthenticationCacheEntry(authnCacheKey, subject);
            return subject;
         } catch (NoCacheLoginException e) {
            handleNoCacheLoginException(e);
            return null;
         }
      }
      return cached.getB();
   }

   private void handleNoCacheLoginException(NoCacheLoginException e) {
      logger.debug("Skipping authentication cache due to exception: {}", e.getMessage());
   }

   private void putAuthenticationCacheEntry(String key, Subject subject) {
      if (authenticationCache != null) {
         Pair<Boolean, Subject> value = new Pair<>(subject != null, subject);
         authenticationCache.put(key, value);
         logger.trace("Put into authn cache; key: {}; value: {}", key, value);
      }
   }

   private Pair<Boolean, Subject> getAuthenticationCacheEntry(String key) {
      if (authenticationCache == null) {
         return null;
      } else {
         Pair<Boolean, Subject> value = authenticationCache.getIfPresent(key);
         logger.trace("Get from authn cache; key: {}; value: {}", key, value);
         return value;
      }
   }

   private void putAuthorizationCacheEntry(ConcurrentHashSet<SimpleString> value, String key) {
      if (authorizationCache != null) {
         authorizationCache.put(key, value);
         logger.trace("Put into authz cache; key: {}; value: {}", key, value);
      }
   }

   private ConcurrentHashSet<SimpleString> getAuthorizationCacheEntry(String key) {
      if (authorizationCache == null) {
         return null;
      } else {
         ConcurrentHashSet<SimpleString> value = authorizationCache.getIfPresent(key);
         logger.trace("Get from authz cache; key: {}; value: {}", key, value);
         return value;
      }
   }

   public void invalidateAuthorizationCache() {
      if (authorizationCache != null) {
         authorizationCache.invalidateAll();
         logger.trace("Invalidated authz cache");
      }
   }

   public void invalidateAuthenticationCache() {
      if (authenticationCache != null) {
         authenticationCache.invalidateAll();
         logger.trace("Invalidated authn cache");
      }
   }

   public long getAuthenticationCacheSize() {
      if (authenticationCache == null) {
         return 0;
      } else {
         return authenticationCache.estimatedSize();
      }
   }

   public long getAuthorizationCacheSize() {
      if (authorizationCache == null) {
         return 0;
      } else {
         return authorizationCache.estimatedSize();
      }
   }

   private boolean checkAuthorizationCache(final SimpleString dest, final String user, final CheckType checkType) {
      boolean granted = false;

      ConcurrentHashSet<SimpleString> act = getAuthorizationCacheEntry(createAuthorizationCacheKey(user, checkType));
      if (act != null) {
         granted = act.contains(dest);
      }

      return granted;
   }

   protected String createAuthenticationCacheKey(String username, String password, RemotingConnection connection) {
      MessageDigest md = getDigestClone();
      if (username != null) {
         md.update(username.getBytes(StandardCharsets.UTF_8));
      }
      md.update(CACHE_KEY_SEPARATOR);
      if (password != null) {
         md.update(password.getBytes(StandardCharsets.UTF_8));
      }
      md.update(CACHE_KEY_SEPARATOR);
      String certSubjectDN = CertificateUtil.getCertSubjectDN(connection);
      if (!CERT_SUBJECT_DN_UNAVAILABLE.equals(certSubjectDN)) {
         md.update(certSubjectDN.getBytes(StandardCharsets.UTF_8));
      }
      return ByteUtil.bytesToHex(md.digest());
   }

   private static MessageDigest getDigestClone() {
      try {
         return (MessageDigest) SHA256.clone();
      } catch (CloneNotSupportedException cns) {
         // This should never happen, but just in case log it and fail safely.
         if (!messageDigestCnsLogged) {
            ActiveMQServerLogger.LOGGER.sha256CloneNotSupported(cns);
            messageDigestCnsLogged = true;
         }
         return getDigestInstance();
      }
   }

   private static MessageDigest getDigestInstance() {
      try {
         return MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
         // This should never happen. The JavaDoc for MessageDigest states that SHA-256 is always available.
         throw new RuntimeException(e);
      }
   }

   private String createAuthorizationCacheKey(String user, CheckType checkType) {
      return user + "." + checkType.name();
   }

   public Cache<String, Pair<Boolean, Subject>> getAuthenticationCache() {
      return authenticationCache;
   }

   public Cache<String, ConcurrentHashSet<SimpleString>> getAuthorizationCache() {
      return authorizationCache;
   }

   @Override
   public long getAuthenticationSuccessCount() {
      return authenticationSuccessCount;
   }

   @Override
   public long getAuthenticationFailureCount() {
      return authenticationFailureCount;
   }

   @Override
   public long getAuthorizationSuccessCount() {
      return authorizationSuccessCount;
   }

   @Override
   public long getAuthorizationFailureCount() {
      return authorizationFailureCount;
   }
}
