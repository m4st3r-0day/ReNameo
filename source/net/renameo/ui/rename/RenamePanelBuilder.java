
package net.renameo.ui.rename;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.renameo.ResourceManager;
import net.renameo.ui.PanelBuilder;

public class RenamePanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "Rename";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.rename");
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof RenamePanelBuilder;
	}

	@Override
	public JComponent create() {
		return new RenamePanel();
	}

}
