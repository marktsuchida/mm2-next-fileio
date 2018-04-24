package org.micromanager.data.internal.io.asynctiff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.micromanager.data.internal.io.BufferedPositionGroup;
import org.micromanager.data.internal.io.UnbufferedPosition;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TiffValueTest {
   @Test
   public void testReadUndefined() throws EOFException {
      ByteBuffer b = ByteBuffer.allocate(5);
      TiffValue v = TiffValue.read(TiffFieldType.UNDEFINED, 5, b);
      assertEquals(TiffFieldType.UNDEFINED, v.getTiffType());
      assertEquals(5, v.getCount());
   }

   @Test
   public void testReadBytes() throws EOFException {
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
   public void testWriteBytes() {
      ByteBuffer b = ByteBuffer.allocate(128);
      BufferedPositionGroup posGroup = BufferedPositionGroup.create();
      TiffValue v = TiffValue.Bytes.create(new byte[] { 0, 1, 127, (byte) 128, (byte) 255 });
      v.write(b, posGroup);
      b.rewind();
      assertEquals((byte) 0, b.get());
      assertEquals((byte) 1, b.get());
      assertEquals((byte) 127, b.get());
      assertEquals((byte) 128, b.get());
      assertEquals((byte) 255, b.get());
   }

   @Test
   public void testWriteBytesPadded() {
      ByteBuffer b = ByteBuffer.allocate(128);
      Arrays.fill(b.array(), (byte) 0xDE);
      BufferedPositionGroup posGroup = BufferedPositionGroup.create();
      TiffValue v = TiffValue.Bytes.create(new byte[] { 1, 2 });
      v.writeAndPad(b, posGroup, 4);
      assertEquals(4, b.position());
      b.rewind();
      assertEquals((byte) 1, b.get());
      assertEquals((byte) 2, b.get());
      assertEquals((byte) 0, b.get());
      assertEquals((byte) 0, b.get());
      assertEquals((byte) 0xDE, b.get());
   }

   @Test
   public void testReadAscii() throws Exception {
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

   @Test
   public void testWriteAscii() throws Exception {
      ByteBuffer b = ByteBuffer.allocate(128);
      Arrays.fill(b.array(), (byte) 0xDE);
      BufferedPositionGroup posGroup = BufferedPositionGroup.create();
      TiffValue v = TiffValue.Ascii.createUtf8("\u00B5Manager");
      v.write(b, posGroup);

      byte[] encoded = "\u00B5Manager".getBytes("UTF-8");
      assertEquals(encoded.length + 1, b.position());
      b.rewind();
      for (int i = 0; i < encoded.length; ++i) {
         assertEquals(encoded[i], b.get());
      }
      assertEquals(0, b.get());
      assertEquals((byte) 0xDE, b.get());
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   public void testReadShorts(int o) throws EOFException {
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
   public void testWriteShorts(int o) {
      ByteOrder order = o != 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
      ByteBuffer b = ByteBuffer.allocate(128).order(order);
      BufferedPositionGroup posGroup = BufferedPositionGroup.create();
      TiffValue v = TiffValue.Shorts.create(new short[] { 0, 32767, (short) 32768, (short) 65535 });
      v.write(b, posGroup);
      assertEquals(8, b.position());
      b.rewind();
      assertEquals(0, b.getShort());
      assertEquals(32767, b.getShort());
      assertEquals((short) 32768, b.getShort());
      assertEquals((short) 65535, b.getShort());
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   public void testReadLongs(int o) throws EOFException {
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
   public void testReadRationals() throws EOFException {
      ByteBuffer b = ByteBuffer.allocate(8);
      b.asIntBuffer().put(new int[] { 1, 2 });
      b.rewind();
      TiffValue v = TiffValue.read(TiffFieldType.RATIONAL, 1, b);
      assertEquals(TiffFieldType.RATIONAL, v.getTiffType());
      assertEquals(1, v.getCount());
      assertEquals(0.5f, v.floatValue(0));
      assertEquals(0.5, v.doubleValue(0));
   }

   @Test
   public void testWriteOffsets() {
      ByteBuffer b = ByteBuffer.allocate(128);
      BufferedPositionGroup posGroup = BufferedPositionGroup.forBufferAt(32);
      TiffOffsetFieldGroup fieldGroup = TiffOffsetFieldGroup.create();
      TiffValue v = TiffValue.Offsets.create(1, "test", fieldGroup);
      v.offsetValue(0).setOffsetValue(UnbufferedPosition.at(64));
      v.write(b, posGroup);
      b.rewind();
      assertEquals(32 + 0, v.offsetValue(0).getFieldPosition().get());
      assertEquals(64, b.getInt());
   }
}
