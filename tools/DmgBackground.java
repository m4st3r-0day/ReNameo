import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Background of the macOS disk image window: where to drag the app, and how to open it the first time.
 *
 * Usage: java tools/DmgBackground.java packaging/macos/dmg-background.png 1 (and 2 for the @2x Retina version)
 *
 * The window is 660 x 420 points; the app icon sits at (165, 205) and Applications at (495, 205), see tools/dmg-settings.py.
 */
public class DmgBackground {

	public static void main(String[] args) throws Exception {
		File out = new File(args[0]);
		int scale = Integer.parseInt(args[1]);
		int w = 660, h = 420;

		BufferedImage image = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.scale(scale, scale);

		// light, so the Finder's icon labels stay readable
		g.setPaint(new GradientPaint(0, 0, new Color(0xF8FAFD), 0, h, new Color(0xE9EEF7)));
		g.fillRect(0, 0, w, h);

		// title
		Font title = new Font("Helvetica Neue", Font.BOLD, 22);
		Font text = new Font("Helvetica Neue", Font.PLAIN, 13);
		center(g, "Install ReNameo", title, new Color(0x161B2E), 62, w);
		center(g, "Drag the app onto the Applications folder", text, new Color(0x5B6478), 86, w);

		// arrow from the app to Applications, in the app's blue to violet
		int y = 200;
		float x1 = 252, x2 = 404;
		g.setPaint(new GradientPaint(x1, y, new Color(0x3B82F6), x2, y, new Color(0x8B5CF6)));
		g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawLine((int) x1, y, (int) x2 - 6, y);
		GeneralPath head = new GeneralPath();
		head.moveTo(x2 + 6, y);
		head.lineTo(x2 - 12, y - 13);
		head.lineTo(x2 - 12, y + 13);
		head.closePath();
		g.setColor(new Color(0x8B5CF6));
		g.fill(head);

		// first start without notarization
		Font small = new Font("Helvetica Neue", Font.PLAIN, 12).deriveFont(11.5f);
		center(g, "First start: if macOS blocks it, open System Settings → Privacy & Security → Open Anyway.", small, new Color(0x6B7385), 372, w);
		center(g, "Settings, keys, history and plugins are kept when you update.", small, new Color(0x8A91A1), 390, w);

		g.dispose();
		ImageIO.write(image, "png", out);
	}

	private static void center(Graphics2D g, String s, Font font, Color color, int baseline, int width) {
		g.setFont(font);
		g.setColor(color);
		FontMetrics fm = g.getFontMetrics();
		g.drawString(s, (width - fm.stringWidth(s)) / 2f, baseline);
	}

}
