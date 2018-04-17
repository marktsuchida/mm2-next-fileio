package org.micromanager.data.internal.io.mmtiff;

import java.io.IOException;

class TiffFormatException extends IOException {
   TiffFormatException(String message) {
      super(message);
   }

   TiffFormatException(String message, Throwable cause) {
      super(message, cause);
   }
}
