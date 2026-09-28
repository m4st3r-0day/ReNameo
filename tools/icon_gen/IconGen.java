import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

/**
 * Renders a flat, modern (Material-style) icon set for the FileBot UI.
 * Draws each glyph on a 24-unit artboard and exports it at the size of the original icon and twice that (@2x).
 */
public class IconGen {

	private static final double UNIT = 24.0;

	private static final Color BLUE = new Color(0x3B82F6);
	private static final Color TEAL = new Color(0x14B8A6);
	private static final Color VIOLET = new Color(0x8B5CF6);
	private static final Color GREEN = new Color(0x22C55E);
	private static final Color AMBER = new Color(0xF59E0B);
	private static final Color PINK = new Color(0xEC4899);

	private static final Color WHITE = new Color(0xFFFFFF);

	private static final class Canvas {
		final int width;
		final int height;
		final Graphics2D g;

		Canvas(int width, int height, Graphics2D g) {
			this.width = width;
			this.height = height;
			this.g = g;
		}

		// the stroke width is given in artboard units, the transform scales it to pixels
		void init(Color color, double strokeWidth) {
			double s = Math.min(width, height) / UNIT;
			g.setColor(color);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.translate((width - UNIT * s) / 2, (height - UNIT * s) / 2);
			g.transform(AffineTransform.getScaleInstance(s, s));
			g.setStroke(new BasicStroke((float) strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		}

		void ellipse(double x, double y, double w, double h) {
			g.draw(new java.awt.geom.Ellipse2D.Double(x, y, w, h));
		}

		void ellipseArc(double x, double y, double w, double h, double startDeg, double extentDeg) {
			g.draw(new Arc2D.Double(x, y, w, h, startDeg, extentDeg, Arc2D.OPEN));
		}

		void setColor(Color c) {
			g.setColor(c);
		}

		void line(double x1, double y1, double x2, double y2) {
			g.draw(new Line2D.Double(x1, y1, x2, y2));
		}

		void rrect(double x, double y, double w, double h, double arc) {
			g.draw(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
		}

		void fillRrect(double x, double y, double w, double h, double arc) {
			g.fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
		}

		void circle(double x, double y, double r) {
			g.draw(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
		}

		void fillCircle(double x, double y, double r) {
			g.fill(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
		}

		void path(double[] x, double[] y, boolean close) {
			Path2D p = new Path2D.Double();
			p.moveTo(x[0], y[0]);
			for (int i = 1; i < x.length; i++) {
				p.lineTo(x[i], y[i]);
			}
			if (close) {
				p.closePath();
			}
			g.draw(p);
		}

		void fill(double[] x, double[] y) {
			Path2D p = new Path2D.Double();
			p.moveTo(x[0], y[0]);
			for (int i = 1; i < x.length; i++) {
				p.lineTo(x[i], y[i]);
			}
			p.closePath();
			g.fill(p);
		}

		void arc(double cx, double cy, double r, double startDeg, double extentDeg) {
			g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, startDeg, extentDeg, Arc2D.OPEN));
		}
	}

	private static File outputDir;
	private static File referenceDir;

	/**
	 * Icons must keep the pixel size of the original resource they replace, because Swing draws an ImageIcon at the size of its base image.
	 */
	private static Dimension baseSize(String name, int fallback) throws Exception {
		File ref = new File(referenceDir, name + ".png");
		if (ref.exists()) {
			BufferedImage img = ImageIO.read(ref);
			return new Dimension(img.getWidth(), img.getHeight());
		}
		return new Dimension(fallback, fallback);
	}

	private static void export(String name, Color color, int fallback, Consumer<Canvas> draw) throws Exception {
		Dimension base = baseSize(name, fallback);
		write(name, color, base.width, base.height, draw);
		write(name + "@2x", color, base.width * 2, base.height * 2, draw);
	}

	private static void write(String file, Color color, int width, int height, Consumer<Canvas> draw) throws Exception {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		Canvas c = new Canvas(width, height, g);
		c.init(color, 2.0);
		draw.accept(c);
		g.dispose();
		File out = new File(outputDir, file + ".png");
		out.getParentFile().mkdirs();
		ImageIO.write(img, "png", out);
		System.out.println("wrote " + out + " " + width + "x" + height);
	}

	/**
	 * Usage: java IconGen <output-dir> [<reference-dir>]
	 *
	 * The reference dir holds the original icons whose pixel sizes are matched (defaults to the output dir).
	 */
	public static void main(String[] args) throws Exception {
		outputDir = new File(args.length > 0 ? args[0] : "resources");
		referenceDir = new File(args.length > 1 ? args[1] : outputDir.getPath());

		Map<String, Color> icons = new LinkedHashMap<String, Color>();
		Map<String, Consumer<Canvas>> draw = new LinkedHashMap<String, Consumer<Canvas>>();

		// ---- panel icons (colored) ----
		icons.put("panel.rename", BLUE);
		draw.put("panel.rename", c -> {
			c.rrect(2, 2, 20, 20, 5);
			c.path(new double[] { 14, 17.5, 10, 6.5, 6.5 }, new double[] { 6.5, 10, 17.5, 17.5, 14 }, true);
			c.line(12.5, 8, 16, 11.5);
		});

		icons.put("panel.episodelist", TEAL);
		draw.put("panel.episodelist", c -> {
			c.rrect(3.5, 5, 17, 12.5, 2.5);
			c.line(7.5, 3.5, 12, 6);
			c.line(16.5, 3.5, 12, 6);
			c.fill(new double[] { 9.8, 9.8, 14.3 }, new double[] { 9.8, 15.2, 12.5 });
		});

		icons.put("panel.subtitle", VIOLET);
		draw.put("panel.subtitle", c -> {
			c.rrect(3, 4, 18, 12.5, 4);
			c.fill(new double[] { 5.5, 5.5, 9.5 }, new double[] { 15.5, 20.5, 15.5 });
			c.line(6.5, 8.5, 17.5, 8.5);
			c.line(6.5, 11.5, 13.5, 11.5);
		});

		icons.put("panel.sfv", GREEN);
		draw.put("panel.sfv", c -> {
			c.path(new double[] { 12, 19, 19, 12, 5, 5 }, new double[] { 2.8, 5.3, 12, 21, 12, 5.3 }, true);
			c.path(new double[] { 8.5, 11.2, 15.8 }, new double[] { 11.2, 14.2, 8.2 }, false);
		});

		icons.put("panel.analyze", AMBER);
		draw.put("panel.analyze", c -> {
			c.path(new double[] { 4, 20, 14, 14, 10, 10 }, new double[] { 3.5, 3.5, 11, 18.5, 20.5, 11 }, true);
		});

		icons.put("panel.list", PINK);
		draw.put("panel.list", c -> {
			c.rrect(5.5, 3, 13, 18, 3);
			c.line(8.5, 3, 8.5, 5.5);
			c.line(15.5, 3, 15.5, 5.5);
			c.path(new double[] { 8.7, 11, 15.2 }, new double[] { 11, 13.8, 8.5 }, false);
		});

		// ---- action icons (monochrome blue) ----
		icons.put("action.rename", BLUE);
		draw.put("action.rename", c -> {
			c.path(new double[] { 14.5, 19, 8.5, 4.5, 4.5 }, new double[] { 5, 9.5, 20, 20, 16 }, true);
			c.line(12.5, 7, 17, 11.5);
		});
		icons.put("action.match", BLUE);
		draw.put("action.match", c -> {
			c.circle(9.5, 12, 4.5);
			c.circle(14.5, 12, 4.5);
		});
		icons.put("action.format", BLUE);
		draw.put("action.format", c -> {
			c.path(new double[] { 15.5, 13.2, 12, 12, 10.3 }, new double[] { 2.5, 4, 6.5, 9.5, 12 }, false);
			c.path(new double[] { 15.5, 13.2, 12, 12, 10.3 }, new double[] { 21.5, 20, 17.5, 14.5, 12 }, false);
			c.path(new double[] { 8.5, 10.8, 12, 12, 13.7 }, new double[] { 2.5, 4, 6.5, 9.5, 12 }, false);
			c.path(new double[] { 8.5, 10.8, 12, 12, 13.7 }, new double[] { 21.5, 20, 17.5, 14.5, 12 }, false);
		});
		icons.put("action.revert", BLUE);
		draw.put("action.revert", c -> {
			c.path(new double[] { 8.5, 4.5, 8.5 }, new double[] { 5.5, 9.5, 13.5 }, false);
			c.line(4.5, 9.5, 14, 9.5);
			c.arc(14, 14.5, 5, 90, -180);
			c.line(14, 19.5, 8, 19.5);
		});
		icons.put("action.fetch", BLUE);
		draw.put("action.fetch", c -> {
			c.line(12, 3.5, 12, 13.5);
			c.line(8, 10, 12, 14);
			c.line(16, 10, 12, 14);
			c.line(4.5, 17, 19.5, 17);
			c.line(4.5, 17, 4.5, 20.5);
			c.line(19.5, 17, 19.5, 20.5);
			c.line(4.5, 20.5, 19.5, 20.5);
		});
		icons.put("action.find", BLUE);
		draw.put("action.find", c -> {
			c.circle(10.5, 10.5, 5.5);
			c.line(14.8, 14.8, 20, 20);
		});
		icons.put("action.properties", BLUE);
		draw.put("action.properties", c -> {
			c.rrect(6, 3, 12, 18, 3);
			c.line(9, 8, 15, 8);
			c.line(9, 11.5, 15, 11.5);
			c.line(9, 15, 12.5, 15);
		});
		icons.put("action.settings", BLUE);
		draw.put("action.settings", c -> {
			c.circle(12, 12, 3.6);
			for (int i = 0; i < 8; i++) {
				double a = Math.toRadians(i * 45);
				double r1 = 6.4, r2 = 8.3;
				c.line(12 + r1 * Math.cos(a), 12 + r1 * Math.sin(a), 12 + r2 * Math.cos(a), 12 + r2 * Math.sin(a));
			}
			c.fillCircle(12, 12, 1.1);
		});
		icons.put("action.preferences", BLUE);
		draw.put("action.preferences", c -> {
			c.line(6, 5, 6, 19);
			c.line(12, 5, 12, 19);
			c.line(18, 5, 18, 19);
			c.fillRrect(3, 6.5, 6, 3, 1.5);
			c.fillRrect(9, 12, 6, 3, 1.5);
			c.fillRrect(15, 9, 6, 3, 1.5);
		});
		icons.put("action.report", BLUE);
		draw.put("action.report", c -> {
			c.rrect(5, 3, 14, 18, 2.5);
			c.line(8, 11, 8, 15);
			c.line(11.5, 8, 11.5, 15);
			c.line(15, 11, 15, 15);
		});
		icons.put("action.save", BLUE);
		draw.put("action.save", c -> {
			c.rrect(4, 3, 16, 18, 3);
			c.rrect(8, 3.4, 8, 6, 1.5);
			c.line(7, 15, 17, 15);
			c.line(7, 20, 17, 20);
		});
		icons.put("action.load", BLUE);
		draw.put("action.load", c -> {
			c.path(new double[] { 3, 3, 9, 11, 21, 21 }, new double[] { 19, 5, 5, 7.5, 7.5, 19 }, true);
			c.line(3, 11, 21, 11);
		});
		icons.put("action.up", BLUE);
		draw.put("action.up", c -> {
			c.fill(new double[] { 12, 6.5, 17.5, 12 }, new double[] { 7.5, 15, 15, 7.5 });
		});
		icons.put("action.down", BLUE);
		draw.put("action.down", c -> {
			c.fill(new double[] { 12, 6.5, 17.5, 12 }, new double[] { 16.5, 9, 9, 16.5 });
		});
		icons.put("action.clear", BLUE);
		draw.put("action.clear", c -> {
			c.line(6, 7.5, 18, 7.5);
			c.line(8, 7.5, 8, 18);
			c.line(16, 7.5, 16, 18);
			c.line(8, 18, 16, 18);
			c.line(7, 5, 17, 5);
			c.line(9.5, 2.5, 14.5, 2.5);
		});
		icons.put("action.list", BLUE);
		draw.put("action.list", c -> {
			c.fill(new double[] { 5, 12, 19 }, new double[] { 10, 2, 10 });
			c.fill(new double[] { 5, 12, 19 }, new double[] { 14, 22, 14 });
		});
		icons.put("action.search", BLUE);
		draw.put("action.search", c -> {
			c.circle(10.5, 10.5, 5.5);
			c.line(14.8, 14.8, 20, 20);
		});
		icons.put("action.variables", BLUE);
		draw.put("action.variables", c -> {
			c.path(new double[] { 8, 6.5, 6.5, 4.5, 6.5, 6.5, 8 }, new double[] { 4.5, 5.5, 10.5, 12, 13.5, 18.5, 19.5 }, false);
			c.path(new double[] { 16, 17.5, 17.5, 19.5, 17.5, 17.5, 16 }, new double[] { 4.5, 5.5, 10.5, 12, 13.5, 18.5, 19.5 }, false);
			c.line(10, 9, 14, 15);
			c.line(14, 9, 10, 15);
		});
		icons.put("action.script", BLUE);
		draw.put("action.script", c -> {
			c.rrect(4, 5, 16, 14, 3);
			c.line(7, 13, 10.5, 9.5);
			c.line(10.5, 9.5, 7, 6.5);
			c.line(12, 15.5, 16.5, 15.5);
		});
		icons.put("action.select", BLUE);
		draw.put("action.select", c -> {
			c.fill(new double[] { 3, 21, 12 }, new double[] { 7, 7, 18 });
		});
		icons.put("action.user", BLUE);
		draw.put("action.user", c -> {
			c.fillCircle(12, 8.5, 3);
			c.fill(new double[] { 5.5, 6, 18, 18.5 }, new double[] { 20, 13.5, 13.5, 20 });
		});
		icons.put("action.auto", BLUE);
		draw.put("action.auto", c -> {
			c.line(4.5, 19.5, 14.5, 9.5);
			c.line(17, 3, 17, 7);
			c.line(15, 5, 19, 5);
			c.line(19.5, 11, 19.5, 14);
			c.line(18, 12.5, 21, 12.5);
			c.line(9.5, 3.5, 9.5, 6.5);
			c.line(8, 5, 11, 5);
		});
		icons.put("action.extension.preserve", BLUE);
		draw.put("action.extension.preserve", c -> {
			c.rrect(7, 4, 11, 13, 2);
			c.path(new double[] { 9.5, 11, 14.8 }, new double[] { 11, 13.8, 8.5 }, false);
		});
		icons.put("action.extension.override", BLUE);
		draw.put("action.extension.override", c -> {
			c.path(new double[] { 5, 8, 5 }, new double[] { 8, 16, 8 }, false);
			c.line(5, 10, 17, 10);
			c.line(17, 10, 14, 7);
			c.line(17, 10, 14, 13);
			c.line(8, 16, 18, 16);
			c.line(18, 16, 15.5, 13);
			c.line(18, 16, 15.5, 19);
		});
		icons.put("action.script.add", BLUE);
		draw.put("action.script.add", c -> {
			c.circle(12, 12, 7);
			c.line(12, 8.5, 12, 15.5);
			c.line(8.5, 12, 15.5, 12);
		});

		// ---- rename action icons ----
		icons.put("rename.action.move", BLUE);
		draw.put("rename.action.move", c -> {
			c.rrect(3, 4.5, 9, 13, 2);
			c.line(9, 12, 21, 12);
			c.path(new double[] { 17.5, 21, 17.5 }, new double[] { 8.5, 12, 15.5 }, false);
		});
		icons.put("rename.action.copy", BLUE);
		draw.put("rename.action.copy", c -> {
			c.rrect(5, 4, 11, 11, 2.5);
			c.rrect(9, 8, 11, 11, 2.5);
		});
		icons.put("rename.action.hardlink", BLUE);
		draw.put("rename.action.hardlink", c -> {
			c.circle(7.5, 12, 3.5);
			c.circle(16.5, 12, 3.5);
			c.line(9.5, 12, 14.5, 12);
		});
		icons.put("rename.action.symlink", BLUE);
		draw.put("rename.action.symlink", c -> {
			c.arc(7.5, 12, 3.5, 0, 360);
			c.arc(16.5, 12, 3.5, 0, 360);
			c.line(9.7, 12, 14.3, 12);
			c.line(13.5, 8, 17.5, 8);
		});
		icons.put("rename.action.keeplink", BLUE);
		draw.put("rename.action.keeplink", c -> {
			c.rrect(4, 3, 16, 18, 3);
			c.line(9, 15, 15, 9);
			c.line(10.5, 9, 15, 9);
			c.line(15, 9, 15, 13.5);
		});
		icons.put("rename.action.clone", BLUE);
		draw.put("rename.action.clone", c -> {
			c.rrect(4, 3, 11, 13, 2.5);
			c.rrect(9, 8, 11, 13, 2.5);
			c.line(14.5, 11.5, 14.5, 17.5);
			c.line(11.5, 14.5, 17.5, 14.5);
		});
		icons.put("rename.action.test", BLUE);
		draw.put("rename.action.test", c -> {
			c.circle(12, 12, 7);
			c.path(new double[] { 8.5, 11, 14.8 }, new double[] { 11, 13.8, 8.5 }, false);
		});

		// ---- service tiles (brand-ish flat tiles, white glyph) ----
		Map<String, Color> tiles = new LinkedHashMap<String, Color>();
		Map<String, Consumer<Canvas>> tileDraw = new LinkedHashMap<String, Consumer<Canvas>>();

		tiles.put("search.thetvdb", new Color(0x23A55A));
		tileDraw.put("search.thetvdb", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.fill(new double[] { 9.5, 9.5, 16 }, new double[] { 8, 16, 12 });
		});

		tiles.put("search.themoviedb", new Color(0x0B4DA2));
		tileDraw.put("search.themoviedb", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.fill(new double[] { 12, 13.8, 18.5, 13.8, 12, 10.2, 5.5, 10.2 }, new double[] { 5.5, 10.2, 12, 13.8, 18.5, 13.8, 12, 10.2 });
		});

		tiles.put("search.anidb", new Color(0x157F8F));
		tileDraw.put("search.anidb", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.line(8.2, 17.5, 12, 8);
			c.line(15.8, 17.5, 12, 8);
			c.line(9.6, 13.5, 14.4, 13.5);
		});

		tiles.put("search.opensubtitles", new Color(0x2E5AA8));
		tileDraw.put("search.opensubtitles", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.rrect(5.5, 10, 13, 5.5, 2);
			c.line(7.5, 11.7, 16.5, 11.7);
			c.line(7.5, 13.8, 12.5, 13.8);
		});

		tiles.put("search.tvmaze", new Color(0xE08A1E));
		tileDraw.put("search.tvmaze", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.rrect(5.5, 10.5, 13, 7, 1.8);
			c.line(9.5, 5.5, 12, 8.2);
			c.line(14.5, 5.5, 12, 8.2);
			c.line(10, 14, 14, 14);
		});

		tiles.put("search.omdb", new Color(0xD9762B));
		tileDraw.put("search.omdb", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.circle(11.2, 11.2, 4.2);
			c.line(14.4, 14.4, 18, 18);
		});

		tiles.put("search.xattr", new Color(0x6E7078));
		tileDraw.put("search.xattr", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.rrect(7, 4.5, 10, 15, 2.2);
			c.line(9.2, 10.5, 14.8, 10.5);
			c.line(9.2, 13.5, 12.8, 13.5);
		});

		tiles.put("search.acoustid", new Color(0x2EA76F));
		tileDraw.put("search.acoustid", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.fillCircle(9, 14.6, 2.5);
			c.line(11.5, 14.6, 11.5, 7.5);
			c.line(11.5, 7.5, 15.5, 9);
			c.line(15.5, 9, 15.5, 12.5);
		});

		// ---- status / worker icons (inline list badges) ----
		Map<String, Color> status = new LinkedHashMap<String, Color>();
		Map<String, Consumer<Canvas>> statusDraw = new LinkedHashMap<String, Consumer<Canvas>>();

		status.put("worker.pending", new Color(0x8E8E93));
		statusDraw.put("worker.pending", c -> {
			c.fillCircle(12, 12, 6.8);
			c.setColor(WHITE);
			c.fillCircle(9, 12, 1.0);
			c.fillCircle(12, 12, 1.0);
			c.fillCircle(15, 12, 1.0);
		});

		status.put("worker.started", new Color(0x0A84FF));
		statusDraw.put("worker.started", c -> {
			c.fillCircle(12, 12, 6.8);
			c.setColor(WHITE);
			c.fill(new double[] { 10.2, 10.2, 15.2 }, new double[] { 9, 15, 12 });
		});

		status.put("status.warning", new Color(0xE08A1E));
		statusDraw.put("status.warning", c -> {
			c.fill(new double[] { 12, 18.8, 5.2 }, new double[] { 5.2, 18.8, 18.8 });
			c.setColor(WHITE);
			c.line(12, 9.8, 12, 14.2);
			c.fillCircle(12, 16.6, 1.15);
		});

		status.put("dialog.continue", new Color(0x2EA76F));
		statusDraw.put("dialog.continue", c -> {
			c.fillCircle(12, 12, 7);
			c.setColor(WHITE);
			c.line(8.7, 12.2, 11.1, 14.6);
			c.line(11.1, 14.6, 15.6, 9.6);
		});

		status.put("dialog.cancel", new Color(0xFF453A));
		statusDraw.put("dialog.cancel", c -> {
			c.fillCircle(12, 12, 7);
			c.setColor(WHITE);
			c.line(9, 9, 15, 15);
			c.line(15, 9, 9, 15);
		});

		// ---- remaining legacy icons (small, inline) ----
		Map<String, Color> small = new LinkedHashMap<String, Color>();
		Map<String, Consumer<Canvas>> smallDraw = new LinkedHashMap<String, Consumer<Canvas>>();

		small.put("status.error", new Color(0xFF453A));
		smallDraw.put("status.error", c -> {
			c.fillCircle(12, 12, 7);
			c.setColor(WHITE);
			c.line(9, 9, 15, 15);
			c.line(15, 9, 9, 15);
		});

		small.put("status.info", new Color(0x0A84FF));
		smallDraw.put("status.info", c -> {
			c.fillCircle(12, 12, 7);
			c.setColor(WHITE);
			c.fillCircle(12, 11, 1.1);
			c.line(12, 13.6, 12, 17);
		});

		small.put("status.ok", new Color(0x2EA76F));
		smallDraw.put("status.ok", c -> {
			c.fillCircle(12, 12, 7);
			c.setColor(WHITE);
			c.line(8.7, 12.2, 11.1, 14.6);
			c.line(11.1, 14.6, 15.6, 9.6);
		});

		small.put("status.unknown", new Color(0x8E8E93));
		smallDraw.put("status.unknown", c -> {
			c.fillCircle(12, 12, 7);
			c.setColor(WHITE);
			c.line(9, 12, 15, 12);
		});

		small.put("status.link.ok", new Color(0x2EA76F));
		smallDraw.put("status.link.ok", c -> {
			c.fillCircle(12, 12, 7);
			c.setColor(WHITE);
			c.line(8.7, 12.2, 11.1, 14.6);
			c.line(11.1, 14.6, 15.6, 9.6);
		});

		small.put("status.link.broken", new Color(0xE08A1E));
		smallDraw.put("status.link.broken", c -> {
			c.fillCircle(12, 12, 7);
			c.setColor(WHITE);
			c.line(12, 8.5, 12, 13);
			c.fillCircle(12, 16, 1.1);
		});

		small.put("tree.expand", new Color(0x8E8E93));
		smallDraw.put("tree.expand", c -> {
			c.path(new double[] { 9.5, 14.5, 9.5 }, new double[] { 7, 12, 17 }, false);
		});

		small.put("tree.collapse", new Color(0x8E8E93));
		smallDraw.put("tree.collapse", c -> {
			c.path(new double[] { 7, 12, 17 }, new double[] { 9.5, 14.5, 9.5 }, false);
		});

		small.put("bullet.green", new Color(0x2EA76F));
		smallDraw.put("bullet.green", c -> {
			c.fillCircle(12, 12, 4);
		});

		small.put("action.match.small", new Color(0x3B82F6));
		smallDraw.put("action.match.small", c -> {
			c.circle(9.5, 12, 4.5);
			c.circle(14.5, 12, 4.5);
		});

		small.put("action.menu", new Color(0x3B82F6));
		smallDraw.put("action.menu", c -> {
			c.fillCircle(12, 6, 1.6);
			c.fillCircle(12, 12, 1.6);
			c.fillCircle(12, 18, 1.6);
		});

		small.put("search.generic", new Color(0x6E7078));
		smallDraw.put("search.generic", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.circle(11, 11, 4);
			c.line(14, 14, 17.5, 17.5);
		});

		small.put("search.exif", new Color(0x157F8F));
		smallDraw.put("search.exif", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.rrect(6.5, 6.5, 11, 11, 2);
			c.line(6.5, 15, 17.5, 15);
		});

		small.put("search.mediainfo", new Color(0x14B8A6));
		smallDraw.put("search.mediainfo", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.line(8, 12, 8, 16);
			c.line(12, 9, 12, 16);
			c.line(16, 11, 16, 16);
		});

		small.put("search.shooter", new Color(0x0B4DA2));
		smallDraw.put("search.shooter", c -> {
			c.fillRrect(3, 3, 18, 18, 5);
			c.setColor(WHITE);
			c.rrect(5, 9.5, 9, 6.5, 1.5);
			c.path(new double[] { 14, 18.5, 18.5, 14 }, new double[] { 11.5, 9, 16.5, 14 }, true);
		});

		// ---- remaining legacy icons (regular size) ----
		Map<String, Color> more = new LinkedHashMap<String, Color>();
		Map<String, Consumer<Canvas>> moreDraw = new LinkedHashMap<String, Consumer<Canvas>>();

		more.put("action.export", BLUE);
		moreDraw.put("action.export", c -> {
			c.line(12, 3.5, 12, 14.5);
			c.line(8, 10.5, 12, 14.5);
			c.line(16, 10.5, 12, 14.5);
			c.line(4.5, 18, 19.5, 18);
			c.line(4.5, 18, 4.5, 20.5);
			c.line(19.5, 18, 19.5, 20.5);
		});

		more.put("edit.clear", BLUE);
		moreDraw.put("edit.clear", c -> {
			c.rrect(4, 5, 16, 14, 3);
			c.line(8.5, 10.5, 15.5, 10.5);
			c.line(8.5, 14, 15.5, 14);
		});

		more.put("script.go", BLUE);
		moreDraw.put("script.go", c -> {
			c.rrect(4, 5, 16, 14, 3);
			c.line(9, 12, 15, 12);
			c.line(12.5, 9.5, 15, 12);
			c.line(12.5, 14.5, 15, 12);
		});

		more.put("script.add", BLUE);
		moreDraw.put("script.add", c -> {
			c.rrect(4, 5, 16, 14, 3);
			c.line(9.5, 12, 14.5, 12);
			c.line(12, 9.5, 12, 14.5);
		});

		more.put("script.cancel", new Color(0xFF453A));
		moreDraw.put("script.cancel", c -> {
			c.rrect(4, 5, 16, 14, 3);
			c.line(9.5, 9.5, 14.5, 14.5);
			c.line(14.5, 9.5, 9.5, 14.5);
		});

		more.put("package.fetch", BLUE);
		moreDraw.put("package.fetch", c -> {
			c.rrect(4, 11, 16, 9.5, 2);
			c.line(12, 2.5, 12, 11);
			c.line(9, 8, 12, 11);
			c.line(15, 8, 12, 11);
		});

		more.put("package.extract", BLUE);
		moreDraw.put("package.extract", c -> {
			c.rrect(4, 5, 16, 5, 1.5);
			c.line(12, 11.5, 12, 19.5);
			c.line(9, 16.5, 12, 19.5);
			c.line(15, 16.5, 12, 19.5);
		});

		more.put("button.repeat", BLUE);
		moreDraw.put("button.repeat", c -> {
			c.path(new double[] { 5, 5, 19, 19 }, new double[] { 13, 7.5, 7.5, 11 }, false);
			c.path(new double[] { 16, 19, 16 }, new double[] { 4.5, 7.5, 10.5 }, false);
			c.path(new double[] { 19, 19, 5, 5 }, new double[] { 11, 16.5, 16.5, 13 }, false);
			c.path(new double[] { 8, 5, 8 }, new double[] { 13.5, 16.5, 19.5 }, false);
		});

		more.put("button.repeat.selected", BLUE);
		moreDraw.put("button.repeat.selected", c -> {
			c.fillCircle(12, 12, 7.5);
		});

		more.put("dialog.switch", BLUE);
		moreDraw.put("dialog.switch", c -> {
			c.rrect(2.5, 8, 19, 8, 4);
			c.fillCircle(8, 12, 2.6);
		});

		more.put("dialog.continue.invalid", new Color(0x8E8E93));
		moreDraw.put("dialog.continue.invalid", c -> {
			c.circle(12, 12, 7);
			c.line(9, 12, 15, 12);
		});

		more.put("database.ok", new Color(0x2EA76F));
		moreDraw.put("database.ok", c -> {
			c.ellipse(5.5, 3, 13, 5);
			c.ellipseArc(5.5, 9, 13, 5, 180, 180);
			c.ellipseArc(5.5, 16, 13, 5, 180, 180);
			c.line(5.5, 5.5, 5.5, 18.5);
			c.line(18.5, 5.5, 18.5, 18.5);
		});

		more.put("database.error", new Color(0xFF453A));
		moreDraw.put("database.error", c -> {
			c.ellipse(5.5, 3, 13, 5);
			c.ellipseArc(5.5, 9, 13, 5, 180, 180);
			c.ellipseArc(5.5, 16, 13, 5, 180, 180);
			c.line(5.5, 5.5, 5.5, 18.5);
			c.line(18.5, 5.5, 18.5, 18.5);
			c.line(10, 13, 14, 17);
			c.line(14, 13, 10, 17);
		});

		more.put("database.go", BLUE);
		moreDraw.put("database.go", c -> {
			c.ellipse(5.5, 3, 13, 5);
			c.ellipseArc(5.5, 9, 13, 5, 180, 180);
			c.ellipseArc(5.5, 16, 13, 5, 180, 180);
			c.line(5.5, 5.5, 5.5, 18.5);
			c.line(18.5, 5.5, 18.5, 18.5);
			c.line(9, 13.5, 15, 13.5);
			c.line(12.5, 11, 15, 13.5);
			c.line(12.5, 16, 15, 13.5);
		});

		for (Map.Entry<String, Color> e : icons.entrySet()) {
			export(e.getKey(), e.getValue(), 16, draw.get(e.getKey()));
		}

		for (Map.Entry<String, Color> e : tiles.entrySet()) {
			export(e.getKey(), e.getValue(), 16, tileDraw.get(e.getKey()));
		}

		for (Map.Entry<String, Color> e : status.entrySet()) {
			export(e.getKey(), e.getValue(), 16, statusDraw.get(e.getKey()));
		}

		for (Map.Entry<String, Color> e : small.entrySet()) {
			export(e.getKey(), e.getValue(), 16, smallDraw.get(e.getKey()));
		}

		for (Map.Entry<String, Color> e : more.entrySet()) {
			export(e.getKey(), e.getValue(), 16, moreDraw.get(e.getKey()));
		}
	}
}