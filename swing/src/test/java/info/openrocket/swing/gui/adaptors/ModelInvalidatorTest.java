package info.openrocket.swing.gui.adaptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import info.openrocket.core.util.ChangeSource;
import info.openrocket.core.util.StateChangeListener;
import java.util.EventObject;
import org.junit.jupiter.api.Test;

class ModelInvalidatorTest {

    private static class StubChangeSource implements ChangeSource {
        StateChangeListener removedListener;
        int removeCount;

        @Override
        public void addChangeListener(StateChangeListener listener) {
            // no-op
        }

        @Override
        public void removeChangeListener(StateChangeListener listener) {
            removedListener = listener;
            removeCount++;
        }
    }

    private static class StubListener implements StateChangeListener {
        @Override
        public void stateChanged(EventObject e) {
            // no-op
        }
    }

    @Test
    void addAndRemoveChangeListenerUpdateInternalList() {
        StubChangeSource source = new StubChangeSource();
        StubListener model = new StubListener();
        ModelInvalidator invalidator = new ModelInvalidator(source, model);
        StubListener listener = new StubListener();

        invalidator.addChangeListener(listener);

        assertEquals(1, invalidator.listeners.size());

        invalidator.removeChangeListener(listener);

        assertEquals(0, invalidator.listeners.size());
    }

    @Test
    void invalidateClearsListenersAndNotifiesSource() {
        StubChangeSource source = new StubChangeSource();
        StubListener model = new StubListener();
        ModelInvalidator invalidator = new ModelInvalidator(source, model);

        invalidator.addChangeListener(new StubListener());
		assertEquals(1, invalidator.listeners.size());
        invalidator.invalidateMe();

        assertEquals(0, invalidator.listeners.size());
        assertSame(model, source.removedListener);
        assertEquals(1, source.removeCount);
    }

    @Test
    void repeatedInvalidationDoesNotReRegisterListeners() {
        StubChangeSource source = new StubChangeSource();
        StubListener model = new StubListener();
        ModelInvalidator invalidator = new ModelInvalidator(source, model);

        invalidator.invalidateMe();
        invalidator.invalidateMe();

        assertEquals(0, invalidator.listeners.size());
        assertEquals(2, source.removeCount);
    }
}
