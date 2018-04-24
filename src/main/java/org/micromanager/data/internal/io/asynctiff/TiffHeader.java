package org.micromanager.data.internal.io.asynctiff;

import org.micromanager.data.internal.io.Async;
import org.micromanager.data.internal.io.BufferedPositionGroup;
import org.micromanager.data.internal.io.UnbufferedPosition;
import org.micromanager.data.internal.io.Unsigned;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TiffHeader {
   private static final int HEADER_SIZE = 8;
   private static final short BIG_ENDIAN_MARK = 0x4D4D; // 'MM'
   private static final short LITTLE_ENDIAN_MARK = 0x4949; // 'II'
   private static final short TIFF_MAGIC = 42;

   private ByteOrder byteOrder_;
   private short magic_;
   private TiffOffsetField firstIFDOffset_;

   //
   //
   //

   public static CompletionStage<TiffHeader> read(AsynchronousFileChannel chan) {
      ByteBuffer buffer = ByteBuffer.allocateDirect(HEADER_SIZE);
      return Async.read(chan, buffer, 0).thenComposeAsync(b -> {
         b.rewind();
         try {
            ByteOrder byteOrder = readByteOrder(b);
            b.order(byteOrder);
            short magic = readMagic(b);
            long firstIFDOffset = readIFDOffset(b);
            return CompletableFuture.completedFuture(
               new TiffHeader(byteOrder, magic, firstIFDOffset));
         }
         catch (IOException e) {
            return Async.completedExceptionally(e);
         }
      });
   }

   public static TiffHeader createForWrite(ByteOrder order, TiffOffsetField firstIFDOffsetField) {
      return new TiffHeader(order, firstIFDOffsetField);
   }

   // Read
   private TiffHeader(ByteOrder order, short magic, long firstIFDOffset) {
      byteOrder_ = order;
      magic_ = magic;
      firstIFDOffset_ = TiffOffsetField.forOffsetValue(UnbufferedPosition.at(firstIFDOffset),
         "Read-only FirstIFDOffset");
   }

   // Writing
   private TiffHeader(ByteOrder order, TiffOffsetField firstIFDOffsetField) {
      byteOrder_ = order;
      magic_ = TIFF_MAGIC;
      firstIFDOffset_ = firstIFDOffsetField;
   }

   //
   //
   //

   private static ByteOrder readByteOrder(ByteBuffer b) throws TiffFormatException {
      short byteOrder = b.getShort();
      switch (byteOrder) {
         case LITTLE_ENDIAN_MARK:
            return ByteOrder.LITTLE_ENDIAN;
         case BIG_ENDIAN_MARK:
            return ByteOrder.BIG_ENDIAN;
         default:
            throw new TiffFormatException(String.format(
               "Invalid TIFF byte order marker (0x%04X)", byteOrder));
      }
   }

   private static short readMagic(ByteBuffer b) throws TiffFormatException {
      short magic = b.getShort();
      if (magic != TIFF_MAGIC) {
         throw new TiffFormatException(String.format(
            "Incorrect TIFF header magic (expected 0x002A, got 0x%04X)", magic));
      }
      return magic;
   }

   private static long readIFDOffset(ByteBuffer b) throws IOException {
      long ifdOffset = Unsigned.from(b.getInt());
      if (ifdOffset % 2 != 0) { // TIFF spec saysIFD must begin on a word boundary
         throw new TiffFormatException(String.format(
            "Incorrect TIFF IFD offset (must be word-aligned; got 0x%08X)",
            ifdOffset));
      }
      return ifdOffset;
   }

   //
   //
   //

   public ByteOrder getTiffByteOrder() {
      return byteOrder_;
   }

   public short getTiffMagic() {
      return magic_;
   }

   public CompletionStage<TiffIFD> readFirstIFD(AsynchronousFileChannel chan) {
      return TiffIFD.read(chan, byteOrder_, firstIFDOffset_.getOffsetValue().get());
   }

   //
   //
   //

   public CompletionStage<Void> write(AsynchronousFileChannel chan) {
      ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE).order(byteOrder_);
      write(buffer);
      buffer.rewind();
      return Async.write(chan, buffer, 0);
   }

   public void write(ByteBuffer dest) {
      dest.putShort(byteOrder_.equals(ByteOrder.BIG_ENDIAN) ? BIG_ENDIAN_MARK : LITTLE_ENDIAN_MARK).
         putShort(TIFF_MAGIC);
      BufferedPositionGroup posGroup = BufferedPositionGroup.forBufferAt(0);
      firstIFDOffset_.write(dest, posGroup);
   }
}
