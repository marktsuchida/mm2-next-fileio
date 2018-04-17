package org.micromanager.data.internal.io.mmtiff;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.micromanager.data.internal.io.mmtiff.TiffFieldType.*;

public abstract class TiffTag {
   private final short tiffConstant_;

   public static TiffTag fromTiffConstant(int value) {
      Known knownTag = Known.fromTiffConstant(value);
      if (knownTag == null) {
         return new UnknownTag(value);
      }
      return knownTag.get();
   }

   private TiffTag(int value) {
      tiffConstant_ = (short) value;
   }

   public int getTiffConstant() {
      return Unsigned.from(tiffConstant_);
   }

   public abstract String name();
   public abstract void checkType(TiffFieldType type) throws TiffFormatException;

   //
   //
   //

   private static class UnknownTag extends TiffTag {
      private UnknownTag(int value) {
         super(value);
      }

      @Override
      public String name() {
         return String.format("TIFFTag%d", getTiffConstant());
      }

      @Override
      public void checkType(TiffFieldType type) {
         return;
      }
   }

   private static class KnownTag extends TiffTag {
      private final Known known_;

      private KnownTag(Known known) {
         super(known.getTiffConstant());
         known_ = known;
      }

      @Override
      public String name() {
         return known_.name();
      }

      @Override
      public void checkType(TiffFieldType type) throws TiffFormatException {
         if (known_.getAllowedTypes().contains(type)) {
            return;
         }
         throw new TiffFormatException(String.format(
            "Invalid type (%s) found for TIFF tag %s (expected %s)",
            type.name(),
            known_.name(),
            (known_.getAllowedTypes().size() > 1 ? "one of " : "") +
               known_.getAllowedTypes().stream().
                  map(e -> e.name()).collect(Collectors.joining(", "))));
      }
   }

   //
   //
   //

   public enum Known {
      BitsPerSample(258, SHORT),
      Compression(259, SHORT),
      ImageDescription(270, ASCII),
      ImageLength(257, SHORT, LONG),
      ImageWidth(256, SHORT, LONG),
      PhotometricInterpretation(262, SHORT),
      ResolutionUnit(296, SHORT),
      RowsPerStrip(278, SHORT, LONG),
      SamplesPerPixel(277, SHORT),
      Software(305, ASCII),
      StripByteCounts(279, SHORT, LONG),
      StripOffsets(273, SHORT, LONG),
      XResolution(282, RATIONAL),
      YResolution(283, RATIONAL),

      IJMetadataByteCounts(50838, LONG),
      IJMetadata(50839, LONG),
      MicroManagerMetadata(51123, ASCII),;

      private static final Map<Short, Known> VALUES = new HashMap<>();
      static {
         for (Known t : Known.values()) {
            VALUES.put(t.tiffConstant_, t);
         }
      }

      private final KnownTag instance_;
      private final short tiffConstant_;
      private final List<TiffFieldType> allowedTypes_;

      Known(int tiffConstant, TiffFieldType... allowedTypes) {
         instance_ = new KnownTag(this);
         tiffConstant_ = (short) tiffConstant;
         allowedTypes_ = ImmutableList.copyOf(allowedTypes);
      }

      public static Known fromTiffConstant(int value) {
         return VALUES.get((short) value);
      }

      public KnownTag get() {
         return instance_;
      }

      public int getTiffConstant() {
         return Unsigned.from(tiffConstant_);
      }

      public List<TiffFieldType> getAllowedTypes() {
         return allowedTypes_;
      }
   }
}
