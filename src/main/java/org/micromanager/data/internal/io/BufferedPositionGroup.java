package org.micromanager.data.internal.io;

import java.util.HashSet;
import java.util.Set;

public class BufferedPositionGroup {
   private final Set<BufferedPosition> positions_ = new HashSet<>();
   private long bufferStartPositionInFile_; // -1 == unset

   private BufferedPositionGroup(long bufferStart) {
      bufferStartPositionInFile_ = bufferStart;
   }

   public static BufferedPositionGroup create() {
      return new BufferedPositionGroup(-1);
   }

   public static BufferedPositionGroup forBufferAt(long bufferStart) {
      return new BufferedPositionGroup(bufferStart);
   }

   public BufferedPosition positionInBuffer(int position) {
      BufferedPosition ret = BufferedPosition.forPositionInBuffer(position);
      if (bufferStartPositionInFile_ != -1) {
         ret.setBufferFileOffset(bufferStartPositionInFile_);
      }
      positions_.add(ret);
      return ret;
   }

   public void setBufferFileOffset(long bufferStart) {
      bufferStartPositionInFile_ = bufferStart;
      for (BufferedPosition p : positions_) {
         p.setBufferFileOffset(bufferStart);
      }
   }
}
