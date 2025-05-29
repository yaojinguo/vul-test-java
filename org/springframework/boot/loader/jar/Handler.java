package org.springframework.boot.loader.jar;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Handler extends URLStreamHandler {
   private static final String JAR_PROTOCOL = "jar:";
   private static final String FILE_PROTOCOL = "file:";
   private static final String SEPARATOR = "!/";
   private static final Pattern SEPARATOR_PATTERN = Pattern.compile("!/", 16);
   private static final String CURRENT_DIR = "/./";
   private static final Pattern CURRENT_DIR_PATTERN = Pattern.compile("/./", 16);
   private static final String PARENT_DIR = "/../";
   private static final String PROTOCOL_HANDLER = "java.protocol.handler.pkgs";
   private static final String[] FALLBACK_HANDLERS = new String[]{"sun.net.www.protocol.jar.Handler"};
   private static URL jarContextUrl;
   private static SoftReference<Map<File, JarFile>> rootFileCache = new SoftReference(null);
   private final JarFile jarFile;
   private URLStreamHandler fallbackHandler;

   public Handler() {
      this(null);
   }

   public Handler(JarFile jarFile) {
      this.jarFile = jarFile;
   }

   protected URLConnection openConnection(URL url) throws IOException {
      if (this.jarFile != null && this.isUrlInJarFile(url, this.jarFile)) {
         return JarURLConnection.get(url, this.jarFile);
      } else {
         try {
            return JarURLConnection.get(url, this.getRootJarFileFromUrl(url));
         } catch (Exception var3) {
            return this.openFallbackConnection(url, var3);
         }
      }
   }

   private boolean isUrlInJarFile(URL url, JarFile jarFile) throws MalformedURLException {
      return url.getPath().startsWith(jarFile.getUrl().getPath()) && url.toString().startsWith(jarFile.getUrlString());
   }

   private URLConnection openFallbackConnection(URL url, Exception reason) throws IOException {
      try {
         URLConnection connection = this.openFallbackContextConnection(url);
         return connection != null ? connection : this.openFallbackHandlerConnection(url);
      } catch (Exception var4) {
         if (reason instanceof IOException) {
            this.log(false, "Unable to open fallback handler", var4);
            throw (IOException)reason;
         } else {
            this.log(true, "Unable to open fallback handler", var4);
            if (reason instanceof RuntimeException) {
               throw (RuntimeException)reason;
            } else {
               throw new IllegalStateException(reason);
            }
         }
      }
   }

   private URLConnection openFallbackContextConnection(URL url) {
      try {
         if (jarContextUrl != null) {
            return new URL(jarContextUrl, url.toExternalForm()).openConnection();
         }
      } catch (Exception var3) {
      }

      return null;
   }

   private URLConnection openFallbackHandlerConnection(URL url) throws Exception {
      URLStreamHandler fallbackHandler = this.getFallbackHandler();
      return new URL(null, url.toExternalForm(), fallbackHandler).openConnection();
   }

   private URLStreamHandler getFallbackHandler() {
      if (this.fallbackHandler != null) {
         return this.fallbackHandler;
      } else {
         for(String handlerClassName : FALLBACK_HANDLERS) {
            try {
               Class<?> handlerClass = Class.forName(handlerClassName);
               this.fallbackHandler = (URLStreamHandler)handlerClass.getDeclaredConstructor().newInstance();
               return this.fallbackHandler;
            } catch (Exception var6) {
            }
         }

         throw new IllegalStateException("Unable to find fallback handler");
      }
   }

   private void log(boolean warning, String message, Exception cause) {
      try {
         Level level = warning ? Level.WARNING : Level.FINEST;
         Logger.getLogger(this.getClass().getName()).log(level, message, cause);
      } catch (Exception var5) {
         if (warning) {
            System.err.println("WARNING: " + message);
         }
      }

   }

   protected void parseURL(URL context, String spec, int start, int limit) {
      if (spec.regionMatches(true, 0, "jar:", 0, "jar:".length())) {
         this.setFile(context, this.getFileFromSpec(spec.substring(start, limit)));
      } else {
         this.setFile(context, this.getFileFromContext(context, spec.substring(start, limit)));
      }

   }

   private String getFileFromSpec(String spec) {
      int separatorIndex = spec.lastIndexOf("!/");
      if (separatorIndex == -1) {
         throw new IllegalArgumentException("No !/ in spec '" + spec + "'");
      } else {
         try {
            new URL(spec.substring(0, separatorIndex));
            return spec;
         } catch (MalformedURLException var4) {
            throw new IllegalArgumentException("Invalid spec URL '" + spec + "'", var4);
         }
      }
   }

   private String getFileFromContext(URL context, String spec) {
      String file = context.getFile();
      if (spec.startsWith("/")) {
         return this.trimToJarRoot(file) + "!/" + spec.substring(1);
      } else if (file.endsWith("/")) {
         return file + spec;
      } else {
         int lastSlashIndex = file.lastIndexOf(47);
         if (lastSlashIndex == -1) {
            throw new IllegalArgumentException("No / found in context URL's file '" + file + "'");
         } else {
            return file.substring(0, lastSlashIndex + 1) + spec;
         }
      }
   }

   private String trimToJarRoot(String file) {
      int lastSeparatorIndex = file.lastIndexOf("!/");
      if (lastSeparatorIndex == -1) {
         throw new IllegalArgumentException("No !/ found in context URL's file '" + file + "'");
      } else {
         return file.substring(0, lastSeparatorIndex);
      }
   }

   private void setFile(URL context, String file) {
      String path = this.normalize(file);
      String query = null;
      int queryIndex = path.lastIndexOf(63);
      if (queryIndex != -1) {
         query = path.substring(queryIndex + 1);
         path = path.substring(0, queryIndex);
      }

      this.setURL(context, "jar:", null, -1, null, null, path, query, context.getRef());
   }

   private String normalize(String file) {
      if (!file.contains("/./") && !file.contains("/../")) {
         return file;
      } else {
         int afterLastSeparatorIndex = file.lastIndexOf("!/") + "!/".length();
         String afterSeparator = file.substring(afterLastSeparatorIndex);
         afterSeparator = this.replaceParentDir(afterSeparator);
         afterSeparator = this.replaceCurrentDir(afterSeparator);
         return file.substring(0, afterLastSeparatorIndex) + afterSeparator;
      }
   }

   private String replaceParentDir(String file) {
      int parentDirIndex;
      while((parentDirIndex = file.indexOf("/../")) >= 0) {
         int precedingSlashIndex = file.lastIndexOf(47, parentDirIndex - 1);
         if (precedingSlashIndex >= 0) {
            file = file.substring(0, precedingSlashIndex) + file.substring(parentDirIndex + 3);
         } else {
            file = file.substring(parentDirIndex + 4);
         }
      }

      return file;
   }

   private String replaceCurrentDir(String file) {
      return CURRENT_DIR_PATTERN.matcher(file).replaceAll("/");
   }

   protected int hashCode(URL u) {
      return this.hashCode(u.getProtocol(), u.getFile());
   }

   private int hashCode(String protocol, String file) {
      int result = protocol != null ? protocol.hashCode() : 0;
      int separatorIndex = file.indexOf("!/");
      if (separatorIndex == -1) {
         return result + file.hashCode();
      } else {
         String source = file.substring(0, separatorIndex);
         String entry = this.canonicalize(file.substring(separatorIndex + 2));

         try {
            result += new URL(source).hashCode();
         } catch (MalformedURLException var8) {
            result += source.hashCode();
         }

         return result + entry.hashCode();
      }
   }

   protected boolean sameFile(URL u1, URL u2) {
      if (u1.getProtocol().equals("jar") && u2.getProtocol().equals("jar")) {
         int separator1 = u1.getFile().indexOf("!/");
         int separator2 = u2.getFile().indexOf("!/");
         if (separator1 != -1 && separator2 != -1) {
            String nested1 = u1.getFile().substring(separator1 + "!/".length());
            String nested2 = u2.getFile().substring(separator2 + "!/".length());
            if (!nested1.equals(nested2)) {
               String canonical1 = this.canonicalize(nested1);
               String canonical2 = this.canonicalize(nested2);
               if (!canonical1.equals(canonical2)) {
                  return false;
               }
            }

            String root1 = u1.getFile().substring(0, separator1);
            String root2 = u2.getFile().substring(0, separator2);

            try {
               return super.sameFile(new URL(root1), new URL(root2));
            } catch (MalformedURLException var10) {
               return super.sameFile(u1, u2);
            }
         } else {
            return super.sameFile(u1, u2);
         }
      } else {
         return false;
      }
   }

   private String canonicalize(String path) {
      return SEPARATOR_PATTERN.matcher(path).replaceAll("/");
   }

   public JarFile getRootJarFileFromUrl(URL url) throws IOException {
      String spec = url.getFile();
      int separatorIndex = spec.indexOf("!/");
      if (separatorIndex == -1) {
         throw new MalformedURLException("Jar URL does not contain !/ separator");
      } else {
         String name = spec.substring(0, separatorIndex);
         return this.getRootJarFile(name);
      }
   }

   private JarFile getRootJarFile(String name) throws IOException {
      try {
         if (!name.startsWith("file:")) {
            throw new IllegalStateException("Not a file URL");
         } else {
            File file = new File(URI.create(name));
            Map<File, JarFile> cache = (Map)rootFileCache.get();
            JarFile result = cache != null ? (JarFile)cache.get(file) : null;
            if (result == null) {
               result = new JarFile(file);
               addToRootFileCache(file, result);
            }

            return result;
         }
      } catch (Exception var5) {
         throw new IOException("Unable to open root Jar file '" + name + "'", var5);
      }
   }

   static void addToRootFileCache(File sourceFile, JarFile jarFile) {
      Map<File, JarFile> cache = (Map)rootFileCache.get();
      if (cache == null) {
         cache = new ConcurrentHashMap();
         rootFileCache = new SoftReference(cache);
      }

      cache.put(sourceFile, jarFile);
   }

   static void captureJarContextUrl() {
      if (canResetCachedUrlHandlers()) {
         String handlers = System.getProperty("java.protocol.handler.pkgs", "");

         try {
            System.clearProperty("java.protocol.handler.pkgs");

            try {
               resetCachedUrlHandlers();
               jarContextUrl = new URL("jar:file:context.jar!/");
               URLConnection connection = jarContextUrl.openConnection();
               if (connection instanceof JarURLConnection) {
                  jarContextUrl = null;
               }
            } catch (Exception var5) {
            }
         } finally {
            if (handlers == null) {
               System.clearProperty("java.protocol.handler.pkgs");
            } else {
               System.setProperty("java.protocol.handler.pkgs", handlers);
            }

         }

         resetCachedUrlHandlers();
      }

   }

   private static boolean canResetCachedUrlHandlers() {
      try {
         resetCachedUrlHandlers();
         return true;
      } catch (Error var1) {
         return false;
      }
   }

   private static void resetCachedUrlHandlers() {
      URL.setURLStreamHandlerFactory(null);
   }

   public static void setUseFastConnectionExceptions(boolean useFastConnectionExceptions) {
      JarURLConnection.setUseFastExceptions(useFastConnectionExceptions);
   }
}
