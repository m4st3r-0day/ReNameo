package net.renameo.util.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.Timer;

/**
 * Discreet notifications in the bottom right corner of a window: they fade in, stay a few seconds and fade out without blocking the user. A click dismisses them early.
 */
public class ToastLayer extends JComponent {

	private static final int WIDTH = 340;
	private static final int HEIGHT = 64;
	private static final int MARGIN = 20;
	private static final int DURATION = 4500;

	private static class Toast {

		final String title;
		final String detail;
		final Color tint;
		final long created = System.currentTimeMillis();

		Toast(String title, String detail, Color tint) {
			this.title = title;
			this.detail = detail;
			this.tint = tint;
		}
	}

	private final Deque<Toast> toasts = new ArrayDeque<Toast>();
	private final Timer timer = new Timer(40, evt -> tick());

	public static ToastLayer install(JRootPane root) {
		ToastLayer layer = new ToastLayer();
		root.getLayeredPane().add(layer, JLayeredPane.POPUP_LAYER);
		root.getLayeredPane().addComponentListener(new java.awt.event.ComponentAdapter() {

			@Override
			public void componentResized(java.awt.event.ComponentEvent e) {
				layer.relayout();
			}
		});
		layer.relayout();
		return layer;
	}

	private ToastLayer() {
		setOpaque(false);
		addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				toasts.clear();
				relayout();
				repaint();
			}
		});
	}

	public void show(String title, String detail, Color tint) {
		toasts.addFirst(new Toast(title, detail, tint));
		while (toasts.size() > 3) {
			toasts.removeLast();
		}
		relayout();
		timer.start();
		repaint();
	}

	private void relayout() {
		JComponent parent = (JComponent) getParent();
		if (parent == null) {
			return;
		}
		int height = toasts.isEmpty() ? 0 : toasts.size() * (HEIGHT + 10);
		setBounds(parent.getWidth() - WIDTH - MARGIN, parent.getHeight() - height - MARGIN - 64, WIDTH, height);
	}

	private void tick() {
		long now = System.currentTimeMillis();
		boolean removed = toasts.removeIf(t -> now - t.created > DURATION);
		if (toasts.isEmpty()) {
			timer.stop();
		}
		if (removed) {
			relayout();
		}
		repaint();
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(WIDTH, toasts.size() * (HEIGHT + 10));
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = Modern.smooth(g);
		long now = System.currentTimeMillis();
		int y = 0;
		for (Toast t : toasts) {
			long age = now - t.created;
			float alpha = Math.min(1f, Math.min(age / 200f, (DURATION - age) / 400f));
			g2d.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, alpha)));

			RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, y + 0.5f, WIDTH - 1, HEIGHT - 1, 14, 14);
			g2d.setColor(NightTheme.getCardHover());
			g2d.fill(shape);
			g2d.setColor(Modern.mix(NightTheme.getBorder(), t.tint, 0.4f));
			g2d.draw(shape);
			g2d.setColor(t.tint);
			g2d.fill(new RoundRectangle2D.Float(12, y + 14, 4, HEIGHT - 28, 4, 4));

			g2d.setFont(Modern.font(13.5f, Font.BOLD));
			FontMetrics fm = g2d.getFontMetrics();
			g2d.setColor(NightTheme.getForeground());
			g2d.drawString(t.title, 28, y + 26);
			if (t.detail != null) {
				g2d.setFont(Modern.font(12f, Font.PLAIN));
				g2d.setColor(NightTheme.getDimForeground());
				g2d.drawString(t.detail, 28, y + 26 + fm.getHeight());
			}
			y += HEIGHT + 10;
		}
		g2d.dispose();
	}

}
