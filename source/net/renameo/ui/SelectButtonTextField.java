package net.renameo.ui;

import static javax.swing.BorderFactory.*;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;

import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SelectButton;
import net.renameo.util.ui.SwingUI;
import net.miginfocom.swing.MigLayout;

public class SelectButtonTextField<T> extends JComponent {

	private SelectButton<T> selectButton = new SelectButton<T>();

	private JComboBox<Object> editor = new JComboBox<Object>() {

		@Override
		public void updateUI() {
			// keep the custom text-field style even when the look-and-feel
			// (re)installs its own default combobox UI during realization
			super.updateUI();
			setUI(new TextFieldComboBoxUI(selectButton));
		}
	};

	public SelectButtonTextField() {
		selectButton.addActionListener(textFieldFocusOnClick);

		editor.setBorder(BorderFactory.createEmptyBorder());
		editor.setOpaque(false);

		// one rounded field: source button, divider, text
		setLayout(new MigLayout("insets 1 2 1 8, nogrid, novisualpadding, fill", "", "[center]"));
		add(selectButton, "h 32!, w 44!");
		add(editor, "gap 6, w 260px!, h 30!");

		addFocusListenerDeep(editor);

		// the look and feel reinstalls its own text field UI (with a second border) whenever the theme changes
		Modern.onThemeChange(this, () -> {
			java.awt.Component c = editor.getEditor().getEditorComponent();
			if (c instanceof JTextComponent) {
				JTextComponent field = (JTextComponent) c;
				field.setUI(new javax.swing.plaf.basic.BasicTextFieldUI());
				field.setOpaque(false);
				field.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
				field.setForeground(NightTheme.getForeground());
				field.setCaretColor(NightTheme.getForeground());
				field.setSelectionColor(Modern.alpha(NightTheme.getAccent(), 110));
				field.setFont(Modern.font(13f, java.awt.Font.PLAIN));
			}
			editor.setBorder(BorderFactory.createEmptyBorder());
			editor.setOpaque(false);
		});

		editor.setPrototypeDisplayValue("X");
		editor.setRenderer(new CompletionCellRenderer());
		editor.setMaximumRowCount(10);

		SwingUI.installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.CTRL_DOWN_MASK), new SpinClientAction(-1));
		SwingUI.installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.CTRL_DOWN_MASK), new SpinClientAction(1));
	}

	private boolean focused = false;

	private void addFocusListenerDeep(JComboBox<?> combo) {
		java.awt.event.FocusAdapter repaint = new java.awt.event.FocusAdapter() {

			@Override
			public void focusGained(FocusEvent e) {
				focused = true;
				repaint();
			}

			@Override
			public void focusLost(FocusEvent e) {
				focused = false;
				repaint();
			}
		};
		combo.addFocusListener(repaint);
		if (combo.getEditor().getEditorComponent() != null) {
			combo.getEditor().getEditorComponent().addFocusListener(repaint);
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = Modern.smooth(g);
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 12, 12);
		g2d.setColor(NightTheme.getFieldBackground());
		g2d.fill(shape);
		g2d.setColor(focused ? NightTheme.getAccent() : NightTheme.getBorder());
		g2d.draw(shape);
		g2d.setColor(NightTheme.getBorder());
		int x = selectButton.getX() + selectButton.getWidth() + 2;
		g2d.fillRect(x, 7, 1, getHeight() - 14);
		g2d.dispose();
	}

	private TextFieldComboBoxUI ui() {
		if (editor.getUI() instanceof TextFieldComboBoxUI) {
			return (TextFieldComboBoxUI) editor.getUI();
		}
		return null;
	}

	public String getText() {
		TextFieldComboBoxUI ui = ui();
		if (ui != null) {
			return ui.getEditor().getText();
		}
		Object item = editor.getEditor().getItem();
		return item == null ? "" : item.toString();
	}

	public int getSelectionStart() {
		TextFieldComboBoxUI ui = ui();
		if (ui != null) {
			return ui.getEditor().getSelectionStart();
		}
		return getText().length();
	}

	public JComboBox getEditor() {
		return editor;
	}

	public SelectButton<T> getSelectButton() {
		return selectButton;
	}

	private final ActionListener textFieldFocusOnClick = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			getEditor().requestFocus();
		}

	};

	private class SpinClientAction extends AbstractAction {

		private int spin;

		public SpinClientAction(int spin) {
			super(String.format("Spin%+d", spin));
			this.spin = spin;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			selectButton.spinValue(spin);
		}
	}

	private class CompletionCellRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			setBorder(new EmptyBorder(1, 4, 1, 4));

			String highlightText = SelectButtonTextField.this.getText().substring(0, Math.min(SelectButtonTextField.this.getSelectionStart(), SelectButtonTextField.this.getText().length()));

			// highlight the matching sequence
			Matcher matcher = Pattern.compile(highlightText, Pattern.LITERAL | Pattern.CASE_INSENSITIVE).matcher(value.toString());

			// use no-break, because we really don't want line-wrapping in our table cells
			StringBuffer htmlText = new StringBuffer("<html><nobr>");
			if (matcher.find()) {
				if (isSelected) {
					matcher.appendReplacement(htmlText, "<span style='font-weight: bold;'>$0</span>");
				} else {
					matcher.appendReplacement(htmlText, "<span style='color: " + SwingUI.toHex(list.getSelectionBackground()) + "; font-weight: bold;'>$0</span>");
				}
			}
			matcher.appendTail(htmlText);
			htmlText.append("</nobr></html>");

			setText(htmlText.toString());
			return this;
		}
	}

	private static class TextFieldComboBoxUI extends BasicComboBoxUI {

		private SelectButton<?> button;

		public TextFieldComboBoxUI(SelectButton<?> button) {
			this.button = button;
		}

		@Override
		protected void installDefaults() {
			super.installDefaults();
			squareButton = false; // the completion list opens while typing, no arrow button needed
			comboBox.setOpaque(false);
		}

		@Override
		protected JButton createArrowButton() {
			JButton hidden = new JButton();
			hidden.setPreferredSize(new Dimension(0, 0));
			hidden.setBorder(createEmptyBorder());
			hidden.setVisible(false);
			return hidden;
		}

		@Override
		public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
			// transparent, the surrounding field paints the background
		}

		@Override
		protected void configureEditor() {
			JTextComponent editor = getEditor();

			// plain text field UI: the look and feel painter would draw a second border inside the field
			editor.setUI(new javax.swing.plaf.basic.BasicTextFieldUI());
			editor.setOpaque(false);
			editor.setForeground(NightTheme.getForeground());
			editor.setCaretColor(NightTheme.getForeground());
			editor.setSelectionColor(Modern.alpha(NightTheme.getAccent(), 110));
			editor.setSelectedTextColor(NightTheme.getForeground());
			editor.setEnabled(comboBox.isEnabled());
			editor.setFocusable(comboBox.isFocusable());
			editor.setFont(Modern.font(13f, java.awt.Font.PLAIN));
			editor.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));

			editor.addFocusListener(createFocusListener());

			editor.getDocument().addDocumentListener(new DocumentListener() {

				@Override
				public void changedUpdate(DocumentEvent e) {
					popup.getList().repaint();
				}

				@Override
				public void insertUpdate(DocumentEvent e) {
					popup.getList().repaint();
				}

				@Override
				public void removeUpdate(DocumentEvent e) {
					popup.getList().repaint();
				}

			});

			// massive performance boost for list rendering is cell height is fixed
			popup.getList().setPrototypeCellValue("X");
			popup.getList().setBackground(NightTheme.getCardHover());
			popup.getList().setForeground(NightTheme.getForeground());
			popup.getList().setSelectionBackground(Modern.alpha(NightTheme.getAccent(), 90));
			popup.getList().setSelectionForeground(NightTheme.getForeground());
		}

		public JTextComponent getEditor() {
			return (JTextComponent) editor;
		}

		@Override
		protected ComboPopup createPopup() {
			return new BasicComboPopup(comboBox) {

				@Override
				public void show(Component invoker, int x, int y) {
					super.show(invoker, x - button.getWidth(), y);
				}

				@Override
				protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
					Rectangle bounds = super.computePopupBounds(px, py, pw, ph);
					bounds.width += button.getWidth();

					return bounds;
				}
			};
		}

		@Override
		protected FocusListener createFocusListener() {
			return new FocusHandler() {

				/**
				 * Prevent action events from being fired on focusLost.
				 */
				@Override
				public void focusLost(FocusEvent e) {
					if (isPopupVisible(comboBox)) {
						setPopupVisible(comboBox, false);
					}
				}
			};
		}

	}

}
