package org.micromanager.data.internal.io.asynctiff;

import com.google.common.collect.ImmutableList;
import org.micromanager.data.internal.io.Async;
import org.micromanager.data.internal.io.BufferedPositionGroup;
import org.micromanager.data.internal.io.UnbufferedPosition;
import org.micromanager.data.internal.io.Unsigned;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TiffIFD {
   private static final int ENTRY_COUNT_SIZE = 2;
   private static final int ENTRY_SIZE = 12;
   private static final int NEXT_IFD_OFFSET_SIZE = 4;

   private final ByteOrder byteOrder_;
   private final List<TiffIFDEntry> entries_;
   private final TiffOffsetField nextIFDOffset_;

   //
   //
   //

   public static CompletionStage<TiffIFD> read(AsynchronousFileChannel chan, ByteOrder order, long offset) {
      ByteBuffer countBuffer = ByteBuffer.allocate(ENTRY_COUNT_SIZE).order(order);
      return Async.read(chan, countBuffer, offset).
         thenComposeAsync(cb -> {
            cb.rewind();
            int entryCount = Unsigned.from(cb.getShort());
            int remainingSize = entryCount * ENTRY_SIZE + NEXT_IFD_OFFSET_SIZE;
            ByteBuffer bodyBuffer = ByteBuffer.allocateDirect(remainingSize).order(order);
            return Async.read(chan, bodyBuffer, offset + ENTRY_COUNT_SIZE).
               thenComposeAsync(bb -> {
                  bb.rewind();
                  try {
                     return CompletableFuture.completedFuture(
                        readEntriesAndNextOffset(bb, entryCount));
                  }
                  catch (IOException e) {
                     return Async.completedExceptionally(e);
                  }
               });
         });
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

   public static TiffIFD createForWrite(ByteOrder order, Collection<TiffIFDEntry> entries,
                                        TiffOffsetField nextIFDOffsetField) {
      return new TiffIFD(order, entries, nextIFDOffsetField);
   }

   public static Builder builder(ByteOrder order, TiffOffsetField nextIFDOffsetField, TiffOffsetFieldGroup ifdFieldGroup) {
      return new Builder(order, nextIFDOffsetField, ifdFieldGroup);
   }

   public static class Builder {
      private final ByteOrder order_;
      private final TiffOffsetField nextIFDOffset_;
      private final TiffOffsetFieldGroup ifdFieldGroup_;
      private List<TiffIFDEntry> entries_ = new ArrayList<>();

      private Builder(ByteOrder order, TiffOffsetField nextIFDOffsetField, TiffOffsetFieldGroup ifdFieldGroup) {
         order_ = order;
         nextIFDOffset_ = nextIFDOffsetField;
         ifdFieldGroup_ = ifdFieldGroup;
      }

      public Builder entry(TiffTag tag, TiffValue value) {
         entries_.add(TiffIFDEntry.createForWrite(order_, tag, value, ifdFieldGroup_));
         return this;
      }

      public TiffIFD build() {
         return createForWrite(order_, entries_, nextIFDOffset_);
      }
   }

   // Read
   private TiffIFD(ByteOrder order, List<TiffIFDEntry> entries, long nextIFDOffset) {
      byteOrder_ = order;
      entries_ = ImmutableList.copyOf(entries);
      nextIFDOffset_ = TiffOffsetField.forOffsetValue(
         UnbufferedPosition.at(nextIFDOffset),
         "Read-only NextIFDOffset");
   }

   // Writing
   private TiffIFD(ByteOrder order, Collection<TiffIFDEntry> entries, TiffOffsetField nextIFDOffsetField) {
      byteOrder_ = order;
      List<TiffIFDEntry> sortEntries = new ArrayList<>(entries);
      sortEntries.sort(Comparator.comparingInt(e -> e.getTag().getTiffConstant()));
      entries_ = ImmutableList.copyOf(sortEntries);
      nextIFDOffset_ = nextIFDOffsetField;
   }

   //
   //
   //

   public List<TiffIFDEntry> getEntries() {
      return entries_;
   }

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
      return nextIFDOffset_.getOffsetValue().get() != 0;
   }

   public CompletionStage<TiffIFD> readNextIFD(AsynchronousFileChannel chan) throws IOException {
      // TODO Or should we re-read the NextIFDOffset?
      if (!hasNextIFD()) {
         throw new EOFException();
      }
      return TiffIFD.read(chan, byteOrder_, nextIFDOffset_.getOffsetValue().get());
   }


   //
   //
   //

   private CompletionStage<Boolean> isSingleStrip(AsynchronousFileChannel chan) {
      try {
         CompletionStage<TiffValue> getImageLength = getRequiredEntryWithTag(
            TiffTag.Known.ImageLength.get()).readValue(chan);
         CompletionStage<TiffValue> getRowsPerStrip = getRequiredEntryWithTag(
            TiffTag.Known.RowsPerStrip.get()).readValue(chan);
         return getImageLength.thenCombine(getRowsPerStrip,
               (length, rows) -> length.longValue(0) == rows.longValue(0));
      }
      catch (IOException e) {
         return Async.completedExceptionally(e);
      }
   }

   public CompletionStage<ByteBuffer> readPixels(AsynchronousFileChannel chan) {
      return isSingleStrip(chan).thenComposeAsync(ok ->
         ok ? readPixelsSingleStrip(chan) :
            Async.completedExceptionally(new TiffFormatException(
               "Only images stored in a single strip are currently supported")));
   }

   private CompletionStage<ByteBuffer> readPixelsSingleStrip(AsynchronousFileChannel chan) {
      try {
         CompletionStage<TiffValue> getStripOffsets = getRequiredEntryWithTag(
            TiffTag.Known.StripOffsets.get()).readValue(chan);
         CompletionStage<TiffValue> getStripByteCounts = getRequiredEntryWithTag(
            TiffTag.Known.StripByteCounts.get()).readValue(chan);
         return getStripOffsets.thenCombine(getStripByteCounts,
            (offsets, sizes) -> readBlock(chan, offsets.longValue(0), sizes.longValue(0))).
            thenCompose(Function.identity());
      }
      catch (IOException e) {
         return Async.completedExceptionally(e);
      }
   }

   private CompletionStage<ByteBuffer> readBlock(AsynchronousFileChannel chan,
                                                 long offset, long size) {
      ByteBuffer buffer = ByteBuffer.allocateDirect((int) size);
      return Async.read(chan, buffer, offset);
   }

   //
   //
   //

   public CompletionStage<Long> write(AsynchronousFileChannel chan) {
      ByteBuffer buffer = ByteBuffer.allocate(
         ENTRY_COUNT_SIZE + ENTRY_SIZE * entries_.size() + NEXT_IFD_OFFSET_SIZE).
         order(byteOrder_);
      BufferedPositionGroup posGroup = BufferedPositionGroup.create();
      write(buffer, posGroup);

      return Async.pad(chan, 4).
         thenCompose(v -> {
            try {
               long offset = chan.size();
               posGroup.setBufferFileOffset(offset);
               return Async.write(chan, buffer, offset).
                  thenApply(v2 -> offset);
            }
            catch (IOException e) {
               return Async.completedExceptionally(e);
            }
         });
   }

   public int write(ByteBuffer dest, BufferedPositionGroup posGroup) {
      int pos = dest.position();
      dest.putShort((short) entries_.size());
      for (TiffIFDEntry entry : entries_) {
         entry.write(dest, posGroup);
      }
      nextIFDOffset_.write(dest, posGroup);
      return pos;
   }
}
