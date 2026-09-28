package net.renameo.util.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.plaf.basic.BasicToolTipUI;

/**
 * Rounded, flat tooltip that follows the active color scheme.
 */
public class ThemeToolTipUI extends BasicToolTipUI {

	private static final int ARC = 8;
	private static final int PADDING_X = 9;
	private static final int PADDING_Y = 5;

	@Override
	public Dimension getPreferredSize(JComponent c) {
		if (c instanceof JToolTip) {
			String text = ((JToolTip) c).getTipText();
			if (text != null && !isHtml(text)) {
				FontMetrics fm = c.getFontMetrics(c.getFont());
				return new Dimension(fm.stringWidth(text) + (PADDING_X * 2), fm.getHeight() + (PADDING_Y * 2));
			}
		}

		return super.getPreferredSize(c);
	}

	@Override
	public void paint(Graphics g, JComponent c) {
		String text = c instanceof JToolTip ? ((JToolTip) c).getTipText() : null;

		// fall back to the default rendering for rich text
		if (text == null || isHtml(text)) {
			super.paint(g, c);
			return;
		}

		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Color background = c.getBackground();
		if (background == null) {
			background = NightTheme.getPanelBackground();
		}

		RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, c.getWidth() - 1, c.getHeight() - 1, ARC, ARC);
		g2d.setColor(background);
		g2d.fill(shape);
		g2d.setColor(NightTheme.getBorder());
		g2d.draw(shape);

		g2d.setFont(c.getFont());
		FontMetrics fm = g2d.getFontMetrics();
		g2d.setColor(c.getForeground() != null ? c.getForeground() : NightTheme.getForeground());
		g2d.drawString(text, PADDING_X, PADDING_Y + fm.getAscent());

		g2d.dispose();
	}

	private static boolean isHtml(String text) {
		String t = text.trim();
		return t.regionMatches(true, 0, "<html", 0, 5);
	}

}
