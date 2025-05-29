package org.springframework.boot.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.util.SystemPropertyUtils;

public class PropertiesLauncher extends Launcher {
   private static final Class<?>[] PARENT_ONLY_PARAMS = new Class[]{ClassLoader.class};
   private static final Class<?>[] URLS_AND_PARENT_PARAMS = new Class[]{URL[].class, ClassLoader.class};
   private static final Class<?>[] NO_PARAMS = new Class[0];
   private static final URL[] NO_URLS = new URL[0];
   private static final String DEBUG = "loader.debug";
   public static final String MAIN = "loader.main";
   public static final String PATH = "loader.path";
   public static final String HOME = "loader.home";
   public static final String ARGS = "loader.args";
   public static final String CONFIG_NAME = "loader.config.name";
   public static final String CONFIG_LOCATION = "loader.config.location";
   public static final String SET_SYSTEM_PROPERTIES = "loader.system";
   private static final Pattern WORD_SEPARATOR = Pattern.compile("\\W+");
   private static final String NESTED_ARCHIVE_SEPARATOR = "!" + File.separator;
   private final File home;
   private List<String> paths = new ArrayList();
   private final Properties properties = new Properties();
   private final Archive parent;
   private volatile PropertiesLauncher.ClassPathArchives classPathArchives;

   public PropertiesLauncher() {
      try {
         this.home = this.getHomeDirectory();
         this.initializeProperties();
         this.initializePaths();
         this.parent = this.createArchive();
      } catch (Exception var2) {
         throw new IllegalStateException(var2);
      }
   }

   protected File getHomeDirectory() {
      try {
         return new File(this.getPropertyWithDefault("loader.home", "${user.dir}"));
      } catch (Exception var2) {
         throw new IllegalStateException(var2);
      }
   }

   private void initializeProperties() throws Exception {
      List<String> configs = new ArrayList();
      if (this.getProperty("loader.config.location") != null) {
         configs.add(this.getProperty("loader.config.location"));
      } else {
         String[] names = this.getPropertyWithDefault("loader.config.name", "loader").split(",");

         for(String name : names) {
            configs.add("file:" + this.getHomeDirectory() + "/" + name + ".properties");
            configs.add("classpath:" + name + ".properties");
            configs.add("classpath:BOOT-INF/classes/" + name + ".properties");
         }
      }

      for(String config : configs) {
         InputStream resource = this.getResource(config);
         Throwable var20 = null;

         try {
            if (resource == null) {
               this.debug("Not found: " + config);
               continue;
            }

            this.debug("Found: " + config);
            this.loadResource(resource);
         } catch (Throwable var15) {
            var20 = var15;
            throw var15;
         } finally {
            if (resource != null) {
               if (var20 != null) {
                  try {
                     resource.close();
                  } catch (Throwable var14) {
                     var20.addSuppressed(var14);
                  }
               } else {
                  resource.close();
               }
            }

         }

         return;
      }

   }

   private void loadResource(InputStream resource) throws Exception {
      this.properties.load(resource);

      for(Object key : Collections.list(this.properties.propertyNames())) {
         String text = this.properties.getProperty((String)key);
         String value = SystemPropertyUtils.resolvePlaceholders(this.properties, text);
         if (value != null) {
            this.properties.put(key, value);
         }
      }

      if ("true".equals(this.getProperty("loader.system"))) {
         this.debug("Adding resolved properties to System properties");

         for(Object key : Collections.list(this.properties.propertyNames())) {
            String value = this.properties.getProperty((String)key);
            System.setProperty((String)key, value);
         }
      }

   }

   private InputStream getResource(String config) throws Exception {
      if (config.startsWith("classpath:")) {
         return this.getClasspathResource(config.substring("classpath:".length()));
      } else {
         config = this.handleUrl(config);
         return this.isUrl(config) ? this.getURLResource(config) : this.getFileResource(config);
      }
   }

   private String handleUrl(String path) throws UnsupportedEncodingException {
      if (path.startsWith("jar:file:") || path.startsWith("file:")) {
         path = URLDecoder.decode(path, "UTF-8");
         if (path.startsWith("file:")) {
            path = path.substring("file:".length());
            if (path.startsWith("//")) {
               path = path.substring(2);
            }
         }
      }

      return path;
   }

   private boolean isUrl(String config) {
      return config.contains("://");
   }

   private InputStream getClasspathResource(String config) {
      while(config.startsWith("/")) {
         config = config.substring(1);
      }

      config = "/" + config;
      this.debug("Trying classpath: " + config);
      return this.getClass().getResourceAsStream(config);
   }

   private InputStream getFileResource(String config) throws Exception {
      File file = new File(config);
      this.debug("Trying file: " + config);
      return file.canRead() ? new FileInputStream(file) : null;
   }

   private InputStream getURLResource(String config) throws Exception {
      URL url = new URL(config);
      if (this.exists(url)) {
         URLConnection con = url.openConnection();

         try {
            return con.getInputStream();
         } catch (IOException var5) {
            if (con instanceof HttpURLConnection) {
               ((HttpURLConnection)con).disconnect();
            }

            throw var5;
         }
      } else {
         return null;
      }
   }

   private boolean exists(URL url) throws IOException {
      URLConnection connection = url.openConnection();

      try {
         connection.setUseCaches(connection.getClass().getSimpleName().startsWith("JNLP"));
         if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection)connection;
            httpConnection.setRequestMethod("HEAD");
            int responseCode = httpConnection.getResponseCode();
            if (responseCode == 200) {
               return true;
            }

            if (responseCode == 404) {
               return false;
            }
         }

         return connection.getContentLength() >= 0;
      } finally {
         if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection)connection).disconnect();
         }

      }
   }

   private void initializePaths() throws Exception {
      String path = this.getProperty("loader.path");
      if (path != null) {
         this.paths = this.parsePathsProperty(path);
      }

      this.debug("Nested archive paths: " + this.paths);
   }

   private List<String> parsePathsProperty(String commaSeparatedPaths) {
      List<String> paths = new ArrayList();

      for(String path : commaSeparatedPaths.split(",")) {
         path = this.cleanupPath(path);
         path = path != null && !path.isEmpty() ? path : "/";
         paths.add(path);
      }

      if (paths.isEmpty()) {
         paths.add("lib");
      }

      return paths;
   }

   protected String[] getArgs(String... args) throws Exception {
      String loaderArgs = this.getProperty("loader.args");
      if (loaderArgs != null) {
         String[] defaultArgs = loaderArgs.split("\\s+");
         String[] additionalArgs = args;
         args = new String[defaultArgs.length + args.length];
         System.arraycopy(defaultArgs, 0, args, 0, defaultArgs.length);
         System.arraycopy(additionalArgs, 0, args, defaultArgs.length, additionalArgs.length);
      }

      return args;
   }

   @Override
   protected String getMainClass() throws Exception {
      String mainClass = this.getProperty("loader.main", "Start-Class");
      if (mainClass == null) {
         throw new IllegalStateException("No 'loader.main' or 'Start-Class' specified");
      } else {
         return mainClass;
      }
   }

   @Override
   protected ClassLoader createClassLoader(Iterator<Archive> archives) throws Exception {
      String customLoaderClassName = this.getProperty("loader.classLoader");
      if (customLoaderClassName == null) {
         return super.createClassLoader(archives);
      } else {
         Set<URL> urls = new LinkedHashSet();

         while(archives.hasNext()) {
            urls.add(((Archive)archives.next()).getUrl());
         }

         ClassLoader loader = new LaunchedURLClassLoader((URL[])urls.toArray(NO_URLS), this.getClass().getClassLoader());
         this.debug("Classpath for custom loader: " + urls);
         ClassLoader var5 = this.wrapWithCustomClassLoader(loader, customLoaderClassName);
         this.debug("Using custom class loader: " + customLoaderClassName);
         return var5;
      }
   }

   private ClassLoader wrapWithCustomClassLoader(ClassLoader parent, String className) throws Exception {
      Class<ClassLoader> type = Class.forName(className, true, parent);
      ClassLoader classLoader = this.newClassLoader(type, PARENT_ONLY_PARAMS, parent);
      if (classLoader == null) {
         classLoader = this.newClassLoader(type, URLS_AND_PARENT_PARAMS, NO_URLS, parent);
      }

      if (classLoader == null) {
         classLoader = this.newClassLoader(type, NO_PARAMS);
      }

      if (classLoader == null) {
         throw new IllegalArgumentException("Unable to create class loader for " + className);
      } else {
         return classLoader;
      }
   }

   private ClassLoader newClassLoader(Class<ClassLoader> loaderClass, Class<?>[] parameterTypes, Object... initargs) throws Exception {
      try {
         Constructor<ClassLoader> constructor = loaderClass.getDeclaredConstructor(parameterTypes);
         constructor.setAccessible(true);
         return (ClassLoader)constructor.newInstance(initargs);
      } catch (NoSuchMethodException var5) {
         return null;
      }
   }

   private String getProperty(String propertyKey) throws Exception {
      return this.getProperty(propertyKey, null, null);
   }

   private String getProperty(String propertyKey, String manifestKey) throws Exception {
      return this.getProperty(propertyKey, manifestKey, null);
   }

   private String getPropertyWithDefault(String propertyKey, String defaultValue) throws Exception {
      return this.getProperty(propertyKey, null, defaultValue);
   }

   private String getProperty(String propertyKey, String manifestKey, String defaultValue) throws Exception {
      if (manifestKey == null) {
         manifestKey = propertyKey.replace('.', '-');
         manifestKey = toCamelCase(manifestKey);
      }

      String property = SystemPropertyUtils.getProperty(propertyKey);
      if (property != null) {
         String value = SystemPropertyUtils.resolvePlaceholders(this.properties, property);
         this.debug("Property '" + propertyKey + "' from environment: " + value);
         return value;
      } else if (this.properties.containsKey(propertyKey)) {
         String value = SystemPropertyUtils.resolvePlaceholders(this.properties, this.properties.getProperty(propertyKey));
         this.debug("Property '" + propertyKey + "' from properties: " + value);
         return value;
      } else {
         try {
            label151:
            if (this.home != null) {
               String var9;
               try (ExplodedArchive archive = new ExplodedArchive(this.home, false)) {
                  Manifest manifest = archive.getManifest();
                  if (manifest == null) {
                     break label151;
                  }

                  String value = manifest.getMainAttributes().getValue(manifestKey);
                  if (value == null) {
                     break label151;
                  }

                  this.debug("Property '" + manifestKey + "' from home directory manifest: " + value);
                  var9 = SystemPropertyUtils.resolvePlaceholders(this.properties, value);
               }

               return var9;
            }
         } catch (IllegalStateException var22) {
         }

         Manifest manifest = this.createArchive().getManifest();
         if (manifest != null) {
            String value = manifest.getMainAttributes().getValue(manifestKey);
            if (value != null) {
               this.debug("Property '" + manifestKey + "' from archive manifest: " + value);
               return SystemPropertyUtils.resolvePlaceholders(this.properties, value);
            }
         }

         return defaultValue != null ? SystemPropertyUtils.resolvePlaceholders(this.properties, defaultValue) : defaultValue;
      }
   }

   @Override
   protected Iterator<Archive> getClassPathArchivesIterator() throws Exception {
      PropertiesLauncher.ClassPathArchives classPathArchives = this.classPathArchives;
      if (classPathArchives == null) {
         classPathArchives = new PropertiesLauncher.ClassPathArchives();
         this.classPathArchives = classPathArchives;
      }

      return classPathArchives.iterator();
   }

   public static void main(String[] args) throws Exception {
      PropertiesLauncher launcher = new PropertiesLauncher();
      args = launcher.getArgs(args);
      launcher.launch(args);
   }

   public static String toCamelCase(CharSequence string) {
      if (string == null) {
         return null;
      } else {
         StringBuilder builder = new StringBuilder();
         Matcher matcher = WORD_SEPARATOR.matcher(string);

         int pos;
         for(pos = 0; matcher.find(); pos = matcher.end()) {
            builder.append(capitalize(string.subSequence(pos, matcher.end()).toString()));
         }

         builder.append(capitalize(string.subSequence(pos, string.length()).toString()));
         return builder.toString();
      }
   }

   private static String capitalize(String str) {
      return Character.toUpperCase(str.charAt(0)) + str.substring(1);
   }

   private void debug(String message) {
      if (Boolean.getBoolean("loader.debug")) {
         System.out.println(message);
      }

   }

   private String cleanupPath(String path) {
      path = path.trim();
      if (path.startsWith("./")) {
         path = path.substring(2);
      }

      String lowerCasePath = path.toLowerCase(Locale.ENGLISH);
      if (!lowerCasePath.endsWith(".jar") && !lowerCasePath.endsWith(".zip")) {
         if (path.endsWith("/*")) {
            path = path.substring(0, path.length() - 1);
         } else if (!path.endsWith("/") && !path.equals(".")) {
            path = path + "/";
         }

         return path;
      } else {
         return path;
      }
   }

   void close() throws Exception {
      if (this.classPathArchives != null) {
         this.classPathArchives.close();
      }

      if (this.parent != null) {
         this.parent.close();
      }

   }

   private static final class ArchiveEntryFilter implements Archive.EntryFilter {
      private static final String DOT_JAR = ".jar";
      private static final String DOT_ZIP = ".zip";

      private ArchiveEntryFilter() {
      }

      @Override
      public boolean matches(Archive.Entry entry) {
         return entry.getName().endsWith(".jar") || entry.getName().endsWith(".zip");
      }
   }

   private class ClassPathArchives implements Iterable<Archive> {
      private final List<Archive> classPathArchives;
      private final List<JarFileArchive> jarFileArchives = new ArrayList();

      ClassPathArchives() throws Exception {
         this.classPathArchives = new ArrayList();

         for(String path : PropertiesLauncher.this.paths) {
            for(Archive archive : this.getClassPathArchives(path)) {
               this.addClassPathArchive(archive);
            }
         }

         this.addNestedEntries();
      }

      private void addClassPathArchive(Archive archive) throws IOException {
         if (!(archive instanceof ExplodedArchive)) {
            this.classPathArchives.add(archive);
         } else {
            this.classPathArchives.add(archive);
            this.classPathArchives.addAll(this.asList(archive.getNestedArchives(null, new PropertiesLauncher.ArchiveEntryFilter())));
         }
      }

      private List<Archive> getClassPathArchives(String path) throws Exception {
         String root = PropertiesLauncher.this.cleanupPath(PropertiesLauncher.this.handleUrl(path));
         List<Archive> lib = new ArrayList();
         File file = new File(root);
         if (!"/".equals(root)) {
            if (!this.isAbsolutePath(root)) {
               file = new File(PropertiesLauncher.this.home, root);
            }

            if (file.isDirectory()) {
               PropertiesLauncher.this.debug("Adding classpath entries from " + file);
               Archive archive = new ExplodedArchive(file, false);
               lib.add(archive);
            }
         }

         Archive archive = this.getArchive(file);
         if (archive != null) {
            PropertiesLauncher.this.debug("Adding classpath entries from archive " + archive.getUrl() + root);
            lib.add(archive);
         }

         List<Archive> nestedArchives = this.getNestedArchives(root);
         if (nestedArchives != null) {
            PropertiesLauncher.this.debug("Adding classpath entries from nested " + root);
            lib.addAll(nestedArchives);
         }

         return lib;
      }

      private boolean isAbsolutePath(String root) {
         return root.contains(":") || root.startsWith("/");
      }

      private Archive getArchive(File file) throws IOException {
         if (this.isNestedArchivePath(file)) {
            return null;
         } else {
            String name = file.getName().toLowerCase(Locale.ENGLISH);
            return !name.endsWith(".jar") && !name.endsWith(".zip") ? null : this.getJarFileArchive(file);
         }
      }

      private boolean isNestedArchivePath(File file) {
         return file.getPath().contains(PropertiesLauncher.NESTED_ARCHIVE_SEPARATOR);
      }

      private List<Archive> getNestedArchives(String path) throws Exception {
         Archive parent = PropertiesLauncher.this.parent;
         String root = path;
         if ((path.equals("/") || !path.startsWith("/")) && !parent.getUrl().toURI().equals(PropertiesLauncher.this.home.toURI())) {
            int index = path.indexOf(33);
            if (index != -1) {
               File file = new File(PropertiesLauncher.this.home, path.substring(0, index));
               if (path.startsWith("jar:file:")) {
                  file = new File(path.substring("jar:file:".length(), index));
               }

               parent = this.getJarFileArchive(file);
               root = path.substring(index + 1);

               while(root.startsWith("/")) {
                  root = root.substring(1);
               }
            }

            if (root.endsWith(".jar")) {
               File file = new File(PropertiesLauncher.this.home, root);
               if (file.exists()) {
                  parent = this.getJarFileArchive(file);
                  root = "";
               }
            }

            if (root.equals("/") || root.equals("./") || root.equals(".")) {
               root = "";
            }

            Archive.EntryFilter filter = new PropertiesLauncher.PrefixMatchingArchiveFilter(root);
            List<Archive> archives = this.asList(parent.getNestedArchives(null, filter));
            if ((root == null || root.isEmpty() || ".".equals(root)) && !path.endsWith(".jar") && parent != PropertiesLauncher.this.parent) {
               archives.add(parent);
            }

            return archives;
         } else {
            return null;
         }
      }

      private void addNestedEntries() {
         try {
            Iterator<Archive> archives = PropertiesLauncher.this.parent.getNestedArchives(null, JarLauncher.NESTED_ARCHIVE_ENTRY_FILTER);

            while(archives.hasNext()) {
               this.classPathArchives.add(archives.next());
            }
         } catch (IOException var2) {
         }

      }

      private List<Archive> asList(Iterator<Archive> iterator) {
         List<Archive> list = new ArrayList();

         while(iterator.hasNext()) {
            list.add(iterator.next());
         }

         return list;
      }

      private JarFileArchive getJarFileArchive(File file) throws IOException {
         JarFileArchive archive = new JarFileArchive(file);
         this.jarFileArchives.add(archive);
         return archive;
      }

      public Iterator<Archive> iterator() {
         return this.classPathArchives.iterator();
      }

      void close() throws IOException {
         for(JarFileArchive archive : this.jarFileArchives) {
            archive.close();
         }

      }
   }

   private static final class PrefixMatchingArchiveFilter implements Archive.EntryFilter {
      private final String prefix;
      private final PropertiesLauncher.ArchiveEntryFilter filter = new PropertiesLauncher.ArchiveEntryFilter();

      private PrefixMatchingArchiveFilter(String prefix) {
         this.prefix = prefix;
      }

      @Override
      public boolean matches(Archive.Entry entry) {
         if (entry.isDirectory()) {
            return entry.getName().equals(this.prefix);
         } else {
            return entry.getName().startsWith(this.prefix) && this.filter.matches(entry);
         }
      }
   }
}
