package com.ctf.run.utils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.servlet.http.HttpServletResponse;

public class ServletUtils {
   private static final SecureRandom secureRandom = new SecureRandom();

   public static String renderString(HttpServletResponse response, String string) {
      try {
         response.setContentType("application/json");
         response.setCharacterEncoding("utf-8");
         response.getWriter().print(string);
      } catch (IOException var3) {
         var3.printStackTrace();
      }

      return null;
   }

   public static String generateToken() {
      byte[] bytes = new byte[32];
      secureRandom.nextBytes(bytes);
      return Base64.getEncoder().encodeToString(bytes);
   }
}
