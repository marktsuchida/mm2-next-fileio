package org.micromanager.data.internal.io;

public interface FilePosition {
   boolean isComplete();

   long get();

   default boolean hasPositionInBuffer() {
      return false;
   }

   default int getPositionInBuffer() {
      throw new UnsupportedOperationException();
   }
}
