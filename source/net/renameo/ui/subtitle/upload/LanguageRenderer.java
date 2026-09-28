package net.renameo.ui.subtitle.upload;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import net.renameo.Language;
import net.renameo.ResourceManager;
import net.renameo.util.ui.NightTheme;

class LanguageRenderer implements TableCellRenderer, ListCellRenderer {

	private DefaultTableCellRenderer tableCell = new DefaultTableCellRenderer();
	private DefaultListCellRenderer listCell = new DefaultListCellRenderer();

	private Component configure(JLabel c, JComponent parent, Object value, boolean isSelected, boolean hasFocus) {
		if (value != null) {
			Language language = (Language) value;
			c.setText(language.getName());
			c.setIcon(ResourceManager.getFlagIcon(language.getCode()));
			c.setForeground(parent.getForeground());
		} else {
			c.setText("<Click to select language>");
			c.setIcon(null);
			c.setForeground(NightTheme.getDimForeground());
		}
		return c;
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		return configure((DefaultTableCellRenderer) tableCell.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column), table, value, isSelected, hasFocus);
	}

	@Override
	public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		return configure((DefaultListCellRenderer) listCell.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus), list, value, isSelected, cellHasFocus);
	}

}