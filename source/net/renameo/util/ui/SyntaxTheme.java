package net.renameo.util.ui;

import java.awt.Color;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

/**
 * Colors for the format expression editors that follow the app theme (the editor component has its own light default scheme).
 */
public final class SyntaxTheme {

	private SyntaxTheme() {
		throw new UnsupportedOperationException();
	}

	public static void apply(RSyntaxTextArea editor) {
		boolean dark = NightTheme.isNightMode();
		editor.setBackground(NightTheme.getFieldBackground());
		editor.setForeground(NightTheme.getForeground());
		editor.setCaretColor(NightTheme.getForeground());
		editor.setSelectionColor(Modern.alpha(NightTheme.getAccent(), 110));
		editor.setCurrentLineHighlightColor(NightTheme.getFieldBackground());
		editor.setMatchedBracketBGColor(Modern.alpha(NightTheme.getAccent(), 60));
		editor.setMatchedBracketBorderColor(NightTheme.getAccent());

		SyntaxScheme scheme = (SyntaxScheme) editor.getSyntaxScheme().clone();
		Color text = NightTheme.getForeground();
		Color keyword = dark ? new Color(0xC4B5FD) : new Color(0x7C3AED);
		Color string = dark ? new Color(0x86EFAC) : new Color(0x15803D);
		Color number = dark ? new Color(0xFCD34D) : new Color(0xB45309);
		Color operator = dark ? new Color(0x67E8F9) : new Color(0x0E7490);
		Color comment = NightTheme.getDimForeground();

		for (int i = 0; i < scheme.getStyleCount(); i++) {
			Style style = scheme.getStyle(i);
			if (style != null) {
				style.foreground = text;
				style.background = null;
			}
		}
		set(scheme, TokenTypes.RESERVED_WORD, keyword);
		set(scheme, TokenTypes.RESERVED_WORD_2, keyword);
		set(scheme, TokenTypes.FUNCTION, operator);
		set(scheme, TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, string);
		set(scheme, TokenTypes.LITERAL_CHAR, string);
		set(scheme, TokenTypes.LITERAL_BACKQUOTE, string);
		set(scheme, TokenTypes.LITERAL_NUMBER_DECIMAL_INT, number);
		set(scheme, TokenTypes.LITERAL_NUMBER_FLOAT, number);
		set(scheme, TokenTypes.LITERAL_NUMBER_HEXADECIMAL, number);
		set(scheme, TokenTypes.LITERAL_BOOLEAN, number);
		set(scheme, TokenTypes.OPERATOR, operator);
		set(scheme, TokenTypes.SEPARATOR, operator);
		set(scheme, TokenTypes.COMMENT_EOL, comment);
		set(scheme, TokenTypes.COMMENT_MULTILINE, comment);
		set(scheme, TokenTypes.COMMENT_DOCUMENTATION, comment);
		editor.setSyntaxScheme(scheme);
	}

	private static void set(SyntaxScheme scheme, int token, Color color) {
		if (token < scheme.getStyleCount() && scheme.getStyle(token) != null) {
			scheme.getStyle(token).foreground = color;
		}
	}

}
