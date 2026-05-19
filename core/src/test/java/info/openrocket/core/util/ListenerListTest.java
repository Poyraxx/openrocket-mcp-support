package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

class ListenerListTest {

	@Test
	void addListenerRejectsDuplicatesByIdentity() {
		ListenerList<Runnable> listeners = new ListenerList<>();
		Runnable listener = () -> {};

		assertTrue(listeners.addListener(listener));
		assertFalse(listeners.addListener(listener));
		assertEquals(1, listeners.getListenerCount());
		assertThrows(NullPointerException.class, () -> listeners.addListener(null));
	}

	@Test
	void removeListenerOperatesOnObjectIdentity() {
		ListenerList<Runnable> listeners = new ListenerList<>();
		Runnable listener1 = () -> {};
		Runnable listener2 = () -> {};
		Runnable unmatched = () -> {};

		listeners.addListener(listener1);
		listeners.addListener(listener2);

		assertTrue(listeners.removeListener(listener1));
		assertFalse(listeners.removeListener(listener1));
		assertFalse(listeners.removeListener(unmatched));
		assertEquals(1, listeners.getListenerCount());
	}

	@Test
	void iteratorProvidesSnapshotAndDoesNotSupportRemove() {
		ListenerList<Runnable> listeners = new ListenerList<>();
		Runnable listener1 = () -> {};
		Runnable listener2 = () -> {};
		Runnable late = () -> {};

		listeners.addListener(listener1);
		listeners.addListener(listener2);

		Iterator<Runnable> iterator = listeners.iterator();
		listeners.addListener(late);

		List<Runnable> iterated = new ArrayList<>();
		while (iterator.hasNext()) {
			iterated.add(iterator.next());
		}

		assertEquals(List.of(listener1, listener2), iterated);
		assertThrows(UnsupportedOperationException.class, iterator::remove);
	}

	@Test
	void invalidationPreventsFurtherAddsAndClearsListeners() {
		ListenerList<Runnable> listeners = new ListenerList<>();
		Runnable listener = () -> {};
		Runnable other = () -> {};

		listeners.addListener(listener);
		assertFalse(listeners.isInvalidated());

		listeners.invalidateMe();
		assertTrue(listeners.isInvalidated());

		assertThrows(BugException.class, () -> listeners.addListener(other));
		assertFalse(listeners.removeListener(listener));

		Iterator<Runnable> iterator = listeners.iterator();
		assertFalse(iterator.hasNext());
	}

	@Test
	void instantiationLocationCapturesStackTrace() {
		ListenerList<Runnable> listeners = new ListenerList<>();
		assertNotNull(listeners.getInstantiationLocation());
	}
}
