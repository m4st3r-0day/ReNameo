package net.renameo.ui;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.NightTheme;

public class LibraryPanelBuilder implements PanelBuilder {

	// drawn at twice the sidebar size, like the @2x images of the other pages
	private static final Icon ICON = Glyph.of(Glyph.Shape.CHART, 40, NightTheme::getAccent);

	@Override
	public String getName() {
		return "Library";
	}

	@Override
	public Icon getIcon() {
		return ICON;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof LibraryPanelBuilder;
	}

	@Override
	public int hashCode() {
		return LibraryPanelBuilder.class.hashCode();
	}

	@Override
	public JComponent create() {
		return new LibraryPanel();
	}

}
