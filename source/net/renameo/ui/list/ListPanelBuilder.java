
package net.renameo.ui.list;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.renameo.ResourceManager;
import net.renameo.ui.PanelBuilder;

public class ListPanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "List";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.list");
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ListPanelBuilder;
	}

	@Override
	public JComponent create() {
		return new ListPanel();
	}

}
