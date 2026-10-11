package net.renameo.util.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.View;

/**
 * Plain text that wraps at the width the layout gives it, and is just as high as its lines (an HTML label would be cut off, a plain text area would ask for
 * one word per line before it knows its width).
 */
public class WrappingText extends JTextArea {

	private int lastWidth = -1;

	public WrappingText(String text, float size) {
		super(text);
		setLineWrap(true);
		setWrapStyleWord(true);
		setEditable(false);
		setFocusable(false);
		Modern.onThemeChange(this, () -> {
			// the Nimbus text area paints a background even when not opaque
			setUI(new BasicTextAreaUI());
			setOpaque(false);
			setBorder(null);
			setFont(Modern.font(size, Font.PLAIN));
			setForeground(NightTheme.getDimForeground());
		});
	}

	@Override
	public Dimension getPreferredSize() {
		int width = getWidth() > 0 ? getWidth() : 400;
		View root = getUI().getRootView(this);
		root.setSize(width, Float.MAX_VALUE);
		Insets insets = getInsets();
		return new Dimension(80, (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom);
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(0, getPreferredSize().height);
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
		// another width means another height: lay out again
		if (width != lastWidth) {
			lastWidth = width;
			SwingUtilities.invokeLater(this::revalidate);
		}
	}

}
