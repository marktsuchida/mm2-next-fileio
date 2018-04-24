package org.micromanager.data.internal.io.mmtiff;

import com.google.common.base.Preconditions;
import org.micromanager.data.internal.io.Async;
import org.micromanager.data.internal.io.BufferedPositionGroup;
import org.micromanager.data.internal.io.FilePosition;
import org.micromanager.data.internal.io.UnbufferedPosition;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.util.concurrent.CompletionStage;

public class TiffOffsetField {
   private static final int PLACEHOLDER_VALUE = 0;

   private FilePosition fieldPosition_;
   private FilePosition offsetValue_;

   private final String debugAnnotation_;

   private TiffOffsetField(FilePosition position, FilePosition value, String annotation) {
      fieldPosition_ = position;
      offsetValue_ = value;
      debugAnnotation_ = annotation;
   }

   public static TiffOffsetField create(String annotation) {
      return new TiffOffsetField(null, null, annotation);
   }

   public static TiffOffsetField atPosition(FilePosition position,
                                            String annotation) {
      Preconditions.checkNotNull(position);
      return new TiffOffsetField(position, null, annotation);
   }

   public static TiffOffsetField forOffsetValue(FilePosition offset,
                                                String annotation) {
      Preconditions.checkNotNull(offset);
      return new TiffOffsetField(null, offset, annotation);
   }

   public void setFieldPosition(FilePosition position) {
      Preconditions.checkNotNull(position);
      Preconditions.checkState(fieldPosition_ == null,
         "Attempt to overwrite field position in " + this);
      fieldPosition_ = position;
   }

   public void setOffsetValue(FilePosition offset) {
      Preconditions.checkNotNull(offset);
      Preconditions.checkState(offsetValue_ == null,
         "Attempt to overwrite offset value in " + this);
      offsetValue_ = offset;
   }

   public FilePosition getFieldPosition() {
      Preconditions.checkState(fieldPosition_ != null,
         "Missing field position in " + this);
      return fieldPosition_;
   }

   public FilePosition getOffsetValue() {
      Preconditions.checkState(offsetValue_ != null,
         "Missing offset value in " + this);
      return offsetValue_;
   }

   //
   //
   //

   private void checkCompleteFieldPosition() {
      Preconditions.checkState(fieldPosition_ != null &&
            fieldPosition_.isComplete(),
         "Missing or incomplete field position in " + this);
   }

   private void checkBufferRelativeFieldPosition() {
      Preconditions.checkState(fieldPosition_ != null &&
            fieldPosition_.hasPositionInBuffer(),
         "Field position does not have a buffer-relative offset in " + this);
   }

   private void checkCompleteOffsetValue() {
      Preconditions.checkState(isOffsetValueComplete(),
         "Missing or incomplete offset value in " + this);
   }

   private boolean isOffsetValueComplete() {
      return offsetValue_ != null && offsetValue_.isComplete();
   }

   private static ByteBuffer makeWriteBuffer(int value, ByteOrder order) {
      ByteBuffer buffer = ByteBuffer.allocate(4).order(order);
      buffer.putInt(value).rewind();
      return buffer;
   }

   //
   //
   //

   CompletionStage<Void> write(AsynchronousFileChannel chan,
                               ByteOrder order,
                               long offset) {
      setFieldPosition(UnbufferedPosition.at(offset));
      return Async.write(chan,
         makeWriteBuffer(
            isOffsetValueComplete() ? (int) offsetValue_.get() : PLACEHOLDER_VALUE,
            order),
         fieldPosition_.get());
   }

   CompletionStage<Void> update(AsynchronousFileChannel chan, ByteOrder order) {
      checkCompleteFieldPosition();
      checkCompleteOffsetValue();
      return Async.write(chan, makeWriteBuffer((int) offsetValue_.get(), order),
         fieldPosition_.get());
   }

   public void write(ByteBuffer buffer, BufferedPositionGroup posGroup) {
      setFieldPosition(posGroup.positionInBuffer(buffer.position()));
      if (isOffsetValueComplete()) {
         write(buffer);
      }
      else {
         buffer.putInt(PLACEHOLDER_VALUE);
      }
   }

   public void write(ByteBuffer buffer) {
      checkCompleteOffsetValue();
      buffer.putInt((int) offsetValue_.get());
   }

   public void update(ByteBuffer buffer) {
      checkCompleteOffsetValue();
      checkBufferRelativeFieldPosition();
      buffer.putInt(fieldPosition_.getPositionInBuffer(),
         (int) offsetValue_.get());
   }

   @Override
   public String toString() {
      return String.format("<TiffOffsetField (%s) fieldPosition=%s offsetValue=%s>",
         debugAnnotation_, fieldPosition_, offsetValue_);
   }
}
