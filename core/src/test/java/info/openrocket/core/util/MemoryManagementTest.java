package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.util.List;

import org.junit.jupiter.api.Test;

class MemoryManagementTest {

	@Test
	void collectableRejectsNullObjects() {
		assertThrows(IllegalArgumentException.class, () -> MemoryManagement.collectable(null));
	}

	@Test
	void collectableEntriesDisappearWhenNoLongerReferenced() throws InterruptedException {
		int baseline = MemoryManagement.getRemainingCollectableObjects().size();

		Object tracked = new Object();
		WeakReference<Object> ref = new WeakReference<>(tracked);

		MemoryManagement.collectable(tracked);
		List<MemoryManagement.MemoryData> withStrongRef = MemoryManagement.getRemainingCollectableObjects();
		assertTrue(withStrongRef.size() >= baseline);
		boolean found = false;
		for (MemoryManagement.MemoryData data : withStrongRef) {
			if (data.getReference().get() == tracked) {
				found = true;
				break;
			}
		}
		assertTrue(found);

		tracked = null;
		awaitCleared(ref);

		List<MemoryManagement.MemoryData> afterRelease = MemoryManagement.getRemainingCollectableObjects();
		assertTrue(afterRelease.size() <= baseline);
	}

	@Test
	void listenerListsAreReleasedAfterGarbageCollection() throws InterruptedException {
		int baseline = MemoryManagement.getRemainingListenerLists().size();

		ListenerList<Object> listeners = new ListenerList<>();
		WeakReference<ListenerList<?>> ref = new WeakReference<>(listeners);

		MemoryManagement.registerListenerList(listeners);
		assertTrue(MemoryManagement.getRemainingListenerLists().contains(listeners));

		listeners = null;
		awaitCleared(ref);

		List<ListenerList<?>> remaining = MemoryManagement.getRemainingListenerLists();
		assertTrue(remaining.size() <= baseline);
		assertFalse(remaining.contains(null));
	}

	private void awaitCleared(WeakReference<?> ref) throws InterruptedException {
		for (int i = 0; i < 50 && ref.get() != null; i++) {
			System.gc();
			System.runFinalization();
			Thread.sleep(5);
		}
		if (ref.get() != null) {
			throw new AssertionError("Garbage collector did not clear reference within allotted attempts");
		}
	}
}
