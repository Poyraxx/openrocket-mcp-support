package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public class LimitedInputStreamTest {

	@Test
	public void readStopsAfterLimitUsingSingleByteReads() throws IOException {
		byte[] data = new byte[] { 5, 6, 7, 8 };
		LimitedInputStream in = new LimitedInputStream(new ByteArrayInputStream(data), 3);

		assertEquals(3, in.available());
		assertEquals(5, in.read());
		assertEquals(6, in.read());
		assertEquals(1, in.available());
		assertEquals(7, in.read());
		assertEquals(-1, in.read());
		assertEquals(0, in.available());
	}

	@Test
	public void readHonorsLimitForBufferedReads() throws IOException {
		byte[] data = new byte[] { 1, 2, 3, 4, 5 };
		LimitedInputStream in = new LimitedInputStream(new ByteArrayInputStream(data), 4);

		byte[] buffer = new byte[10];
		int read = in.read(buffer, 0, buffer.length);
		assertEquals(4, read);
		assertEquals(1, buffer[0]);
		assertEquals(4, buffer[3]);
		assertEquals(-1, in.read(buffer, 0, buffer.length));
	}

	@Test
	public void skipConsumesRemainingBytesUpToLimit() throws IOException {
		byte[] data = new byte[] { 0, 1, 2, 3, 4 };
		LimitedInputStream in = new LimitedInputStream(new ByteArrayInputStream(data), 5);

		assertEquals(5, in.skip(10));
		assertEquals(-1, in.read());
	}

	@Test
	public void markAndResetAreDisabled() throws IOException {
		LimitedInputStream in = new LimitedInputStream(new ByteArrayInputStream(new byte[] { 1 }), 1);

		assertFalse(in.markSupported());
		assertThrows(IOException.class, in::reset);
	}
}
