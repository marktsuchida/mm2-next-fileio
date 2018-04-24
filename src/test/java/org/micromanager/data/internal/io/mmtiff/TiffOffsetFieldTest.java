package org.micromanager.data.internal.io.mmtiff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.micromanager.data.internal.io.UnbufferedPosition;
import org.micromanager.data.internal.io.BufferedPositionGroup;
import org.micromanager.data.internal.io.Unsigned;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/*
 * This test demonstrates how TiffOffsetField can be used to manage pointers
 * (offset fields) inside TIFF files for different combinations of:
 * - Whether the pointee is written before or after the pointer
 * - Whether the pointer is written directly to the file or via a buffer
 * - Whether the pointee is written directly to the file or via a buffer
 * - If both pointer and pointee are written first to a buffer, whether they
 *   are both written to the same buffer or to two different buffers
 */

public class TiffOffsetFieldTest {
   private Path file_;

   private static final byte FILE_BACKGROUND = (byte) 0xDE;
   private static final byte BUFFER_BACKGROUND = (byte) 0xBE;

   private static final byte[] POINTEE_DATA = new byte[] { 0x11, 0x22, 0x33, 0x44 };

   @BeforeEach
   public void init() throws Exception {
      file_ = Files.createTempFile(getClass().getSimpleName(), null);

      ByteBuffer b = ByteBuffer.allocate(1024);
      Arrays.fill(b.array(), FILE_BACKGROUND);
      try (FileChannel chan = FileChannel.open(file_, StandardOpenOption.WRITE)) {
         chan.write(b);
      }
   }

   @AfterEach
   public void tearDown() throws Exception {
      Files.deleteIfExists(file_);
   }

   @Test
   public void testPointeeThenPointerToFile() throws Exception {
      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(file_,
         StandardOpenOption.WRITE)) {
         // First we write the pointee data
         TiffOffsetField field = TiffOffsetField.forOffsetValue(
            UnbufferedPosition.at(32), "test");
         chan.write(ByteBuffer.wrap(POINTEE_DATA),
            field.getOffsetValue().get()). get();

         // Later we write the pointer
         field.write(chan, ByteOrder.BIG_ENDIAN, 64).
            toCompletableFuture().get();
      }

      ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file_));
      b.position(64);
      assertEquals(32, Unsigned.from(b.asIntBuffer().get()));
      b.position(32);
      byte[] data = new byte[POINTEE_DATA.length];
      b.get(data);
      assertArrayEquals(POINTEE_DATA, data);
   }

   @Test
   public void testPointerThenPointeeToFile() throws Exception {
      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(file_,
         StandardOpenOption.WRITE)) {
         TiffOffsetFieldGroup fieldGroup = TiffOffsetFieldGroup.create();

         // First we leave a placeholder for the pointer
         TiffOffsetField field = TiffOffsetField.create("test");
         fieldGroup.add(field);
         field.write(chan, ByteOrder.BIG_ENDIAN, 32).
            toCompletableFuture().get();

         // Later we write the pointee
         field.setOffsetValue(UnbufferedPosition.at(64));
         chan.write(ByteBuffer.wrap(POINTEE_DATA),
            field.getOffsetValue().get()).get();

         // Finally we update the pointer
         fieldGroup.updateAll(chan, ByteOrder.BIG_ENDIAN)
            .toCompletableFuture().get();
      }

      ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file_));
      b.position(32);
      assertEquals(64, Unsigned.from(b.asIntBuffer().get()));
      b.position(64);
      byte[] data = new byte[POINTEE_DATA.length];
      b.get(data);
      assertArrayEquals(POINTEE_DATA, data);
   }

   @Test
   public void testPointeeThenPointerToSameBuffer() throws Exception {
      ByteBuffer buffer = ByteBuffer.allocate(128);
      Arrays.fill(buffer.array(), BUFFER_BACKGROUND);

      BufferedPositionGroup posGroup = BufferedPositionGroup.create();

      // First we write the pointee data
      TiffOffsetField field = TiffOffsetField.forOffsetValue(
         posGroup.positionInBuffer(32), "test");
      buffer.position(field.getOffsetValue().getPositionInBuffer());
      buffer.put(POINTEE_DATA);
      buffer.rewind();

      // When it's time to write the pointer, we leave a placeholder as we do
      // not yet know the absolute offset of the pointee
      buffer.position(64);
      field.write(buffer, posGroup);
      buffer.rewind();

      // When it's time to flush the buffer to disk, we know where it will be
      // written, so can make the offset value definite and update the pointer
      long bufferStart = 128;
      posGroup.setBufferFileOffset(bufferStart);
      field.update(buffer);

      // Finally, we flush the buffer to disk
      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(file_,
         StandardOpenOption.WRITE)) {
         chan.write(buffer, bufferStart).get();
      }

      ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file_));
      b.position(128 + 64);
      assertEquals(128 + 32, b.asIntBuffer().get());
      b.position(128 + 32);
      byte[] data = new byte[POINTEE_DATA.length];
      b.get(data);
      assertArrayEquals(POINTEE_DATA, data);
   }

   @Test
   public void testPointerThenPointeeToSameBuffer() throws Exception {
      ByteBuffer buffer = ByteBuffer.allocate(128);
      Arrays.fill(buffer.array(), BUFFER_BACKGROUND);

      BufferedPositionGroup posGroup = BufferedPositionGroup.create();
      TiffOffsetFieldGroup fieldGroup = TiffOffsetFieldGroup.create();

      // First we write a placeholder for the pointer
      TiffOffsetField field = TiffOffsetField.create("test");
      fieldGroup.add(field);
      buffer.position(32);
      field.write(buffer, posGroup);
      buffer.rewind();

      // Next, we write the pointee data
      field.setOffsetValue(posGroup.positionInBuffer(64));
      buffer.position(field.getOffsetValue().getPositionInBuffer());
      buffer.put(POINTEE_DATA);
      buffer.rewind();

      // Finally, before flushing to disk, we update the pointer value
      long bufferStart = 128;
      posGroup.setBufferFileOffset(bufferStart);
      fieldGroup.updateAll(buffer);

      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(file_,
         StandardOpenOption.WRITE)) {
         chan.write(buffer, bufferStart).get();
      }

      ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file_));
      b.position(128 + 32);
      assertEquals(128 + 64, b.asIntBuffer().get());
      b.position(128 + 64);
      byte[] data = new byte[POINTEE_DATA.length];
      b.get(data);
      assertArrayEquals(POINTEE_DATA, data);
   }

   @Test
   public void testPointeeToFileThenPointerToBuffer() throws Exception {
      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(file_,
         StandardOpenOption.WRITE)) {
         // First we write the pointee data
         TiffOffsetField field = TiffOffsetField.forOffsetValue(
            UnbufferedPosition.at(32), "test");
         chan.write(ByteBuffer.wrap(POINTEE_DATA),
            field.getOffsetValue().get()).get();

         // Later we write the pointer via a buffer
         ByteBuffer buffer = ByteBuffer.allocate(128);
         Arrays.fill(buffer.array(), BUFFER_BACKGROUND);
         buffer.position(32);
         field.write(buffer);
         buffer.rewind();

         long bufferStart = 128;
         chan.write(buffer, bufferStart).get();
      }

      ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file_));
      b.position(128 + 32);
      assertEquals(32, Unsigned.from(b.asIntBuffer().get()));
      b.position(32);
      byte[] data = new byte[POINTEE_DATA.length];
      b.get(data);
      assertArrayEquals(POINTEE_DATA, data);
   }

   @Test
   public void testPointerToBufferThenPointeeToFile() throws Exception {
      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(file_,
         StandardOpenOption.WRITE)) {
         ByteBuffer buffer = ByteBuffer.allocate(128);
         Arrays.fill(buffer.array(), BUFFER_BACKGROUND);

         BufferedPositionGroup posGroup = BufferedPositionGroup.create();
         TiffOffsetFieldGroup fieldGroup = TiffOffsetFieldGroup.create();

         // First we write a placeholder for the pointer in the buffer
         TiffOffsetField field = TiffOffsetField.create("test");
         fieldGroup.add(field);
         buffer.position(32);
         field.write(buffer, posGroup);
         buffer.rewind();

         // The buffer gets flushed to disk before we know where the pointee
         // will reside
         long bufferStart = 128;
         posGroup.setBufferFileOffset(bufferStart);
         chan.write(buffer, bufferStart).get();

         // Later, we write the pointee data to the file
         field.setOffsetValue(UnbufferedPosition.at(512));
         chan.write(ByteBuffer.wrap(POINTEE_DATA),
            field.getOffsetValue().get()).get();

         // Finally, we update the offset field in the file
         fieldGroup.updateAll(chan, ByteOrder.BIG_ENDIAN).
            toCompletableFuture().get();
      }

      ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file_));
      b.position(128 + 32);
      assertEquals(512, b.asIntBuffer().get());
      b.position(512);
      byte[] data = new byte[POINTEE_DATA.length];
      b.get(data);
      assertArrayEquals(POINTEE_DATA, data);
   }

   @Test
   public void testPointerThenPointeeToDifferentBuffers() throws Exception {
      ByteBuffer buffer1 = ByteBuffer.allocate(128);
      Arrays.fill(buffer1.array(), BUFFER_BACKGROUND);
      BufferedPositionGroup posGroup1 = BufferedPositionGroup.create();

      ByteBuffer buffer2 = ByteBuffer.allocate(128);
      Arrays.fill(buffer2.array(), BUFFER_BACKGROUND);
      BufferedPositionGroup posGroup2 = BufferedPositionGroup.create();

      // Use an offset field group to manage offsets written in buffer1 but
      // pointing to data in buffer2
      // (In general, a separate field group is needed for each combination of
      // when the field offset will be known and when the data offset will be
      // known.)
      TiffOffsetFieldGroup fieldGroup12 = TiffOffsetFieldGroup.create();

      // First we write a placeholder for the pointer
      TiffOffsetField field = TiffOffsetField.create("test");
      fieldGroup12.add(field);
      buffer1.position(32);
      field.write(buffer1, posGroup1);
      buffer1.rewind();

      // Next, we write the pointee data
      field.setOffsetValue(posGroup2.positionInBuffer(32));
      buffer2.position(field.getOffsetValue().getPositionInBuffer());
      buffer2.put(POINTEE_DATA);
      buffer2.rewind();

      // Let's suppose we are able to determine the start offsets of both
      // buffers before writing either of them
      long bufferStart1 = 128;
      long bufferStart2 = 512;

      // Thus we can update the pointer in buffer1
      posGroup1.setBufferFileOffset(bufferStart1);
      posGroup2.setBufferFileOffset(bufferStart2);
      fieldGroup12.updateAll(buffer1);

      // And write both buffers to disk
      try (AsynchronousFileChannel chan = AsynchronousFileChannel.open(file_,
         StandardOpenOption.WRITE)) {
         chan.write(buffer1, bufferStart1).get();
         chan.write(buffer2, bufferStart2).get();
      }

      ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file_));
      b.position(128 + 32);
      assertEquals(512 + 32, b.asIntBuffer().get());
      b.position(512 + 32);
      byte[] data = new byte[POINTEE_DATA.length];
      b.get(data);
      assertArrayEquals(POINTEE_DATA, data);
   }
}
