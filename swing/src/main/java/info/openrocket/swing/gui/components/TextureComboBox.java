package info.openrocket.swing.gui.components;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;

import info.openrocket.core.appearance.DecalImage;
import info.openrocket.swing.gui.adaptors.DecalModel;
import info.openrocket.swing.gui.adaptors.DecalModel.DecalPreviewData;

/**
 * Combo box for selecting decals. Adds hover previews on top of the underlying
 * DecalModel behaviour.
 */
public class TextureComboBox extends JComboBox<DecalImage> {
	private static final long serialVersionUID = 2493299948337060422L;

    private final DecalModel decalModel;
    private final DecalPreviewPopup previewPopup = new DecalPreviewPopup();
    private DecalImage previousSelection;
    private JList<?> popupList;

    private final MouseAdapter hoverListener = new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
            handleListHover(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            previewPopup.hidePopup();
        }
    };

    private final MouseAdapter comboHoverListener = new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
            if (!isPopupVisible()) {
                showSelectedPreview();
            }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            if (!isPopupVisible()) {
                showSelectedPreview();
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (!isPopupVisible()) {
                previewPopup.hidePopup();
            }
        }
    };

	public TextureComboBox(DecalModel decalModel) {
		super(decalModel);
		this.decalModel = decalModel;
        setMaximumRowCount(20);
        previousSelection = (DecalImage) decalModel.getSelectedItem();
        addActionListener(this::handleSelectionChange);
        addMouseMotionListener(comboHoverListener);
        addMouseListener(comboHoverListener);
		addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				installPopupListeners(e.getSource());
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
				previewPopup.hidePopup();
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
				previewPopup.hidePopup();
			}
		});
	}

	@Override
	public void removeNotify() {
		previewPopup.hidePopup();
		uninstallPopupListeners();
		super.removeNotify();
	}

	private void handleSelectionChange(ActionEvent e) {
		DecalModel model = (DecalModel) getModel();
		DecalImage decal = (DecalImage) getSelectedItem();

		if (decal == previousSelection) {
			model.setSelectedItem(decal);
		}
		previousSelection = decal;
	}

	private void installPopupListeners(Object popupSource) {
		JList<?> list = null;
		if (popupSource instanceof ComboPopup) {
			list = ((ComboPopup) popupSource).getList();
		}
		if (list == null) {
			list = resolvePopupList();
		}
		if (list == null) {
			return;
		}
		if (popupList == list) {
			return;
		}
		uninstallPopupListeners();
		popupList = list;
		popupList.addMouseMotionListener(hoverListener);
		popupList.addMouseListener(hoverListener);
	}

	private void uninstallPopupListeners() {
		if (popupList != null) {
			popupList.removeMouseMotionListener(hoverListener);
			popupList.removeMouseListener(hoverListener);
			popupList = null;
		}
	}

	private JList<?> resolvePopupList() {
		ComboBoxUI ui = getUI();
		if (ui == null) {
			return null;
		}
		Object popup = ui.getAccessibleChild(this, 0);
		if (popup instanceof ComboPopup) {
			return ((ComboPopup) popup).getList();
		}
		return null;
	}

    private void handleListHover(MouseEvent e) {
        if (!(e.getSource() instanceof JList<?> list)) {
            return;
        }
		int index = list.locationToIndex(e.getPoint());
		if (index < 0) {
			previewPopup.hidePopup();
			return;
		}
		Rectangle bounds = list.getCellBounds(index, index);
		if (bounds == null || !bounds.contains(e.getPoint())) {
			previewPopup.hidePopup();
			return;
		}
		Object item = list.getModel().getElementAt(index);
		if (!(item instanceof DecalImage)) {
			previewPopup.hidePopup();
			return;
		}
		DecalPreviewData previewData = decalModel.getPreviewData((DecalImage) item);
        Point popupLocation = new Point(bounds.x + bounds.width + 8, bounds.y);
        SwingUtilities.convertPointToScreen(popupLocation, list);
        previewPopup.show(list, popupLocation, previewData);
    }

    private void showSelectedPreview() {
        DecalPreviewData data = decalModel.getPreviewData((DecalImage) getSelectedItem());
        Point location = new Point(getWidth() + 8, 0);
        SwingUtilities.convertPointToScreen(location, this);
        previewPopup.show(this, location, data);
    }
}
