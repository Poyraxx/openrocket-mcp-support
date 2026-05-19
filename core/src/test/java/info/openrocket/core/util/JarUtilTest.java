package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class JarUtilTest {

	@Test
	public void urlToFileHandlesStandardFileUrls() throws Exception {
		Path temp = Files.createTempFile("jarutil", ".txt");
		try {
			URL url = temp.toUri().toURL();
			File converted = JarUtil.urlToFile(url);
			assertEquals(temp.toFile(), converted);
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	@Test
	public void urlToFileHandlesUnescapedCharacters() throws MalformedURLException {
		String tempDir = System.getProperty("java.io.tmpdir");
		String path = tempDir + "jar util/test%zz";
		URL broken = new URL("file:" + path);

		File converted = JarUtil.urlToFile(broken);
		assertEquals(new File(path), converted);
	}

	@Test
	public void urlToFileRejectsUnsupportedProtocols() throws MalformedURLException {
		assertThrows(IllegalArgumentException.class, () -> JarUtil.urlToFile(new URL("http://example.com/test")));
	}
}
