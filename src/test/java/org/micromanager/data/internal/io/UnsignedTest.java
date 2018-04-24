package org.micromanager.data.internal.io;

import org.junit.jupiter.api.Test;
import org.micromanager.data.internal.io.Unsigned;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnsignedTest {
   @Test
   public void testReadUnsignedByte() {
      assertEquals(0, Unsigned.from((byte) 0x00));
      assertEquals(1, Unsigned.from((byte) 0x01));
      assertEquals(127, Unsigned.from((byte) 0x7F));
      assertEquals(128, Unsigned.from((byte) 0x80));
      assertEquals(255, Unsigned.from((byte) 0xFF));
   }

   @Test
   public void testReadUnsignedShort() {
      assertEquals(0, Unsigned.from((short) 0x0000));
      assertEquals(1, Unsigned.from((short) 0x0001));
      assertEquals(32767, Unsigned.from((short) 0x7FFF));
      assertEquals(32768, Unsigned.from((short) 0x8000));
      assertEquals(65535, Unsigned.from((short) 0xFFFF));
   }

   @Test
   public void testReadUnsignedInt() {
      assertEquals(0, Unsigned.from(0x00000000));
      assertEquals(1, Unsigned.from(0x00000001));
      assertEquals(Integer.MAX_VALUE, Unsigned.from(0x7FFFFFFF));
      assertEquals((long) Integer.MAX_VALUE + 1, Unsigned.from(0x80000000));
      assertEquals((long) 2 * Integer.MAX_VALUE + 1, Unsigned.from(0xFFFFFFFF));
   }
}
