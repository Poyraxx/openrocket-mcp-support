package info.openrocket.swing.gui.main;

import info.openrocket.core.appearance.DecalImage;
import info.openrocket.core.util.DecalNotFoundException;
import info.openrocket.core.util.StateChangeListener;
import info.openrocket.swing.util.BaseTestCase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportDecalSelectionDialogTest extends BaseTestCase {

	@Test
	void initializesWithAllDecalsSelectedAndShowsPreviewIcon() throws Exception {
		StubDecalImage decal = new StubDecalImage("decals/alpha.png", createPngBytes(Color.RED), false);

		runWithDialog(List.of(decal), dialog -> {
			assertThat(dialog.getSelectedDecals()).containsExactly(decal);
			JLabel preview = extractPreviewLabel(dialog);
			assertThat(preview.getIcon()).isNotNull();
			assertThat(preview.getText()).isNull();
		});
	}

	@Test
	void previewFallsBackWhenImageBytesCannotBeRead() throws Exception {
		StubDecalImage decal = new StubDecalImage("decals/broken.png", null, true);

		runWithDialog(List.of(decal), dialog -> {
			JLabel preview = extractPreviewLabel(dialog);
			assertThat(preview.getIcon()).isNull();
			assertThat(preview.getText()).contains("ExportDecalSelectionDialog.preview.unavailable");
		});
	}

	@Test
	void selectNoneAndSelectAllButtonsUpdateTheSelectionModel() throws Exception {
		List<DecalImage> decals = Arrays.asList(
				new StubDecalImage("decals/alpha.png", createPngBytes(Color.BLUE), false),
				new StubDecalImage("decals/beta.png", createPngBytes(Color.GREEN), false));

		runWithDialog(decals, dialog -> {
			JButton selectNone = findButton(dialog.getContentPane(),
					"ExportDecalSelectionDialog.button.selectNone");
			JButton selectAll = findButton(dialog.getContentPane(),
					"ExportDecalSelectionDialog.button.selectAll");

			assertThat(selectNone).as("select none button").isNotNull();
			assertThat(selectAll).as("select all button").isNotNull();

			selectNone.doClick();
			assertThat(dialog.getSelectedDecals()).isEmpty();

			selectAll.doClick();
			assertThat(dialog.getSelectedDecals()).containsExactlyElementsOf(decals);
		});
	}

	private void runWithDialog(List<DecalImage> decals, java.util.function.Consumer<ExportDecalSelectionDialog> consumer)
			throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
				"Headless environment cannot construct Swing dialogs");
		SwingUtilities.invokeAndWait(() -> {
			ExportDecalSelectionDialog dialog = new ExportDecalSelectionDialog(null, decals);
			try {
				consumer.accept(dialog);
			} finally {
				dialog.dispose();
			}
		});
	}

	private JLabel extractPreviewLabel(ExportDecalSelectionDialog dialog) {
		try {
			var field = ExportDecalSelectionDialog.class.getDeclaredField("previewLabel");
			field.setAccessible(true);
			return (JLabel) field.get(dialog);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to access preview label", e);
		}
	}

	private JButton findButton(Container container, String keyFragment) {
		for (Component component : container.getComponents()) {
			if (component instanceof JButton) {
				JButton button = (JButton) component;
				String text = button.getText();
				if (text != null && (text.equals(keyFragment) || text.contains(keyFragment))) {
					return button;
				}
			}
			if (component instanceof Container) {
				JButton nested = findButton((Container) component, keyFragment);
				if (nested != null) {
					return nested;
				}
			}
		}
		return null;
	}

	private static byte[] createPngBytes(Color fillColor) throws IOException {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(fillColor);
			graphics.fillRect(0, 0, 2, 2);
		} finally {
			graphics.dispose();
		}
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", output);
			return output.toByteArray();
		}
	}

	private static final class StubDecalImage implements DecalImage {
		private final String name;
		private final byte[] bytes;
		private final boolean failOnRead;
		private File decalFile;
		private boolean ignored;

		private StubDecalImage(String name, byte[] bytes, boolean failOnRead) {
			this.name = name;
			this.bytes = bytes;
			this.failOnRead = failOnRead;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public InputStream getBytes() throws IOException, DecalNotFoundException {
			if (failOnRead) {
				throw new IOException("Unable to read decal");
			}
			return new ByteArrayInputStream(bytes);
		}

		@Override
		public void exportImage(File file) throws IOException, DecalNotFoundException {
			throw new UnsupportedOperationException("Not required for this test");
		}

		@Override
		public void fireChangeEvent(Object source) {
			// no-op for tests
		}

		@Override
		public File getDecalFile() {
			return decalFile;
		}

		@Override
		public void setDecalFile(File file) {
			this.decalFile = file;
		}

		@Override
		public boolean isIgnored() {
			return ignored;
		}

		@Override
		public void setIgnored(boolean ignored) {
			this.ignored = ignored;
		}

		@Override
		public int compareTo(DecalImage o) {
			return this.getName().compareTo(o.getName());
		}

		@Override
		public void addChangeListener(StateChangeListener listener) {
			// no-op
		}

		@Override
		public void removeChangeListener(StateChangeListener listener) {
			// no-op
		}
	}
}
