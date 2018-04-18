package org.micromanager.data.internal.io.mmtiff;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class Async {
   private Async() {}

   public static <T> CompletionStage<Integer> read(
      AsynchronousFileChannel chan, ByteBuffer buffer, long offset) {
      CompletableFuture<Integer> future = new CompletableFuture<>();
      chan.read(buffer, offset, future,
         new CompletionHandler<Integer, CompletableFuture<Integer>>() {
            @Override
            public void completed(Integer result, CompletableFuture<Integer> f) {
               f.complete(result);
            }

            @Override
            public void failed(Throwable t, CompletableFuture<Integer> f) {
               f.completeExceptionally(t);
            }
         });
      return future;
   }

   public static <T> CompletionStage<Integer> write(
      AsynchronousFileChannel chan, ByteBuffer buffer, long offset) {
      CompletableFuture<Integer> future = new CompletableFuture<>();
      chan.write(buffer, offset, future,
         new CompletionHandler<Integer, CompletableFuture<Integer>>() {
            @Override
            public void completed(Integer result, CompletableFuture<Integer> f) {
               f.complete(result);
            }

            @Override
            public void failed(Throwable t, CompletableFuture<Integer> f) {
               f.completeExceptionally(t);
            }
         });
      return future;
   }

   public static <T> CompletionStage<T> completedExceptionally(Throwable t) {
      CompletableFuture<T> ret = new CompletableFuture<>();
      ret.completeExceptionally(t);
      return ret;
   }
}
