package org.micromanager.data.internal.io.mmtiff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TiffHeader {
   private static final int HEADER_SIZE = 8;

   private ByteOrder byteOrder_;
   private short magic_;
   private long firstIFDOffset_;

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

   private static ByteOrder readByteOrder(ByteBuffer b) throws TiffFormatException {
      short byteOrder = b.getShort();
      switch (byteOrder) {
         case 0x4949: // 'II'
            return ByteOrder.LITTLE_ENDIAN;
         case 0x4D4D: // 'MM'
            return ByteOrder.BIG_ENDIAN;
         default:
            throw new TiffFormatException(String.format(
               "Invalid TIFF byte order marker (0x%04X)", byteOrder));
      }
   }

   private static short readMagic(ByteBuffer b) throws TiffFormatException {
      short magic = b.getShort();
      if (magic != 42) {
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

   private TiffHeader(ByteOrder order, short magic, long firstIFDOffset) {
      byteOrder_ = order;
      magic_ = magic;
      firstIFDOffset_ = firstIFDOffset;
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
      return TiffIFD.read(chan, byteOrder_, firstIFDOffset_);
   }
}
