package org.springframework.boot.loader.jarmode;

import java.util.Arrays;

class TestJarMode implements JarMode {
   @Override
   public boolean accepts(String mode) {
      return "test".equals(mode);
   }

   @Override
   public void run(String mode, String[] args) {
      System.out.println("running in " + mode + " jar mode " + Arrays.asList(args));
   }
}
