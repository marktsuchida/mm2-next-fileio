package org.micromanager.data.internal.io.mmtiff;

import java.io.EOFException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public abstract class TiffValue {
   public static TiffValue read(TiffFieldType type, int count, ByteBuffer b) throws EOFException {
      int totalLength = type.getElementSize() * count;
      if (b.remaining() < totalLength) {
         throw new EOFException();
      }
      ByteBuffer bb = b.slice().order(b.order());
      b.position(b.position() + totalLength);
      return type.read(count, bb);
   }

   public abstract TiffFieldType getTiffType();
   public abstract int getCount();

   //
   //
   //

   public int intValue(int index) {
      throw new UnsupportedOperationException();
   }

   public long longValue(int index) {
      return intValue(index);
   }

   public float floatValue(int index) {
      throw new UnsupportedOperationException();
   }

   public double doubleValue(int index) {
      throw new UnsupportedOperationException();
   }

   public String utf8Value() {
      throw new UnsupportedOperationException();
   }

   //
   //
   //

   public static class Undefined extends TiffValue {
      protected final byte[] bytes_;

      Undefined(int count, ByteBuffer b) {
         bytes_ = new byte[count];
         b.get(bytes_);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.UNDEFINED;
      }

      @Override
      public int getCount() {
         return bytes_.length;
      }
   }

   public static class Bytes extends Undefined {
      Bytes(int count, ByteBuffer b) {
         super(count, b);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.BYTE;
      }

      @Override
      public int intValue(int index) {
         return Unsigned.from(bytes_[index]);
      }
   }

   public static class SignedBytes extends Bytes {
      SignedBytes(int count, ByteBuffer b) {
         super(count, b);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.SBYTE;
      }

      @Override
      public int intValue(int index) {
         return bytes_[index];
      }
   }

   public static class Ascii extends Undefined {
      Ascii(int count, ByteBuffer b) {
         super(count, b);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.ASCII;
      }

      @Override
      public String utf8Value() {
         // Exclude any null terminator
         int len = bytes_.length;
         while (bytes_[len - 1] == 0) {
            --len;
         }

         try {
            return new String(bytes_, 0, len, "UTF-8");
         }
         catch (UnsupportedEncodingException guaranteedWontOccur) {
            return null;
         }
      }
   }

   public static class Shorts extends TiffValue {
      protected final short[] values_;

      Shorts(int count, ByteBuffer b) {
         values_ = new short[count];
         b.asShortBuffer().get(values_);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.SHORT;
      }

      @Override
      public int getCount() {
         return values_.length;
      }

      @Override
      public int intValue(int index) {
         return Unsigned.from(values_[index]);
      }
   }

   public static class SignedShorts extends Shorts {
      SignedShorts(int count, ByteBuffer b) {
         super(count, b);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.SSHORT;
      }

      @Override
      public int intValue(int index) {
         return values_[index];
      }
   }

   public static class Longs extends TiffValue {
      protected final int[] values_;

      Longs(int count, ByteBuffer b) {
         values_ = new int[count];
         b.asIntBuffer().get(values_);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.LONG;
      }

      @Override
      public int getCount() {
         return values_.length;
      }

      @Override
      public long longValue(int index) {
         return Unsigned.from(values_[index]);
      }
   }

   public static class SignedLongs extends Longs {
      SignedLongs(int count, ByteBuffer b) {
         super(count, b);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.SLONG;
      }

      @Override
      public int intValue(int index) {
         return values_[index];
      }

      @Override
      public long longValue(int index) {
         return values_[index];
      }
   }

   public static class Rationals extends TiffValue {
      protected final int[] numersDenoms_;

      Rationals(int count, ByteBuffer b) {
         numersDenoms_ = new int[2 * count];
         b.asIntBuffer().get(numersDenoms_);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.RATIONAL;
      }

      @Override
      public int getCount() {
         return numersDenoms_.length / 2;
      }

      @Override
      public float floatValue(int index) {
         return (float) doubleValue(index);
      }

      @Override
      public double doubleValue(int index) {
         return (double) Unsigned.from(numersDenoms_[2 * index]) /
            Unsigned.from(numersDenoms_[2 * index + 1]);
      }
   }

   public static class SignedRationals extends Rationals {
      SignedRationals(int count, ByteBuffer b) {
         super(count, b);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.SRATIONAL;
      }

      @Override
      public double doubleValue(int index) {
         return (double) numersDenoms_[2 * index] / numersDenoms_[2 * index + 1];
      }
   }

   public static class Floats extends TiffValue {
      private final float[] values_;

      Floats(int count, ByteBuffer b) {
         values_ = new float[count];
         b.asFloatBuffer().get(values_);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.FLOAT;
      }

      @Override
      public int getCount() {
         return values_.length;
      }

      @Override
      public float floatValue(int index) {
         return values_[index];
      }

      @Override
      public double doubleValue(int index) {
         return values_[index];
      }
   }

   public static class Doubles extends TiffValue {
      private final double[] values_;

      Doubles(int count, ByteBuffer b) {
         values_ = new double[count];
         b.asDoubleBuffer().get(values_);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.DOUBLE;
      }

      @Override
      public int getCount() {
         return values_.length;
      }

      @Override
      public float floatValue(int index) {
         return (float) values_[index];
      }

      @Override
      public double doubleValue(int index) {
         return values_[index];
      }
   }
}
