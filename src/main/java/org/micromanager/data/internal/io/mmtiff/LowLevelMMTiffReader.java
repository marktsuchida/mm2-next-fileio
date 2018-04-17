package org.micromanager.data.internal.io.mmtiff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A TIFF reader that also reads MM-specific data.
 *
 * This class handles reading, not interpreting, of the data.
 */
public class LowLevelMMTiffReader extends StandardTiffReader {
   public static LowLevelMMTiffReader create(SeekableByteChannel channel) throws IOException {
      LowLevelMMTiffReader instance = new LowLevelMMTiffReader(channel);
      instance.validateHeader();
      instance.validateMMFields();
      return instance;
   }

   private LowLevelMMTiffReader(SeekableByteChannel channel) {
      super(channel);
   }

   private void validateMMFields() throws IOException {
      // TODO
   }

   // Methods of this class are intentionally written to return raw arrays.
   // Client code should convert to Strings, etc., with proper error recovery.

   public int[] getRawIndexMap() throws IOException {
      ByteBuffer b = readMMBlockPointer(8, 0x0343C790, 0x0034b2b7, 20);
      int[] ret = new int[b.capacity() / 4];
      b.asIntBuffer().get(ret);
      return ret;
   }

   public byte[] getRawDisplaySettings() throws IOException {
      return bytesFromByteBuffer(readMMBlockPointer(16, 0x1CD5AE84, 0x14BB8964, 1));
   }

   public byte[] getRawComments() throws IOException {
      return bytesFromByteBuffer(readMMBlockPointer(24, 0x05EC7D92, 0x050CBB65, 1));
   }

   public byte[] getRawSummaryMetadata() throws IOException {
      return bytesFromByteBuffer(readMMBlock(32, 0x0023F124, 1));
   }

   //
   //
   //

   private ByteBuffer readMMBlockPointer(long pointerOffset, int pointerMagic,
                                         int blockMagic, int entrySize) throws IOException {
      ByteBuffer offsetBuffer = ByteBuffer.allocateDirect(8).order(getTiffByteOrder());
      channel().position(pointerOffset);
      channel().read(offsetBuffer);
      if (offsetBuffer.getInt() != pointerMagic) {
         throw new TiffFormatException("Incorrect MM block pointer magic");
      }
      long offset = Unsigned.from(offsetBuffer.getInt());
      return readMMBlock(offset, blockMagic, entrySize);
   }

   private ByteBuffer readMMBlock(long offset, long blockMagic, int entrySize)
      throws IOException {
      ByteBuffer blockHeaderBuffer = ByteBuffer.allocateDirect(8).order(getTiffByteOrder());
      channel().position(offset);
      channel().read(blockHeaderBuffer);
      if (blockHeaderBuffer.getInt() != blockMagic) {
         throw new TiffFormatException("Incorrect MM block magic");
      }
      long length = Unsigned.from(blockHeaderBuffer.getInt());

      ByteBuffer result = ByteBuffer.allocateDirect((int) length * entrySize).
         order(getTiffByteOrder());
      channel().read(result);
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
