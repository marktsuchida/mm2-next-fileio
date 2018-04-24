package org.micromanager.data.internal.io.asynctiff;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TiffOffsetFieldGroup {
   private final List<TiffOffsetField> fields_ = new ArrayList<>();

   private TiffOffsetFieldGroup() {}

   public static TiffOffsetFieldGroup create() {
      return new TiffOffsetFieldGroup();
   }

   public void add(TiffOffsetField field) {
      fields_.add(field);
   }

   public CompletionStage<Void> updateAll(AsynchronousFileChannel chan,
                                          ByteOrder order) {
      fields_.sort(Comparator.comparingLong(f -> f.getFieldPosition().get()));

      CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
      for (TiffOffsetField field : fields_) {
         stage = stage.thenCompose(v -> field.update(chan, order));
      }
      return stage;
   }

   public void updateAll(ByteBuffer buffer) {
      for (TiffOffsetField field : fields_) {
         field.update(buffer);
      }
   }
}
