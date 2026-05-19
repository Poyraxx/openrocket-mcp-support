package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import info.openrocket.core.rocketcomponent.ComponentChangeEvent;
import info.openrocket.core.rocketcomponent.RocketComponent;

public class ComponentChangeAdapterTest {

	@Test
	public void componentChangedDelegatesToStateChangeListener() {
		StateChangeListener listener = mock(StateChangeListener.class);
		ComponentChangeAdapter adapter = new ComponentChangeAdapter(listener);

		ComponentChangeEvent event = new ComponentChangeEvent(mock(RocketComponent.class), ComponentChangeEvent.MASS_CHANGE);
		adapter.componentChanged(event);

		verify(listener).stateChanged(event);
	}

	@Test
	public void equalsDependsOnWrappedListener() {
		StateChangeListener listener = mock(StateChangeListener.class);
		ComponentChangeAdapter first = new ComponentChangeAdapter(listener);
		ComponentChangeAdapter second = new ComponentChangeAdapter(listener);
		ComponentChangeAdapter different = new ComponentChangeAdapter(mock(StateChangeListener.class));

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
		assertNotEquals(first, different);
		assertNotEquals("component", first);
	}
}
