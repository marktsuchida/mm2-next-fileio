package org.micromanager.data.internal.io.mmtiff;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;

public class StandardTiffReader {
   private final SeekableByteChannel channel_;

   private ByteOrder tiffByteOrder_;
   private short tiffMagic_;
   private long tiffFirstIFDOffset_;

   //
   //
   //

   public static StandardTiffReader create(SeekableByteChannel channel) throws IOException {
      StandardTiffReader instance = new StandardTiffReader(channel);
      instance.validateHeader();
      return instance;
   }

   protected StandardTiffReader(SeekableByteChannel channel) {
      Preconditions.checkNotNull(channel);
      channel_ = channel;
   }

   protected SeekableByteChannel channel() {
      return channel_;
   }

   protected void validateHeader() throws IOException {
      try {
         readTiffHeader();
      }
      catch (TiffFormatException e) {
         throw new TiffFormatException("The file does not appear to be a valid TIFF file", e);
      }
   }

   public ByteOrder getTiffByteOrder() throws IOException {
      if (tiffByteOrder_ == null) {
         readTiffHeader();
      }
      return tiffByteOrder_;
   }

   public short getTiffMagic() throws IOException {
      if (tiffMagic_ == 0) {
         readTiffHeader();
      }
      return tiffMagic_;
   }

   private long getFirstIFDOffset() throws IOException {
      if (tiffFirstIFDOffset_ == 0) {
         readTiffHeader();
      }
      return tiffFirstIFDOffset_;
   }

   public TiffIFD readFirstIFD() throws IOException {
      channel().position(getFirstIFDOffset());
      return TiffIFD.read(channel(), tiffByteOrder_);
   }

   //
   //
   //

   private void readTiffHeader() throws IOException {
      ByteBuffer headerBuffer = ByteBuffer.allocateDirect(8);
      channel().position(0);
      channel().read(headerBuffer);

      headerBuffer.rewind();
      tiffByteOrder_ = readTiffByteOrder(headerBuffer);
      headerBuffer.order(tiffByteOrder_);
      tiffMagic_ = readTiffMagic(headerBuffer);
      tiffFirstIFDOffset_ = readIFDOffset(headerBuffer);
   }

   //
   //
   //

   private ByteOrder readTiffByteOrder(ByteBuffer b) throws TiffFormatException {
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

   private short readTiffMagic(ByteBuffer b) throws TiffFormatException {
      short magic = b.getShort();
      if (magic != 42) {
         throw new TiffFormatException(String.format(
            "Incorrect TIFF header magic (expected 0x002A, got 0x%04X)", magic));
      }
      return magic;
   }

   private long readIFDOffset(ByteBuffer b) throws IOException {
      long ifdOffset = Unsigned.from(b.getInt());
      if (ifdOffset % 2 != 0) { // IFD must begin on a word boundary
         throw new TiffFormatException(String.format(
            "Incorrect TIFF IFD offset (must be word-aligned; got 0x%08X)",
            ifdOffset));
      }
      if (ifdOffset >= channel().size()) {
         throw new TiffFormatException(String.format(
            "TIFF IFD offset (0x%08X) is beyond the end of the file (%d)",
            ifdOffset, channel().size()));
      }
      return ifdOffset;
   }
}
