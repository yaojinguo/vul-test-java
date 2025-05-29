package com.ctf.run.utils;

public class StringUtils extends org.apache.commons.lang3.StringUtils {
   private static final String NULLSTR = "";

   public static boolean isEmpty(String str) {
      return isNull(str) || "".equals(str.trim());
   }

   public static boolean isNull(Object object) {
      return object == null;
   }
}
