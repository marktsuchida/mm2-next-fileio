package org.micromanager.data.internal.io.mmtiff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.micromanager.testing.TemporaryDirectory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

public class TiffHeaderTest {
   TemporaryDirectory tempDir_;
   Path tempPath_;

   @BeforeEach
   public void init() throws IOException {
      tempDir_ = new TemporaryDirectory(getClass());
      tempPath_ = tempDir_.getPath();
   }

   @AfterEach
   public void tearDown() throws IOException {
      tempDir_.close();
   }

   @Test
   public void testEmptyFile() throws IOException {
      Path tif = tempPath_.resolve("empty.tif");
      Files.createFile(tif);
      try (FileChannel chan = FileChannel.open(tif, StandardOpenOption.READ)) {
         assertThrows(TiffFormatException.class, () -> TiffHeader.read(chan));
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "MM", "II" })
   public void testMinimalLittleEndian(String byteOrder) throws IOException {
      ByteBuffer b = ByteBuffer.allocate(1024).order(byteOrder.equals("MM") ?
         ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

      // TIFF header
      b.put(byteOrder.getBytes("US-ASCII"));
      b.putShort((short) 0x002A);
      int firstIFDOffsetFieldOffset = b.position();
      b.putInt(0xdeadbeef); // placeholder

      // Pixels
      int firstStripOffset = b.position();
      for (int y = 0; y < 4; ++y) {
         for (int x = 0; x < 8; ++x) {
            b.put((byte) ((y << 4) + x)); // 8x4 image
         }
      }

      // Referenced data
      int xResolutionOffset = b.position();
      b.putInt(1).putInt(10000);
      int yResolutionOffset = b.position();
      b.putInt(1).putInt(10000);

      // IFD
      int firstIFDOffset = b.position();
      b.putInt(firstIFDOffsetFieldOffset, firstIFDOffset);
      b.putShort((short) 11); // entry count

      // ImageWidth (LONG)
      b.putShort((short) 0x0100).putShort((short) 0x0004);
      b.putInt(1).putInt(8);

      // ImageLength (SHORT)
      b.putShort((short) 0x0101).putShort((short) 0x0003);
      b.putInt(1).putShort((short) 4).putShort((short) 0xdead);

      // BitsPerSample (SHORT)
      b.putShort((short) 0x0102).putShort((short) 0x0003);
      b.putInt(1).putShort((short) 8).putShort((short) 0xdead);

      // Compression (SHORT)
      b.putShort((short) 0x0103).putShort((short) 0x0003);
      b.putInt(1).putShort((short) 1).putShort((short) 0xdead);

      // PhotometricInterpretation (SHORT)
      b.putShort((short) 0x0106).putShort((short) 0x0003);
      b.putInt(1).putShort((short) 1).putShort((short) 0xdead);

      // StripOffsets (LONG)
      b.putShort((short) 0x0111).putShort((short) 0x0004);
      b.putInt(1).putInt(firstStripOffset);

      // RowsPerStrip (LONG)
      b.putShort((short) 0x0116).putShort((short) 0x0004);
      b.putInt(1).putInt(4);

      // StripByteCounts (LONG)
      b.putShort((short) 0x0117).putShort((short) 0x0004);
      b.putInt(1).putInt(32);

      // XResolution (RATIONAL)
      b.putShort((short) 0x011a).putShort((short) 0x0005);
      b.putInt(1).putInt(xResolutionOffset);

      // YResolution (RATIONAL)
      b.putShort((short) 0x011b).putShort((short) 0x0005);
      b.putInt(1).putInt(yResolutionOffset);

      // ResolutionUnit (SHORT)
      b.putShort((short) 0x0128).putShort((short) 0x0003);
      b.putInt(1).putInt(3); // centimeter

      b.putInt(0); // NextIFDOffset

      b.limit(b.position());
      b.rewind();

      Path tif = tempPath_.resolve(String.format("simple-%s.tif", byteOrder));
      try (FileChannel writeChan = FileChannel.open(tif,
         StandardOpenOption.WRITE,
         StandardOpenOption.CREATE,
         StandardOpenOption.TRUNCATE_EXISTING)) {
         int size = writeChan.write(b);
         System.out.printf("%d bytes written to %s\n", size, tif.toString());
      }

      try (FileChannel chan = FileChannel.open(tif, StandardOpenOption.READ)) {
         TiffHeader header = TiffHeader.read(chan);
         assertEquals(42, header.getTiffMagic());
         assertEquals(byteOrder.equals("MM") ?
            ByteOrder.BIG_ENDIAN: ByteOrder.LITTLE_ENDIAN,
            header.getTiffByteOrder());
         TiffIFD ifd = header.readFirstIFD(chan);
         assertNotNull(ifd);
         assertFalse(ifd.hasNextIFD());
         assertTrue(ifd.isSingleStrip(chan));
         ByteBuffer pixels = ifd.readPixels(chan);
         assertEquals(8 * 4, pixels.limit());
      }
   }
}
