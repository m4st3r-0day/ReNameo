package net.renameo.ui;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.NightTheme;

public class SettingsPanelBuilder implements PanelBuilder {

	// drawn at twice the sidebar size, like the @2x images of the other pages
	private static final Icon ICON = Glyph.of(Glyph.Shape.SETTINGS, 40, NightTheme::getAccent);

	@Override
	public String getName() {
		return "Settings";
	}

	@Override
	public Icon getIcon() {
		return ICON;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof SettingsPanelBuilder;
	}

	@Override
	public int hashCode() {
		return SettingsPanelBuilder.class.hashCode();
	}

	@Override
	public JComponent create() {
		return new SettingsPanel();
	}

}
