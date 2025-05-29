package org.springframework.boot.loader.jar;

import java.nio.charset.StandardCharsets;

final class AsciiBytes {
   private static final String EMPTY_STRING = "";
   private static final int[] INITIAL_BYTE_BITMASK = new int[]{127, 31, 15, 7};
   private static final int SUBSEQUENT_BYTE_BITMASK = 63;
   private final byte[] bytes;
   private final int offset;
   private final int length;
   private String string;
   private int hash;

   AsciiBytes(String string) {
      this(string.getBytes(StandardCharsets.UTF_8));
      this.string = string;
   }

   AsciiBytes(byte[] bytes) {
      this(bytes, 0, bytes.length);
   }

   AsciiBytes(byte[] bytes, int offset, int length) {
      if (offset >= 0 && length >= 0 && offset + length <= bytes.length) {
         this.bytes = bytes;
         this.offset = offset;
         this.length = length;
      } else {
         throw new IndexOutOfBoundsException();
      }
   }

   int length() {
      return this.length;
   }

   boolean startsWith(AsciiBytes prefix) {
      if (this == prefix) {
         return true;
      } else if (prefix.length > this.length) {
         return false;
      } else {
         for(int i = 0; i < prefix.length; ++i) {
            if (this.bytes[i + this.offset] != prefix.bytes[i + prefix.offset]) {
               return false;
            }
         }

         return true;
      }
   }

   boolean endsWith(AsciiBytes postfix) {
      if (this == postfix) {
         return true;
      } else if (postfix.length > this.length) {
         return false;
      } else {
         for(int i = 0; i < postfix.length; ++i) {
            if (this.bytes[this.offset + (this.length - 1) - i] != postfix.bytes[postfix.offset + (postfix.length - 1) - i]) {
               return false;
            }
         }

         return true;
      }
   }

   AsciiBytes substring(int beginIndex) {
      return this.substring(beginIndex, this.length);
   }

   AsciiBytes substring(int beginIndex, int endIndex) {
      int length = endIndex - beginIndex;
      if (this.offset + length > this.bytes.length) {
         throw new IndexOutOfBoundsException();
      } else {
         return new AsciiBytes(this.bytes, this.offset + beginIndex, length);
      }
   }

   boolean matches(CharSequence name, char suffix) {
      int charIndex = 0;
      int nameLen = name.length();
      int totalLen = nameLen + (suffix != 0 ? 1 : 0);

      for(int i = this.offset; i < this.offset + this.length; ++i) {
         int b = this.bytes[i];
         int remainingUtfBytes = this.getNumberOfUtfBytes(b) - 1;
         b &= INITIAL_BYTE_BITMASK[remainingUtfBytes];

         for(int j = 0; j < remainingUtfBytes; ++j) {
            b = (b << 6) + (this.bytes[++i] & 63);
         }

         char c = this.getChar(name, suffix, charIndex++);
         if (b <= 65535) {
            if (c != b) {
               return false;
            }
         } else {
            if (c != (b >> 10) + 55232) {
               return false;
            }

            c = this.getChar(name, suffix, charIndex++);
            if (c != (b & 1023) + 56320) {
               return false;
            }
         }
      }

      return charIndex == totalLen;
   }

   private char getChar(CharSequence name, char suffix, int index) {
      if (index < name.length()) {
         return name.charAt(index);
      } else {
         return index == name.length() ? suffix : '\u0000';
      }
   }

   private int getNumberOfUtfBytes(int b) {
      if ((b & 128) == 0) {
         return 1;
      } else {
         int numberOfUtfBytes;
         for(numberOfUtfBytes = 0; (b & 128) != 0; ++numberOfUtfBytes) {
            b <<= 1;
         }

         return numberOfUtfBytes;
      }
   }

   public boolean equals(Object obj) {
      if (obj == null) {
         return false;
      } else if (this == obj) {
         return true;
      } else {
         if (obj.getClass() == AsciiBytes.class) {
            AsciiBytes other = (AsciiBytes)obj;
            if (this.length == other.length) {
               for(int i = 0; i < this.length; ++i) {
                  if (this.bytes[this.offset + i] != other.bytes[other.offset + i]) {
                     return false;
                  }
               }

               return true;
            }
         }

         return false;
      }
   }

   public int hashCode() {
      int hash = this.hash;
      if (hash == 0 && this.bytes.length > 0) {
         for(int i = this.offset; i < this.offset + this.length; ++i) {
            int b = this.bytes[i];
            int remainingUtfBytes = this.getNumberOfUtfBytes(b) - 1;
            b &= INITIAL_BYTE_BITMASK[remainingUtfBytes];

            for(int j = 0; j < remainingUtfBytes; ++j) {
               b = (b << 6) + (this.bytes[++i] & 63);
            }

            if (b <= 65535) {
               hash = 31 * hash + b;
            } else {
               hash = 31 * hash + (b >> 10) + 55232;
               hash = 31 * hash + (b & 1023) + 56320;
            }
         }

         this.hash = hash;
      }

      return hash;
   }

   public String toString() {
      if (this.string == null) {
         if (this.length == 0) {
            this.string = "";
         } else {
            this.string = new String(this.bytes, this.offset, this.length, StandardCharsets.UTF_8);
         }
      }

      return this.string;
   }

   static String toString(byte[] bytes) {
      return new String(bytes, StandardCharsets.UTF_8);
   }

   static int hashCode(CharSequence charSequence) {
      return charSequence instanceof StringSequence ? charSequence.hashCode() : charSequence.toString().hashCode();
   }

   static int hashCode(int hash, char suffix) {
      return suffix != 0 ? 31 * hash + suffix : hash;
   }
}
