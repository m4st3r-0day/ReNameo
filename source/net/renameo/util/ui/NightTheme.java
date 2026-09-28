package net.renameo.util.ui;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Painter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import net.renameo.Settings;

public final class NightTheme {

	private static final String PREF_KEY = "night.mode.enabled";
	private static final String PREF_ACCENT = "ui.accent";
	private static final String PREF_COMPACT = "ui.compact.rows";

	private static Color customAccent = null;

	// dark color scheme (deep navy, flat)
	private static final Color BG = new Color(0x0D111C);
	private static final Color BG_DARK = new Color(0x0A0E17);
	private static final Color BG_LIGHT = new Color(0x1A2133);
	private static final Color CARD = new Color(0x121827);
	private static final Color CARD_HOVER = new Color(0x182034);
	private static final Color FG = new Color(0xE8ECF4);
	private static final Color FG_DIM = new Color(0x8B93A7);
	private static final Color ACCENT = new Color(0x3B82F6);
	private static final Color VIOLET = new Color(0x8B5CF6);
	private static final Color BORDER = new Color(0x232B3E);
	private static final Color SIDEBAR = new Color(0x0F1420);
	private static final Color HEADER = new Color(0x0F1420);

	// light color scheme
	private static final Color LIGHT_BG = new Color(0xF4F6FA);
	private static final Color LIGHT_BG_DARK = new Color(0xFFFFFF);
	private static final Color LIGHT_BG_LIGHT = new Color(0xEBEEF4);
	private static final Color LIGHT_CARD = new Color(0xFFFFFF);
	private static final Color LIGHT_CARD_HOVER = new Color(0xF3F5F9);
	private static final Color LIGHT_FG = new Color(0x151A26);
	private static final Color LIGHT_FG_DIM = new Color(0x667085);
	private static final Color LIGHT_ACCENT = new Color(0x2563EB);
	private static final Color LIGHT_VIOLET = new Color(0x7C3AED);
	private static final Color LIGHT_BORDER = new Color(0xE2E6EE);
	private static final Color LIGHT_SIDEBAR = new Color(0xEEF1F6);
	private static final Color LIGHT_HEADER = new Color(0xF8F9FC);

	private static final Color SUCCESS = new Color(0x22C55E);
	private static final Color WARNING = new Color(0xF59E0B);
	private static final Color DANGER = new Color(0xEF4444);

	private static final int ARC = 8;

	private NightTheme() {
	}

	public static boolean isNightMode() {
		String override = System.getProperty("renameo.night.mode");
		if (override != null && override.length() > 0) {
			return Boolean.parseBoolean(override);
		}
		return Boolean.parseBoolean(Settings.forPackage(NightTheme.class).get(PREF_KEY, "true"));
	}

	public static void setNightMode(boolean enabled) {
		Settings.forPackage(NightTheme.class).put(PREF_KEY, String.valueOf(enabled));
		apply(enabled);
	}

	public static void applyNightModeIfEnabled() {
		loadCustomAccent();
		apply(isNightMode());
	}

	public static void apply(boolean dark) {
		// apply and re-set the LAF so that any previously installed overrides are discarded on revert back to light mode
		try {
			if (dark) {
				SwingUI.setNimbusLookAndFeel();
				installDarkDefaults();
				installFlatPainters(dark);
			} else if (Settings.isPortableApp()) {
				SwingUI.setNimbusLookAndFeel();
				installLightDefaults();
				installFlatPainters(dark);
			} else {
				SwingUI.setSystemLookAndFeel();
			}

			// apply modern UI font
			SwingUI.installModernUIFont();

			// re-skin all open windows
			for (Frame frame : JFrame.getFrames()) {
				SwingUtilities.updateComponentTreeUI(frame);
				Modern.reinstall(frame);
				frame.repaint();
			}
		} catch (Exception e) {
			// never let theme issues break the application
		}
	}

	/**
	 * Accent color used for selection, focus and highlights. Falls back to the system blue unless the user has picked a custom color.
	 */
	private static Color resolveAccent(boolean dark) {
		if (customAccent != null) {
			return customAccent;
		}

		return dark ? ACCENT : LIGHT_ACCENT;
	}

	public static Color getCustomAccent() {
		return customAccent;
	}

	/** Preset highlight colors offered in the View menu */
	public static final Color[] ACCENT_PRESETS = {
			ACCENT,
			new Color(0x7D5FFF), // violet
			new Color(0x00A6A6), // teal
			new Color(0x30B455), // green
			new Color(0xE0A300), // amber
			new Color(0xE5484D), // red
			new Color(0xD96BA0)  // pink
	};

	public static final String[] ACCENT_PRESET_NAMES = { "Blue", "Violet", "Teal", "Green", "Amber", "Red", "Pink" };

	public static void setAccent(Color color) {
		if (color == null) {
			customAccent = null;
			Settings.forPackage(NightTheme.class).remove(PREF_ACCENT);
		} else {
			customAccent = color;
			Settings.forPackage(NightTheme.class).put(PREF_ACCENT, String.format("%06X", color.getRGB() & 0xFFFFFF));
		}

		apply(isNightMode());
	}

	public static void loadCustomAccent() {
		customAccent = null;

		String hex = Settings.forPackage(NightTheme.class).get(PREF_ACCENT, null);
		if (hex != null && hex.length() == 6) {
			try {
				customAccent = new Color(Integer.parseInt(hex, 16));
			} catch (NumberFormatException e) {
				customAccent = null;
			}
		}
	}

	/**
	 * Row density: compact rows for large libraries, comfortable rows otherwise.
	 */
	public static boolean isCompact() {
		return Boolean.parseBoolean(Settings.forPackage(NightTheme.class).get(PREF_COMPACT, "false"));
	}

	public static void setCompact(boolean compact) {
		Settings.forPackage(NightTheme.class).put(PREF_COMPACT, String.valueOf(compact));
		apply(isNightMode());
	}

	public static int getRowHeight() {
		return isCompact() ? 22 : 28;
	}

	private static void put(String key, Object value) {
		UIManager.put(key, value);
		UIManager.getLookAndFeelDefaults().put(key, value);
	}

	private static void putColor(String key, Color color) {
		put(key, color);
	}

	private static void installDarkDefaults() {
		installDefaults(true);
	}

	private static void installLightDefaults() {
		installDefaults(false);
	}

	private static void installDefaults(boolean dark) {
		Color bg = dark ? BG : LIGHT_BG;
		Color bgDark = dark ? BG_DARK : LIGHT_BG_DARK;
		Color bgLight = dark ? BG_LIGHT : LIGHT_BG_LIGHT;
		Color fg = dark ? FG : LIGHT_FG;
		Color fgDim = dark ? FG_DIM : LIGHT_FG_DIM;
		Color accent = resolveAccent(dark);
		Color border = dark ? BORDER : LIGHT_BORDER;

		// Nimbus base colors
		putColor("nimbusBase", dark ? BG_LIGHT : new Color(0xA0A8B2));
		putColor("nimbusBlueGrey", dark ? BG_LIGHT : new Color(0xE2E5EA));
		putColor("nimbusLightBackground", bg);
		putColor("nimbusSelectionBackground", accent);
		putColor("nimbusSelectionForeground", Color.WHITE);
		putColor("nimbusSelectedText", dark ? BG_DARK : new Color(0xE6E9EE));
		putColor("nimbusFocus", accent);
		putColor("nimbusOrange", accent);
		putColor("nimbusBorder", border);
		putColor("nimbusDisabledText", fgDim);
		putColor("nimbusControlShadow", dark ? BORDER : new Color(0xB7BDC5));
		putColor("nimbusControlDarkShadow", dark ? BG_LIGHT : new Color(0x9AA1AA));
		putColor("nimbusControlHighlight", dark ? CARD_HOVER : new Color(0xFFFFFF));
		putColor("nimbusInfoBlue", accent);

		// generic controls
		putColor("control", bgLight);
		putColor("text", fg);
		putColor("textForeground", fg);
		putColor("textHighlight", accent);
		putColor("textHighlightText", Color.WHITE);
		putColor("textInactiveText", fgDim);
		putColor("desktop", bg);
		putColor("info", dark ? CARD : new Color(0xFFFFFF));
		putColor("infoText", fg);

		// panels and windows
		putColor("Panel.background", bg);
		putColor("Panel.foreground", fg);
		putColor("Frame.background", bg);
		putColor("Window.background", bg);
		putColor("Dialog.background", bg);
		putColor("OptionPane.background", bg);
		putColor("OptionPane.messageForeground", fg);
		putColor("InternalFrame.background", bg);
		putColor("Viewport.background", bg);
		putColor("ToolBar.background", bg);
		putColor("ToolBar.foreground", fg);
		putColor("SplitPane.background", bg);
		putColor("SplitPane.dividerFocusColor", accent);

		// labels and text components
		putColor("Label.foreground", fg);
		putColor("TextField.background", bgDark);
		putColor("TextField.foreground", fg);
		putColor("TextField.caretForeground", fg);
		putColor("TextArea.background", bgDark);
		putColor("TextArea.foreground", fg);
		putColor("TextArea.caretForeground", fg);
		putColor("TextPane.background", bgDark);
		putColor("TextPane.foreground", fg);
		putColor("EditorPane.background", bgDark);
		putColor("EditorPane.foreground", fg);
		putColor("FormattedTextField.background", bgDark);
		putColor("FormattedTextField.foreground", fg);
		putColor("PasswordField.background", bgDark);
		putColor("PasswordField.foreground", fg);

		// buttons
		putColor("Button.background", bgLight);
		putColor("Button.foreground", fg);
		putColor("Button.select", accent);
		putColor("Button.disable", fgDim);
		putColor("CheckBox.background", bg);
		putColor("CheckBox.foreground", fg);
		putColor("RadioButton.background", bg);
		putColor("RadioButton.foreground", fg);
		putColor("ToggleButton.background", bgLight);
		putColor("ToggleButton.foreground", fg);

		// tables
		putColor("Table.background", bg);
		putColor("Table.foreground", fg);
		putColor("Table.selectionBackground", accent);
		putColor("Table.selectionForeground", Color.WHITE);
		putColor("Table.gridColor", dark ? BORDER : LIGHT_BORDER);
		putColor("TableHeader.background", bgLight);
		putColor("TableHeader.foreground", fg);

		// lists, trees and combo boxes
		putColor("List.background", bg);
		putColor("List.foreground", fg);
		putColor("List.selectionBackground", accent);
		putColor("List.selectionForeground", Color.WHITE);
		putColor("Tree.background", bg);
		putColor("Tree.foreground", fg);
		putColor("Tree.textBackground", bg);
		putColor("Tree.textForeground", fg);
		putColor("Tree.selectionBackground", accent);
		putColor("Tree.selectionForeground", Color.WHITE);
		putColor("Tree.selectionBorderColor", accent);
		putColor("ComboBox.background", bgLight);
		putColor("ComboBox.foreground", fg);
		putColor("ComboBox.selectionBackground", accent);
		putColor("ComboBox.selectionForeground", Color.WHITE);
		putColor("ComboBox.buttonBackground", bgLight);
		putColor("ComboBox.buttonShadow", border);

		// misc widgets
		putColor("Spinner.background", bgLight);
		putColor("Spinner.foreground", fg);
		putColor("Slider.background", bg);
		putColor("Slider.foreground", fg);
		putColor("ProgressBar.background", bgDark);
		putColor("ProgressBar.foreground", accent);
		putColor("ScrollPane.background", bg);
		putColor("ScrollPane.foreground", fg);
		putColor("ScrollBar.background", bg);
		putColor("ScrollBar.foreground", fgDim);
		putColor("ScrollBar.thumb", bgLight);
		putColor("ScrollBar.thumbShadow", bgDark);
		putColor("ScrollBar.thumbHighlight", bgLight);
		putColor("ScrollBar.thumbDarkShadow", bgDark);
		putColor("ScrollBar.track", bg);
		putColor("ScrollBar.trackHighlight", bgDark);

		// tabs
		putColor("TabbedPane.background", bg);
		putColor("TabbedPane.foreground", fg);
		putColor("TabbedPane.selected", bgDark);
		putColor("TabbedPane.contentAreaColor", bgDark);
		putColor("TabbedPane.focus", bgDark);

		// menus
		putColor("Menu.background", bg);
		putColor("Menu.foreground", fg);
		putColor("Menu.selectionBackground", accent);
		putColor("Menu.selectionForeground", Color.WHITE);
		putColor("MenuItem.background", bg);
		putColor("MenuItem.foreground", fg);
		putColor("MenuItem.selectionBackground", accent);
		putColor("MenuItem.selectionForeground", Color.WHITE);
		putColor("MenuBar.background", bg);
		putColor("MenuBar.foreground", fg);
		putColor("PopupMenu.background", dark ? CARD : new Color(0xFFFFFF));
		putColor("PopupMenu.foreground", fg);
		putColor("PopupMenu.border", border);
		// Nimbus paints popups with its own key: without this the popup stays Nimbus gray
		putColor("nimbusPopupMenuBackground", dark ? CARD : new Color(0xFFFFFF));
		putColor("nimbusPopupMenuBorder", border);
		putColor("nimbusMenuBackground", bg);
		putColor("nimbusMenuBarBackground", bg);
		putColor("nimbusMenuItemBackground", bg);
		putColor("nimbusSeparatorForeground", border);
		putColor("nimbusSeparatorBackground", bg);
		putColor("Separator.foreground", border);
		putColor("Separator.background", bg);

		// row density
		put("List.rowHeight", getRowHeight());

		// tooltips, titles and borders
		putColor("ToolTip.background", dark ? CARD : new Color(0xFFFFFF));
		putColor("ToolTip.foreground", fg);
		putColor("ToolTip.border", border);
		put("ToolTipUI", ThemeToolTipUI.class.getName());
		put("ToolTip.font", UIManager.getFont("Label.font"));
		putColor("TitledBorder.titleColor", fg);
		putColor("TitledBorder.border", border);
		put("TitledBorder.titleFont", UIManager.getFont("Label.font"));

		// enable opaque painting so panels repaint correctly when switching themes
		put("Panel.opaque", Boolean.TRUE);
		put("TabbedPane.opaque", Boolean.TRUE);
		put("Label.opaque", Boolean.FALSE); // opaque labels paint dark boxes on cards and popups
	}

	// color helpers for components that paint themselves (keep hardcoded light colors working in dark mode)
	public static Color getBackground() {
		return isNightMode() ? BG : LIGHT_BG;
	}

	public static Color getPanelBackground() {
		return isNightMode() ? BG : LIGHT_BG;
	}

	public static Color getFieldBackground() {
		return isNightMode() ? BG_DARK : LIGHT_BG_DARK;
	}

	public static Color getControlColor() {
		return isNightMode() ? BG_LIGHT : LIGHT_BG_LIGHT;
	}

	public static Color getForeground() {
		return isNightMode() ? FG : LIGHT_FG;
	}

	public static Color getDimForeground() {
		return isNightMode() ? FG_DIM : LIGHT_FG_DIM;
	}

	public static Color getAccent() {
		return resolveAccent(isNightMode());
	}

	public static Color getAccentHover() {
		Color accent = resolveAccent(isNightMode());
		return new Color(Math.max(0, (int) (accent.getRed() * 0.88)), Math.max(0, (int) (accent.getGreen() * 0.88)), Math.max(0, (int) (accent.getBlue() * 0.88)));
	}

	public static Color getBorder() {
		return isNightMode() ? BORDER : LIGHT_BORDER;
	}

	public static Color getGridColor() {
		return isNightMode() ? BORDER : LIGHT_BORDER;
	}

	public static Color getCardBackground() {
		return isNightMode() ? CARD : LIGHT_CARD;
	}

	public static Color getCardHover() {
		return isNightMode() ? CARD_HOVER : LIGHT_CARD_HOVER;
	}

	public static Color getSecondaryAccent() {
		return isNightMode() ? VIOLET : LIGHT_VIOLET;
	}

	public static Color getSuccess() {
		return SUCCESS;
	}

	public static Color getWarning() {
		return WARNING;
	}

	public static Color getDanger() {
		return DANGER;
	}

	public static Color getSidebarBackground() {
		return isNightMode() ? SIDEBAR : LIGHT_SIDEBAR;
	}

	public static Color getHeaderColor() {
		return isNightMode() ? HEADER : LIGHT_HEADER;
	}

	private static Color tint(Color base, double amount) {
		double f = 1 + amount;
		return new Color(Math.min(255, (int) (base.getRed() * f)), Math.min(255, (int) (base.getGreen() * f)), Math.min(255, (int) (base.getBlue() * f)));
	}

	// Nimbus flat painters: replace the default bevel/gradient rendering with flat angular-friendly fills
	private static void installFlatPainters(boolean dark) {
		Color control = dark ? BG_LIGHT : LIGHT_BG_LIGHT;
		Color controlPressed = dark ? tint(BG_LIGHT, 0.08) : new Color(0xE2E5E8);
		Color controlDisabled = dark ? CARD : new Color(0xF1F2F4);
		Color border = dark ? BORDER : LIGHT_BORDER;
		Color focus = resolveAccent(dark);
		Color field = dark ? BG_DARK : LIGHT_BG_DARK;
		Color progressFill = focus;
		Color thumb = dark ? tint(BG_LIGHT, 0.10) : new Color(0xC9CDD3);

		Painter button = fill(control, border);
		Painter buttonOver = fill(tint(BG_LIGHT, 0.045), border);
		Painter buttonPressed = fill(controlPressed, focus);
		Painter buttonFocus = fill(control, focus);
		Painter buttonDisabled = fill(controlDisabled, border);

		put("Button[Enabled].backgroundPainter", button);
		put("Button[MouseOver].backgroundPainter", buttonOver);
		put("Button[Pressed].backgroundPainter", buttonPressed);
		put("Button[Focused].backgroundPainter", buttonFocus);
		put("Button[Disabled].backgroundPainter", buttonDisabled);

		put("ToggleButton[Enabled].backgroundPainter", button);
		put("ToggleButton[MouseOver].backgroundPainter", buttonOver);
		put("ToggleButton[Pressed].backgroundPainter", buttonPressed);
		put("ToggleButton[Focused].backgroundPainter", buttonFocus);
		put("ToggleButton[Disabled].backgroundPainter", buttonDisabled);

		put("ComboBox[Enabled].backgroundPainter", fill(field, border));
		put("ComboBox[Focused].backgroundPainter", fill(field, focus));
		put("Spinner[Enabled].backgroundPainter", fill(field, border));
		put("Spinner[Focused].backgroundPainter", fill(field, focus));
		put("TextField[Enabled].backgroundPainter", fill(field, border));
		put("TextField[Focused].backgroundPainter", fill(field, focus));
		put("FormattedTextField[Enabled].backgroundPainter", fill(field, border));
		put("FormattedTextField[Focused].backgroundPainter", fill(field, focus));
		put("PasswordField[Enabled].backgroundPainter", fill(field, border));
		put("PasswordField[Focused].backgroundPainter", fill(field, focus));

		// popups and menu items: flat theme surface, accent highlight
		Color popupSurface = dark ? CARD_HOVER : LIGHT_CARD;
		put("PopupMenu[Enabled].backgroundPainter", fill(popupSurface, border, 0));
		put("PopupMenu[Disabled].backgroundPainter", fill(popupSurface, border, 0));
		put("MenuItem[MouseOver].backgroundPainter", fill(alpha(focus, 70), alpha(focus, 70)));
		put("MenuItem[MouseOver].textForeground", dark ? FG : LIGHT_FG);
		put("MenuItem[Enabled].textForeground", dark ? FG : LIGHT_FG);
		put("CheckBoxMenuItem[MouseOver].backgroundPainter", fill(alpha(focus, 70), alpha(focus, 70)));
		put("CheckBoxMenuItem[MouseOver+Selected].backgroundPainter", fill(alpha(focus, 70), alpha(focus, 70)));
		put("Menu[Enabled+Selected].backgroundPainter", fill(alpha(focus, 70), alpha(focus, 70)));
		put("PopupMenuSeparator[Enabled].backgroundPainter", line(border));
		put("ComboBox:\"ComboBox.listRenderer\"[Selected].background", alpha(focus, 90));
		putColor("ComboBox.background", field);
		putColor("List[Selected].textBackground", alpha(focus, 90));

		// tabs: no bevels, the selected tab gets a soft accent pill
		Painter<JComponent> none = (Graphics2D g, JComponent c, int w, int h) -> {
		};
		Painter selectedTab = fill(alpha(focus, 55), alpha(focus, 55));
		Painter hoverTab = fill(dark ? CARD_HOVER : LIGHT_CARD_HOVER, dark ? CARD_HOVER : LIGHT_CARD_HOVER);
		for (String state : new String[] { "Enabled", "Disabled", "Focused" }) {
			put("TabbedPane:TabbedPaneTab[" + state + "].backgroundPainter", none);
		}
		put("TabbedPane:TabbedPaneTab[Enabled+MouseOver].backgroundPainter", hoverTab);
		put("TabbedPane:TabbedPaneTab[Enabled+Pressed].backgroundPainter", hoverTab);
		for (String state : new String[] { "Selected", "MouseOver+Selected", "Pressed+Selected", "Focused+Selected", "Focused+MouseOver+Selected", "Focused+Pressed+Selected", "Disabled+Selected" }) {
			put("TabbedPane:TabbedPaneTab[" + state + "].backgroundPainter", selectedTab);
		}
		for (String state : new String[] { "Enabled", "Disabled", "Enabled+MouseOver", "Enabled+Pressed" }) {
			put("TabbedPane:TabbedPaneTabArea[" + state + "].backgroundPainter", line(border));
		}
		put("TabbedPane:TabbedPaneTab.contentMargins", new java.awt.Insets(4, 12, 4, 12));
		putColor("TabbedPane:TabbedPaneTab[Enabled].textForeground", dark ? FG_DIM : LIGHT_FG_DIM);
		putColor("TabbedPane:TabbedPaneTab[Selected].textForeground", dark ? FG : LIGHT_FG);
		putColor("TabbedPane:TabbedPaneTab[MouseOver+Selected].textForeground", dark ? FG : LIGHT_FG);
		putColor("TabbedPane:TabbedPaneTab[Focused+Selected].textForeground", dark ? FG : LIGHT_FG);

		// table headers
		Painter header = fill(dark ? CARD : LIGHT_CARD, dark ? CARD : LIGHT_CARD, 0);
		for (String state : new String[] { "Enabled", "MouseOver", "Pressed", "Enabled+Focused", "Enabled+Sorted", "Enabled+Focused+Sorted", "Disabled" }) {
			put("TableHeader:\"TableHeader.renderer\"[" + state + "].backgroundPainter", header);
		}
		putColor("TableHeader.foreground", dark ? FG_DIM : LIGHT_FG_DIM);

		put("ProgressBar[Enabled].backgroundPainter", fill(field, border));
		put("ProgressBar[Enabled].foregroundPainter", fill(progressFill, progressFill));
		put("ScrollBar[Enabled].thumbPainter", fill(thumb, thumb));

		// neutral shadows -> flat
		putColor("nimbusShadow", dark ? BG_DARK : LIGHT_BG_DARK);
		putColor("nimbusBlueGrey", control);
		putColor("nimbusBorder", border);
	}

	private static Color alpha(Color c, int alpha) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}

	/**
	 * Single hairline at the bottom, e.g. under a tab strip or between menu items.
	 */
	private static Painter<JComponent> line(Color color) {
		return (Graphics2D g, JComponent c, int w, int h) -> {
			g.setColor(color);
			g.fillRect(0, h - 1, w, 1);
		};
	}

	private static Painter<JComponent> fill(Color fill, Color line, int arc) {
		return (Graphics2D g, JComponent c, int w, int h) -> {
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setPaint(fill);
			g2d.fillRect(0, 0, w, h);
			if (arc >= 0 && line != null && !line.equals(fill)) {
				g2d.setPaint(line);
				g2d.drawRect(0, 0, w - 1, h - 1);
			}
			g2d.dispose();
		};
	}

	private static Painter<JComponent> fill(Color fill, Color line) {
		return (Graphics2D g, JComponent c, int w, int h) -> {
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, ARC, ARC);
			g2d.setPaint(fill);
			g2d.fill(shape);
			g2d.setPaint(line);
			g2d.draw(shape);
			g2d.dispose();
		};
	}

}