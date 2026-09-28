package net.renameo.util.ui;

import java.awt.AlphaComposite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.Timer;

/**
 * Progress of a long running task (rename, match, extract) as a card at the bottom of the main window, instead of a separate dialog. The rest of the window stays visible.
 */
public class ProgressLayer extends JComponent {

	private static final int WIDTH = 460;
	private static final int HEIGHT = 86;
	private static final int MARGIN = 20;

	private static ProgressLayer main;

	public static ProgressLayer install(JRootPane root) {
		ProgressLayer layer = new ProgressLayer();
		root.getLayeredPane().add(layer, JLayeredPane.POPUP_LAYER);
		root.getLayeredPane().addComponentListener(new ComponentAdapter() {

			@Override
			public void componentResized(ComponentEvent e) {
				layer.relayout();
			}
		});
		layer.relayout();
		main = layer;
		return layer;
	}

	/**
	 * @return the progress card of the main window, or null if there is no main window (e.g. command line)
	 */
	public static ProgressLayer getMain() {
		return main;
	}

	private String title;
	private String message;
	private double fraction = -1;
	private Runnable cancel;
	private boolean visible;
	private long shown;

	private final Timer animation = new Timer(30, evt -> repaint());

	private ProgressLayer() {
		setOpaque(false);
		addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				if (cancel != null && getCancelBounds().contains(e.getPoint())) {
					message = "Cancelling …";
					cancel.run();
					cancel = null;
					repaint();
				}
			}
		});
		addMouseMotionListener(new MouseAdapter() {

			@Override
			public void mouseMoved(MouseEvent e) {
				setCursor(cancel != null && getCancelBounds().contains(e.getPoint()) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
		});
	}

	public void start(String title, String message, Runnable cancel) {
		this.title = title;
		this.message = message;
		this.fraction = -1;
		this.cancel = cancel;
		this.visible = true;
		this.shown = System.currentTimeMillis();
		relayout();
		animation.start();
		repaint();
	}

	public void update(String message, double fraction) {
		if (!visible) {
			return;
		}
		if (message != null && cancel != null) {
			this.message = message;
		}
		if (fraction >= 0) {
			this.fraction = Math.min(1, fraction);
		}
		repaint();
	}

	public void finish() {
		visible = false;
		cancel = null;
		animation.stop();
		relayout();
		repaint();
	}

	private void relayout() {
		JComponent parent = (JComponent) getParent();
		if (parent == null) {
			return;
		}
		if (!visible) {
			setBounds(0, 0, 0, 0);
			return;
		}
		int width = Math.min(WIDTH, parent.getWidth() - 2 * MARGIN);
		setBounds((parent.getWidth() - width) / 2, parent.getHeight() - HEIGHT - MARGIN, width, HEIGHT);
	}

	private Rectangle getCancelBounds() {
		return new Rectangle(getWidth() - 88, 12, 76, 26);
	}

	private static String ellipsis(String text, FontMetrics fm, int width) {
		if (fm.stringWidth(text) <= width) {
			return text;
		}
		String dots = "…";
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > width) {
			end--;
		}
		return text.substring(0, end) + dots;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(WIDTH, HEIGHT);
	}

	@Override
	protected void paintComponent(Graphics g) {
		if (!visible) {
			return;
		}
		Graphics2D g2d = Modern.smooth(g);
		int w = getWidth(), h = getHeight();
		long now = System.currentTimeMillis();
		g2d.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, (now - shown) / 180f)));

		RoundRectangle2D card = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 16, 16);
		g2d.setColor(NightTheme.getCardHover());
		g2d.fill(card);
		g2d.setColor(Modern.mix(NightTheme.getBorder(), NightTheme.getAccent(), 0.35f));
		g2d.draw(card);

		// title and percentage
		g2d.setFont(Modern.font(13.5f, Font.BOLD));
		FontMetrics fm = g2d.getFontMetrics();
		g2d.setColor(NightTheme.getForeground());
		String head = fraction >= 0 ? String.format("%s · %d%%", title, Math.round(fraction * 100)) : title;
		g2d.drawString(ellipsis(head, fm, w - 120), 18, 30);

		// cancel button
		if (cancel != null) {
			Rectangle b = getCancelBounds();
			g2d.setColor(NightTheme.getCardBackground());
			g2d.fill(new RoundRectangle2D.Float(b.x, b.y, b.width, b.height, 10, 10));
			g2d.setColor(NightTheme.getBorder());
			g2d.draw(new RoundRectangle2D.Float(b.x + 0.5f, b.y + 0.5f, b.width - 1, b.height - 1, 10, 10));
			g2d.setFont(Modern.font(12f, Font.BOLD));
			FontMetrics bm = g2d.getFontMetrics();
			g2d.setColor(NightTheme.getForeground());
			g2d.drawString("Cancel", b.x + (b.width - bm.stringWidth("Cancel")) / 2, b.y + (b.height + bm.getAscent() - bm.getDescent()) / 2);
		}

		// current item
		if (message != null) {
			g2d.setFont(Modern.font(12f, Font.PLAIN));
			g2d.setColor(NightTheme.getDimForeground());
			g2d.drawString(ellipsis(message, g2d.getFontMetrics(), w - 36), 18, 52);
		}

		// progress bar (indeterminate: a sliding segment)
		int bx = 18, by = h - 20, bw = w - 36, bh = 6;
		g2d.setColor(NightTheme.getBorder());
		g2d.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
		g2d.setColor(NightTheme.getAccent());
		if (fraction >= 0) {
			g2d.fill(new RoundRectangle2D.Float(bx, by, Math.max(bh, (float) (bw * fraction)), bh, bh, bh));
		} else {
			float segment = bw * 0.28f;
			float t = (now % 1400) / 1400f;
			float x = bx - segment + (bw + segment) * t;
			Graphics2D clip = (Graphics2D) g2d.create();
			clip.clip(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
			clip.fill(new RoundRectangle2D.Float(x, by, segment, bh, bh, bh));
			clip.dispose();
		}
		g2d.dispose();
	}

}
