package org.micromanager.data.internal.io;

import com.google.common.base.Preconditions;

import java.nio.ByteBuffer;

public class Alignment {
   private Alignment() {}

   public static int align(int i, int alignment) {
      Preconditions.checkArgument(alignment > 0,
         "Alignment must be positive");
      Preconditions.checkArgument(Integer.highestOneBit(alignment) == alignment,
         "Alignment must be to a power of 2");

      return ((i - 1) & ~(alignment - 1)) + alignment;
   }

   public static long align(long i, long alignment) {
      Preconditions.checkArgument(alignment > 0L,
         "Alignment must be positive");
      Preconditions.checkArgument(Long.highestOneBit(alignment) == alignment,
         "Alignment must be to a power of 2");

      return ((i - 1L) & ~(alignment - 1L)) + alignment;
   }
}
