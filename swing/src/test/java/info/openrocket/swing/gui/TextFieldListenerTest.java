package info.openrocket.swing.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.util.Arrays;

import javax.swing.JTextField;

import org.junit.jupiter.api.Test;

class TextFieldListenerTest {

    private static class RecordingListener extends TextFieldListener {
        String lastValue;

        @Override
        public void setText(String text) {
            lastValue = text;
        }
    }

    @Test
    void actionPerformedPassesCurrentText() {
        JTextField field = new JTextField("initial");
        RecordingListener listener = new RecordingListener();

        listener.listenTo(field);
        field.setText("trigger");
        listener.actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, "cmd"));

        assertEquals("trigger", listener.lastValue);
        assertTrue(Arrays.asList(field.getActionListeners()).contains(listener));
        assertTrue(Arrays.asList(field.getFocusListeners()).contains(listener));
    }

    @Test
    void focusLostAlsoPropagatesText() {
        JTextField field = new JTextField("focus");
        RecordingListener listener = new RecordingListener();

        listener.listenTo(field);
        field.setText("blurred");
        listener.focusLost(new FocusEvent(field, FocusEvent.FOCUS_LOST));

        assertEquals("blurred", listener.lastValue);
    }

	@Test
	void switchingFieldsRemovesListenersFromPreviousField() {
		JTextField first = new JTextField("first");
		JTextField second = new JTextField("second");
		RecordingListener listener = new RecordingListener();

		listener.listenTo(first);
		listener.listenTo(second);

		assertFalse(Arrays.asList(first.getActionListeners()).contains(listener));
		assertFalse(Arrays.asList(first.getFocusListeners()).contains(listener));
		assertTrue(Arrays.asList(second.getActionListeners()).contains(listener));
		assertTrue(Arrays.asList(second.getFocusListeners()).contains(listener));
	}

	@Test
	void focusGainedDoesNotMutateState() {
		JTextField field = new JTextField("value");
		RecordingListener listener = new RecordingListener();
		listener.listenTo(field);
		listener.lastValue = "existing";

		listener.focusGained(new FocusEvent(field, FocusEvent.FOCUS_GAINED));

		assertEquals("existing", listener.lastValue);
	}

	@Test
	void listenToNullDetachesListeners() {
		JTextField field = new JTextField("value");
		RecordingListener listener = new RecordingListener();

		listener.listenTo(field);
		listener.listenTo(null);

		assertFalse(Arrays.asList(field.getActionListeners()).contains(listener));
		assertFalse(Arrays.asList(field.getFocusListeners()).contains(listener));
	}
}
