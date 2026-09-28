package net.renameo.util.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;

/**
 * Small toolkit of flat, rounded components that paint themselves from {@link NightTheme} colors, so they look the same with every look and feel.
 */
public final class Modern {

	public enum Style {
		PRIMARY, VIOLET, SECONDARY, GHOST, TILE
	}

	private static final String STYLE_PROPERTY = "modern.style";
	private static final String REFRESH_PROPERTY = "modern.refresh";

	public static final int RADIUS = 12;

	private Modern() {
		throw new UnsupportedOperationException();
	}

	public static Font font(float size, int style) {
		Font base = UIManager.getFont("defaultFont");
		if (base == null) {
			base = UIManager.getFont("Label.font");
		}
		if (base == null) {
			base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
		}
		return base.deriveFont(style, size);
	}

	public static <T extends AbstractButton> T style(T button, Style style) {
		button.putClientProperty(STYLE_PROPERTY, style);
		button.setUI(new ModernButtonUI(style));
		return button;
	}

	/**
	 * Button whose icon stays fixed even if the backing action swaps its own icon later on.
	 */
	public static JButton button(Action action, Icon icon, Style style) {
		JButton button = new JButton(action) {

			@Override
			protected void actionPropertyChanged(Action action, String propertyName) {
				if (!Action.SMALL_ICON.equals(propertyName) && !Action.LARGE_ICON_KEY.equals(propertyName)) {
					super.actionPropertyChanged(action, propertyName);
				}
			}
		};
		button.setIcon(icon);
		return style(button, style);
	}

	public static JButton iconButton(Action action, Glyph.Shape shape, String tooltip) {
		JButton button = button(action, Glyph.of(shape, 16, NightTheme::getDimForeground), Style.GHOST);
		button.setHideActionText(true);
		button.setToolTipText(tooltip != null ? tooltip : String.valueOf(action.getValue(Action.NAME)));
		return button;
	}

	/**
	 * Register a callback that re-applies explicitly set colors after the theme changed.
	 */
	public static <T extends JComponent> T onThemeChange(T component, Runnable refresh) {
		Object previous = component.getClientProperty(REFRESH_PROPERTY);
		Runnable chain = previous instanceof Runnable ? () -> {
			((Runnable) previous).run();
			refresh.run();
		} : refresh;
		component.putClientProperty(REFRESH_PROPERTY, chain);
		refresh.run();
		return component;
	}

	/**
	 * Called after {@code SwingUtilities.updateComponentTreeUI} because it replaces every custom button UI with the look and feel default.
	 */
	public static void reinstall(Component component) {
		if (component instanceof JComponent) {
			JComponent c = (JComponent) component;
			Object style = c.getClientProperty(STYLE_PROPERTY);
			if (style instanceof Style && c instanceof AbstractButton) {
				((AbstractButton) c).setUI(new ModernButtonUI((Style) style));
			}
			Object refresh = c.getClientProperty(REFRESH_PROPERTY);
			if (refresh instanceof Runnable) {
				((Runnable) refresh).run();
			}
			if (c.getComponentPopupMenu() != null) {
				reinstall(c.getComponentPopupMenu());
			}
		}
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				reinstall(child);
			}
		}
	}

	/**
	 * Thin, rounded scroll bars in the theme colors instead of the look and feel ones.
	 */
	public static <T extends javax.swing.JScrollPane> T slim(T scroll) {
		return onThemeChange(scroll, () -> {
			for (javax.swing.JScrollBar bar : new javax.swing.JScrollBar[] { scroll.getVerticalScrollBar(), scroll.getHorizontalScrollBar() }) {
				bar.setUI(new SlimScrollBarUI());
				bar.setOpaque(false);
				bar.setPreferredSize(bar.getOrientation() == javax.swing.JScrollBar.VERTICAL ? new Dimension(10, 0) : new Dimension(0, 10));
				bar.setUnitIncrement(16);
			}
		});
	}

	private static class SlimScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {

		@Override
		protected javax.swing.JButton createDecreaseButton(int orientation) {
			return zeroButton();
		}

		@Override
		protected javax.swing.JButton createIncreaseButton(int orientation) {
			return zeroButton();
		}

		private static javax.swing.JButton zeroButton() {
			javax.swing.JButton b = new javax.swing.JButton();
			b.setPreferredSize(new Dimension(0, 0));
			b.setMinimumSize(new Dimension(0, 0));
			b.setMaximumSize(new Dimension(0, 0));
			return b;
		}

		@Override
		protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
			// transparent track
		}

		@Override
		protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
			if (r.isEmpty()) {
				return;
			}
			Graphics2D g2d = smooth(g);
			g2d.setColor(isThumbRollover() ? mix(NightTheme.getBorder(), NightTheme.getDimForeground(), 0.6f) : mix(NightTheme.getBorder(), NightTheme.getDimForeground(), 0.25f));
			int w = r.width - 4, h = r.height - 4;
			g2d.fill(new RoundRectangle2D.Float(r.x + 2, r.y + 2, w, h, Math.min(w, h), Math.min(w, h)));
			g2d.dispose();
		}
	}

	/**
	 * Rounded field look for spinners: theme colors and small chevrons instead of the look and feel arrow buttons.
	 */
	public static javax.swing.JSpinner spinner(javax.swing.JSpinner spinner) {
		return onThemeChange(spinner, () -> {
			spinner.setUI(new javax.swing.plaf.basic.BasicSpinnerUI() {

				@Override
				protected Component createNextButton() {
					return arrow(Glyph.Shape.CHEVRON_DOWN, true, "Spinner.nextButton", this::installNextButtonListeners);
				}

				@Override
				protected Component createPreviousButton() {
					return arrow(Glyph.Shape.CHEVRON_DOWN, false, "Spinner.previousButton", this::installPreviousButtonListeners);
				}

				private Component arrow(Glyph.Shape shape, boolean up, String name, java.util.function.Consumer<Component> install) {
					JButton b = new JButton(new Icon() {

						private final Icon glyph = Glyph.of(shape, 11, NightTheme::getDimForeground);

						@Override
						public void paintIcon(Component c, Graphics g, int x, int y) {
							Graphics2D g2d = (Graphics2D) g.create();
							if (up) {
								g2d.rotate(Math.PI, x + 5.5, y + 5.5); // chevron up
							}
							glyph.paintIcon(c, g2d, x, y);
							g2d.dispose();
						}

						@Override
						public int getIconWidth() {
							return 11;
						}

						@Override
						public int getIconHeight() {
							return 11;
						}
					});
					b.setName(name);
					b.setUI(new BasicButtonUI());
					b.setOpaque(false);
					b.setContentAreaFilled(false);
					b.setBorder(new EmptyBorder(0, 4, 0, 8));
					b.setFocusable(false);
					install.accept(b);
					return b;
				}
			});
			spinner.setOpaque(false);
			spinner.setBorder(new javax.swing.border.AbstractBorder() {

				@Override
				public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
					Graphics2D g2d = smooth(g);
					RoundRectangle2D shape = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 12, 12);
					g2d.setColor(NightTheme.getFieldBackground());
					g2d.fill(shape);
					g2d.setColor(NightTheme.getBorder());
					g2d.draw(shape);
					g2d.dispose();
				}

				@Override
				public java.awt.Insets getBorderInsets(Component c) {
					return new java.awt.Insets(1, 10, 1, 2);
				}
			});
			if (spinner.getEditor() instanceof javax.swing.JSpinner.DefaultEditor) {
				JTextField field = ((javax.swing.JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
				field.setUI(new javax.swing.plaf.basic.BasicTextFieldUI());
				field.setOpaque(false);
				field.setBorder(new EmptyBorder(0, 0, 0, 0));
				field.setForeground(NightTheme.getForeground());
				field.setCaretColor(NightTheme.getForeground());
				field.setFont(font(13f, Font.PLAIN));
				spinner.getEditor().setOpaque(false);
			}
		});
	}

	/**
	 * Marks a component tree that paints itself, so {@link #modernize(Component)} leaves it alone.
	 */
	public static final String CUSTOM = "modern.custom";

	/**
	 * Bring a generic Swing panel in line with the rest of the app: titled borders become cards, plain buttons, spinners and text fields get the flat theme style, and lists, tables and trees sit on the card color.
	 */
	public static void modernize(Component component) {
		if (component instanceof JComponent) {
			JComponent c = (JComponent) component;
			if (Boolean.TRUE.equals(c.getClientProperty(CUSTOM))) {
				return;
			}

			if (c.getBorder() instanceof javax.swing.border.TitledBorder) {
				c.setBorder(new CardBorder(((javax.swing.border.TitledBorder) c.getBorder()).getTitle()));
			}

			if (c instanceof javax.swing.JScrollPane) {
				javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) c;
				if (scroll.getBorder() == null || scroll.getBorder() instanceof javax.swing.plaf.UIResource) {
					scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
				}
				scroll.getViewport().setBackground(NightTheme.getCardBackground());
				if (!Boolean.TRUE.equals(scroll.getClientProperty("modern.slim"))) {
					scroll.putClientProperty("modern.slim", true);
					slim(scroll);
				}
			} else if (c instanceof javax.swing.JList || c instanceof javax.swing.JTable || c instanceof javax.swing.JTree) {
				c.setBackground(NightTheme.getCardBackground());
				c.setForeground(NightTheme.getForeground());
			} else if (c instanceof javax.swing.JSpinner) {
				if (!Boolean.TRUE.equals(c.getClientProperty("modern.spinner"))) {
					c.putClientProperty("modern.spinner", true);
					spinner((javax.swing.JSpinner) c);
				}
				return;
			} else if (c.getClass() == JButton.class && c.getClientProperty(STYLE_PROPERTY) == null) {
				style((JButton) c, Style.SECONDARY);
			} else if (c.getClass() == JTextField.class) {
				field((JTextField) c);
			} else if (c instanceof JLabel) {
				c.setOpaque(false);
			} else if (c.getClass() == JPanel.class) {
				c.setOpaque(false); // plain layout panels must not paint over cards
			}
		}

		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				modernize(child);
			}
		}
	}

	/**
	 * Rounded field look for a plain text field.
	 */
	public static JTextField field(JTextField field) {
		// the rounded background is painted by the UI before the text; a border would paint over the text
		field.setUI(field instanceof javax.swing.JPasswordField ? new RoundPasswordFieldUI() : new RoundTextFieldUI());
		field.setOpaque(false);
		field.setForeground(NightTheme.getForeground());
		field.setCaretColor(NightTheme.getForeground());
		field.setSelectionColor(alpha(NightTheme.getAccent(), 110));
		field.setFont(font(13f, Font.PLAIN));
		field.setBorder(fieldOutline());
		return field;
	}

	private static void paintFieldBackground(Graphics g, JComponent c) {
		Graphics2D g2d = smooth(g);
		g2d.setColor(NightTheme.getFieldBackground());
		g2d.fill(new RoundRectangle2D.Float(0.5f, 0.5f, c.getWidth() - 1, c.getHeight() - 1, 10, 10));
		g2d.dispose();
	}

	private static class RoundTextFieldUI extends javax.swing.plaf.basic.BasicTextFieldUI {

		@Override
		protected void paintSafely(Graphics g) {
			paintFieldBackground(g, getComponent());
			super.paintSafely(g);
		}
	}

	private static class RoundPasswordFieldUI extends javax.swing.plaf.basic.BasicPasswordFieldUI {

		@Override
		protected void paintSafely(Graphics g) {
			paintFieldBackground(g, getComponent());
			super.paintSafely(g);
		}
	}

	/**
	 * Only the rounded outline, for components that paint their own background.
	 */
	private static javax.swing.border.Border fieldOutline() {
		return new javax.swing.border.AbstractBorder() {

			@Override
			public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
				Graphics2D g2d = smooth(g);
				g2d.setColor(c.hasFocus() ? NightTheme.getAccent() : NightTheme.getBorder());
				g2d.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 10, 10));
				g2d.dispose();
			}

			@Override
			public java.awt.Insets getBorderInsets(Component c) {
				return new java.awt.Insets(6, 10, 6, 10);
			}
		};
	}

	/**
	 * Rounded field background and outline, accent colored while the component has focus.
	 */
	public static javax.swing.border.Border fieldBorder() {
		return new javax.swing.border.AbstractBorder() {

			@Override
			public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
				Graphics2D g2d = smooth(g);
				RoundRectangle2D shape = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 10, 10);
				g2d.setColor(NightTheme.getFieldBackground());
				g2d.fill(shape);
				g2d.setColor(c.hasFocus() ? NightTheme.getAccent() : NightTheme.getBorder());
				g2d.draw(shape);
				g2d.dispose();
			}

			@Override
			public java.awt.Insets getBorderInsets(Component c) {
				return new java.awt.Insets(6, 10, 6, 10);
			}
		};
	}

	/**
	 * Card surface with a title, replacing the look and feel titled border.
	 */
	public static class CardBorder extends javax.swing.border.AbstractBorder {

		private final String title;

		public CardBorder(String title) {
			this.title = title == null || title.isEmpty() ? null : title;
		}

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
			Graphics2D g2d = smooth(g);
			RoundRectangle2D shape = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 16, 16);
			g2d.setColor(NightTheme.getCardBackground());
			g2d.fill(shape);
			g2d.setColor(NightTheme.getBorder());
			g2d.draw(shape);
			if (title != null) {
				g2d.setFont(font(14f, Font.BOLD));
				g2d.setColor(NightTheme.getForeground());
				g2d.drawString(title, x + 16, y + 26);
			}
			g2d.dispose();
		}

		@Override
		public java.awt.Insets getBorderInsets(Component c) {
			return new java.awt.Insets(title == null ? 10 : 38, 10, 10, 10);
		}

		@Override
		public boolean isBorderOpaque() {
			return false;
		}
	}

	public static JLabel caption(String text) {
		JLabel label = new JLabel(text.toUpperCase());
		label.setOpaque(false);
		label.setFont(font(10.5f, Font.BOLD));
		return onThemeChange(label, () -> label.setForeground(NightTheme.getDimForeground()));
	}

	public static JLabel label(String text, float size, int style, boolean dim) {
		JLabel label = new JLabel(text);
		label.setOpaque(false);
		label.setFont(font(size, style));
		return onThemeChange(label, () -> label.setForeground(dim ? NightTheme.getDimForeground() : NightTheme.getForeground()));
	}

	public static Color alpha(Color c, int alpha) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}

	public static Color mix(Color a, Color b, float f) {
		return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * f), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * f), (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * f));
	}

	public static Graphics2D smooth(Graphics g) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return g2d;
	}

	/**
	 * Paints a rounded chip and returns its width.
	 */
	public static int paintChip(Graphics2D g, String text, int x, int y, int height, Font font, Color background, Color foreground) {
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int width = fm.stringWidth(text) + 14;
		g.setColor(background);
		g.fill(new RoundRectangle2D.Float(x, y, width, height, 8, 8));
		g.setColor(foreground);
		g.drawString(text, x + 7, y + (height - fm.getHeight()) / 2 + fm.getAscent());
		return width;
	}

	public static int chipWidth(Graphics2D g, String text, Font font) {
		return g.getFontMetrics(font).stringWidth(text) + 14;
	}

	/**
	 * Rounded surface used for all content containers.
	 */
	public static class Card extends JPanel {

		private final Supplier<Color> fill;
		private final int radius;

		public Card(LayoutManager layout) {
			this(layout, NightTheme::getCardBackground, 16);
		}

		public Card(LayoutManager layout, Supplier<Color> fill, int radius) {
			super(layout);
			this.fill = fill;
			this.radius = radius;
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = smooth(g);
			RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, radius, radius);
			g2d.setColor(fill.get());
			g2d.fill(shape);
			g2d.setColor(NightTheme.getBorder());
			g2d.draw(shape);
			g2d.dispose();
		}
	}

	/**
	 * Small rounded counter such as "6 files".
	 */
	public static class Badge extends JLabel {

		public Badge(String text) {
			super(text);
			setOpaque(false);
			setFont(font(11f, Font.PLAIN));
			setBorder(new EmptyBorder(3, 9, 3, 9));
			onThemeChange(this, () -> setForeground(NightTheme.getDimForeground()));
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = smooth(g);
			g2d.setColor(NightTheme.getControlColor());
			g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
			g2d.dispose();
			super.paintComponent(g);
		}
	}

	/**
	 * Rounded search box with a leading magnifier, placeholder and trailing shortcut hint.
	 */
	public static class SearchField extends JTextField {

		private final String placeholder;
		private final String shortcut;
		private final Icon icon = Glyph.of(Glyph.Shape.SEARCH, 15, NightTheme::getDimForeground);

		public SearchField(String placeholder, String shortcut) {
			this.placeholder = placeholder;
			this.shortcut = shortcut;
			setOpaque(false);
			setFont(font(13f, Font.PLAIN));
			setBorder(new EmptyBorder(7, 34, 7, 44));
			onThemeChange(this, () -> {
				setForeground(NightTheme.getForeground());
				setCaretColor(NightTheme.getForeground());
				setSelectionColor(alpha(NightTheme.getAccent(), 110));
				setSelectedTextColor(NightTheme.getForeground());
			});
			addFocusListener(new FocusAdapter() {

				@Override
				public void focusGained(FocusEvent e) {
					repaint();
				}

				@Override
				public void focusLost(FocusEvent e) {
					repaint();
				}
			});
		}

		@Override
		public void updateUI() {
			// the basic UI leaves painting to us, look and feel painters would cover icon and placeholder
			setUI(new javax.swing.plaf.basic.BasicTextFieldUI());
			setOpaque(false);
			setBorder(new EmptyBorder(7, 34, 7, 44));
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = smooth(g);
			RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 12, 12);
			g2d.setColor(NightTheme.getFieldBackground());
			g2d.fill(shape);
			g2d.setColor(hasFocus() ? NightTheme.getAccent() : NightTheme.getBorder());
			g2d.draw(shape);

			icon.paintIcon(this, g2d, 12, (getHeight() - icon.getIconHeight()) / 2);

			FontMetrics fm = g2d.getFontMetrics(getFont());
			int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

			if (getText().isEmpty() && placeholder != null) {
				g2d.setFont(getFont());
				g2d.setColor(NightTheme.getDimForeground());
				g2d.drawString(placeholder, getInsets().left, baseline);
			}

			if (shortcut != null) {
				Font hint = font(11f, Font.PLAIN);
				int w = chipWidth(g2d, shortcut, hint);
				paintChip(g2d, shortcut, getWidth() - w - 8, (getHeight() - 20) / 2, 20, hint, NightTheme.getControlColor(), NightTheme.getDimForeground());
			}
			g2d.dispose();

			super.paintComponent(g);
		}
	}

	/**
	 * Flat rounded button painting driven by a {@link Style}.
	 */
	public static class ModernButtonUI extends BasicButtonUI {

		private final Style style;

		public ModernButtonUI(Style style) {
			this.style = style;
		}

		@Override
		protected void installDefaults(AbstractButton b) {
			super.installDefaults(b);
			b.setOpaque(false);
			b.setBorderPainted(false);
			b.setFocusPainted(false);
			b.setContentAreaFilled(false);
			b.setRolloverEnabled(true);
			b.setIconTextGap(8);

			boolean iconOnly = b.getHideActionText() || b.getText() == null || b.getText().isEmpty();
			switch (style) {
			case TILE:
				b.setFont(font(12f, Font.BOLD));
				b.setBorder(new EmptyBorder(12, 14, 10, 14));
				b.setVerticalTextPosition(SwingConstants.BOTTOM);
				b.setHorizontalTextPosition(SwingConstants.CENTER);
				break;
			case GHOST:
				b.setFont(font(13f, Font.PLAIN));
				b.setBorder(iconOnly ? new EmptyBorder(6, 6, 6, 6) : new EmptyBorder(7, 10, 7, 10));
				break;
			default:
				b.setFont(font(13f, Font.BOLD));
				b.setBorder(iconOnly ? new EmptyBorder(9, 10, 9, 10) : new EmptyBorder(9, 16, 9, 18));
				break;
			}
		}

		@Override
		public void paint(Graphics g, JComponent c) {
			AbstractButton b = (AbstractButton) c;
			ButtonModel model = b.getModel();
			boolean hover = model.isRollover() && b.isEnabled();
			boolean pressed = model.isArmed() && model.isPressed();

			Graphics2D g2d = smooth(g);
			if (!b.isEnabled()) {
				g2d.setComposite(AlphaComposite.SrcOver.derive(0.45f));
			}

			RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, c.getWidth() - 1, c.getHeight() - 1, RADIUS, RADIUS);
			Color fill = null;
			Color line = null;
			Color text = NightTheme.getForeground();

			switch (style) {
			case PRIMARY:
			case VIOLET:
				Color base = style == Style.PRIMARY ? NightTheme.getAccent() : NightTheme.getSecondaryAccent();
				fill = pressed ? mix(base, Color.BLACK, 0.15f) : hover ? mix(base, Color.WHITE, 0.12f) : base;
				text = Color.WHITE;
				break;
			case SECONDARY:
				if (model.isSelected()) {
					fill = alpha(NightTheme.getAccent(), 60);
					line = NightTheme.getAccent();
					break;
				}
				fill = pressed ? NightTheme.getControlColor() : hover ? NightTheme.getCardHover() : NightTheme.getCardBackground();
				line = hover ? mix(NightTheme.getBorder(), NightTheme.getDimForeground(), 0.3f) : NightTheme.getBorder();
				break;
			case TILE:
				fill = pressed ? NightTheme.getControlColor() : hover ? NightTheme.getCardHover() : NightTheme.getCardBackground();
				line = NightTheme.getBorder();
				break;
			case GHOST:
				fill = pressed ? NightTheme.getControlColor() : hover ? NightTheme.getCardHover() : null;
				text = hover ? NightTheme.getForeground() : NightTheme.getDimForeground();
				break;
			}

			if (fill != null) {
				g2d.setColor(fill);
				g2d.fill(shape);
			}
			if (line != null) {
				g2d.setColor(line);
				g2d.setStroke(new BasicStroke(1f));
				g2d.draw(shape);
			}
			if (b.isFocusOwner() && style != Style.GHOST) {
				g2d.setColor(alpha(NightTheme.getAccent(), 140));
				g2d.setStroke(new BasicStroke(2f));
				g2d.draw(new RoundRectangle2D.Float(1, 1, c.getWidth() - 2, c.getHeight() - 2, RADIUS, RADIUS));
			}

			b.setForeground(text);
			super.paint(g2d, c);
			g2d.dispose();
		}

		@Override
		protected void paintText(Graphics g, AbstractButton b, java.awt.Rectangle textRect, String text) {
			g.setColor(b.getForeground());
			FontMetrics fm = g.getFontMetrics(b.getFont());
			g.setFont(b.getFont());
			g.drawString(text, textRect.x, textRect.y + fm.getAscent());
		}

		@Override
		public Dimension getPreferredSize(JComponent c) {
			Dimension d = super.getPreferredSize(c);
			if (style != Style.GHOST && style != Style.TILE) {
				d.height = Math.max(d.height, 36);
			}
			return d;
		}
	}

}
