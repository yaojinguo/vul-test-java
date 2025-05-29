package org.springframework.boot.loader.archive;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;
import org.springframework.boot.loader.jar.JarFile;

public class JarFileArchive implements Archive {
   private static final String UNPACK_MARKER = "UNPACK:";
   private static final int BUFFER_SIZE = 32768;
   private final JarFile jarFile;
   private URL url;
   private File tempUnpackDirectory;

   public JarFileArchive(File file) throws IOException {
      this(file, file.toURI().toURL());
   }

   public JarFileArchive(File file, URL url) throws IOException {
      this(new JarFile(file));
      this.url = url;
   }

   public JarFileArchive(JarFile jarFile) {
      this.jarFile = jarFile;
   }

   @Override
   public URL getUrl() throws MalformedURLException {
      return this.url != null ? this.url : this.jarFile.getUrl();
   }

   @Override
   public Manifest getManifest() throws IOException {
      return this.jarFile.getManifest();
   }

   @Override
   public Iterator<Archive> getNestedArchives(Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) throws IOException {
      return new JarFileArchive.NestedArchiveIterator(this.jarFile.iterator(), searchFilter, includeFilter);
   }

   @Deprecated
   @Override
   public Iterator<Archive.Entry> iterator() {
      return new JarFileArchive.EntryIterator(this.jarFile.iterator(), null, null);
   }

   @Override
   public void close() throws IOException {
      this.jarFile.close();
   }

   protected Archive getNestedArchive(Archive.Entry entry) throws IOException {
      JarEntry jarEntry = ((JarFileArchive.JarFileEntry)entry).getJarEntry();
      if (jarEntry.getComment().startsWith("UNPACK:")) {
         return this.getUnpackedNestedArchive(jarEntry);
      } else {
         try {
            JarFile jarFile = this.jarFile.getNestedJarFile(jarEntry);
            return new JarFileArchive(jarFile);
         } catch (Exception var4) {
            throw new IllegalStateException("Failed to get nested archive for entry " + entry.getName(), var4);
         }
      }
   }

   private Archive getUnpackedNestedArchive(JarEntry jarEntry) throws IOException {
      String name = jarEntry.getName();
      if (name.lastIndexOf(47) != -1) {
         name = name.substring(name.lastIndexOf(47) + 1);
      }

      File file = new File(this.getTempUnpackDirectory(), name);
      if (!file.exists() || file.length() != jarEntry.getSize()) {
         this.unpack(jarEntry, file);
      }

      return new JarFileArchive(file, file.toURI().toURL());
   }

   private File getTempUnpackDirectory() {
      if (this.tempUnpackDirectory == null) {
         File tempDirectory = new File(System.getProperty("java.io.tmpdir"));
         this.tempUnpackDirectory = this.createUnpackDirectory(tempDirectory);
      }

      return this.tempUnpackDirectory;
   }

   private File createUnpackDirectory(File parent) {
      int attempts = 0;

      while(attempts++ < 1000) {
         String fileName = new File(this.jarFile.getName()).getName();
         File unpackDirectory = new File(parent, fileName + "-spring-boot-libs-" + UUID.randomUUID());
         if (unpackDirectory.mkdirs()) {
            return unpackDirectory;
         }
      }

      throw new IllegalStateException("Failed to create unpack directory in directory '" + parent + "'");
   }

   private void unpack(JarEntry entry, File file) throws IOException {
      InputStream inputStream = this.jarFile.getInputStream(entry);
      Throwable var4 = null;

      try {
         OutputStream outputStream = new FileOutputStream(file);
         Throwable var6 = null;

         try {
            byte[] buffer = new byte['耀'];

            int bytesRead;
            while((bytesRead = inputStream.read(buffer)) != -1) {
               outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();
         } catch (Throwable var30) {
            var6 = var30;
            throw var30;
         } finally {
            if (outputStream != null) {
               if (var6 != null) {
                  try {
                     outputStream.close();
                  } catch (Throwable var29) {
                     var6.addSuppressed(var29);
                  }
               } else {
                  outputStream.close();
               }
            }

         }
      } catch (Throwable var32) {
         var4 = var32;
         throw var32;
      } finally {
         if (inputStream != null) {
            if (var4 != null) {
               try {
                  inputStream.close();
               } catch (Throwable var28) {
                  var4.addSuppressed(var28);
               }
            } else {
               inputStream.close();
            }
         }

      }

   }

   public String toString() {
      try {
         return this.getUrl().toString();
      } catch (Exception var2) {
         return "jar archive";
      }
   }

   private abstract static class AbstractIterator<T> implements Iterator<T> {
      private final Iterator<JarEntry> iterator;
      private final Archive.EntryFilter searchFilter;
      private final Archive.EntryFilter includeFilter;
      private Archive.Entry current;

      AbstractIterator(Iterator<JarEntry> iterator, Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) {
         this.iterator = iterator;
         this.searchFilter = searchFilter;
         this.includeFilter = includeFilter;
         this.current = this.poll();
      }

      public boolean hasNext() {
         return this.current != null;
      }

      public T next() {
         T result = this.adapt(this.current);
         this.current = this.poll();
         return result;
      }

      private Archive.Entry poll() {
         while(this.iterator.hasNext()) {
            JarFileArchive.JarFileEntry candidate = new JarFileArchive.JarFileEntry((JarEntry)this.iterator.next());
            if ((this.searchFilter == null || this.searchFilter.matches(candidate)) && (this.includeFilter == null || this.includeFilter.matches(candidate))) {
               return candidate;
            }
         }

         return null;
      }

      protected abstract T adapt(Archive.Entry entry);
   }

   private static class EntryIterator extends JarFileArchive.AbstractIterator<Archive.Entry> {
      EntryIterator(Iterator<JarEntry> iterator, Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) {
         super(iterator, searchFilter, includeFilter);
      }

      protected Archive.Entry adapt(Archive.Entry entry) {
         return entry;
      }
   }

   private static class JarFileEntry implements Archive.Entry {
      private final JarEntry jarEntry;

      JarFileEntry(JarEntry jarEntry) {
         this.jarEntry = jarEntry;
      }

      JarEntry getJarEntry() {
         return this.jarEntry;
      }

      @Override
      public boolean isDirectory() {
         return this.jarEntry.isDirectory();
      }

      @Override
      public String getName() {
         return this.jarEntry.getName();
      }
   }

   private class NestedArchiveIterator extends JarFileArchive.AbstractIterator<Archive> {
      NestedArchiveIterator(Iterator<JarEntry> iterator, Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) {
         super(iterator, searchFilter, includeFilter);
      }

      protected Archive adapt(Archive.Entry entry) {
         try {
            return JarFileArchive.this.getNestedArchive(entry);
         } catch (IOException var3) {
            throw new IllegalStateException(var3);
         }
      }
   }
}
