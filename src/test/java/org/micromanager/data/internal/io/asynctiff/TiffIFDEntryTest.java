package org.micromanager.data.internal.io.asynctiff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.micromanager.data.internal.io.BufferedPositionGroup;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TiffIFDEntryTest {
   @Test
   public void testReadImmediate() throws Exception {
      ByteBuffer b = ByteBuffer.wrap(new byte[] {
         0x66, 0x77,
         0x00, 0x01,
         0x00, 0x00, 0x00, 0x04,
         0x11, 0x22, 0x33, 0x44,
      });
      TiffIFDEntry e = TiffIFDEntry.read(b);
      assertEquals(0x6677, e.getTag().getTiffConstant());
      assertEquals(TiffFieldType.BYTE, e.getType());
      assertEquals(4, e.getCount());
      TiffValue v = e.readValue(null).toCompletableFuture().get();
      assertEquals(0x11, v.intValue(0));
      assertEquals(0x22, v.intValue(1));
      assertEquals(0x33, v.intValue(2));
      assertEquals(0x44, v.intValue(3));
   }

   @Test
   public void testReadPointer() throws Exception {
      ByteBuffer b = ByteBuffer.wrap(new byte[] {
         0x00, 0x00,
         0x00, 0x01,
         0x00, 0x00, 0x00, 0x05,
         0x00, 0x00, 0x00, 0x04,
      });
      TiffIFDEntry entry = TiffIFDEntry.read(b);
      assertEquals(TiffFieldType.BYTE, entry.getType());
      assertEquals(5, entry.getCount());

      Path tmpFile = Files.createTempFile(getClass().getSimpleName(), null);
      try (FileChannel chan = FileChannel.open(tmpFile,
         StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
         chan.write(ByteBuffer.wrap(new byte[]{
            0x00, 0x00, 0x00, 0x00,
            0x11, 0x22, 0x33, 0x44,
            0x55,
         }));
      }

      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(tmpFile,
         StandardOpenOption.READ)) {
         TiffValue v = entry.readValue(chan).
            toCompletableFuture().get();
         assertEquals(0x11, v.intValue(0));
         assertEquals(0x22, v.intValue(1));
         assertEquals(0x33, v.intValue(2));
         assertEquals(0x44, v.intValue(3));
         assertEquals(0x55, v.intValue(4));
      }
      finally {
         Files.delete(tmpFile);
      }
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   public void testWriteImmediate(int o) throws Exception {
      ByteOrder order = o != 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
      TiffOffsetFieldGroup fieldGroup = TiffOffsetFieldGroup.create();
      TiffIFDEntry entry = TiffIFDEntry.createForWrite(order,
         TiffTag.Known.ImageWidth.get(),
         TiffValue.Shorts.create((short) 42),
         fieldGroup);
      ByteBuffer b = ByteBuffer.allocate(128).order(order);
      BufferedPositionGroup posGroup = BufferedPositionGroup.create();
      entry.writeValue(b, posGroup);
      assertEquals(0, b.position(), "no value should be written");
      entry.write(b, posGroup);
      assertEquals(12, b.position());

      b.rewind();
      TiffIFDEntry readBack = TiffIFDEntry.read(b);
      assertEquals(TiffTag.Known.ImageWidth.get(), readBack.getTag());
      assertEquals(TiffFieldType.SHORT, readBack.getType());
      assertEquals(1, readBack.getCount());

      TiffValue v = readBack.readValue(null).toCompletableFuture().get();
      assertEquals(TiffFieldType.SHORT, v.getTiffType());
      assertEquals(42, v.intValue(0));
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   public void testWritePointer(int o) throws Exception {
      ByteOrder order = o != 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
      TiffOffsetFieldGroup fieldGroup = TiffOffsetFieldGroup.create();
      TiffIFDEntry entry = TiffIFDEntry.createForWrite(order,
         TiffTag.Known.XResolution.get(),
         TiffValue.Rationals.create(1, 10000),
         fieldGroup);
      ByteBuffer b = ByteBuffer.allocate(128).order(order);
      BufferedPositionGroup posGroup = BufferedPositionGroup.forBufferAt(32);
      entry.writeValue(b, posGroup);
      entry.write(b, posGroup);
      b.rewind();

      Path tmpFile = Files.createTempFile(getClass().getSimpleName(), null);
      try {
         try (FileChannel chan = FileChannel.open(tmpFile, StandardOpenOption.WRITE)) {
            chan.write(b, 32);
         }

         try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(tmpFile, StandardOpenOption.READ)) {
            ByteBuffer readBuf = ByteBuffer.allocate(128 - 32).order(order);
            chan.read(readBuf, 32).get();
            readBuf.position(8);
            TiffIFDEntry readBack = TiffIFDEntry.read(readBuf);
            assertEquals(TiffTag.Known.XResolution.get(), readBack.getTag());
            assertEquals(TiffFieldType.RATIONAL, readBack.getType());
            assertEquals(1, readBack.getCount());
            TiffValue v = readBack.readValue(chan).toCompletableFuture().get();
            assertEquals(1e-4, v.doubleValue(0), 1e-10);
         }
      }
      finally {
         Files.deleteIfExists(tmpFile);
      }
   }
}
