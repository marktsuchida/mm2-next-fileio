package org.micromanager.data.internal.io.mmtiff;

import com.google.common.collect.ImmutableList;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TiffIFD {
   private static final int ENTRY_COUNT_SIZE = 2;
   private static final int ENTRY_SIZE = 12;
   private static final int NEXT_IFD_OFFSET_SIZE = 4;

   private final ByteOrder byteOrder_;
   private final List<TiffIFDEntry> entries_;
   private final long nextIFDOffset_;

   //
   //
   //

   public static TiffIFD read(SeekableByteChannel chan, ByteOrder order) throws IOException {
      ByteBuffer entryCountBuffer = ByteBuffer.allocate(ENTRY_COUNT_SIZE).order(order);
      chan.read(entryCountBuffer);
      entryCountBuffer.rewind();
      int entryCount = Unsigned.from(entryCountBuffer.getShort());

      int remainingSize = entryCount * ENTRY_SIZE + NEXT_IFD_OFFSET_SIZE;

      ByteBuffer b = ByteBuffer.allocateDirect(remainingSize).order(order);
      chan.read(b);
      b.rewind();
      return readEntriesAndNextOffset(b, entryCount);
   }

   public static TiffIFD read(ByteBuffer b) throws IOException {
      int entryCount = Unsigned.from(b.getShort());
      if (entryCount * ENTRY_SIZE + NEXT_IFD_OFFSET_SIZE > b.remaining()) {
         throw new EOFException();
      }
      return readEntriesAndNextOffset(b, entryCount);
   }

   private static TiffIFD readEntriesAndNextOffset(ByteBuffer b, int entryCount) throws IOException {
      List<TiffIFDEntry> entries = new ArrayList<>();
      for (int i = 0; i < entryCount; ++i) {
         entries.add(TiffIFDEntry.read(b));
      }
      long nextIFDOffset = Unsigned.from(b.getInt());
      return new TiffIFD(b.order(), entries, nextIFDOffset);
   }

   private TiffIFD(ByteOrder order, List<TiffIFDEntry> entries, long nextIFDOffset) {
      byteOrder_ = order;
      entries_ = ImmutableList.copyOf(entries);
      nextIFDOffset_ = nextIFDOffset;
   }

   //
   //
   //

   public TiffIFDEntry getEntryWithTag(TiffTag tag) {
      for (TiffIFDEntry e : entries_) {
         if (e.getTag().equals(tag)) {
            return e;
         }
      }
      return null;
   }

   public TiffIFDEntry getRequiredEntryWithTag(TiffTag tag) throws TiffFormatException {
      TiffIFDEntry ret = getEntryWithTag(tag);
      if (ret == null) {
         throw new TiffFormatException(String.format(
            "Required TIFF IFD entry %s is missing", tag.name()));
      }
      return ret;
   }

   /**
    * Get all entries whose type matches the given value.
    *
    * Although the TIFF specification does not allow multiple entries with
    * the same type, in practice we see this in the wild. One example is the
    * inclusion of two ImageDescription entries by Micro-Manager, one for OME
    * and one for ImageJ.
    *
    * @param tag the TIFF tag
    * @return
    */
   public List<TiffIFDEntry> getAllEntriesWithTag(TiffTag tag) {
      return entries_.stream().
         filter(entry -> entry.getTag().equals(tag)).
         collect(Collectors.toList());
   }

   public boolean hasNextIFD() {
      return nextIFDOffset_ != 0;
   }

   public TiffIFD readNextIFD(SeekableByteChannel chan) throws IOException {
      if (!hasNextIFD()) {
         throw new EOFException();
      }
      chan.position(nextIFDOffset_);
      return TiffIFD.read(chan, byteOrder_);
   }

   //
   //
   //

   public boolean isSingleStrip(SeekableByteChannel chan) throws IOException {
      long imageLength = getRequiredEntryWithTag(TiffTag.Known.ImageLength.get()).
         readValue(chan).longValue(0);
      long rowsPerStrip = getRequiredEntryWithTag(TiffTag.Known.RowsPerStrip.get()).
         readValue(chan).longValue(0);
      return imageLength == rowsPerStrip;
   }

   public ByteBuffer readPixels(SeekableByteChannel chan) throws IOException {
      if (!isSingleStrip(chan)) {
         throw new TiffFormatException(
            "Only images stored in a single strip are currently supported");
      }

      long firstStripOffset = getRequiredEntryWithTag(TiffTag.Known.StripOffsets.get()).
         readValue(chan).longValue(0);
      long firstStripByteCount = getRequiredEntryWithTag(TiffTag.Known.StripByteCounts.get()).
         readValue(chan).longValue(0);
      ByteBuffer pixels = ByteBuffer.allocateDirect((int) firstStripByteCount).
         order(byteOrder_);
      chan.position(firstStripOffset).read(pixels);
      return pixels;
   }
}
