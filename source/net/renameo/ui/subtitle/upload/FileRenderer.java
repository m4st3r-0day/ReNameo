package net.renameo.ui.subtitle.upload;

import static net.renameo.MediaTypes.*;

import java.awt.Component;
import java.io.File;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import net.renameo.ResourceManager;
import net.renameo.util.ui.NightTheme;

class FileRenderer extends DefaultTableCellRenderer {

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

		if (value != null) {
			File file = (File) value;
			setText(file.getName());
			setToolTipText(file.getPath());
			if (SUBTITLE_FILES.accept(file)) {
				setIcon(ResourceManager.getIcon("file.subtitle"));
			} else if (VIDEO_FILES.accept(file)) {
				setIcon(ResourceManager.getIcon("file.video"));
			}
			setForeground(table.getForeground());
		} else {
			setText("<Click to select video file>");
			setToolTipText(null);
			setIcon(null);
			setForeground(NightTheme.getDimForeground());
		}

		return this;
	}
}
