package org.micromanager.data.internal.io.mmtiff;

import org.micromanager.data.internal.io.Unsigned;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public enum TiffFieldType {
   BYTE(1, 1, TiffValue.Bytes::new),
   ASCII(2, 1, TiffValue.Ascii::new),
   SHORT(3, 2, TiffValue.Shorts::new),
   LONG(4, 4, TiffValue.Longs::new),
   RATIONAL(5, 8, TiffValue.Rationals::new),
   SBYTE(6, 1, TiffValue.SignedBytes::new),
   UNDEFINED(7, 1, TiffValue.Undefined::new),
   SSHORT(8, 2, TiffValue.SignedShorts::new),
   SLONG(9, 4, TiffValue.SignedLongs::new),
   SRATIONAL(10, 8, TiffValue.SignedRationals::new),
   FLOAT(11, 4, TiffValue.Floats::new),
   DOUBLE(12, 8, TiffValue.Doubles::new),
   ;

   private static final Map<Short, TiffFieldType> VALUES = new HashMap<>();
   static {
      for (TiffFieldType t : TiffFieldType.values()) {
         VALUES.put(t.tiffConstant_, t);
      }
   }

   private final short tiffConstant_;
   private final int elementSize_;
   private final BiFunction<Integer, ? super ByteBuffer, ? extends TiffValue> reader_;

   TiffFieldType(int tiffConstant, int elementSize,
                 BiFunction<Integer, ? super ByteBuffer, ? extends TiffValue> reader) {
      tiffConstant_ = (short) tiffConstant;
      elementSize_ = elementSize;
      reader_ = reader;
   }

   public static TiffFieldType fromTiffConstant(int value) {
      return VALUES.get((short) value);
   }

   public int getTiffConstant() {
      return Unsigned.from(tiffConstant_);
   }

   public int getElementSize() {
      return elementSize_;
   }

   public boolean fitsInIFDEntry(int count) {
      return elementSize_ * count <= 4;
   }

   public TiffValue read(int count, ByteBuffer b) {
      return reader_.apply(count, b);
   }
}
