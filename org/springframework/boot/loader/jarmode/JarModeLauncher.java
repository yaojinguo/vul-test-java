package org.springframework.boot.loader.jarmode;

import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;

public final class JarModeLauncher {
   static final String DISABLE_SYSTEM_EXIT = JarModeLauncher.class.getName() + ".DISABLE_SYSTEM_EXIT";

   private JarModeLauncher() {
   }

   public static void main(String[] args) {
      String mode = System.getProperty("jarmode");

      for(JarMode candidate : SpringFactoriesLoader.loadFactories(JarMode.class, ClassUtils.getDefaultClassLoader())) {
         if (candidate.accepts(mode)) {
            candidate.run(mode, args);
            return;
         }
      }

      System.err.println("Unsupported jarmode '" + mode + "'");
      if (!Boolean.getBoolean(DISABLE_SYSTEM_EXIT)) {
         System.exit(1);
      }

   }
}
