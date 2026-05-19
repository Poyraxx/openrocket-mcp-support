package info.openrocket.swing.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JTable;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.Test;

class TableRowTraversalPolicyTest {

    @Test
    void nextRowActionAdvancesSelectionAndResetsColumn() {
        JTable table = createTableWithPolicy();
        table.setRowSelectionInterval(0, 0);
        table.setColumnSelectionInterval(1, 1);

        invokeAction(table, "Action.NextRow");

        assertEquals(1, table.getSelectedRow());
        assertEquals(0, table.getColumnModel().getSelectionModel().getMinSelectionIndex());
    }

    @Test
    void nextRowActionDoesNotWrapWithoutCycle() {
        JTable table = createTableWithPolicy();
        table.setRowSelectionInterval(2, 2);

        invokeAction(table, "Action.NextRow");

        assertEquals(2, table.getSelectedRow());
    }

    @Test
    void nextRowCycleWrapsToTop() {
        JTable table = createTableWithPolicy();
        table.setRowSelectionInterval(2, 2);

        invokeAction(table, "Action.NextRowCycle");

        assertEquals(0, table.getSelectedRow());
    }

    @Test
    void previousRowActionDoesNotWrapWithoutCycle() {
        JTable table = createTableWithPolicy();
        table.setRowSelectionInterval(0, 0);

        invokeAction(table, "Action.PreviousRow");

        assertEquals(0, table.getSelectedRow());
    }

    @Test
    void previousRowActionMovesUpAndResetsColumn() {
        JTable table = createTableWithPolicy();
        table.setRowSelectionInterval(2, 2);
        table.setColumnSelectionInterval(1, 1);

        invokeAction(table, "Action.PreviousRow");

        assertEquals(1, table.getSelectedRow());
        assertEquals(0, table.getColumnModel().getSelectionModel().getMinSelectionIndex());
    }

    @Test
    void previousRowCycleWrapsToBottom() {
        JTable table = createTableWithPolicy();
        table.setRowSelectionInterval(0, 0);

        invokeAction(table, "Action.PreviousRowCycle");

        assertEquals(2, table.getSelectedRow());
    }

    @Test
    void installsExpectedKeyBindings() {
        JTable table = createTableWithPolicy();
        InputMap inputMap = table.getInputMap();

        assertEquals("Action.NextRowCycle", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)));
        assertEquals("Action.NextRow", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)));
        assertEquals("Action.PreviousRowCycle",
                inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)));
        assertEquals("Action.PreviousRow", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)));
    }

    private static JTable createTableWithPolicy() {
        JTable table = new JTable(3, 2);
        table.setCellSelectionEnabled(true);
        table.setColumnSelectionAllowed(true);
        table.setRowSelectionAllowed(true);
        TableRowTraversalPolicy.setTableRowTraversalPolicy(table);
        return table;
    }

    private static void invokeAction(JTable table, String actionKey) {
        Action action = table.getActionMap().get(actionKey);
        action.actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, actionKey));
    }
}
