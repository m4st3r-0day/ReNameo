
package net.renameo.ui;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.renameo.ui.episodelist.EpisodeListPanelBuilder;
import net.renameo.ui.filter.FilterPanelBuilder;
import net.renameo.ui.list.ListPanelBuilder;
import net.renameo.ui.rename.RenamePanelBuilder;
import net.renameo.ui.sfv.SfvPanelBuilder;
import net.renameo.ui.subtitle.SubtitlePanelBuilder;

public interface PanelBuilder {

	public String getName();

	public Icon getIcon();

	public JComponent create();

	public static PanelBuilder[] defaultSequence() {
		return new PanelBuilder[] { new RenamePanelBuilder(), new EpisodeListPanelBuilder(), new SubtitlePanelBuilder(), new SfvPanelBuilder(), new FilterPanelBuilder(), new ListPanelBuilder(), new PluginsPanelBuilder() };
	}

	public static PanelBuilder[] episodeHandlerSequence() {
		return new PanelBuilder[] { new RenamePanelBuilder(), new ListPanelBuilder(), new PluginsPanelBuilder() };
	}

	public static PanelBuilder[] fileHandlerSequence() {
		return new PanelBuilder[] { new RenamePanelBuilder(), new SfvPanelBuilder(), new ListPanelBuilder(), new PluginsPanelBuilder() };
	}

	public static PanelBuilder[] textHandlerSequence() {
		return new PanelBuilder[] { new RenamePanelBuilder() };
	}

}
