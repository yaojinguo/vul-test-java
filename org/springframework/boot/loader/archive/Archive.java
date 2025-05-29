package org.springframework.boot.loader.archive;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.jar.Manifest;

public interface Archive extends Iterable<Archive.Entry>, AutoCloseable {
   URL getUrl() throws MalformedURLException;

   Manifest getManifest() throws IOException;

   default Iterator<Archive> getNestedArchives(Archive.EntryFilter searchFilter, Archive.EntryFilter includeFilter) throws IOException {
      Archive.EntryFilter combinedFilter = entry -> (searchFilter == null || searchFilter.matches(entry))
            && (includeFilter == null || includeFilter.matches(entry));
      List<Archive> nestedArchives = this.getNestedArchives(combinedFilter);
      return nestedArchives.iterator();
   }

   @Deprecated
   default List<Archive> getNestedArchives(Archive.EntryFilter filter) throws IOException {
      throw new IllegalStateException("Unexpected call to getNestedArchives(filter)");
   }

   @Deprecated
   Iterator<Archive.Entry> iterator();

   @Deprecated
   default void forEach(Consumer<? super Archive.Entry> action) {
      Objects.requireNonNull(action);

      for(Archive.Entry entry : this) {
         action.accept(entry);
      }

   }

   @Deprecated
   default Spliterator<Archive.Entry> spliterator() {
      return Spliterators.spliteratorUnknownSize(this.iterator(), 0);
   }

   default boolean isExploded() {
      return false;
   }

   default void close() throws Exception {
   }

   public interface Entry {
      boolean isDirectory();

      String getName();
   }

   @FunctionalInterface
   public interface EntryFilter {
      boolean matches(Archive.Entry entry);
   }
}
