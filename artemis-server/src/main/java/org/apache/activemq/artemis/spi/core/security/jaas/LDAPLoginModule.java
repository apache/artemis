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

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LDAPLoginModule implements AuditLoginModule {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final ThreadLocal<Map<String, String>> environmentThreadLocal = new ThreadLocal<>();

   public static Map<String, String> getEnvironment() {
      return environmentThreadLocal.get();
   }

   protected static void setEnvironment(Map<String, String> environment) {
      environmentThreadLocal.set(environment);
   }

   protected static void removeEnvironment() {
      environmentThreadLocal.remove();
   }

   enum ConfigKey {

      DEBUG("debug"),
      INITIAL_CONTEXT_FACTORY("initialContextFactory"),
      CONNECTION_URL("connectionURL"),
      CONNECTION_USERNAME("connectionUsername"),
      CONNECTION_PASSWORD("connectionPassword"),
      CONNECTION_PROTOCOL("connectionProtocol"),
      AUTHENTICATION("authentication"),
      USER_BASE("userBase"),
      USER_SEARCH_MATCHING("userSearchMatching"),
      USER_SEARCH_SUBTREE("userSearchSubtree"),
      ROLE_BASE("roleBase"),
      ROLE_NAME("roleName"),
      ROLE_SEARCH_MATCHING("roleSearchMatching"),
      ROLE_SEARCH_SUBTREE("roleSearchSubtree"),
      USER_ROLE_NAME("userRoleName"),
      EXPAND_ROLES("expandRoles"),
      EXPAND_ROLES_MATCHING("expandRolesMatching"),
      SASL_LOGIN_CONFIG_SCOPE("saslLoginConfigScope"),
      AUTHENTICATE_USER("authenticateUser"),
      REFERRAL("referral"),
      IGNORE_PARTIAL_RESULT_EXCEPTION("ignorePartialResultException"),
      PASSWORD_CODEC("passwordCodec"),
      CONNECTION_TIMEOUT("connectionTimeout"),
      READ_TIMEOUT("readTimeout"),
      NO_CACHE_EXCEPTIONS("noCacheExceptions");

      private final String name;

      ConfigKey(String name) {
         this.name = name;
      }

      String getName() {
         return name;
      }

      static boolean contains(String key) {
         for (ConfigKey k: values()) {
            if (k.name.equals(key)) {
               return true;
            }
         }
         return false;
      }
   }

   protected DirContext context;

   private Subject subject;
   private CallbackHandler handler;
   private final Set<LDAPLoginProperty> config = new HashSet<>();
   private String username;
   private final Set<RolePrincipal> groups = new HashSet<>();
   private boolean userAuthenticated = false;
   private boolean authenticateUser = true;
   private Subject brokerGssapiIdentity = null;
   private boolean isRoleAttributeSet = false;
   private String roleAttributeName = null;
   private List<String> noCacheExceptions;

   private String codecClass = null;

   @Override
   public void initialize(Subject subject,
                          CallbackHandler callbackHandler,
                          Map<String, ?> sharedState,
                          Map<String, ?> options) {
      this.subject = subject;
      this.handler = callbackHandler;

      // copy all options to config, ignoring non-string entries
      config.clear();
      for (Map.Entry<String, ?> entry : options.entrySet()) {
         if (entry.getValue() instanceof String) {
            config.add(new LDAPLoginProperty(entry.getKey(), (String) entry.getValue()));
         }
      }

      if (isLoginPropertySet(ConfigKey.AUTHENTICATE_USER)) {
         authenticateUser = Boolean.parseBoolean(getLDAPPropertyValue(ConfigKey.AUTHENTICATE_USER));
      }
      isRoleAttributeSet = isLoginPropertySet(ConfigKey.ROLE_NAME);
      roleAttributeName = getLDAPPropertyValue(ConfigKey.ROLE_NAME);
      codecClass = getLDAPPropertyValue(ConfigKey.PASSWORD_CODEC);
      if (isLoginPropertySet(ConfigKey.NO_CACHE_EXCEPTIONS)) {
         noCacheExceptions = Arrays.asList(getLDAPPropertyValue(ConfigKey.NO_CACHE_EXCEPTIONS).split(","));
         noCacheExceptions.replaceAll(String::trim);
      } else {
         noCacheExceptions = Collections.emptyList();
      }
   }

   private String getPlainPassword(String password) {
      try {
         return PasswordMaskingUtil.resolveMask(password, codecClass);
      } catch (Exception e) {
         throw new IllegalArgumentException("Failed to decode password", e);
      }
   }

   @Override
   public boolean login() throws LoginException {
      try {
         if (!authenticateUser) {
            return false;
         }

         Callback[] callbacks = new Callback[2];

         callbacks[0] = new NameCallback("User name");
         callbacks[1] = new PasswordCallback("Password", false);
         try {
            handler.handle(callbacks);
         } catch (IOException | UnsupportedCallbackException e) {
            throw (LoginException) new LoginException().initCause(e);
         }

         String password = null;

         username = ((NameCallback) callbacks[0]).getName();
         if (username == null) {
            return false;
         }

         if (((PasswordCallback) callbacks[1]).getPassword() != null) {
            password = new String(((PasswordCallback) callbacks[1]).getPassword());
         }

         /*
          * https://tools.ietf.org/html/rfc4513#section-6.3.1
          *
          * Clients that use the results from a simple Bind operation to make
          * authorization decisions should actively detect unauthenticated Bind
          * requests (by verifying that the supplied password is not empty) and
          * react appropriately.
          */
         if (password == null || password.isEmpty()) {
            throw new FailedLoginException("Password cannot be null or empty");
         }

         // authenticate will throw LoginException
         // in case of failed authentication
         authenticate(username, password);
         userAuthenticated = true;
         return true;
      } catch (LoginException e) {
         throw handleException(e);
      }
   }

   @Override
   public boolean logout() throws LoginException {
      clear();
      return true;
   }

   @Override
   public boolean commit() throws LoginException {
      try {
         boolean result = userAuthenticated;
         Set<UserPrincipal> authenticatedUsers = subject.getPrincipals(UserPrincipal.class);
         Set<Principal> principals = subject.getPrincipals();
         if (result) {
            principals.add(new UserPrincipal(username));
         }

         // assign roles to any other UserPrincipal
         for (UserPrincipal authenticatedUser : authenticatedUsers) {
            List<String> roles = new ArrayList<>();
            try {
               String dn = resolveDN(authenticatedUser.getName(), roles);
               resolveRolesForDN(context, dn, authenticatedUser.getName(), roles);
            } catch (NamingException e) {
               closeContext();
               FailedLoginException ex = new FailedLoginException("Error contacting LDAP");
               ex.initCause(e);
               throw ex;
            }
         }

         principals.addAll(groups);

         clear();
         return result;
      } catch (LoginException e) {
         throw handleException(e);
      }
   }

   private LoginException handleException(LoginException e) {
      Throwable rootCause = ExceptionUtils.getRootCause(e);
      String rootCauseClass = rootCause.getClass().getName();

      // try to match statically first
      if (noCacheExceptions.contains(rootCauseClass)) {
         return getNoCacheLoginException(e, rootCause);
      } else {
         // if no static matches are found try regex
         for (String match : noCacheExceptions) {
            if (Pattern.matches(match, rootCauseClass)) {
               return getNoCacheLoginException(e, rootCause);
            }
         }
      }
      return e;
   }

   private NoCacheLoginException getNoCacheLoginException(LoginException e, Throwable rootCause) {
      return (NoCacheLoginException) new NoCacheLoginException(rootCause.getClass().getName() + (rootCause.getMessage() == null ? "" : ": " + rootCause.getMessage())).initCause(e);
   }

   private void clear() {
      username = null;
      userAuthenticated = false;
      closeContext();
   }

   @Override
   public boolean abort() throws LoginException {
      registerFailureForAudit(username);
      clear();
      return true;
   }

   protected void closeContext() {
      if (context != null) {
         try {
            context.close();
            context = null;
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.failedToCloseContext(e);
         }
      }
   }

   protected boolean authenticate(String username, String password) throws LoginException {
      List<String> roles = new ArrayList<>();
      try {
         String dn = resolveDN(username, roles);

         // check the credentials by binding to server
         if (bindUser(context, dn, password)) {
            // if authenticated add more roles
            resolveRolesForDN(context, dn, username, roles);
         } else {
            throw new FailedLoginException("Password does not match for user: " + username);
         }
      } catch (NamingException e) {
         closeContext();
         FailedLoginException ex = new FailedLoginException("Error contacting LDAP");
         ex.initCause(e);
         throw ex;
      }

      return true;
   }

   private void resolveRolesForDN(DirContext context, String dn, String username, List<String> roles) throws NamingException {
      addRoles(context, dn, username, roles);
      logger.debug("Roles {} for user {}", roles, username);
      for (String role : roles) {
         groups.add(new RolePrincipal(role));
      }
   }

   private String resolveDN(String username, List<String> roles) throws FailedLoginException {
      String dn = null;

      logger.debug("Create the LDAP initial context.");
      try {
         openContext();
      } catch (Exception ne) {
         FailedLoginException ex = new FailedLoginException("Error opening LDAP connection");
         ex.initCause(ne);
         throw ex;
      }

      if (!isLoginPropertySet(ConfigKey.USER_SEARCH_MATCHING)) {
         return username;
      }

      MessageFormat userSearchMatchingFormat = new MessageFormat(getLDAPPropertyValue(ConfigKey.USER_SEARCH_MATCHING));
      boolean userSearchSubtreeBool = Boolean.parseBoolean(getLDAPPropertyValue(ConfigKey.USER_SEARCH_SUBTREE));
      boolean ignorePartialResultExceptionBool = Boolean.parseBoolean(getLDAPPropertyValue(ConfigKey.IGNORE_PARTIAL_RESULT_EXCEPTION));

      try {

         String filter = userSearchMatchingFormat.format(new String[]{doRFC2254Encoding(username)});
         SearchControls constraints = new SearchControls();
         if (userSearchSubtreeBool) {
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
         } else {
            constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
         }

         // setup attributes
         List<String> list = new ArrayList<>();
         if (isLoginPropertySet(ConfigKey.USER_ROLE_NAME)) {
            list.add(getLDAPPropertyValue(ConfigKey.USER_ROLE_NAME));
         }
         String[] attribs = new String[list.size()];
         list.toArray(attribs);
         constraints.setReturningAttributes(attribs);

         if (logger.isDebugEnabled()) {
            logger.debug("Get the user DN.");
            logger.debug("Looking for the user in LDAP with ");
            logger.debug("  base DN: {}", getLDAPPropertyValue(ConfigKey.USER_BASE));
            logger.debug("  filter: {}", filter);
         }

         NamingEnumeration<SearchResult> results = null;
         try {
            results = SecurityManagerShim.callAs(brokerGssapiIdentity, (Callable<NamingEnumeration<SearchResult>>) () -> context.search(getLDAPPropertyValue(ConfigKey.USER_BASE), filter, constraints));
         } catch (CompletionException e) {
            Throwable cause = e.getCause();
            FailedLoginException ex = new FailedLoginException("Error executing search query to resolve DN");
            ex.initCause(cause);
            throw ex;
         }

         if (results == null || !results.hasMore()) {
            throw new FailedLoginException("User " + username + " not found in LDAP.");
         }

         SearchResult result = results.next();

         try {
            if (results.hasMore()) {
               // ignore for now
            }
         } catch (PartialResultException e) {
            // Workaround for AD servers not handling referrals correctly.
            if (ignorePartialResultExceptionBool) {
               logger.debug("PartialResultException encountered and ignored", e);
            } else {
               throw e;
            }
         }

         if (result.isRelative()) {
            logger.debug("LDAP returned a relative name: {}", result.getName());

            NameParser parser = context.getNameParser("");
            Name contextName = parser.parse(context.getNameInNamespace());
            Name baseName = parser.parse(getLDAPPropertyValue(ConfigKey.USER_BASE));
            Name entryName = parser.parse(result.getName());
            Name name = contextName.addAll(baseName);
            name = name.addAll(entryName);
            dn = name.toString();
         } else {
            logger.debug("LDAP returned an absolute name: {}", result.getName());

            try {
               URI uri = new URI(result.getName());
               String path = uri.getPath();

               if (path.startsWith("/")) {
                  dn = path.substring(1);
               } else {
                  dn = path;
               }
            } catch (URISyntaxException e) {
               closeContext();
               FailedLoginException ex = new FailedLoginException("Error parsing absolute name as URI.");
               ex.initCause(e);
               throw ex;
            }
         }

         logger.debug("Using DN [{}] for binding.", dn);

         Attributes attrs = result.getAttributes();
         if (attrs == null) {
            throw new FailedLoginException("User found, but LDAP entry malformed: " + username);
         }
         if (isLoginPropertySet(ConfigKey.USER_ROLE_NAME)) {
            Attribute roleNames = attrs.get(getLDAPPropertyValue(ConfigKey.USER_ROLE_NAME));
            if (roleNames != null) {
               NamingEnumeration<?> e = roleNames.getAll();
               while (e.hasMore()) {
                  String roleDnString = (String) e.next();
                  if (isRoleAttributeSet) {
                     // parse out the attribute from the group Dn
                     LdapName ldapRoleName = new LdapName(roleDnString);
                     for (int i = 0; i < ldapRoleName.size(); i++) {
                        Rdn candidate = ldapRoleName.getRdn(i);
                        if (roleAttributeName.equals(candidate.getType())) {
                           roles.add((String) candidate.getValue());
                        }
                     }
                  } else {
                     roles.add(roleDnString);
                  }
               }
            }
         }
      } catch (NamingException e) {
         closeContext();
         FailedLoginException ex = new FailedLoginException("Error contacting LDAP");
         ex.initCause(e);
         throw ex;
      }

      return dn;
   }

   protected void addRoles(DirContext context,
                                   String dn,
                                   String username,
                                   List<String> currentRoles) throws NamingException {

      if (!isLoginPropertySet(ConfigKey.ROLE_SEARCH_MATCHING)) {
         return;
      }

      MessageFormat roleSearchMatchingFormat = new MessageFormat(getLDAPPropertyValue(ConfigKey.ROLE_SEARCH_MATCHING));
      boolean roleSearchSubtreeBool = Boolean.parseBoolean(getLDAPPropertyValue(ConfigKey.ROLE_SEARCH_SUBTREE));
      boolean expandRolesBool = Boolean.parseBoolean(getLDAPPropertyValue(ConfigKey.EXPAND_ROLES));
      boolean ignorePartialResultExceptionBool = Boolean.parseBoolean(getLDAPPropertyValue(ConfigKey.IGNORE_PARTIAL_RESULT_EXCEPTION));

      final String filter = roleSearchMatchingFormat.format(new String[]{doRFC2254Encoding(dn), doRFC2254Encoding(username)});

      SearchControls constraints = new SearchControls();
      if (roleSearchSubtreeBool) {
         constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
      } else {
         constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      }
      if (logger.isDebugEnabled()) {
         logger.debug("Get user roles.");
         logger.debug("Looking for the user roles in LDAP with ");
         logger.debug("  base DN: {}", getLDAPPropertyValue(ConfigKey.ROLE_BASE));
         logger.debug("  filter: {}", filter);
      }
      Set<String> haveSeenNames = new HashSet<>();
      Queue<String> pendingNameExpansion = new LinkedList<>();
      NamingEnumeration<SearchResult> results = null;
      try {
         results = SecurityManagerShim.callAs(brokerGssapiIdentity, (Callable<NamingEnumeration<SearchResult>>) () -> context.search(getLDAPPropertyValue(ConfigKey.ROLE_BASE), filter, constraints));
      } catch (CompletionException e) {
         Throwable cause = e.getCause();
         NamingException ex = new NamingException("Error executing search query to resolve roles");
         ex.initCause(cause);
         throw ex;
      }

      try {
         while (results.hasMore()) {
            SearchResult result = results.next();
            if (expandRolesBool) {
               haveSeenNames.add(result.getNameInNamespace());
               pendingNameExpansion.add(result.getNameInNamespace());
            }
            addRoleAttribute(result, currentRoles);
         }
      } catch (PartialResultException e) {
         // Workaround for AD servers not handling referrals correctly.
         if (ignorePartialResultExceptionBool) {
            logger.debug("PartialResultException encountered and ignored", e);
         } else {
            throw e;
         }
      }
      if (expandRolesBool) {
         MessageFormat expandRolesMatchingFormat = new MessageFormat(getLDAPPropertyValue(ConfigKey.EXPAND_ROLES_MATCHING));
         while (!pendingNameExpansion.isEmpty()) {
            String name = pendingNameExpansion.remove();
            final String expandFilter = expandRolesMatchingFormat.format(new String[]{name});
            if (logger.isDebugEnabled()) {
               logger.debug("Get 'expanded' user roles.");
               logger.debug("Looking for the 'expanded' user roles in LDAP with ");
               logger.debug("  base DN: {}", getLDAPPropertyValue(ConfigKey.ROLE_BASE));
               logger.debug("  filter: {}", expandFilter);
            }
            try {
               results = SecurityManagerShim.callAs(brokerGssapiIdentity, (Callable<NamingEnumeration<SearchResult>>) () -> context.search(getLDAPPropertyValue(ConfigKey.ROLE_BASE), expandFilter, constraints));
            } catch (CompletionException e) {
               Throwable cause = e.getCause();
               NamingException ex = new NamingException("Error executing search query to expand roles");
               ex.initCause(cause);
               throw ex;
            }
            try {
               while (results.hasMore()) {
                  SearchResult result = results.next();
                  name = result.getNameInNamespace();
                  if (!haveSeenNames.contains(name)) {
                     addRoleAttribute(result, currentRoles);
                     haveSeenNames.add(name);
                     pendingNameExpansion.add(name);
                  }
               }
            } catch (PartialResultException e) {
               // Workaround for AD servers not handling referrals correctly.
               if (ignorePartialResultExceptionBool) {
                  logger.debug("PartialResultException encountered and ignored", e);
               } else {
                  throw e;
               }
            }
         }
      }
   }

   protected String doRFC2254Encoding(String inputString) {
      StringBuilder buf = new StringBuilder(inputString.length());
      for (int i = 0; i < inputString.length(); i++) {
         char c = inputString.charAt(i);
         switch (c) {
            case '\\':
               buf.append("\\5c");
               break;
            case '*':
               buf.append("\\2a");
               break;
            case '(':
               buf.append("\\28");
               break;
            case ')':
               buf.append("\\29");
               break;
            case '\0':
               buf.append("\\00");
               break;
            default:
               buf.append(c);
               break;
         }
      }
      return buf.toString();
   }

   protected boolean bindUser(DirContext context, String dn, String password) throws NamingException {
      boolean isValid = false;

      logger.debug("Binding the user.");
      context.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
      context.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
      context.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
      try {
         String baseDn = getLDAPPropertyValue(ConfigKey.CONNECTION_URL).replaceFirst(".*/", ",");
         String userDn = dn.replace(baseDn, "");
         logger.debug("Get user Attributes with dn {}", userDn);
         context.getAttributes(userDn, null);
         isValid = true;
         logger.debug("User {} successfully bound.", dn);
      } catch (AuthenticationException e) {
         isValid = false;
         logger.debug("Authentication failed for dn={}", dn);
      }

      if (isLoginPropertySet(ConfigKey.CONNECTION_USERNAME)) {
         context.addToEnvironment(Context.SECURITY_PRINCIPAL, getLDAPPropertyValue(ConfigKey.CONNECTION_USERNAME));
      } else {
         context.removeFromEnvironment(Context.SECURITY_PRINCIPAL);
      }
      if (isLoginPropertySet(ConfigKey.CONNECTION_PASSWORD)) {
         context.addToEnvironment(Context.SECURITY_CREDENTIALS, getPlainPassword(getLDAPPropertyValue(ConfigKey.CONNECTION_PASSWORD)));
      } else {
         context.removeFromEnvironment(Context.SECURITY_CREDENTIALS);
      }
      context.addToEnvironment(Context.SECURITY_AUTHENTICATION, getLDAPPropertyValue(ConfigKey.AUTHENTICATION));

      return isValid;
   }

   private void addRoleAttribute(SearchResult searchResult, List<String> roles) throws NamingException {
      if (isRoleAttributeSet) {
         Attribute roleAttribute = searchResult.getAttributes().get(roleAttributeName);
         if (roleAttribute != null) {
            roles.add((String) roleAttribute.get());
         }
      } else {
         roles.add(searchResult.getNameInNamespace());
      }
   }


   protected void openContext() throws Exception {
      if (context == null) {
         try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, getLDAPPropertyValue(ConfigKey.INITIAL_CONTEXT_FACTORY));
            env.put(Context.SECURITY_PROTOCOL, getLDAPPropertyValue(ConfigKey.CONNECTION_PROTOCOL));
            env.put(Context.PROVIDER_URL, getLDAPPropertyValue(ConfigKey.CONNECTION_URL));
            env.put(Context.SECURITY_AUTHENTICATION, getLDAPPropertyValue(ConfigKey.AUTHENTICATION));
            if (isLoginPropertySet(ConfigKey.CONNECTION_TIMEOUT)) {
               env.put("com.sun.jndi.ldap.connect.timeout", getLDAPPropertyValue(ConfigKey.CONNECTION_TIMEOUT));
            }
            if (isLoginPropertySet(ConfigKey.READ_TIMEOUT)) {
               env.put("com.sun.jndi.ldap.read.timeout", getLDAPPropertyValue(ConfigKey.READ_TIMEOUT));
            }

            // handle LDAP referrals
            // valid values are "throw", "ignore" and "follow"
            String referral = "ignore";
            if (getLDAPPropertyValue(ConfigKey.REFERRAL) != null) {
               referral = getLDAPPropertyValue(ConfigKey.REFERRAL);
            }

            env.put(Context.REFERRAL, referral);
            logger.debug("Referral handling: {}", referral);

            if ("GSSAPI".equalsIgnoreCase(getLDAPPropertyValue(ConfigKey.AUTHENTICATION))) {

               final String configScope = isLoginPropertySet(ConfigKey.SASL_LOGIN_CONFIG_SCOPE) ? getLDAPPropertyValue(ConfigKey.SASL_LOGIN_CONFIG_SCOPE) : "broker-sasl-gssapi";
               try {
                  LoginContext loginContext = new LoginContext(configScope);
                  loginContext.login();
                  brokerGssapiIdentity = loginContext.getSubject();
               } catch (LoginException e) {
                  e.printStackTrace();
                  FailedLoginException ex = new FailedLoginException("Error contacting LDAP using GSSAPI in JAAS loginConfigScope: " + configScope);
                  ex.initCause(e);
                  throw ex;
               }

            } else {

               if (isLoginPropertySet(ConfigKey.CONNECTION_USERNAME)) {
                  env.put(Context.SECURITY_PRINCIPAL, getLDAPPropertyValue(ConfigKey.CONNECTION_USERNAME));
               } else {
                  throw new NamingException("Empty username is not allowed");
               }

               if (isLoginPropertySet(ConfigKey.CONNECTION_PASSWORD)) {
                  env.put(Context.SECURITY_CREDENTIALS, getPlainPassword(getLDAPPropertyValue(ConfigKey.CONNECTION_PASSWORD)));
               } else {
                  throw new NamingException("Empty password is not allowed");
               }
            }

            extendInitialEnvironment(config, env);

            setEnvironment(env);
            try {
               context = SecurityManagerShim.callAs(brokerGssapiIdentity, (Callable<DirContext>) () -> new InitialDirContext(env));
            } catch (CompletionException ce) {
               Throwable cause = ce.getCause();
               if (cause instanceof Exception ex) {
                  throw ex;
               }

               throw ce;
            } finally {
               removeEnvironment();
            }
         } catch (NamingException e) {
            closeContext();
            ActiveMQServerLogger.LOGGER.failedToOpenContext(e);
            throw e;
         }
      }
   }

   protected void extendInitialEnvironment(Set<LDAPLoginProperty> moduleConfig, Map<String, String> initialContextEnv) {
      // sub-classes may override the method if the default implementation is not sufficient:
      // add all non-module configs to initial DirContext environment to support passing
      // any custom/future property to InitialDirContext construction
      for (LDAPLoginProperty prop: moduleConfig) {
         String propName = prop.getPropertyName();
         if (initialContextEnv.get(propName) == null && !ConfigKey.contains(propName)) {
            initialContextEnv.put(propName, prop.getPropertyValue());
         }
      }

      if (codecClass != null) {
         initialContextEnv.put(ConfigKey.PASSWORD_CODEC.getName(), codecClass);
      }
   }

   private String getLDAPPropertyValue(ConfigKey key) {
      for (LDAPLoginProperty conf : config) {
         if (conf.getPropertyName().equals(key.getName())) {
            return conf.getPropertyValue();
         }
      }
      return null;
   }

   private boolean isLoginPropertySet(ConfigKey key) {
      for (LDAPLoginProperty conf : config) {
         if (conf.getPropertyName().equals(key.getName()) && (conf.getPropertyValue() != null && !"".equals(conf.getPropertyValue()))) {
            return true;
         }
      }
      return false;
   }

}
