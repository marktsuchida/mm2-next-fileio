package org.micromanager.data.internal.io.asynctiff;

import com.google.common.base.Preconditions;
import org.micromanager.data.internal.io.BufferedPositionGroup;
import org.micromanager.data.internal.io.Unsigned;

import java.io.EOFException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class TiffValue {
   // Could set this to e.g. 0xDE for testing/debugging
   private static final byte PAD_BYTE = (byte) 0x00;

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

   public final int getByteCount() {
      return getTiffType().getElementSize() * getCount();
   }

   public final boolean fitsInIFDEntry() {
      return getTiffType().fitsInIFDEntry(getCount());
   }

   public abstract void write(ByteBuffer b, BufferedPositionGroup posGroup);

   public void writeAndPad(ByteBuffer b, BufferedPositionGroup posGroup, int fixedSize) {
      Preconditions.checkState(getByteCount() <= fixedSize);
      int saveLimit = b.limit();
      try {
         b.limit(b.position() + fixedSize);
         write(b, posGroup);
         while (b.remaining() > 0) {
            b.put(PAD_BYTE);
         }
      }
      finally {
         b.limit(saveLimit);
      }
   }

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

   public TiffOffsetField offsetValue(int index) {
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

      private Undefined(byte[] bytes) {
         bytes_ = bytes;
      }

      public static Undefined create(byte[] bytes) {
         return new Undefined(bytes);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.UNDEFINED;
      }

      @Override
      public int getCount() {
         return bytes_.length;
      }

      @Override
      public void write(ByteBuffer b, BufferedPositionGroup posGroup) {
         b.put(bytes_);
      }
   }

   public static class Bytes extends Undefined {
      Bytes(int count, ByteBuffer b) {
         super(count, b);
      }

      private Bytes(byte[] bytes) {
         super(bytes);
      }

      public static Bytes create(byte[] bytes) {
         return new Bytes(bytes);
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

      private SignedBytes(byte[] bytes) {
         super(bytes);
      }

      public static SignedBytes create(byte[] bytes) {
         return new SignedBytes(bytes);
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

      private Ascii(byte[] bytes) {
         super(bytes);
      }

      public static Ascii createUtf8(String str) {
         try {
            byte[] encoded = str.getBytes("UTF-8");
            // Add null terminator
            return new Ascii(Arrays.copyOf(encoded, encoded.length + 1));
         }
         catch (UnsupportedEncodingException cannotOccur) {
            throw new RuntimeException(cannotOccur);
         }
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
         b.position(b.position() + 2 * count);
      }

      private Shorts(short[] values) {
         values_ = values;
      }

      public static Shorts create(short[] values) {
         return new Shorts(values);
      }

      public static Shorts create(short value) {
         return create(new short[] { value });
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

      @Override
      public void write(ByteBuffer b, BufferedPositionGroup posGroup) {
         b.asShortBuffer().put(values_);
         b.position(b.position() + 2 * values_.length);
      }
   }

   public static class SignedShorts extends Shorts {
      SignedShorts(int count, ByteBuffer b) {
         super(count, b);
      }

      private SignedShorts(short[] values) {
         super(values);
      }

      public static SignedShorts create(short[] values) {
         return new SignedShorts(values);
      }

      public static SignedShorts create(short value) {
         return create(new short[] { value });
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
         b.position(b.position() + 4 * count);
      }

      private Longs(int[] values) {
         values_ = values;
      }

      public static Longs create(int[] values) {
         return new Longs(values);
      }

      public static Longs create(int value) {
         return create(new int[] { value });
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

      @Override
      public void write(ByteBuffer b, BufferedPositionGroup posGroup) {
         b.asIntBuffer().put(values_);
         b.position(b.position() + 4 * values_.length);
      }
   }

   public static class SignedLongs extends Longs {
      SignedLongs(int count, ByteBuffer b) {
         super(count, b);
      }

      private SignedLongs(int[] values) {
         super(values);
      }

      public static SignedLongs create(int[] values) {
         return new SignedLongs(values);
      }

      public static SignedLongs create(int value) {
         return create(new int[] { value });
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
         b.position(b.position() + 8 * count);
      }

      private Rationals(int[] numersDenoms) {
         numersDenoms_ = numersDenoms;
      }

      public static Rationals create(int[] numersDenoms) {
         return new Rationals(numersDenoms);
      }

      public static Rationals create(int numer, int denom) {
         return create(new int[] { numer, denom });
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

      @Override
      public void write(ByteBuffer b, BufferedPositionGroup posGroup) {
         b.asIntBuffer().put(numersDenoms_);
         b.position(b.position() + 4 * numersDenoms_.length);
      }
   }

   public static class SignedRationals extends Rationals {
      SignedRationals(int count, ByteBuffer b) {
         super(count, b);
      }

      private SignedRationals(int[] numersDenoms) {
         super(numersDenoms);
      }

      public static SignedRationals create(int[] numersDenoms) {
         return new SignedRationals(numersDenoms);
      }

      public static SignedRationals create(int numer, int denom) {
         return create(new int[] { numer, denom });
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
         b.position(b.position() + 4 * count);
      }

      private Floats(float[] values) {
         values_ = values;
      }

      public static Floats create(float[] values) {
         return new Floats(values);
      }

      public static Floats create(float value) {
         return create(new float[] { value });
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

      @Override
      public void write(ByteBuffer b, BufferedPositionGroup posGroup) {
         b.asFloatBuffer().put(values_);
         b.position(b.position() + 4 * values_.length);
      }
   }

   public static class Doubles extends TiffValue {
      private final double[] values_;

      Doubles(int count, ByteBuffer b) {
         values_ = new double[count];
         b.asDoubleBuffer().get(values_);
         b.position(b.position() + 8 * count);
      }

      private Doubles(double[] values) {
         values_ = values;
      }

      public static Doubles create(double[] values) {
         return new Doubles(values);
      }

      public static Doubles create(double value) {
         return create(new double[] { value });
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

      @Override
      public void write(ByteBuffer b, BufferedPositionGroup posGroup) {
         b.asDoubleBuffer().put(values_);
         b.position(b.position() + 8 * values_.length);
      }
   }

   public static class Offsets extends TiffValue {
      private final List<TiffOffsetField> offsets_ = new ArrayList<>();

      private Offsets(int count, String annotation, TiffOffsetFieldGroup fieldGroup) {
         for (int i = 0; i < count; ++i) {
            TiffOffsetField offsetField = TiffOffsetField.create(
               annotation + String.format("[%d]", i));
            fieldGroup.add(offsetField);
            offsets_.add(offsetField);
         }
      }

      public static Offsets create(int count, String annotation,
                                   TiffOffsetFieldGroup fieldGroup) {
         return new Offsets(count, annotation, fieldGroup);
      }

      @Override
      public TiffFieldType getTiffType() {
         return TiffFieldType.LONG;
      }

      @Override
      public int getCount() {
         return offsets_.size();
      }

      @Override
      public TiffOffsetField offsetValue(int index) {
         return offsets_.get(index);
      }

      @Override
      public void write(ByteBuffer b, BufferedPositionGroup posGroup) {
         for (int i = 0; i < offsets_.size(); ++i) {
            offsets_.get(i).write(b, posGroup);
         }
      }
   }
}
