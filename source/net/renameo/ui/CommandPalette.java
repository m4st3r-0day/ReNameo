package net.renameo.ui;

import static javax.swing.BorderFactory.*;

import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JRootPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;

/**
 * ⌘K command palette: type to filter the commands of the current panel and the global ones, Enter runs the highlighted command. The last entry searches the file list instead.
 */
class CommandPalette {

	private static final int MAX_ROWS = 9;

	private final Modern.SearchField field;
	private final Supplier<List<Action>> commands;
	private final DefaultListModel<Action> model = new DefaultListModel<Action>();
	private final JList<Action> list = new JList<Action>(model);
	private final JComponent popup;

	CommandPalette(JRootPane root, Modern.SearchField field, Supplier<List<Action>> commands) {
		this.field = field;
		this.commands = commands;

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setFixedCellHeight(34);
		list.setOpaque(false);
		list.setCellRenderer(new DefaultListCellRenderer() {

			@Override
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				Action action = (Action) value;
				super.getListCellRendererComponent(l, action.getValue(Action.NAME), index, isSelected, false);
				setBorder(createEmptyBorder(0, 12, 0, 12));
				setFont(Modern.font(13f, isSelected ? Font.BOLD : Font.PLAIN));
				setIcon(Glyph.of(action instanceof SearchFiles ? Glyph.Shape.SEARCH : Glyph.Shape.COMMAND, 15, isSelected ? NightTheme::getForeground : NightTheme::getDimForeground));
				setIconTextGap(10);
				setOpaque(isSelected);
				setBackground(Modern.alpha(NightTheme.getAccent(), 70));
				setForeground(NightTheme.getForeground());
				return this;
			}
		});
		list.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {
				int index = list.locationToIndex(e.getPoint());
				if (index >= 0) {
					run(model.get(index));
				}
			}
		});

		popup = new JComponent() {

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = Modern.smooth(g);
				RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 14, 14);
				g2d.setColor(NightTheme.getCardHover());
				g2d.fill(shape);
				g2d.setColor(NightTheme.getBorder());
				g2d.draw(shape);
				g2d.dispose();
			}
		};
		popup.setLayout(new java.awt.BorderLayout());
		popup.setBorder(createEmptyBorder(6, 6, 6, 6));
		popup.add(list);
		popup.setVisible(false);
		root.getLayeredPane().add(popup, JLayeredPane.POPUP_LAYER);

		field.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				refresh();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				refresh();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
			}
		});
		field.addFocusListener(new FocusAdapter() {

			@Override
			public void focusGained(FocusEvent e) {
				refresh();
			}

			@Override
			public void focusLost(FocusEvent e) {
				SwingUtilities.invokeLater(() -> popup.setVisible(false));
			}
		});
		field.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(KeyEvent e) {
				if (!popup.isVisible()) {
					return;
				}
				int size = model.getSize();
				switch (e.getKeyCode()) {
				case KeyEvent.VK_DOWN:
					list.setSelectedIndex(Math.min(size - 1, list.getSelectedIndex() + 1));
					e.consume();
					break;
				case KeyEvent.VK_UP:
					list.setSelectedIndex(Math.max(0, list.getSelectedIndex() - 1));
					e.consume();
					break;
				case KeyEvent.VK_ENTER:
					if (list.getSelectedValue() != null) {
						run(list.getSelectedValue());
						e.consume();
					}
					break;
				case KeyEvent.VK_ESCAPE:
					popup.setVisible(false);
					break;
				}
			}
		});
	}

	private void refresh() {
		String query = field.getText().trim().toLowerCase(Locale.ROOT);
		model.clear();

		for (Action action : commands.get()) {
			if (action.isEnabled() && matches(String.valueOf(action.getValue(Action.NAME)), query)) {
				model.addElement(action);
				if (model.getSize() >= MAX_ROWS - 1) {
					break;
				}
			}
		}
		if (query.length() > 0) {
			model.addElement(new SearchFiles(field.getText().trim()));
		}

		if (model.isEmpty() || !field.hasFocus()) {
			popup.setVisible(false);
			return;
		}

		list.setSelectedIndex(0);
		Point p = SwingUtilities.convertPoint(field, 0, field.getHeight() + 6, popup.getParent());
		popup.setBounds(p.x, p.y, field.getWidth(), model.getSize() * 34 + 14);
		popup.setVisible(true);
		popup.revalidate();
		popup.repaint();
	}

	/**
	 * Every word of the query must appear in the command name, so "ren sel" finds "Rename Selected".
	 */
	private static boolean matches(String name, String query) {
		String n = name.toLowerCase(Locale.ROOT);
		for (String word : query.split("\\s+")) {
			if (!n.contains(word)) {
				return false;
			}
		}
		return true;
	}

	private void run(Action action) {
		popup.setVisible(false);
		if (!(action instanceof SearchFiles)) {
			field.setText("");
		}
		SwingUtilities.invokeLater(() -> action.actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, String.valueOf(action.getValue(Action.NAME)))));
	}

	private static class SearchFiles extends AbstractAction {

		private final String query;

		SearchFiles(String query) {
			super("Search files for “" + query + "”");
			this.query = query;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			SwingEventBus.getInstance().post(new AppEvents.Search(query, true));
		}
	}

}
