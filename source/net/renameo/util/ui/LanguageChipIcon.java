package net.renameo.util.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/**
 * Rounded language chip with the ISO 639-1 code, used instead of low resolution flag images.
 */
public class LanguageChipIcon implements Icon {

	private static final int WIDTH = 22;
	private static final int HEIGHT = 16;

	private final String code;
	private final boolean selected;

	public LanguageChipIcon(String code, boolean selected) {
		// "undefined" stands for all languages and would not fit the chip
		this.code = code == null || code.length() > 3 ? "ALL" : code.toUpperCase();
		this.selected = selected;
	}

	public String getCode() {
		return code;
	}

	@Override
	public int getIconWidth() {
		return WIDTH;
	}

	@Override
	public int getIconHeight() {
		return HEIGHT;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Color accent = NightTheme.getAccent();
		Color background = selected ? accent : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 38);
		Color foreground = selected ? Color.WHITE : accent;

		RoundRectangle2D chip = new RoundRectangle2D.Double(x, y, WIDTH, HEIGHT, 6, 6);
		g2d.setColor(background);
		g2d.fill(chip);

		if (!selected) {
			g2d.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
			g2d.draw(chip);
		}

		// use a small font so two letter codes always fit
		Font font = c != null && c.getFont() != null ? c.getFont() : UIManagerFont();
		font = font.deriveFont(Font.BOLD, 9f);

		g2d.setFont(font);
		FontMetrics fm = g2d.getFontMetrics();
		int textWidth = fm.stringWidth(code);
		int textY = y + ((HEIGHT - fm.getHeight()) / 2) + fm.getAscent();

		g2d.setColor(foreground);
		g2d.drawString(code, x + ((WIDTH - textWidth) / 2), textY);

		g2d.dispose();
	}

	private static Font UIManagerFont() {
		return javax.swing.UIManager.getFont("Label.font");
	}

}
