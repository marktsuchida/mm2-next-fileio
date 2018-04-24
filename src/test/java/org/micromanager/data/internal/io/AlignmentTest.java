package org.micromanager.data.internal.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AlignmentTest {
   @Test
   public void testIntNearZero() {
      assertEquals(0, Alignment.align(0, 4));
      assertEquals(4, Alignment.align(1, 4));
      assertEquals(4, Alignment.align(2, 4));
      assertEquals(4, Alignment.align(3, 4));
      assertEquals(4, Alignment.align(4, 4));
   }

   @Test
   public void testLongNearZero() {
      assertEquals(0L, Alignment.align(0L, 4));
      assertEquals(4L, Alignment.align(1L, 4));
      assertEquals(4L, Alignment.align(2L, 4));
      assertEquals(4L, Alignment.align(3L, 4));
      assertEquals(4L, Alignment.align(4L, 4));
   }

   @Test
   public void testIntNearSignedWraparound() {
      assertEquals(Integer.MIN_VALUE, Alignment.align(Integer.MAX_VALUE - 1, 4));
      assertEquals(Integer.MIN_VALUE, Alignment.align(Integer.MAX_VALUE, 4));
      assertEquals(Integer.MIN_VALUE, Alignment.align(Integer.MIN_VALUE, 4));
      assertEquals(Integer.MIN_VALUE + 4, Alignment.align(Integer.MIN_VALUE + 1, 4));
   }
}
