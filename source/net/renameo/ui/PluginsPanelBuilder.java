package net.renameo.ui;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.NightTheme;

public class PluginsPanelBuilder implements PanelBuilder {

	// drawn at twice the sidebar size, like the @2x images of the other pages
	private static final Icon ICON = Glyph.of(Glyph.Shape.SLIDERS, 40, NightTheme::getAccent);

	@Override
	public String getName() {
		return "Plugins";
	}

	@Override
	public Icon getIcon() {
		return ICON;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof PluginsPanelBuilder;
	}

	@Override
	public int hashCode() {
		return PluginsPanelBuilder.class.hashCode();
	}

	@Override
	public JComponent create() {
		return new PluginsPanel();
	}

}
