package com.ctf.run.web;

import com.ctf.run.web.CustomShiroFilterFactoryBean.MySpringShiroFilter;
import java.util.Map;
import javax.servlet.Filter;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.filter.InvalidRequestFilter;
import org.apache.shiro.web.filter.mgt.DefaultFilter;
import org.apache.shiro.web.filter.mgt.FilterChainManager;
import org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.springframework.beans.factory.BeanInitializationException;

public class CustomShiroFilterFactoryBean extends ShiroFilterFactoryBean {
   public Class<MySpringShiroFilter> getObjectType() {
      return MySpringShiroFilter.class;
   }

   protected AbstractShiroFilter createInstance() throws Exception {
      SecurityManager securityManager = this.getSecurityManager();
      if (securityManager == null) {
         String msg = "SecurityManager property must be set.";
         throw new BeanInitializationException(msg);
      } else if (!(securityManager instanceof WebSecurityManager)) {
         String msg = "The security manager does not implement the WebSecurityManager interface.";
         throw new BeanInitializationException(msg);
      } else {
         FilterChainManager manager = this.createFilterChainManager();
         PathMatchingFilterChainResolver chainResolver = new PathMatchingFilterChainResolver();
         chainResolver.setFilterChainManager(manager);
         Map<String, Filter> filterMap = manager.getFilters();
         Filter invalidRequestFilter = (Filter)filterMap.get(DefaultFilter.invalidRequest.name());
         if (invalidRequestFilter instanceof InvalidRequestFilter) {
            ((InvalidRequestFilter)invalidRequestFilter).setBlockNonAscii(false);
         }

         return new MySpringShiroFilter((WebSecurityManager)securityManager, chainResolver);
      }
   }
}
