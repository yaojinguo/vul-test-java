package com.ctf.run.web;

import org.apache.shiro.web.filter.mgt.FilterChainResolver;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.apache.shiro.web.servlet.AbstractShiroFilter;

final class CustomShiroFilterFactoryBean$MySpringShiroFilter extends AbstractShiroFilter {
   protected CustomShiroFilterFactoryBean$MySpringShiroFilter(WebSecurityManager webSecurityManager, FilterChainResolver resolver) {
      if (webSecurityManager == null) {
         throw new IllegalArgumentException("WebSecurityManager property cannot be null.");
      } else {
         this.setSecurityManager(webSecurityManager);
         if (resolver != null) {
            this.setFilterChainResolver(resolver);
         }

      }
   }
}
