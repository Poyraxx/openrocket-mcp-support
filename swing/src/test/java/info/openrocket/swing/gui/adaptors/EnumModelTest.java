package info.openrocket.swing.gui.adaptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EventObject;

import org.junit.jupiter.api.Test;

class EnumModelTest {

    public enum SampleMode {
        ALPHA,
        BETA,
        GAMMA
    }

    public static class SampleSource {
        private SampleMode mode = SampleMode.ALPHA;
        private int setCount;

        public SampleMode getMode() {
            return mode;
        }

        public void setMode(SampleMode mode) {
            this.mode = mode;
            setCount++;
        }

        void primeMode(SampleMode mode) {
            this.mode = mode;
        }

        int getSetCount() {
            return setCount;
        }
    }

    @Test
    void selectedItemReflectsUnderlyingValue() {
        SampleSource source = new SampleSource();
        source.primeMode(SampleMode.BETA);
        EnumModel<SampleMode> model = new EnumModel<>(source, "Mode");

        assertEquals(SampleMode.BETA, model.getSelectedItem());
        assertEquals(SampleMode.BETA, model.getElementAt(1));
    }

    @Test
    void settingItemUpdatesSourceAndAvoidsDuplicateWrites() {
        SampleSource source = new SampleSource();
        EnumModel<SampleMode> model = new EnumModel<>(source, "Mode");
        int initialSetCount = source.getSetCount();

        model.setSelectedItem(SampleMode.GAMMA);

        assertEquals(SampleMode.GAMMA, source.getMode());
        assertEquals(SampleMode.GAMMA, model.getSelectedItem());

        model.setSelectedItem(SampleMode.GAMMA); // no-op
        assertEquals(initialSetCount + 1, source.getSetCount());
    }

    @Test
    void stringSelectionClearsValueAndShowsNullText() {
        SampleSource source = new SampleSource();
        EnumModel<SampleMode> model =
                new EnumModel<>(source, "Mode", SampleMode.values(), "<none>");

        model.setSelectedItem("<none>");
        model.stateChanged(new EventObject(this));

        assertNull(source.getMode());
        assertEquals("<none>", model.getSelectedItem());
    }

    @Test
	void removingElementsShrinksDisplayedValues() {
		SampleSource source = new SampleSource();
		EnumModel<SampleMode> model = new EnumModel<>(source, "Mode");
		int originalSize = model.getSize();

		model.removeElementAt(0);

		assertEquals(originalSize - 1, model.getSize());
		assertNull(model.getElementAt(originalSize));
	}

	@Test
	void setSelectedItemRejectsNonEnumValues() {
		SampleSource source = new SampleSource();
		EnumModel<SampleMode> model = new EnumModel<>(source, "Mode");

		assertThrows(IllegalArgumentException.class, () -> model.setSelectedItem(42));
	}

	@Test
	void stateChangedReflectsExternalSourceUpdates() {
		SampleSource source = new SampleSource();
		EnumModel<SampleMode> model = new EnumModel<>(source, "Mode");

		source.primeMode(SampleMode.BETA);
		model.stateChanged(new EventObject(this));

		assertEquals(SampleMode.BETA, model.getSelectedItem());
	}

	@Test
	void removeElementBoundsAreGraceful() {
		SampleSource source = new SampleSource();
		EnumModel<SampleMode> model = new EnumModel<>(source, "Mode");
		int originalSize = model.getSize();

		model.removeElement(null);
		model.removeElementAt(-1);
		model.removeElementAt(originalSize);

		assertEquals(originalSize, model.getSize());
	}
}
