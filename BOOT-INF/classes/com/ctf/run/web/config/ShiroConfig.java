package com.ctf.run.web.config;

import com.ctf.run.realm.UserRealm;
import com.ctf.run.web.CustomShiroFilterFactoryBean;
import com.ctf.run.web.filter.CsrfValidateFilter;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.Filter;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShiroConfig {
   public CsrfValidateFilter csrfValidateFilter() {
      CsrfValidateFilter csrfValidateFilter = new CsrfValidateFilter();
      csrfValidateFilter.setEnabled(true);
      return csrfValidateFilter;
   }

   @Bean
   public UserRealm userRealm() {
      return new UserRealm();
   }

   @Bean
   public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(@Qualifier("securityManager") SecurityManager securityManager) {
      AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor = new AuthorizationAttributeSourceAdvisor();
      authorizationAttributeSourceAdvisor.setSecurityManager(securityManager);
      return authorizationAttributeSourceAdvisor;
   }

   @Bean
   public SecurityManager securityManager(UserRealm userRealm) {
      DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
      securityManager.setRealm(userRealm);
      return securityManager;
   }

   @Bean
   public ShiroFilterFactoryBean shiroFilterFactoryBean(SecurityManager securityManager) {
      CustomShiroFilterFactoryBean shiroFilterFactoryBean = new CustomShiroFilterFactoryBean();
      shiroFilterFactoryBean.setSecurityManager(securityManager);
      shiroFilterFactoryBean.setLoginUrl("/index/index");
      shiroFilterFactoryBean.setUnauthorizedUrl("/index/index");
      LinkedHashMap<String, String> filterChainDefinitionMap = new LinkedHashMap();
      Map<String, Filter> filters = new LinkedHashMap();
      filters.put("csrfValidateFilter", this.csrfValidateFilter());
      shiroFilterFactoryBean.setFilters(filters);
      filterChainDefinitionMap.put("/index/login", "anon");
      filterChainDefinitionMap.put("/index/csrf_token", "anon");
      filterChainDefinitionMap.put("/index/*/exec", "user,csrfValidateFilter");
      filterChainDefinitionMap.put("/index/test/exec", "anon,csrfValidateFilter");
      filterChainDefinitionMap.put("/**", "user,csrfValidateFilter");
      shiroFilterFactoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
      return shiroFilterFactoryBean;
   }
}
