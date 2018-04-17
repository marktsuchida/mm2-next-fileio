package org.micromanager.data.internal.io.mmtiff;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TiffIFDEntryTest {
   @Test
   public void testImmediate() throws IOException {
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
      TiffValue v = e.readValue(null);
      assertEquals(0x11, v.intValue(0));
      assertEquals(0x22, v.intValue(1));
      assertEquals(0x33, v.intValue(2));
      assertEquals(0x44, v.intValue(3));
   }

   @Test
   public void testPointer() throws IOException {
      ByteBuffer b = ByteBuffer.wrap(new byte[] {
         0x00, 0x00,
         0x00, 0x01,
         0x00, 0x00, 0x00, 0x05,
         0x00, 0x00, 0x00, 0x04,
      });
      TiffIFDEntry e = TiffIFDEntry.read(b);
      assertEquals(TiffFieldType.BYTE, e.getType());
      assertEquals(5, e.getCount());

      Path tmpFile = Files.createTempFile(getClass().getSimpleName(), null);
      try (FileChannel chan = FileChannel.open(tmpFile,
         StandardOpenOption.READ, StandardOpenOption.WRITE,
         StandardOpenOption.TRUNCATE_EXISTING)) {
         chan.write(ByteBuffer.wrap(new byte[] {
            0x00, 0x00, 0x00, 0x00,
            0x11, 0x22, 0x33, 0x44,
            0x55,
         }));

         chan.position(0);
         TiffValue v = e.readValue(chan);
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
