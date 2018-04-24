package org.micromanager.data.internal.io;

public final class Unsigned {
   public static int from(byte u) {
      return ((short) u) & 0xff;
   }

   public static int from(short u) {
      return ((int) u) & 0xffff;
   }

   public static long from(int u) {
      return ((long) u) & 0xffffffffL;
   }
}
