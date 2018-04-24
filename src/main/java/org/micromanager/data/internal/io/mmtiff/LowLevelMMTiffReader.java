package org.micromanager.data.internal.io.mmtiff;

import org.micromanager.data.internal.io.Async;
import org.micromanager.data.internal.io.Unsigned;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.util.concurrent.CompletionStage;

/**
 * A TIFF reader that also reads MM-specific data.
 *
 * This class handles reading, not interpreting, of the data.
 */
public class LowLevelMMTiffReader {
   private final ByteOrder byteOrder_;

   public static LowLevelMMTiffReader create(ByteOrder order) {
      return new LowLevelMMTiffReader(order);
   }

   private LowLevelMMTiffReader(ByteOrder order) {
      byteOrder_ = order;
   }

   public CompletionStage<int[]> readRawIndexMap(AsynchronousFileChannel chan) {
      return readMMBlockPointer(chan, 8, 0x0343C790, 0x0034b2b7, 20).
         thenApply(buffer -> {
            int[] ret = new int[buffer.capacity() / 4];
            buffer.asIntBuffer().get(ret);
            return ret;
         });
   }

   public CompletionStage<byte[]> readRawDisplaySettings(AsynchronousFileChannel chan) {
      return readMMBlockPointer(chan, 16, 0x1CD5AE84, 0x14BB8964, 1).
         thenApply(b -> b.array());
   }

   public CompletionStage<byte[]> readRawComments(AsynchronousFileChannel chan) {
      return readMMBlockPointer(chan, 24, 0x05EC7D92, 0x050CBB65, 1).
         thenApply(b -> b.array());
   }

   public CompletionStage<byte[]> readRawSummaryMetadata(AsynchronousFileChannel chan) {
      return readMMBlock(chan, 32, 0x0023F124, 1).
         thenApply(b -> b.array());
   }

   //
   //
   //

   private CompletionStage<ByteBuffer> readMMBlockPointer(AsynchronousFileChannel chan,
                                                          long pointerOffset,
                                                          int pointerMagic,
                                                          int blockMagic,
                                                          int entrySize) {
      final int pointerSize = 8;
      ByteBuffer offsetBuffer = ByteBuffer.allocate(pointerSize).order(byteOrder_);
      return Async.read(chan, offsetBuffer, pointerOffset).
         thenComposeAsync(buffer -> {
            int observedPointerMagic = buffer.getInt();
            if (observedPointerMagic != pointerMagic) {
               return Async.completedExceptionally(new TiffFormatException(
                  String.format(
                     "Incorrect MM block pointer magic (expected 0x%08X; found 0x%08X)",
                     pointerMagic, observedPointerMagic)));
            }
            long blockOffset = Unsigned.from(buffer.getInt());
            return readMMBlock(chan, blockOffset, blockMagic, entrySize);
         });
   }

   private CompletionStage<ByteBuffer> readMMBlock(AsynchronousFileChannel chan,
                                                   long offset,
                                                   long blockMagic,
                                                   int entrySize) {
      final int headerSize = 8;
      ByteBuffer blockHeaderBuffer = ByteBuffer.allocate(headerSize).order(byteOrder_);
      return Async.read(chan, blockHeaderBuffer, offset).
         thenComposeAsync(buffer -> {
            int observedBlockMagic = buffer.getInt();
            if (observedBlockMagic != blockMagic) {
               return Async.completedExceptionally(new TiffFormatException(
                  String.format(
                     "Incorrect MM block magic (expected 0x%08X; found 0x%08X)",
                     blockMagic, observedBlockMagic)));
            }
            long length = Unsigned.from(buffer.getInt());
            ByteBuffer resultBuffer = ByteBuffer.allocate(
               (int) length * entrySize).order(byteOrder_);
            return Async.read(chan, resultBuffer, offset + headerSize);
         });
   }
}
