package org.micromanager.data.internal.io;

public class UnbufferedPosition implements FilePosition {
   private final long positionInFile_;

   private UnbufferedPosition(long posInFile) {
      positionInFile_ = posInFile;
   }

   public static UnbufferedPosition at(long posInFile) {
      return new UnbufferedPosition(posInFile);
   }

   @Override
   public boolean isComplete() {
      return true;
   }

   @Override
   public long get() {
      return positionInFile_;
   }

   @Override
   public String toString() {
      return String.format("<UnbufferedPosition %d>", positionInFile_);
   }
}
