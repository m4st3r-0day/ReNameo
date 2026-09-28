package net.renameo.ui;

import static javax.swing.BorderFactory.*;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Graphics;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import net.renameo.util.ui.NightTheme;

public class HeaderPanel extends JComponent {

	private JLabel titleLabel = new JLabel();

	public HeaderPanel() {
		setLayout(new BorderLayout());
		setBackground(NightTheme.getHeaderColor());

		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.setOpaque(false);

		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setVerticalAlignment(SwingConstants.CENTER);
		titleLabel.setOpaque(false);

		// modern title font, derived from the current UI font
		Font uiFont = UIManager.getFont("defaultFont");
		titleLabel.setFont((uiFont != null ? uiFont : titleLabel.getFont()).deriveFont(Font.BOLD, 18f));

		centerPanel.setBorder(createEmptyBorder(11, 0, 12, 0));
		centerPanel.add(titleLabel, BorderLayout.CENTER);

		add(centerPanel, BorderLayout.CENTER);
	}

	public void setTitle(String title) {
		titleLabel.setText(title);
	}

	public JLabel getTitleLabel() {
		return titleLabel;
	}

	@Override
	protected void paintComponent(Graphics g) {
		g.setColor(NightTheme.getHeaderColor());
		g.fillRect(0, 0, getWidth(), getHeight());

		// flat bottom hairline
		g.setColor(NightTheme.getBorder());
		g.fillRect(0, getHeight() - 1, getWidth(), 1);

		// update title color before child components are painted
		titleLabel.setForeground(NightTheme.getForeground());
	}

}