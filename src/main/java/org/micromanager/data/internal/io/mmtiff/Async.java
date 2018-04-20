package org.micromanager.data.internal.io.mmtiff;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class Async {
   private Async() {}

   /**
    * Read from an asynchronous file channel into a byte buffer.
    *
    * This method either reads enough bytes to fill the remaining space in the
    * buffer, or fails entirely. Upon failure, the buffer may contain partial
    * data. If the buffer could not be filled, the returned completion stage
    * will hold an {@link EOFException}.
    *
    * @param chan the asynchronous file channel
    * @param buffer the destination buffer
    * @param offset absolute file offset from which to read
    * @return a completion stage for the pending read, bearing {@code buffer}
    */
   public static CompletionStage<ByteBuffer> read(AsynchronousFileChannel chan,
                                                  ByteBuffer buffer,
                                                  long offset) {
      int bytesToRead = buffer.remaining();
      CompletableFuture<ByteBuffer> future = new CompletableFuture<>();
      chan.read(buffer, offset, future,
         new CompletionHandler<Integer, CompletableFuture<ByteBuffer>>() {
            @Override
            public void completed(Integer result, CompletableFuture<ByteBuffer> f) {
               if (result == bytesToRead) {
                  f.complete(buffer);
               }
               else {
                  f.completeExceptionally(new EOFException());
               }
            }

            @Override
            public void failed(Throwable t, CompletableFuture<ByteBuffer> f) {
               f.completeExceptionally(t);
            }
         });
      return future;
   }

   /**
    * Write from a byte buffer to an asynchronous file channel.
    *
    * @param chan the asynchronous file channel
    * @param buffer the source buffer
    * @param offset absolute file offset at which to write
    * @return a completion stage for the pending write
    */
   public static CompletionStage<Void> write(AsynchronousFileChannel chan,
                                                ByteBuffer buffer,
                                                long offset) {
      int bytesToWrite = buffer.remaining();
      CompletableFuture<Void> future = new CompletableFuture<>();
      chan.write(buffer, offset, future,
         new CompletionHandler<Integer, CompletableFuture<Void>>() {
            @Override
            public void completed(Integer result, CompletableFuture<Void> f) {
               if (result != bytesToWrite) {
                  f.completeExceptionally(new AssertionError(
                     "AsynchronousFileChannel write completed without exception but did not write expected number of bytes"));
               }
               f.complete(null);
            }

            @Override
            public void failed(Throwable t, CompletableFuture<Void> f) {
               f.completeExceptionally(t);
            }
         });
      return future;
   }

   /**
    * Create a completed completion stage holding an exception.
    *
    * This utility method is like {@link CompletableFuture#completedFuture},
    * except that the returned future is completed exceptionally rather than
    * normally.
    *
    * @param t the exception with which to complete the completion stage
    * @param <T> the value type of the completion stage
    * @return the exceptionally completed completion stage
    */
   public static <T> CompletionStage<T> completedExceptionally(Throwable t) {
      CompletableFuture<T> ret = new CompletableFuture<>();
      ret.completeExceptionally(t);
      return ret;
   }
}
