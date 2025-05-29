package com.ctf.run.realm;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.BearerToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

public class UserRealm extends AuthorizingRealm {
   public boolean supports(AuthenticationToken token) {
      return token instanceof BearerToken;
   }

   protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principalCollection) {
      return new SimpleAuthorizationInfo();
   }

   protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) throws AuthenticationException {
      BearerToken bearerToken = (BearerToken)authenticationToken;
      String host = bearerToken.getHost();
      String token = new String(Base64.decode(bearerToken.getToken().getBytes()));
      if (host.equals("127.0.0.1") && token.equals("admin")) {
         return new SimpleAuthenticationInfo(host, authenticationToken.getPrincipal(), this.getName());
      } else {
         throw new AuthenticationException();
      }
   }
}
