package net.renameo.util.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;

/**
 * Thin rounded bar: filled to the fraction, or a sliding segment while it is not known how far the work is. Repaint it regularly while it slides.
 */
public class ThinProgressBar extends JComponent {

	private float fraction = -1;

	/**
	 * @param fraction
	 *            0 to 1, or a negative value when unknown
	 */
	public void setFraction(float fraction) {
		this.fraction = fraction;
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = Modern.smooth(g);
		int w = getWidth(), h = getHeight();
		g2d.setColor(NightTheme.getBorder());
		g2d.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
		g2d.setColor(NightTheme.getAccent());
		if (fraction >= 0) {
			g2d.fill(new RoundRectangle2D.Float(0, 0, Math.max(h, w * fraction), h, h, h));
		} else {
			float segment = w * 0.28f;
			float t = (System.currentTimeMillis() % 1400) / 1400f;
			g2d.clip(new RoundRectangle2D.Float(0, 0, w, h, h, h));
			g2d.fill(new RoundRectangle2D.Float(-segment + (w + segment) * t, 0, segment, h, h, h));
		}
		g2d.dispose();
	}

}
