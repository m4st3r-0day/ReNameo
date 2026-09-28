package net.renameo.util.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import net.miginfocom.swing.MigLayout;

/**
 * Popup with a header, grouped actions and section captions, painted entirely from the theme colors (the look and feel popup painter would draw it grey).
 */
public class ActionPopup extends JPopupMenu {

	protected final JLabel headerLabel = new JLabel();
	protected final JLabel descriptionLabel = new JLabel();
	protected final JLabel statusLabel = new JLabel();

	protected final JPanel actionPanel = new JPanel(new MigLayout("nogrid, insets 0 0 4 0, fill"));

	public ActionPopup(String label, Icon icon) {
		headerLabel.setText(label);
		headerLabel.setIcon(icon);
		headerLabel.setIconTextGap(8);
		headerLabel.setOpaque(false);
		headerLabel.setFont(Modern.font(13f, Font.BOLD));

		actionPanel.setOpaque(false);

		statusLabel.setOpaque(false);
		statusLabel.setFont(Modern.font(10.5f, Font.PLAIN));
		statusLabel.setVisible(false);

		setLayout(new MigLayout("nogrid, fill, insets 0"));

		add(headerLabel, "gapx 12px 12px, gapy 10px 6px, wrap");
		add(new Divider(), "growx, h 1!, wrap 4px");
		add(actionPanel, "growx, wrap 0px");
		add(statusLabel, "growx, gapx 12px, gapy 0 6px, wrap, hidemode 3");

		// make it look better (e.g. window shadows) by forcing heavy-weight windows
		setLightWeightPopupEnabled(false);

		Modern.onThemeChange(this, this::applyTheme);
	}

	/**
	 * Colors are resolved when the popup is shown, so items created before a theme change never keep stale (unreadable) colors.
	 */
	private void applyTheme() {
		setOpaque(true);
		setBackground(NightTheme.getCardHover());
		setBorder(new LineBorder(NightTheme.getBorder()));
		headerLabel.setForeground(NightTheme.getForeground());
		statusLabel.setForeground(NightTheme.getDimForeground());

		for (Component c : actionPanel.getComponents()) {
			if (c instanceof LinkButton) {
				LinkButton link = (LinkButton) c;
				link.setColor(NightTheme.getForeground());
				link.setRolloverColor(NightTheme.getAccent());
			} else if (c instanceof AbstractButton) {
				c.setForeground(NightTheme.getForeground());
			} else if (c instanceof JLabel) {
				c.setForeground(NightTheme.getDimForeground());
			}
		}
	}

	@Override
	public void show(Component invoker, int x, int y) {
		applyTheme();
		super.show(invoker, x, y);
	}

	@Override
	public void updateUI() {
		super.updateUI();
		if (actionPanel != null) {
			applyTheme();
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		// flat themed background instead of the look and feel popup painter
		g.setColor(NightTheme.getCardHover());
		g.fillRect(0, 0, getWidth(), getHeight());
	}

	public void addDescription(JComponent component) {
		if (component instanceof JLabel) {
			// section headers: small caps in the dim color, never with a background
			JLabel label = (JLabel) component;
			label.setText(label.getText().replaceAll(":$", "").toUpperCase());
			label.setOpaque(false);
			label.setFont(Modern.font(10.5f, Font.BOLD));
			label.setForeground(NightTheme.getDimForeground());
		}

		actionPanel.add(component, "gapx 12px, gapy 8px 2px, wrap");
	}

	public void addAction(JComponent component) {
		if (component instanceof AbstractButton && !(component instanceof LinkButton)) {
			AbstractButton button = (AbstractButton) component;
			button.setOpaque(false);
			button.setFont(Modern.font(13f, Font.PLAIN));
			button.setForeground(NightTheme.getForeground());
			if (button instanceof javax.swing.JCheckBox) {
				button.setIcon(new CheckIcon(false));
				button.setSelectedIcon(new CheckIcon(true));
				button.setIconTextGap(10);
				button.setBorder(new EmptyBorder(4, 2, 4, 2));
			}
		}
		actionPanel.add(component, component instanceof javax.swing.JCheckBox ? "gapx 26px 16px, growx, wrap" : "gapx 12px 16px, growx, wrap");
	}

	@Override
	public void addSeparator() {
		actionPanel.add(new Divider(), "growx, h 1!, gapy 6px 2px, wrap");
	}

	@Override
	public JMenuItem add(Action a) {
		LinkButton link = new LinkButton(a);

		// normal text in the regular foreground, accent only while hovering
		link.setColor(NightTheme.getForeground());
		link.setRolloverColor(NightTheme.getAccent());
		link.setRolloverEnabled(true);
		link.setFont(Modern.font(13f, Font.PLAIN));

		// close popup when action is triggered
		link.addActionListener(closeListener);

		addAction(link);
		return null;
	}

	public void clear() {
		actionPanel.removeAll();
	}

	@Override
	public void setLabel(String label) {
		headerLabel.setText(label);
	}

	@Override
	public String getLabel() {
		return headerLabel.getText();
	}

	public void setStatus(String string) {
		statusLabel.setText(string);
		statusLabel.setVisible(string != null && string.length() > 0);
	}

	public String getStatus() {
		return statusLabel.getText();
	}

	private final ActionListener closeListener = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			setVisible(false);
		}
	};

	/**
	 * Hairline in the border color.
	 */
	private static class Divider extends JComponent {

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(10, 1);
		}

		@Override
		protected void paintComponent(Graphics g) {
			g.setColor(NightTheme.getBorder());
			g.fillRect(0, 0, getWidth(), 1);
		}
	}

	/**
	 * Rounded check box in the accent color.
	 */
	private static class CheckIcon implements Icon {

		private final boolean checked;

		CheckIcon(boolean checked) {
			this.checked = checked;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2d = Modern.smooth(g);
			RoundRectangle2D box = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, 15, 15, 6, 6);
			if (checked) {
				g2d.setColor(NightTheme.getAccent());
				g2d.fill(box);
				g2d.setColor(java.awt.Color.WHITE);
				g2d.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
				g2d.drawPolyline(new int[] { x + 4, x + 7, x + 12 }, new int[] { y + 8, y + 11, y + 5 }, 3);
			} else {
				g2d.setColor(NightTheme.getFieldBackground());
				g2d.fill(box);
				g2d.setColor(Modern.mix(NightTheme.getBorder(), NightTheme.getDimForeground(), 0.75f));
				g2d.draw(box);
			}
			g2d.dispose();
		}

		@Override
		public int getIconWidth() {
			return 16;
		}

		@Override
		public int getIconHeight() {
			return 16;
		}
	}

}
