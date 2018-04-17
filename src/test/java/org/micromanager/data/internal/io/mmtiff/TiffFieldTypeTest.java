package org.micromanager.data.internal.io.mmtiff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TiffFieldTypeTest {
   @Test
   public void testFitsInIFDEntry() {
      assertTrue(TiffFieldType.UNDEFINED.fitsInIFDEntry(1));
      assertTrue(TiffFieldType.UNDEFINED.fitsInIFDEntry(2));
      assertTrue(TiffFieldType.UNDEFINED.fitsInIFDEntry(3));
      assertTrue(TiffFieldType.UNDEFINED.fitsInIFDEntry(4));
      assertFalse(TiffFieldType.UNDEFINED.fitsInIFDEntry(5));

      assertTrue(TiffFieldType.ASCII.fitsInIFDEntry(1));
      assertTrue(TiffFieldType.ASCII.fitsInIFDEntry(2));
      assertTrue(TiffFieldType.ASCII.fitsInIFDEntry(3));
      assertTrue(TiffFieldType.ASCII.fitsInIFDEntry(4));
      assertFalse(TiffFieldType.ASCII.fitsInIFDEntry(5));

      assertTrue(TiffFieldType.BYTE.fitsInIFDEntry(1));
      assertTrue(TiffFieldType.BYTE.fitsInIFDEntry(2));
      assertTrue(TiffFieldType.BYTE.fitsInIFDEntry(3));
      assertTrue(TiffFieldType.BYTE.fitsInIFDEntry(4));
      assertFalse(TiffFieldType.BYTE.fitsInIFDEntry(5));

      assertTrue(TiffFieldType.SHORT.fitsInIFDEntry(1));
      assertTrue(TiffFieldType.SHORT.fitsInIFDEntry(2));
      assertFalse(TiffFieldType.SHORT.fitsInIFDEntry(3));

      assertTrue(TiffFieldType.LONG.fitsInIFDEntry(1));
      assertFalse(TiffFieldType.LONG.fitsInIFDEntry(2));

      assertFalse(TiffFieldType.RATIONAL.fitsInIFDEntry(1));

      assertTrue(TiffFieldType.SBYTE.fitsInIFDEntry(1));
      assertTrue(TiffFieldType.SBYTE.fitsInIFDEntry(2));
      assertTrue(TiffFieldType.SBYTE.fitsInIFDEntry(3));
      assertTrue(TiffFieldType.SBYTE.fitsInIFDEntry(4));
      assertFalse(TiffFieldType.SBYTE.fitsInIFDEntry(5));

      assertTrue(TiffFieldType.SSHORT.fitsInIFDEntry(1));
      assertTrue(TiffFieldType.SSHORT.fitsInIFDEntry(2));
      assertFalse(TiffFieldType.SSHORT.fitsInIFDEntry(3));

      assertTrue(TiffFieldType.SLONG.fitsInIFDEntry(1));
      assertFalse(TiffFieldType.SLONG.fitsInIFDEntry(2));

      assertFalse(TiffFieldType.SRATIONAL.fitsInIFDEntry(1));

      assertTrue(TiffFieldType.FLOAT.fitsInIFDEntry(1));
      assertFalse(TiffFieldType.FLOAT.fitsInIFDEntry(2));

      assertFalse(TiffFieldType.DOUBLE.fitsInIFDEntry(1));
   }
}
