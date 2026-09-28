package net.renameo.ui.rename;

import static javax.swing.BorderFactory.*;
import static net.renameo.util.FileUtilities.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.miginfocom.swing.MigLayout;

/**
 * Everything a rename will do, before it happens: old and new names, target folders, data source, conflicts and low confidence matches. Nothing touches the file system until "Apply Changes".
 */
class PreviewDialog extends JDialog {

	enum ConflictPolicy {
		SKIP("Skip"), OVERWRITE("Overwrite"), AUTO_NUMBER("Auto-number");

		private final String label;

		ConflictPolicy(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	enum Status {
		READY("Ready"), REVIEW("Needs review"), EXISTS("Exists"), DUPLICATE("Duplicate"), UNCHANGED("Unchanged"), WOULD_RENAME("Would rename"), WOULD_SKIP("Would skip"), WOULD_OVERWRITE("Would overwrite"), WOULD_NUMBER("Would auto-number"), ERROR("Error");

		final String label;

		Status(String label) {
			this.label = label;
		}
	}

	static class Row {

		final File source;
		final File destination;
		final String sourceName;
		final float confidence;

		boolean include;
		Status status;
		String message;

		Row(File source, File destination, String sourceName, float confidence) {
			this.source = source;
			this.destination = destination;
			this.sourceName = sourceName;
			this.confidence = confidence;
		}
	}

	/**
	 * Final plan: source to absolute destination, plus the destinations that may be replaced.
	 */
	static class Plan {

		final Map<File, File> renameMap = new LinkedHashMap<File, File>();
		final Set<File> overwrite = new HashSet<File>();
	}

	private final List<Row> rows;
	private final RowModel model = new RowModel();
	private final JComboBox<ConflictPolicy> policy = new JComboBox<ConflictPolicy>(ConflictPolicy.values());
	private final JLabel summary = Modern.label("", 12.5f, Font.PLAIN, true);
	private final JLabel dryRun = Modern.label("", 12.5f, Font.PLAIN, false);

	private Plan result;

	PreviewDialog(Window owner, List<Row> rows, String actionName) {
		super(owner, "Preview Changes", ModalityType.DOCUMENT_MODAL);
		this.rows = rows;

		classify();

		JTable table = new JTable(model);
		table.setRowHeight(30);
		table.setShowVerticalLines(false);
		table.setIntercellSpacing(new Dimension(0, 1));
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setDefaultRenderer(Object.class, new CellRenderer());
		table.setAutoCreateRowSorter(true);

		int[] widths = { 34, 170, 250, 300, 250, 80 };
		for (int i = 0; i < widths.length; i++) {
			TableColumn column = table.getColumnModel().getColumn(i);
			column.setPreferredWidth(widths[i]);
			if (i == 0) {
				column.setMaxWidth(40);
			}
		}

		JTableHeader header = table.getTableHeader();
		header.setReorderingAllowed(false);
		header.setDefaultRenderer(new DefaultTableCellRenderer() {

			@Override
			public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				super.getTableCellRendererComponent(t, value, false, false, row, column);
				setFont(Modern.font(11.5f, Font.BOLD));
				setForeground(NightTheme.getDimForeground());
				setBackground(NightTheme.getCardBackground());
				setBorder(createCompoundBorder(createMatteBorder(0, 0, 1, 0, NightTheme.getBorder()), createEmptyBorder(6, 10, 6, 10)));
				return this;
			}
		});

		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(createEmptyBorder());
		Modern.slim(scroll);

		Modern.Card card = new Modern.Card(new MigLayout("insets 8, fill", "[fill, grow]", "[fill, grow]"));
		card.add(scroll, "grow");

		Modern.onThemeChange(table, () -> {
			table.setBackground(NightTheme.getCardBackground());
			table.setForeground(NightTheme.getForeground());
			table.setGridColor(NightTheme.getBorder());
			table.setSelectionBackground(Modern.alpha(NightTheme.getAccent(), 60));
			table.setSelectionForeground(NightTheme.getForeground());
			scroll.getViewport().setBackground(NightTheme.getCardBackground());
			header.setBackground(NightTheme.getCardBackground());
			header.setForeground(NightTheme.getDimForeground());
			header.setFont(Modern.font(11.5f, Font.BOLD));
		});

		policy.setSelectedItem(ConflictPolicy.SKIP);
		policy.addActionListener(evt -> {
			classify();
			model.fireTableDataChanged();
		});

		JButton cancel = Modern.button(newAction("Cancel", evt -> dispose()), null, Modern.Style.SECONDARY);
		JButton dry = Modern.button(newAction("Dry Run", evt -> dryRun()), Glyph.of(Glyph.Shape.EYE, 15, NightTheme::getForeground), Modern.Style.SECONDARY);
		dry.setToolTipText("Check every change without touching any file");
		JButton apply = Modern.button(newAction("Apply Changes", evt -> apply()), Glyph.of(Glyph.Shape.PLAY, 14, Color.WHITE), Modern.Style.VIOLET);
		apply.setToolTipText(actionName + " the ticked files");

		JPanel content = new JPanel(new MigLayout("insets 20, fill", "[fill, grow]", "[]4[]14[fill, grow]12[]"));
		content.setBackground(NightTheme.getBackground());
		content.add(Modern.label("Preview Changes", 20f, Font.BOLD, false), "wrap");
		content.add(summary, "wrap");
		content.add(card, "grow, wrap");

		JPanel footer = new JPanel(new MigLayout("insets 0, fillx", "[][]16[grow][][][]", "[center]"));
		footer.setOpaque(false);
		footer.add(Modern.label("If the destination exists:", 12.5f, Font.PLAIN, true));
		footer.add(policy, "w 140!");
		footer.add(dryRun, "growx");
		footer.add(cancel);
		footer.add(dry);
		footer.add(apply);
		content.add(footer, "growx");

		setContentPane(content);
		installAction(content, KeyStroke.getKeyStroke("ESCAPE"), newAction("Cancel", evt -> dispose()));
		getRootPane().setDefaultButton(apply);

		updateSummary();
		setSize(1180, 640);
		setLocationRelativeTo(owner);
	}

	/**
	 * Shows the dialog and returns the plan to execute, or null if cancelled.
	 */
	Plan showDialog() {
		setVisible(true);
		return result;
	}

	private ConflictPolicy getPolicy() {
		return (ConflictPolicy) policy.getSelectedItem();
	}

	private void classify() {
		Set<File> targets = new HashSet<File>();
		for (Row row : rows) {
			row.message = null;
			if (row.destination.equals(row.source)) {
				row.status = Status.UNCHANGED;
			} else if (!targets.add(row.destination)) {
				row.status = Status.DUPLICATE;
			} else if (row.destination.exists() && !row.destination.equals(row.source) && !isSameFileIgnoringCase(row)) {
				row.status = Status.EXISTS;
			} else if (row.confidence < RenameListCellRenderer.LIKELY) {
				row.status = Status.REVIEW;
			} else {
				row.status = Status.READY;
			}
		}
	}

	private static boolean isSameFileIgnoringCase(Row row) {
		// renaming "dune.mkv" to "Dune.mkv" on a case-insensitive file system is not a conflict
		return row.destination.getPath().equalsIgnoreCase(row.source.getPath());
	}

	void initInclusion(Set<File> only) {
		for (Row row : rows) {
			boolean wanted = only == null || only.contains(row.source);
			row.include = wanted && row.status != Status.UNCHANGED && row.status != Status.REVIEW && !(row.status == Status.DUPLICATE && getPolicy() != ConflictPolicy.AUTO_NUMBER);
		}
		updateSummary();
	}

	private void updateSummary() {
		long included = rows.stream().filter(r -> r.include).count();
		long conflicts = rows.stream().filter(r -> r.status == Status.EXISTS || r.status == Status.DUPLICATE).count();
		long review = rows.stream().filter(r -> r.status == Status.REVIEW).count();
		StringBuilder sb = new StringBuilder();
		sb.append(included).append(" of ").append(rows.size()).append(" files will be renamed");
		if (conflicts > 0) {
			sb.append("  ·  ").append(conflicts).append(conflicts == 1 ? " conflict" : " conflicts");
		}
		if (review > 0) {
			sb.append("  ·  ").append(review).append(" low confidence (tick to confirm)");
		}
		summary.setText(sb.toString());
	}

	/**
	 * Simulates the whole plan: sources must still exist, target folders must be writable and conflicts are resolved with the selected policy.
	 */
	private void dryRun() {
		Plan plan = buildPlan();
		int ok = 0, skipped = 0, errors = 0;
		for (Row row : rows) {
			if (!row.include) {
				continue;
			}
			File target = plan.renameMap.get(row.source);
			if (target == null) {
				row.status = Status.WOULD_SKIP;
				skipped++;
			} else if (!row.source.exists()) {
				row.status = Status.ERROR;
				row.message = "Source file no longer exists";
				errors++;
			} else if (!isWritable(target.getParentFile())) {
				row.status = Status.ERROR;
				row.message = "No write permission for " + target.getParentFile();
				errors++;
			} else {
				row.status = plan.overwrite.contains(target) ? Status.WOULD_OVERWRITE : target.equals(row.destination) ? Status.WOULD_RENAME : Status.WOULD_NUMBER;
				ok++;
			}
		}
		model.fireTableDataChanged();
		dryRun.setText(String.format("Dry run: %d would be renamed, %d skipped, %d errors", ok, skipped, errors));
		dryRun.setForeground(errors > 0 ? NightTheme.getDanger() : NightTheme.getSuccess());
	}

	private static boolean isWritable(File folder) {
		// the folder may not exist yet: check the first existing parent
		for (File f = folder; f != null; f = f.getParentFile()) {
			if (f.exists()) {
				return f.canWrite();
			}
		}
		return false;
	}

	private void apply() {
		result = buildPlan();
		dispose();
	}

	private Plan buildPlan() {
		Plan plan = new Plan();
		Set<File> taken = new HashSet<File>();
		for (Row row : rows) {
			if (!row.include) {
				continue;
			}
			File target = row.destination;
			boolean conflict = (target.exists() && !target.equals(row.source) && !isSameFileIgnoringCase(row)) || taken.contains(target);
			if (conflict) {
				switch (getPolicy()) {
				case SKIP:
					continue;
				case OVERWRITE:
					if (taken.contains(target)) {
						continue; // two files in this batch cannot both become the same file
					}
					plan.overwrite.add(target);
					break;
				case AUTO_NUMBER:
					target = nextFreeName(target, taken);
					break;
				}
			}
			taken.add(target);
			plan.renameMap.put(row.source, target);
		}
		return plan;
	}

	/**
	 * "Dune (2021).mkv" => "Dune (2021) (2).mkv", "Dune (2021) (3).mkv", ...
	 */
	private static File nextFreeName(File target, Set<File> taken) {
		String name = getNameWithoutExtension(target.getName());
		String ext = getExtension(target);
		for (int i = 2; i < 1000; i++) {
			File candidate = new File(target.getParentFile(), String.format("%s (%d)%s", name, i, ext == null ? "" : "." + ext));
			if (!candidate.exists() && !taken.contains(candidate)) {
				return candidate;
			}
		}
		return target;
	}

	private class RowModel extends AbstractTableModel {

		private final String[] columns = { "", "Status", "Original", "New Name", "New Folder", "Source" };

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return columns.length;
		}

		@Override
		public String getColumnName(int column) {
			return columns[column];
		}

		@Override
		public Class<?> getColumnClass(int column) {
			return column == 0 ? Boolean.class : Object.class;
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			return column == 0;
		}

		@Override
		public void setValueAt(Object value, int row, int column) {
			rows.get(row).include = Boolean.TRUE.equals(value);
			updateSummary();
			fireTableRowsUpdated(row, row);
		}

		@Override
		public Object getValueAt(int index, int column) {
			Row row = rows.get(index);
			switch (column) {
			case 0:
				return row.include;
			case 1:
				return row;
			case 2:
				return row.source.getName();
			case 3:
				return row.destination.getName();
			case 4:
				return row.destination.getParent();
			default:
				return row.sourceName == null ? "" : row.sourceName;
			}
		}
	}

	private class CellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
			setBorder(createEmptyBorder(0, 10, 0, 10));
			setFont(Modern.font(12.5f, Font.PLAIN));
			setForeground(NightTheme.getForeground());
			setToolTipText(value == null ? null : value.toString());

			if (value instanceof Row) {
				Row r = (Row) value;
				setText(r.status.label + (r.status == Status.REVIEW || r.status == Status.READY ? String.format(" · %d%%", Math.round(r.confidence * 100)) : ""));
				setForeground(getStatusColor(r.status));
				setFont(Modern.font(12f, Font.BOLD));
				setToolTipText(r.message != null ? r.message : r.status == Status.REVIEW ? "Low confidence match: tick the box to confirm it" : null);
			} else if (column == 3) {
				setFont(Modern.font(12.5f, Font.BOLD));
			} else if (column == 2 || column == 4 || column == 5) {
				setForeground(NightTheme.getDimForeground());
			}
			return this;
		}
	}

	static Color getStatusColor(Status status) {
		switch (status) {
		case READY:
		case WOULD_RENAME:
			return NightTheme.getSuccess();
		case REVIEW:
		case WOULD_NUMBER:
			return NightTheme.getWarning();
		case EXISTS:
		case DUPLICATE:
		case ERROR:
		case WOULD_OVERWRITE:
			return NightTheme.getDanger();
		default:
			return NightTheme.getDimForeground();
		}
	}

}
