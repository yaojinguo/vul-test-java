package com.ctf.run.web.filter;

import com.ctf.run.utils.ServletUtils;
import com.ctf.run.utils.StringUtils;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.web.filter.AccessControlFilter;

public class CsrfValidateFilter extends AccessControlFilter {
   protected boolean isAccessAllowed(ServletRequest request, ServletResponse servletResponse, Object o) throws Exception {
      HttpServletRequest httpServletRequest = (HttpServletRequest)request;
      return !this.isAllowMethod(httpServletRequest) ? true : this.validateResponse(httpServletRequest, httpServletRequest.getHeader("csrf_token"));
   }

   public boolean validateResponse(HttpServletRequest request, String requestToken) {
      Object obj = request.getSession().getAttribute("csrf_token");
      String sessionToken = toStr(obj, "");
      return !StringUtils.isEmpty(requestToken) && requestToken.equalsIgnoreCase(sessionToken);
   }

   protected boolean onAccessDenied(ServletRequest servletRequest, ServletResponse response) throws Exception {
      ServletUtils.renderString((HttpServletResponse)response, "{\"code\":\"1\",\"msg\":\"当前请求的安全验证未通过，请刷新页面后重试。\"}");
      return false;
   }

   private boolean isAllowMethod(HttpServletRequest request) {
      String method = request.getMethod();
      return "POST".equalsIgnoreCase(method);
   }

   public static String toStr(Object value, String defaultValue) {
      if (null == value) {
         return defaultValue;
      } else {
         return value instanceof String ? (String)value : value.toString();
      }
   }
}
