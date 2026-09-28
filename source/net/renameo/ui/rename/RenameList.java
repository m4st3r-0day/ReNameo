package net.renameo.ui.rename;

import static java.util.Collections.*;
import static javax.swing.BorderFactory.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

import ca.odell.glazedlists.EventList;
import net.renameo.ResourceManager;
import net.renameo.ui.ReNameoList;
import net.renameo.ui.transfer.LoadAction;
import net.renameo.util.ui.ActionPopup;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.miginfocom.swing.MigLayout;

class RenameList<E> extends ReNameoList<E> {

	private JPanel buttonPanel;

	public RenameList(EventList<E> model) {
		// replace default model with given model
		setModel(model);

		// disable multi-selection for the sake of simplicity
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// need a fixed cell size for high performance scrolling
		list.setFixedCellHeight(NightTheme.isCompact() ? 40 : 56);

		list.addMouseListener(dndReorderMouseAdapter);
		list.addMouseMotionListener(dndReorderMouseAdapter);

		getRemoveAction().setEnabled(true);

		// the enclosing card draws title and frame
		setBorder(createEmptyBorder());
		listScrollPane.setBorder(createEmptyBorder());
		Modern.onThemeChange(this, () -> {
			list.setFixedCellHeight(NightTheme.isCompact() ? 40 : 56);
			// rows always span the visible width and ellipsize long names instead of growing the list
			list.setFixedCellWidth(10);
			list.setBackground(NightTheme.getCardBackground());
			list.setOpaque(false);
			// basic UI: the look and feel would paint its own frame inside the card
			listScrollPane.setUI(new javax.swing.plaf.basic.BasicScrollPaneUI());
			listScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			listScrollPane.setOpaque(false);
			listScrollPane.getViewport().setOpaque(false);
			listScrollPane.setBorder(createEmptyBorder());
			setBorder(createEmptyBorder());
		});
		Modern.slim(listScrollPane);

		buttonPanel = new JPanel(new MigLayout("insets 0, nogrid, novisualpadding, gap 2", "align right"));
		buttonPanel.setOpaque(false);
		buttonPanel.add(Modern.iconButton(upAction, Glyph.Shape.ARROW_UP, null));
		buttonPanel.add(Modern.iconButton(downAction, Glyph.Shape.ARROW_DOWN, null));
		buttonPanel.add(createLoadButton());
	}

	public JPanel getButtonPanel() {
		return buttonPanel;
	}

	public LoadAction getLoadAction() {
		return loadAction;
	}

	private JButton createLoadButton() {
		ActionPopup actionPopup = new ActionPopup("Load Files", ResourceManager.getIcon("action.load"));

		actionPopup.add(newAction("Select Folders", ResourceManager.getIcon("tree.closed"), evt -> {
			loadAction.actionPerformed(new ActionEvent(evt.getSource(), evt.getID(), evt.getActionCommand(), 0));
		}));

		actionPopup.add(newAction("Select Files", ResourceManager.getIcon("file.generic"), evt -> {
			loadAction.actionPerformed(new ActionEvent(evt.getSource(), evt.getID(), evt.getActionCommand(), ActionEvent.SHIFT_MASK));
		}));

		JButton button = Modern.iconButton(loadAction, Glyph.Shape.FOLDER, "Load files (right click for options)");
		button.setComponentPopupMenu(actionPopup);
		return button;
	}

	private final LoadAction loadAction = new LoadAction(this::getTransferablePolicy);

	private final Action upAction = newAction("Align Up", ResourceManager.getIcon("action.up"), evt -> {
		int index = getListComponent().getSelectedIndex();

		if (index > 0) {
			swap(model, index, index - 1);
			getListComponent().setSelectedIndex(index - 1);
		}
	});

	private final Action downAction = newAction("Align Down", ResourceManager.getIcon("action.down"), evt -> {
		int index = getListComponent().getSelectedIndex();

		if (index < model.size() - 1) {
			swap(model, index, index + 1);
			getListComponent().setSelectedIndex(index + 1);
		}
	});

	private final MouseAdapter dndReorderMouseAdapter = new MouseAdapter() {

		private int lastIndex = -1;

		@Override
		public void mousePressed(MouseEvent m) {
			lastIndex = getListComponent().getSelectedIndex();
		}

		@Override
		public void mouseDragged(MouseEvent m) {
			int currentIndex = getListComponent().getSelectedIndex();

			if (currentIndex != lastIndex && lastIndex >= 0 && currentIndex >= 0) {
				swap(model, lastIndex, currentIndex);
				lastIndex = currentIndex;
			}
		}
	};

}
