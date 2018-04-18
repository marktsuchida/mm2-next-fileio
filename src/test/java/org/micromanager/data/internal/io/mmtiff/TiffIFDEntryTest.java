package org.micromanager.data.internal.io.mmtiff;

import org.junit.jupiter.api.Test;

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
   public void testImmediate() throws Exception {
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
      TiffValue v = e.readValue(null, null).toCompletableFuture().get();
      assertEquals(0x11, v.intValue(0));
      assertEquals(0x22, v.intValue(1));
      assertEquals(0x33, v.intValue(2));
      assertEquals(0x44, v.intValue(3));
   }

   @Test
   public void testPointer() throws Exception {
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
         TiffValue v = entry.readValue(chan, ByteOrder.BIG_ENDIAN).
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
}
