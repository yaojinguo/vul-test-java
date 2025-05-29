package org.springframework.boot.loader.jar;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Permission;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import org.springframework.boot.loader.data.RandomAccessData;
import org.springframework.boot.loader.data.RandomAccessDataFile;

public class JarFile extends AbstractJarFile implements Iterable<java.util.jar.JarEntry> {
   private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";
   private static final String PROTOCOL_HANDLER = "java.protocol.handler.pkgs";
   private static final String HANDLERS_PACKAGE = "org.springframework.boot.loader";
   private static final AsciiBytes META_INF = new AsciiBytes("META-INF/");
   private static final AsciiBytes SIGNATURE_FILE_EXTENSION = new AsciiBytes(".SF");
   private static final String READ_ACTION = "read";
   private final RandomAccessDataFile rootFile;
   private final String pathFromRoot;
   private final RandomAccessData data;
   private final AbstractJarFile.JarFileType type;
   private URL url;
   private String urlString;
   private JarFileEntries entries;
   private Supplier<Manifest> manifestSupplier;
   private SoftReference<Manifest> manifest;
   private boolean signed;
   private String comment;
   private volatile boolean closed;

   public JarFile(File file) throws IOException {
      this(new RandomAccessDataFile(file));
   }

   JarFile(RandomAccessDataFile file) throws IOException {
      this(file, "", file, AbstractJarFile.JarFileType.DIRECT);
   }

   private JarFile(RandomAccessDataFile rootFile, String pathFromRoot, RandomAccessData data, AbstractJarFile.JarFileType type) throws IOException {
      this(rootFile, pathFromRoot, data, null, type, null);
   }

   private JarFile(
      RandomAccessDataFile rootFile,
      String pathFromRoot,
      RandomAccessData data,
      JarEntryFilter filter,
      AbstractJarFile.JarFileType type,
      Supplier<Manifest> manifestSupplier
   ) throws IOException {
      super(rootFile.getFile());
      super.close();
      this.rootFile = rootFile;
      this.pathFromRoot = pathFromRoot;
      CentralDirectoryParser parser = new CentralDirectoryParser();
      this.entries = parser.addVisitor(new JarFileEntries(this, filter));
      this.type = type;
      parser.addVisitor(this.centralDirectoryVisitor());

      try {
         this.data = parser.parse(data, filter == null);
      } catch (RuntimeException var9) {
         this.close();
         throw var9;
      }

      this.manifestSupplier = manifestSupplier != null ? manifestSupplier : () -> {
         try {
            InputStream inputStream = this.getInputStream("META-INF/MANIFEST.MF");
            Throwable var2x = null;

            Object var3x;
            try {
               if (inputStream != null) {
                  return new Manifest(inputStream);
               }

               var3x = null;
            } catch (Throwable var14) {
               var2x = var14;
               throw var14;
            } finally {
               if (inputStream != null) {
                  if (var2x != null) {
                     try {
                        inputStream.close();
                     } catch (Throwable var13) {
                        var2x.addSuppressed(var13);
                     }
                  } else {
                     inputStream.close();
                  }
               }

            }

            return (Manifest)var3x;
         } catch (IOException var16) {
            throw new RuntimeException(var16);
         }
      };
   }

   private CentralDirectoryVisitor centralDirectoryVisitor() {
      return new CentralDirectoryVisitor() {
         @Override
         public void visitStart(CentralDirectoryEndRecord endRecord, RandomAccessData centralDirectoryData) {
            JarFile.this.comment = endRecord.getComment();
         }

         @Override
         public void visitFileHeader(CentralDirectoryFileHeader fileHeader, int dataOffset) {
            AsciiBytes name = fileHeader.getName();
            if (name.startsWith(JarFile.META_INF) && name.endsWith(JarFile.SIGNATURE_FILE_EXTENSION)) {
               JarFile.this.signed = true;
            }

         }

         @Override
         public void visitEnd() {
         }
      };
   }

   @Override
   Permission getPermission() {
      return new FilePermission(this.rootFile.getFile().getPath(), "read");
   }

   protected final RandomAccessDataFile getRootJarFile() {
      return this.rootFile;
   }

   RandomAccessData getData() {
      return this.data;
   }

   public Manifest getManifest() throws IOException {
      Manifest manifest = this.manifest != null ? (Manifest)this.manifest.get() : null;
      if (manifest == null) {
         try {
            manifest = (Manifest)this.manifestSupplier.get();
         } catch (RuntimeException var3) {
            throw new IOException(var3);
         }

         this.manifest = new SoftReference(manifest);
      }

      return manifest;
   }

   public Enumeration<java.util.jar.JarEntry> entries() {
      return new JarFile.JarEntryEnumeration(this.entries.iterator());
   }

   public Stream<java.util.jar.JarEntry> stream() {
      Spliterator<java.util.jar.JarEntry> spliterator = Spliterators.spliterator(this.iterator(), (long)this.size(), 1297);
      return StreamSupport.stream(spliterator, false);
   }

   public Iterator<java.util.jar.JarEntry> iterator() {
      return this.entries.iterator(this::ensureOpen);
   }

   public JarEntry getJarEntry(CharSequence name) {
      return this.entries.getEntry(name);
   }

   public JarEntry getJarEntry(String name) {
      return (JarEntry)this.getEntry(name);
   }

   public boolean containsEntry(String name) {
      return this.entries.containsEntry(name);
   }

   public ZipEntry getEntry(String name) {
      this.ensureOpen();
      return this.entries.getEntry(name);
   }

   @Override
   InputStream getInputStream() throws IOException {
      return this.data.getInputStream();
   }

   public synchronized InputStream getInputStream(ZipEntry entry) throws IOException {
      this.ensureOpen();
      return entry instanceof JarEntry ? this.entries.getInputStream((JarEntry)entry) : this.getInputStream(entry != null ? entry.getName() : null);
   }

   InputStream getInputStream(String name) throws IOException {
      return this.entries.getInputStream(name);
   }

   public synchronized JarFile getNestedJarFile(ZipEntry entry) throws IOException {
      return this.getNestedJarFile((JarEntry)entry);
   }

   public synchronized JarFile getNestedJarFile(JarEntry entry) throws IOException {
      try {
         return this.createJarFileFromEntry(entry);
      } catch (Exception var3) {
         throw new IOException("Unable to open nested jar file '" + entry.getName() + "'", var3);
      }
   }

   private JarFile createJarFileFromEntry(JarEntry entry) throws IOException {
      return entry.isDirectory() ? this.createJarFileFromDirectoryEntry(entry) : this.createJarFileFromFileEntry(entry);
   }

   private JarFile createJarFileFromDirectoryEntry(JarEntry entry) throws IOException {
      AsciiBytes name = entry.getAsciiBytesName();
      JarEntryFilter filter = candidate -> candidate.startsWith(name) && !candidate.equals(name) ? candidate.substring(name.length()) : null;
      return new JarFile(
         this.rootFile,
         this.pathFromRoot + "!/" + entry.getName().substring(0, name.length() - 1),
         this.data,
         filter,
         AbstractJarFile.JarFileType.NESTED_DIRECTORY,
         this.manifestSupplier
      );
   }

   private JarFile createJarFileFromFileEntry(JarEntry entry) throws IOException {
      if (entry.getMethod() != 0) {
         throw new IllegalStateException(
            "Unable to open nested entry '"
               + entry.getName()
               + "'. It has been compressed and nested jar files must be stored without compression. Please check the mechanism used to create your executable jar file"
         );
      } else {
         RandomAccessData entryData = this.entries.getEntryData(entry.getName());
         return new JarFile(this.rootFile, this.pathFromRoot + "!/" + entry.getName(), entryData, AbstractJarFile.JarFileType.NESTED_JAR);
      }
   }

   public String getComment() {
      this.ensureOpen();
      return this.comment;
   }

   public int size() {
      this.ensureOpen();
      return this.entries.getSize();
   }

   public void close() throws IOException {
      if (!this.closed) {
         this.closed = true;
         if (this.type == AbstractJarFile.JarFileType.DIRECT) {
            this.rootFile.close();
         }

      }
   }

   private void ensureOpen() {
      if (this.closed) {
         throw new IllegalStateException("zip file closed");
      }
   }

   boolean isClosed() {
      return this.closed;
   }

   String getUrlString() throws MalformedURLException {
      if (this.urlString == null) {
         this.urlString = this.getUrl().toString();
      }

      return this.urlString;
   }

   @Override
   public URL getUrl() throws MalformedURLException {
      if (this.url == null) {
         String file = this.rootFile.getFile().toURI() + this.pathFromRoot + "!/";
         file = file.replace("file:////", "file://");
         this.url = new URL("jar", "", -1, file, new Handler(this));
      }

      return this.url;
   }

   public String toString() {
      return this.getName();
   }

   public String getName() {
      return this.rootFile.getFile() + this.pathFromRoot;
   }

   boolean isSigned() {
      return this.signed;
   }

   JarEntryCertification getCertification(JarEntry entry) {
      try {
         return this.entries.getCertification(entry);
      } catch (IOException var3) {
         throw new IllegalStateException(var3);
      }
   }

   public void clearCache() {
      this.entries.clearCache();
   }

   protected String getPathFromRoot() {
      return this.pathFromRoot;
   }

   @Override
   AbstractJarFile.JarFileType getType() {
      return this.type;
   }

   public static void registerUrlProtocolHandler() {
      Handler.captureJarContextUrl();
      String handlers = System.getProperty("java.protocol.handler.pkgs", "");
      System.setProperty(
         "java.protocol.handler.pkgs",
         handlers != null && !handlers.isEmpty() ? handlers + "|" + "org.springframework.boot.loader" : "org.springframework.boot.loader"
      );
      resetCachedUrlHandlers();
   }

   private static void resetCachedUrlHandlers() {
      try {
         URL.setURLStreamHandlerFactory(null);
      } catch (Error var1) {
      }

   }

   private static class JarEntryEnumeration implements Enumeration<java.util.jar.JarEntry> {
      private final Iterator<JarEntry> iterator;

      JarEntryEnumeration(Iterator<JarEntry> iterator) {
         this.iterator = iterator;
      }

      public boolean hasMoreElements() {
         return this.iterator.hasNext();
      }

      public java.util.jar.JarEntry nextElement() {
         return (java.util.jar.JarEntry)this.iterator.next();
      }
   }
}
