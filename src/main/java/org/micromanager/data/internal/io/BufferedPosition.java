package org.micromanager.data.internal.io;

import com.google.common.base.Preconditions;

public class BufferedPosition implements FilePosition {
   private long bufferStartPositionInFile_ = -1;
   private int positionInBuffer_ = -1;

   private BufferedPosition() {}

   public static BufferedPosition forBufferAt(long posInFile) {
      BufferedPosition i = new BufferedPosition();
      i.bufferStartPositionInFile_ = posInFile;
      return i;
   }

   public static BufferedPosition forPositionInBuffer(int posInBuffer) {
      BufferedPosition i = new BufferedPosition();
      i.positionInBuffer_ = posInBuffer;
      return i;
   }

   public BufferedPosition setBufferFileOffset(long posInFile) {
      Preconditions.checkState(bufferStartPositionInFile_ == -1,
         "buffer start position already set");
      bufferStartPositionInFile_ = posInFile;
      return this;
   }

   public BufferedPosition setPositionInBuffer(int posInBuffer) {
      Preconditions.checkState(positionInBuffer_ == -1,
         "position in buffer already set");
      positionInBuffer_ = posInBuffer;
      return this;
   }

   @Override
   public boolean isComplete() {
      return bufferStartPositionInFile_ != -1 && positionInBuffer_ != -1;
   }

   @Override
   public long get() {
      Preconditions.checkState(bufferStartPositionInFile_ != -1,
         "buffer start position has not been set");
      Preconditions.checkState(positionInBuffer_ != -1,
         "position in buffer has not been set");
      return bufferStartPositionInFile_ + positionInBuffer_;
   }

   @Override
   public boolean hasPositionInBuffer() {
      return positionInBuffer_ != -1;
   }

   @Override
   public int getPositionInBuffer() {
      Preconditions.checkState(positionInBuffer_ != -1,
         "position in buffer has not been set");
      return positionInBuffer_;
   }

   @Override
   public String toString() {
      StringBuilder values = new StringBuilder();
      if (bufferStartPositionInFile_ != -1) {
         values.append(String.format(" bufferStart=%d", bufferStartPositionInFile_));
      }
      if (positionInBuffer_ != -1) {
         values.append(String.format(" posInBuffer=%d", positionInBuffer_));
      }
      return String.format("<BufferedPosition (%s)%s>",
         isComplete() ? "complete" : "incomplete",
         values);
   }
}
