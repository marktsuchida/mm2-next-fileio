package org.micromanager.data.internal.io.mmtiff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;

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

   public int[] readRawIndexMap(SeekableByteChannel chan) throws IOException {
      ByteBuffer b = readMMBlockPointer(chan, 8, 0x0343C790, 0x0034b2b7, 20);
      int[] ret = new int[b.capacity() / 4];
      b.asIntBuffer().get(ret);
      return ret;
   }

   public byte[] readRawDisplaySettings(SeekableByteChannel chan) throws IOException {
      return bytesFromByteBuffer(readMMBlockPointer(chan, 16, 0x1CD5AE84, 0x14BB8964, 1));
   }

   public byte[] readRawComments(SeekableByteChannel chan) throws IOException {
      return bytesFromByteBuffer(readMMBlockPointer(chan, 24, 0x05EC7D92, 0x050CBB65, 1));
   }

   public byte[] readRawSummaryMetadata(SeekableByteChannel chan) throws IOException {
      return bytesFromByteBuffer(readMMBlock(chan, 32, 0x0023F124, 1));
   }

   //
   //
   //

   private ByteBuffer readMMBlockPointer(SeekableByteChannel chan,
      long pointerOffset, int pointerMagic, int blockMagic, int entrySize)
      throws IOException {
      ByteBuffer offsetBuffer = ByteBuffer.allocateDirect(8).order(byteOrder_);
      chan.position(pointerOffset);
      chan.read(offsetBuffer);
      if (offsetBuffer.getInt() != pointerMagic) {
         throw new TiffFormatException("Incorrect MM block pointer magic");
      }
      long offset = Unsigned.from(offsetBuffer.getInt());
      return readMMBlock(chan, offset, blockMagic, entrySize);
   }

   private ByteBuffer readMMBlock(SeekableByteChannel chan,
      long offset, long blockMagic, int entrySize) throws IOException {
      ByteBuffer blockHeaderBuffer = ByteBuffer.allocateDirect(8).order(byteOrder_);
      chan.position(offset);
      chan.read(blockHeaderBuffer);
      if (blockHeaderBuffer.getInt() != blockMagic) {
         throw new TiffFormatException("Incorrect MM block magic");
      }
      long length = Unsigned.from(blockHeaderBuffer.getInt());

      ByteBuffer result = ByteBuffer.allocateDirect((int) length * entrySize).
         order(byteOrder_);
      chan.read(result);
      return result;
   }

   //
   //
   //

   byte[] bytesFromByteBuffer(ByteBuffer b) {
      if (b.hasArray()) {
         return b.array();
      }
      byte[] ret = new byte[b.capacity()];
      b.get(ret);
      return ret;
   }
}
