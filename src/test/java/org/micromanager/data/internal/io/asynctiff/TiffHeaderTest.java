package org.micromanager.data.internal.io.asynctiff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.micromanager.data.internal.io.Alignment;
import org.micromanager.data.internal.io.Async;
import org.micromanager.data.internal.io.BufferedPositionGroup;
import org.micromanager.data.internal.io.UnbufferedPosition;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class TiffHeaderTest {
   private Path tmpFile_;

   @BeforeEach
   public void init() throws IOException {
      tmpFile_ = Files.createTempFile(getClass().getSimpleName(), ".tif");
   }

   @AfterEach
   public void tearDown() throws IOException {
      Files.deleteIfExists(tmpFile_);
   }

   @Test
   public void testReadEmptyFile() throws IOException {
      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(tmpFile_,
         StandardOpenOption.READ)) {
         Throwable ee = assertThrows(ExecutionException.class,
            () -> TiffHeader.read(chan).toCompletableFuture().get());
         assertTrue(ee.getCause() instanceof IOException);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "MM", "II" })
   public void testReadMinimalExample(String byteOrder) throws Exception {
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

      try (FileChannel writeChan = FileChannel.open(tmpFile_,
         StandardOpenOption.WRITE,
         StandardOpenOption.CREATE,
         StandardOpenOption.TRUNCATE_EXISTING)) {
         int size = writeChan.write(b);
         System.out.printf("%d bytes written to %s\n", size, tmpFile_.toString());
      }

      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(tmpFile_,
         StandardOpenOption.READ)) {
         TiffHeader header = TiffHeader.read(chan).toCompletableFuture().get();
         assertEquals(42, header.getTiffMagic());
         assertEquals(byteOrder.equals("MM") ?
            ByteOrder.BIG_ENDIAN: ByteOrder.LITTLE_ENDIAN,
            header.getTiffByteOrder());
         TiffIFD ifd = header.readFirstIFD(chan).toCompletableFuture().get();
         assertNotNull(ifd);
         assertFalse(ifd.hasNextIFD());
         ByteBuffer pixels = ifd.readPixels(chan).toCompletableFuture().get();
         assertEquals(8 * 4, pixels.limit());
      }
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   public void testWriteMinimalExample(int o) throws Exception {
      ByteOrder order = o != 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

      TiffOffsetField firstIFDOffset = TiffOffsetField.create("FirstIFDOffset");
      TiffHeader header = TiffHeader.createForWrite(order, firstIFDOffset);

      ByteBuffer pixels = ByteBuffer.allocate(4 * 8);
      for (int y = 0; y < 4; ++y) {
         for (int x = 0; x < 8; ++x) {
            pixels.put((byte) ((y << 4) + x)); // 8x4 image
         }
      }
      pixels.rewind();

      TiffOffsetField nextIFDOffset = TiffOffsetField.create("NextIFDOffset");
      TiffOffsetFieldGroup ifdFieldGroup = TiffOffsetFieldGroup.create();
      TiffValue.Offsets stripOffsets = TiffValue.Offsets.create(1, "StripOffsets", ifdFieldGroup);
      TiffOffsetField pixelsOffset = stripOffsets.offsetValue(0);
      TiffIFD ifd = TiffIFD.builder(order, nextIFDOffset, ifdFieldGroup).
         entry(TiffTag.Known.ImageWidth.get(), TiffValue.Longs.create(8)).
         entry(TiffTag.Known.ImageLength.get(), TiffValue.Shorts.create((short) 4)).
         entry(TiffTag.Known.BitsPerSample.get(), TiffValue.Shorts.create((short) 8)).
         entry(TiffTag.Known.Compression.get(), TiffValue.Shorts.create((short) 0)).
         entry(TiffTag.Known.PhotometricInterpretation.get(), TiffValue.Shorts.create((short) 1)).
         entry(TiffTag.Known.StripOffsets.get(), stripOffsets).
         entry(TiffTag.Known.RowsPerStrip.get(), TiffValue.Longs.create(4)).
         entry(TiffTag.Known.StripByteCounts.get(), TiffValue.Longs.create(32)).
         entry(TiffTag.Known.XResolution.get(), TiffValue.Rationals.create(1, 10000)).
         entry(TiffTag.Known.YResolution.get(), TiffValue.Rationals.create(1, 10000)).
         entry(TiffTag.Known.ResolutionUnit.get(), TiffValue.Shorts.create((short) 3)).
         build();

      ByteBuffer ifdBuffer = ByteBuffer.allocate(1024).order(order);
      BufferedPositionGroup ifdBufferPosGroup = BufferedPositionGroup.create();
      for (TiffIFDEntry entry : ifd.getEntries()) {
         entry.writeValue(ifdBuffer, ifdBufferPosGroup);
         ifdBuffer.position(Alignment.align(ifdBuffer.position(), 2));
      }
      firstIFDOffset.setOffsetValue(ifdBufferPosGroup.positionInBuffer(ifdBuffer.position()));
      ifd.write(ifdBuffer, ifdBufferPosGroup);
      ifdBuffer.limit(ifdBuffer.position());
      ifdBuffer.rewind();

      long size;
      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(tmpFile_, StandardOpenOption.WRITE)) {
         size = header.write(chan).
            thenCompose(v -> Async.size(chan)).
            thenCompose(s -> {
               pixelsOffset.setOffsetValue(UnbufferedPosition.at(s));
               return Async.write(chan, pixels, s);
            }).
            thenCompose(v -> Async.pad(chan, 2)).
            thenCompose(v -> Async.size(chan)).
            thenCompose(s -> {
               ifdBufferPosGroup.setBufferFileOffset(s);
               ifdFieldGroup.updateAll(ifdBuffer);
               return Async.write(chan, ifdBuffer, s);
            }).
            thenCompose(v -> firstIFDOffset.update(chan, order)).
            thenCompose(v -> Async.size(chan)).
            toCompletableFuture().get();
      }

      System.out.println(String.format("Wrote %d bytes to %s",
         size, tmpFile_.toString()));

      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(tmpFile_,
         StandardOpenOption.READ)) {
         TiffHeader readBackHeader = TiffHeader.read(chan).toCompletableFuture().get();
         assertEquals(42, readBackHeader.getTiffMagic());
         assertEquals(order, readBackHeader.getTiffByteOrder());

         TiffIFD readBackIFD = readBackHeader.readFirstIFD(chan).toCompletableFuture().get();
         assertNotNull(readBackIFD);
         assertFalse(readBackIFD.hasNextIFD());

         assertEquals(8, readBackIFD.getRequiredEntryWithTag(TiffTag.Known.ImageWidth.get()).
            readValue(chan).toCompletableFuture().get().longValue(0));
         assertEquals(4, readBackIFD.getRequiredEntryWithTag(TiffTag.Known.ImageLength.get()).
            readValue(chan).toCompletableFuture().get().longValue(0));
         assertEquals(8, readBackIFD.getRequiredEntryWithTag(TiffTag.Known.BitsPerSample.get()).
            readValue(chan).toCompletableFuture().get().intValue(0));
         assertEquals(0, readBackIFD.getRequiredEntryWithTag(TiffTag.Known.Compression.get()).
            readValue(chan).toCompletableFuture().get().intValue(0));
         assertEquals(1, readBackIFD.getRequiredEntryWithTag(TiffTag.Known.PhotometricInterpretation.get()).
            readValue(chan).toCompletableFuture().get().intValue(0));
         assertEquals(1e-4, readBackIFD.getRequiredEntryWithTag(TiffTag.Known.XResolution.get()).
            readValue(chan).toCompletableFuture().get().doubleValue(0), 1e-10);
         assertEquals(1e-4, readBackIFD.getRequiredEntryWithTag(TiffTag.Known.YResolution.get()).
            readValue(chan).toCompletableFuture().get().doubleValue(0), 1e-10);
         assertEquals(3, readBackIFD.getRequiredEntryWithTag(TiffTag.Known.ResolutionUnit.get()).
            readValue(chan).toCompletableFuture().get().intValue(0));

         ByteBuffer readBackPixels = readBackIFD.readPixels(chan).toCompletableFuture().get();
         assertEquals(8 * 4, readBackPixels.limit());
      }
   }
}
