package org.micromanager.data.internal.io.mmtiff;

import org.micromanager.data.internal.io.Alignment;
import org.micromanager.data.internal.io.Async;
import org.micromanager.data.internal.io.BufferedPosition;
import org.micromanager.data.internal.io.BufferedPositionGroup;
import org.micromanager.data.internal.io.FilePosition;
import org.micromanager.data.internal.io.UnbufferedPosition;
import org.micromanager.data.internal.io.Unsigned;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public abstract class TiffIFDEntry {
   protected final ByteOrder byteOrder_;
   private final TiffTag tag_;
   private final TiffFieldType type_;
   private final int count_;

   //
   //
   //

   public static TiffIFDEntry read(ByteBuffer b) throws TiffFormatException, EOFException {
      TiffTag tag = TiffTag.fromTiffConstant(Unsigned.from(b.getShort()));
      TiffFieldType type = TiffFieldType.fromTiffConstant(Unsigned.from(b.getShort()));
      tag.checkType(type);

      long longCount = Unsigned.from(b.getInt());
      if (longCount > Integer.MAX_VALUE) {
         throw new TiffFormatException(
            "IFD entry count greater than INT_MAX not supported");
      }
      int count = (int) longCount;

      if (type.fitsInIFDEntry(count)) {
         ByteBuffer bb = b.slice().order(b.order());
         b.getInt();
         TiffValue value = TiffValue.read(type, count, bb);
         return new Immediate(b.order(), tag, type, count, value);
      }
      long offset = Unsigned.from(b.getInt());
      return new Pointer(b.order(), tag, type, count, offset);
   }

   public static TiffIFDEntry createForWrite(ByteOrder order,
                                             TiffTag tag,
                                             TiffValue value,
                                             TiffOffsetFieldGroup fieldGroup) {
      if (value.fitsInIFDEntry()) {
         return new Immediate(order, tag, value);
      }
      TiffOffsetField offsetField = TiffOffsetField.create("Value of " + tag);
      fieldGroup.add(offsetField);
      return new Pointer(order, tag, value, offsetField);
   }

   protected TiffIFDEntry(ByteOrder order, TiffTag tag, TiffFieldType type, int count) {
      byteOrder_ = order;
      tag_ = tag;
      type_ = type;
      count_ = count;
   }

   //
   //
   //

   public TiffTag getTag() {
      return tag_;
   }

   public TiffFieldType getType() {
      return type_;
   }

   public int getCount() {
      return count_;
   }

   public abstract CompletionStage<TiffValue> readValue(AsynchronousFileChannel chan);

   public abstract CompletionStage<Void> writeValue(AsynchronousFileChannel chan);

   public abstract void writeValue(ByteBuffer dest, BufferedPositionGroup posGroup);

   public abstract void write(ByteBuffer dest, BufferedPositionGroup posGroup);

   //
   //
   //

   public static class Immediate extends TiffIFDEntry {
      TiffValue value_;

      // Read
      private Immediate(ByteOrder order, TiffTag tag, TiffFieldType type, int count, TiffValue value) {
         super(order, tag, type, count);
         value_ = value;
      }

      // Writing
      private Immediate(ByteOrder order, TiffTag tag, TiffValue value) {
         super(order, tag, value.getTiffType(), value.getCount());
         value_ = value;
      }

      @Override
      public CompletionStage<TiffValue> readValue(AsynchronousFileChannel chan) {
         return CompletableFuture.completedFuture(value_);
      }

      @Override
      public CompletionStage<Void> writeValue(AsynchronousFileChannel chan) {
         return CompletableFuture.completedFuture(null);
      }

      @Override
      public void writeValue(ByteBuffer dest, BufferedPositionGroup posGroup) {
         // no-op
      }

      @Override
      public void write(ByteBuffer dest, BufferedPositionGroup posGroup) {
         dest.putShort((short) getTag().getTiffConstant()).
            putShort((short) getType().getTiffConstant()).
            putInt(getCount());
         value_.writeAndPad(dest, posGroup, 4);
      }
   }

   public static class Pointer extends TiffIFDEntry {
      private TiffValue value_;
      private final TiffOffsetField valueOffset_;

      // Read
      private Pointer(ByteOrder order, TiffTag tag, TiffFieldType type, int count, long offset) {
         super(order, tag, type, count);

         valueOffset_ = TiffOffsetField.forOffsetValue(
            UnbufferedPosition.at(offset),
            "Read-only TiffIFDEntry.Pointer value");
      }

      // Writing
      private Pointer(ByteOrder order, TiffTag tag, TiffValue value, TiffOffsetField valueOffset) {
         super(order, tag, value.getTiffType(), value.getCount());
         value_ = value;
         valueOffset_ = valueOffset;
      }

      private int dataSize() {
         return getType().getElementSize() * getCount();
      }

      @Override
      public CompletionStage<TiffValue> readValue(AsynchronousFileChannel chan) {
         ByteBuffer buffer = ByteBuffer.allocateDirect(dataSize()).order(byteOrder_);
         return Async.read(chan, buffer, valueOffset_.getOffsetValue().get()).
            thenComposeAsync(b -> {
               b.rewind();
               try {
                  return CompletableFuture.completedFuture(
                     TiffValue.read(getType(), getCount(), b));
               }
               catch (IOException e) {
                  return Async.completedExceptionally(e);
               }
            });
      }

      @Override
      public CompletionStage<Void> writeValue(AsynchronousFileChannel chan) {
         ByteBuffer buffer = ByteBuffer.allocateDirect(dataSize()).
            order(byteOrder_);
         BufferedPositionGroup posGroup = BufferedPositionGroup.create();
         value_.write(buffer, posGroup);
         buffer.rewind();

         return Async.pad(chan, 4).
            thenCompose(v -> {
               try {
                  long offset = chan.size();
                  posGroup.setBufferFileOffset(offset);
                  valueOffset_.setOffsetValue(UnbufferedPosition.at(offset));
                  return Async.write(chan, buffer, offset);
               }
               catch (IOException e) {
                  return Async.completedExceptionally(e);
               }
            });
      }

      @Override
      public void writeValue(ByteBuffer dest, BufferedPositionGroup posGroup) {
         int position = Alignment.align(dest.position(), 4);
         dest.position(position);
         valueOffset_.setOffsetValue(posGroup.positionInBuffer(position));
         value_.write(dest, posGroup);
      }

      @Override
      public void write(ByteBuffer dest, BufferedPositionGroup posGroup) {
         dest.putShort((short) getTag().getTiffConstant()).
            putShort((short) getType().getTiffConstant()).
            putInt(getCount());
         valueOffset_.write(dest, posGroup);
      }
   }
}
