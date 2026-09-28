package net.renameo.util.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.Icon;

/**
 * Resolution independent line icons, drawn on a 24 unit artboard and tinted at paint time so they follow theme changes.
 */
public class Glyph implements Icon {

	public enum Shape {

		SEARCH(p -> {
			p.circle(10.5, 10.5, 6);
			p.line(15, 15, 20, 20);
		}),

		SETTINGS(p -> {
			p.circle(12, 12, 3);
			for (int i = 0; i < 8; i++) {
				double a = Math.toRadians(i * 45);
				p.line(12 + 6 * Math.cos(a), 12 + 6 * Math.sin(a), 12 + 8.5 * Math.cos(a), 12 + 8.5 * Math.sin(a));
			}
			p.circle(12, 12, 6);
		}),

		HELP(p -> {
			p.circle(12, 12, 9);
			p.arc(12, 9.5, 2.8, 160, -220);
			p.line(12, 12.3, 12, 13.6);
			p.dot(12, 16.8, 1.1);
		}),

		MATCH(p -> {
			p.circle(9, 12, 5.5);
			p.circle(15, 12, 5.5);
		}),

		LINK(p -> {
			p.rrect(2.5, 8.5, 9, 7, 3.5);
			p.rrect(12.5, 8.5, 9, 7, 3.5);
			p.line(8.5, 12, 15.5, 12);
		}),

		DOWNLOAD(p -> {
			p.line(12, 3.5, 12, 15);
			p.path(false, 7.5, 10.5, 12, 15, 16.5, 10.5);
			p.path(false, 4, 16, 4, 20, 20, 20, 20, 16);
		}),

		EYE(p -> {
			p.path(true, 2, 12, 7, 6, 12, 5, 17, 6, 22, 12, 17, 18, 12, 19, 7, 18);
			p.circle(12, 12, 3);
		}),

		PENCIL(p -> {
			p.path(true, 15, 4.5, 19.5, 9, 9, 19.5, 4.5, 19.5, 4.5, 15);
			p.line(13, 6.5, 17.5, 11);
		}),

		PLAY(p -> {
			p.fill(8, 5, 8, 19, 19, 12);
		}),

		CHEVRON_DOWN(p -> {
			p.path(false, 6.5, 9.5, 12, 15, 17.5, 9.5);
		}),

		CHEVRON_RIGHT(p -> {
			p.path(false, 9.5, 6.5, 15, 12, 9.5, 17.5);
		}),

		ARROW_RIGHT(p -> {
			p.line(4, 12, 20, 12);
			p.path(false, 14, 6, 20, 12, 14, 18);
		}),

		CLOUD_UPLOAD(p -> {
			p.g.draw(cloud());
			p.line(12, 11, 12, 20.5);
			p.path(false, 8.5, 14.5, 12, 11, 15.5, 14.5);
		}),

		FILM(p -> {
			p.rrect(3, 4, 18, 16, 3);
			p.line(7.5, 4, 7.5, 20);
			p.line(16.5, 4, 16.5, 20);
			p.line(3, 12, 21, 12);
			p.line(3, 8, 7.5, 8);
			p.line(3, 16, 7.5, 16);
			p.line(16.5, 8, 21, 8);
			p.line(16.5, 16, 21, 16);
		}),

		TV(p -> {
			p.rrect(2.5, 7, 19, 13, 3);
			p.path(false, 8, 2.5, 12, 7, 16, 2.5);
		}),

		MUSIC(p -> {
			p.circle(7.5, 17.5, 2.8);
			p.circle(17.5, 15.5, 2.8);
			p.path(false, 10.3, 17.5, 10.3, 5, 20.3, 3, 20.3, 15.5);
			p.line(10.3, 9, 20.3, 7);
		}),

		FILE(p -> {
			p.path(true, 6, 3, 14, 3, 19, 8, 19, 21, 6, 21);
			p.path(false, 14, 3, 14, 8, 19, 8);
		}),

		FOLDER(p -> {
			p.path(true, 3, 19, 3, 5, 9, 5, 11, 7.5, 21, 7.5, 21, 19);
		}),

		STAR(p -> {
			double[] xy = new double[20];
			for (int i = 0; i < 10; i++) {
				double r = i % 2 == 0 ? 9.5 : 4;
				double a = Math.toRadians(-90 + i * 36);
				xy[2 * i] = 12 + r * Math.cos(a);
				xy[2 * i + 1] = 12.8 + r * Math.sin(a);
			}
			p.fill(xy);
		}),

		USER(p -> {
			p.circle(12, 8, 4);
			p.arc(12, 21, 7.5, 0, 180);
		}),

		TAG(p -> {
			p.path(true, 3, 3, 12, 3, 21, 12, 12, 21, 3, 12);
			p.dot(8, 8, 1.4);
		}),

		CLOCK(p -> {
			p.circle(12, 12, 9);
			p.path(false, 12, 7, 12, 12, 15.5, 14);
		}),

		CHECK_CIRCLE(p -> {
			p.circle(12, 12, 9);
			p.path(false, 8, 12.5, 11, 15.5, 16.5, 9.5);
		}),

		WARNING(p -> {
			p.path(true, 12, 3.5, 21.5, 20, 2.5, 20);
			p.line(12, 9.5, 12, 14);
			p.dot(12, 17, 1.1);
		}),

		INFO(p -> {
			p.circle(12, 12, 9);
			p.line(12, 11, 12, 16.5);
			p.dot(12, 7.8, 1.1);
		}),

		PLUS(p -> {
			p.line(12, 5, 12, 19);
			p.line(5, 12, 19, 12);
		}),

		CLOSE(p -> {
			p.line(6, 6, 18, 18);
			p.line(18, 6, 6, 18);
		}),

		TRASH(p -> {
			p.line(4, 6.5, 20, 6.5);
			p.path(false, 9, 6.5, 9, 3.5, 15, 3.5, 15, 6.5);
			p.path(false, 6, 6.5, 7, 20.5, 17, 20.5, 18, 6.5);
			p.line(10, 10.5, 10, 16.5);
			p.line(14, 10.5, 14, 16.5);
		}),

		HISTORY(p -> {
			p.arc(12, 12, 8.5, 200, -330);
			p.path(false, 3, 6.5, 3.6, 11, 8, 10);
			p.path(false, 12, 7.5, 12, 12, 15, 14);
		}),

		BOOKMARK(p -> {
			p.path(true, 6, 3, 18, 3, 18, 21, 12, 16, 6, 21);
		}),

		ARROW_UP(p -> {
			p.line(12, 19, 12, 5);
			p.path(false, 6.5, 10.5, 12, 5, 17.5, 10.5);
		}),

		ARROW_DOWN(p -> {
			p.line(12, 5, 12, 19);
			p.path(false, 6.5, 13.5, 12, 19, 17.5, 13.5);
		}),

		MINUS_CIRCLE(p -> {
			p.circle(12, 12, 9);
			p.line(8, 12, 16, 12);
		}),

		SLIDERS(p -> {
			p.line(4, 7, 20, 7);
			p.line(4, 17, 20, 17);
			p.fillCircle(9, 7, 2.5);
			p.fillCircle(15, 17, 2.5);
		}),

		UNDO(p -> {
			p.path(false, 8.5, 4.5, 4.5, 8.5, 8.5, 12.5);
			p.line(4.5, 8.5, 14, 8.5);
			p.arc(14, 13.5, 5, 90, -180);
			p.line(14, 18.5, 9, 18.5);
		}),

		SIDEBAR(p -> {
			p.rrect(3, 4, 18, 16, 3);
			p.line(9, 4, 9, 20);
		}),

		INSPECTOR(p -> {
			p.rrect(3, 4, 18, 16, 3);
			p.line(15, 4, 15, 20);
		}),

		COMMAND(p -> {
			p.rrect(3, 4, 18, 16, 3);
			p.path(false, 7, 9, 10, 12, 7, 15);
			p.line(12, 15, 17, 15);
		}),

		MORE(p -> {
			p.dot(6, 12, 1.6);
			p.dot(12, 12, 1.6);
			p.dot(18, 12, 1.6);
		});

		private final Consumer<Pen> drawing;

		private Shape(Consumer<Pen> drawing) {
			this.drawing = drawing;
		}

		private static java.awt.Shape cloud() {
			Path2D c = new Path2D.Double();
			c.moveTo(7.5, 17.5);
			c.curveTo(4.5, 17.5, 2.5, 15.5, 2.5, 13);
			c.curveTo(2.5, 10.5, 4.5, 8.6, 7, 8.8);
			c.curveTo(8, 5.8, 10.5, 4, 13.3, 4);
			c.curveTo(17, 4, 19.8, 6.8, 19.8, 10.4);
			c.curveTo(21.3, 11, 22, 12.4, 22, 13.9);
			c.curveTo(22, 15.9, 20.4, 17.5, 18.4, 17.5);
			c.lineTo(16, 17.5);
			c.moveTo(8, 17.5);
			return c;
		}
	}

	/**
	 * Drawing helpers operating in artboard units.
	 */
	public static final class Pen {

		public final Graphics2D g;

		Pen(Graphics2D g) {
			this.g = g;
		}

		public void line(double x1, double y1, double x2, double y2) {
			g.draw(new Line2D.Double(x1, y1, x2, y2));
		}

		public void circle(double cx, double cy, double r) {
			g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
		}

		public void fillCircle(double cx, double cy, double r) {
			g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
		}

		public void dot(double cx, double cy, double r) {
			fillCircle(cx, cy, r);
		}

		public void rrect(double x, double y, double w, double h, double arc) {
			g.draw(new RoundRectangle2D.Double(x, y, w, h, arc * 2, arc * 2));
		}

		public void arc(double cx, double cy, double r, double start, double extent) {
			g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, start, extent, Arc2D.OPEN));
		}

		public void path(boolean close, double... xy) {
			g.draw(polygon(close, xy));
		}

		public void fill(double... xy) {
			g.fill(polygon(true, xy));
		}

		private static Path2D polygon(boolean close, double... xy) {
			Path2D p = new Path2D.Double();
			p.moveTo(xy[0], xy[1]);
			for (int i = 2; i < xy.length; i += 2) {
				p.lineTo(xy[i], xy[i + 1]);
			}
			if (close) {
				p.closePath();
			}
			return p;
		}
	}

	private static final double UNIT = 24;

	private final Shape shape;
	private final int size;
	private final Supplier<Color> color;
	private final float strokeWidth;

	public Glyph(Shape shape, int size, Supplier<Color> color) {
		this(shape, size, color, 1.8f);
	}

	public Glyph(Shape shape, int size, Supplier<Color> color, float strokeWidth) {
		this.shape = shape;
		this.size = size;
		this.color = color;
		this.strokeWidth = strokeWidth;
	}

	public static Glyph of(Shape shape, int size, Color color) {
		return new Glyph(shape, size, () -> color);
	}

	public static Glyph of(Shape shape, int size, Supplier<Color> color) {
		return new Glyph(shape, size, color);
	}

	public Glyph withColor(Supplier<Color> color) {
		return new Glyph(shape, size, color, strokeWidth);
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2d = (Graphics2D) g.create();
		try {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g2d.translate(x, y);
			g2d.scale(size / UNIT, size / UNIT);
			Color tint = color.get();
			if (c != null && !c.isEnabled()) {
				tint = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), tint.getAlpha() / 3);
			}
			g2d.setColor(tint);
			g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			shape.drawing.accept(new Pen(g2d));
		} finally {
			g2d.dispose();
		}
	}

	@Override
	public int getIconWidth() {
		return size;
	}

	@Override
	public int getIconHeight() {
		return size;
	}

}
