package org.micromanager.data.internal.io.mmtiff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TiffValueTest {
   @Test
   public void testUndefined() throws EOFException {
      ByteBuffer b = ByteBuffer.allocate(5);
      TiffValue v = TiffValue.read(TiffFieldType.UNDEFINED, 5, b);
      assertEquals(TiffFieldType.UNDEFINED, v.getTiffType());
      assertEquals(5, v.getCount());
   }

   @Test
   public void testBytes() throws EOFException {
      ByteBuffer b = ByteBuffer.wrap(new byte[] { 0, 1, 127, (byte) 128, (byte) 255 });
      TiffValue v = TiffValue.read(TiffFieldType.BYTE, 5, b);
      assertEquals(TiffFieldType.BYTE, v.getTiffType());
      assertEquals(5, v.getCount());

      assertEquals(0, v.intValue(0));
      assertEquals(1, v.intValue(1));
      assertEquals(127, v.intValue(2));
      assertEquals(128, v.intValue(3));
      assertEquals(255, v.intValue(4));

      b.rewind();
      v = TiffValue.read(TiffFieldType.SBYTE, 5, b);
      assertEquals(TiffFieldType.SBYTE, v.getTiffType());
      assertEquals(5, v.getCount());

      assertEquals(0, v.intValue(0));
      assertEquals(1, v.intValue(1));
      assertEquals(127, v.intValue(2));
      assertEquals(-128, v.intValue(3));
      assertEquals(-1, v.intValue(4));
   }

   @Test
   public void testAscii() throws Exception {
      byte[] encoded = "\u00B5Manager".getBytes("UTF-8");

      // Not null-terminated
      ByteBuffer b = ByteBuffer.wrap(encoded);
      TiffValue v = TiffValue.read(TiffFieldType.ASCII, encoded.length, b);
      assertEquals(TiffFieldType.ASCII, v.getTiffType());
      assertEquals(encoded.length, v.getCount());
      assertEquals("\u00B5Manager", v.utf8Value());

      // Null-terminated (correct TIFF)
      b = ByteBuffer.allocate(b.limit() + 1);
      b.put(encoded).put((byte) 0);
      b.rewind();
      v = TiffValue.read(TiffFieldType.ASCII, encoded.length + 1, b);
      assertEquals(encoded.length + 1, v.getCount());
      assertEquals("\u00B5Manager", v.utf8Value());
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   public void testShorts(int o) throws EOFException {
      ByteOrder order = o != 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
      ByteBuffer b = ByteBuffer.allocate(8).order(order);
      b.asShortBuffer().put(new short[] { 0, 32767, (short) 32768, (short) 65535 });

      b.rewind();
      TiffValue v = TiffValue.read(TiffFieldType.SHORT, 4, b);
      assertEquals(TiffFieldType.SHORT, v.getTiffType());
      assertEquals(4, v.getCount());

      assertEquals(0, v.intValue(0));
      assertEquals(32767, v.intValue(1));
      assertEquals(32768, v.intValue(2));
      assertEquals(65535, v.intValue(3));

      b.rewind();
      v = TiffValue.read(TiffFieldType.SSHORT, 4, b);
      assertEquals(TiffFieldType.SSHORT, v.getTiffType());
      assertEquals(4, v.getCount());

      assertEquals(0, v.intValue(0));
      assertEquals(32767, v.intValue(1));
      assertEquals(-32768, v.intValue(2));
      assertEquals(-1, v.intValue(3));
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   public void testLongs(int o) throws EOFException {
      ByteOrder order = o != 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
      ByteBuffer b = ByteBuffer.allocate(16).order(order);
      b.asIntBuffer().put(new int[] {
         0,
         Integer.MAX_VALUE,
         (int) ((long) Integer.MAX_VALUE + 1),
         (int) ((long) Integer.MAX_VALUE * 2 + 1),
      });

      b.rewind();
      TiffValue v = TiffValue.read(TiffFieldType.LONG, 4, b);
      assertEquals(TiffFieldType.LONG, v.getTiffType());
      assertEquals(4, v.getCount());

      assertEquals(0, v.longValue(0));
      assertEquals((long) Integer.MAX_VALUE, v.longValue(1));
      assertEquals((long) Integer.MAX_VALUE + 1, v.longValue(2));
      assertEquals((long) Integer.MAX_VALUE * 2 + 1, v.longValue(3));

      b.rewind();
      v = TiffValue.read(TiffFieldType.SLONG, 4, b);
      assertEquals(TiffFieldType.SLONG, v.getTiffType());
      assertEquals(4, v.getCount());

      assertEquals(0, v.intValue(0));
      assertEquals(Integer.MAX_VALUE, v.intValue(1));
      assertEquals(Integer.MIN_VALUE, v.intValue(2));
      assertEquals(-1, v.intValue(3));
   }

   @Test
   public void testRationals() throws EOFException {
      ByteBuffer b = ByteBuffer.allocate(8);
      b.asIntBuffer().put(new int[] { 1, 2 });
      b.rewind();
      TiffValue v = TiffValue.read(TiffFieldType.RATIONAL, 1, b);
      assertEquals(TiffFieldType.RATIONAL, v.getTiffType());
      assertEquals(1, v.getCount());
      assertEquals(0.5f, v.floatValue(0));
      assertEquals(0.5, v.doubleValue(0));
   }
}
