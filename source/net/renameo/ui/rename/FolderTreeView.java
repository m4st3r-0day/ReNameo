package net.renameo.ui.rename;

import static javax.swing.BorderFactory.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.plaf.basic.BasicScrollPaneUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;

import net.miginfocom.swing.MigLayout;
import net.renameo.media.LibraryLayout;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;

/**
 * The folders before and after renaming, side by side: which folders are created or go away, which files get a new name and which ones follow their folder.
 */
class FolderTreeView extends JPanel {

	enum Kind {
		FOLDER, NEW_FOLDER, REMOVED_FOLDER, FILE, RENAMED, FOLLOWS
	}

	static class Entry {

		final String name;
		Kind kind;

		Entry(String name, Kind kind) {
			this.name = name;
			this.kind = kind;
		}
	}

	private final JTree before = createTree();
	private final JTree after = createTree();
	private final JLabel root = Modern.label("", 11.5f, Font.PLAIN, true);

	FolderTreeView() {
		super(new MigLayout("insets 0, fill, wrap 2", "[fill, grow, sg][fill, grow, sg]", "[][][fill, grow]"));
		setOpaque(false);
		add(root, "span 2, gapleft 4, gapbottom 4");
		add(Modern.caption("Before"), "gapleft 4");
		add(Modern.caption("After"), "gapleft 4");
		add(scroll(before), "hmin 0, wmin 0");
		add(scroll(after), "hmin 0, wmin 0");
	}

	/**
	 * Show the given renames (source => absolute destination).
	 */
	void update(Map<File, File> renames) {
		Map<File, File> follows = LibraryLayout.previewRelocations(renames);
		Set<File> removed = LibraryLayout.getRelocationsFor(renames).keySet();

		List<File> all = new ArrayList<File>();
		all.addAll(renames.keySet());
		all.addAll(renames.values());
		all.addAll(follows.keySet());
		all.addAll(follows.values());
		File base = commonFolder(all);
		root.setText(base == null ? "" : "In " + base.getPath().replace(System.getProperty("user.home"), "~"));

		Map<String, Entry> beforeEntries = new TreeMap<String, Entry>();
		Map<String, Entry> afterEntries = new TreeMap<String, Entry>();
		renames.forEach((from, to) -> {
			add(beforeEntries, base, from, Kind.FILE, f -> removed.contains(f) ? Kind.REMOVED_FOLDER : Kind.FOLDER);
			add(afterEntries, base, to, Kind.RENAMED, f -> f.exists() && !removed.contains(f) ? Kind.FOLDER : Kind.NEW_FOLDER);
		});
		follows.forEach((from, to) -> {
			add(beforeEntries, base, from, Kind.FILE, f -> removed.contains(f) ? Kind.REMOVED_FOLDER : Kind.FOLDER);
			add(afterEntries, base, to, Kind.FOLLOWS, f -> f.exists() && !removed.contains(f) ? Kind.FOLDER : Kind.NEW_FOLDER);
		});

		show(before, beforeEntries);
		show(after, afterEntries);
	}

	/**
	 * Remember the file and all folders between the base and it, keyed by their relative path.
	 */
	private static void add(Map<String, Entry> entries, File base, File file, Kind kind, java.util.function.Function<File, Kind> folderKind) {
		List<File> path = new ArrayList<File>();
		for (File f = file; f != null && !f.equals(base); f = f.getParentFile()) {
			path.add(0, f);
		}
		StringBuilder key = new StringBuilder();
		for (int i = 0; i < path.size(); i++) {
			File f = path.get(i);
			key.append('/').append(f.getName());
			boolean last = i == path.size() - 1;
			Kind k = last ? kind : folderKind.apply(f);
			entries.putIfAbsent(key.toString(), new Entry(f.getName(), k));
		}
	}

	private static void show(JTree tree, Map<String, Entry> entries) {
		DefaultMutableTreeNode top = new DefaultMutableTreeNode(new Entry("", Kind.FOLDER));
		Map<String, DefaultMutableTreeNode> nodes = new LinkedHashMap<String, DefaultMutableTreeNode>();
		nodes.put("", top);

		for (String key : sortTree(entries.keySet(), entries)) {
			String parent = key.substring(0, key.lastIndexOf('/'));
			DefaultMutableTreeNode node = new DefaultMutableTreeNode(entries.get(key));
			nodes.getOrDefault(parent, top).add(node);
			nodes.put(key, node);
		}

		tree.setModel(new DefaultTreeModel(top));
		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}
	}

	/**
	 * Depth first, folders before files, names in natural order.
	 */
	private static List<String> sortTree(Collection<String> keys, Map<String, Entry> entries) {
		Comparator<String> order = Comparator.comparing((String k) -> isFile(entries.get(k)) ? 1 : 0).thenComparing(k -> k.substring(k.lastIndexOf('/') + 1), String.CASE_INSENSITIVE_ORDER);
		Map<String, List<String>> children = new TreeMap<String, List<String>>();
		for (String k : keys) {
			children.computeIfAbsent(k.substring(0, k.lastIndexOf('/')), p -> new ArrayList<String>()).add(k);
		}
		List<String> sorted = new ArrayList<String>();
		walk("", children, order, sorted);
		return sorted;
	}

	private static void walk(String parent, Map<String, List<String>> children, Comparator<String> order, List<String> sorted) {
		List<String> list = children.get(parent);
		if (list == null) {
			return;
		}
		list.sort(order);
		for (String k : list) {
			sorted.add(k);
			walk(k, children, order, sorted);
		}
	}

	private static boolean isFile(Entry e) {
		return e.kind == Kind.FILE || e.kind == Kind.RENAMED || e.kind == Kind.FOLLOWS;
	}

	static File commonFolder(Collection<File> files) {
		File common = null;
		for (File f : files) {
			File parent = f.getParentFile();
			if (common == null) {
				common = parent;
				continue;
			}
			while (common != null && parent != null && !(parent.getPath() + File.separator).startsWith(common.getPath() + File.separator)) {
				common = common.getParentFile();
			}
		}
		return common;
	}

	private static JTree createTree() {
		JTree tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setRowHeight(26);
		tree.setCellRenderer(new EntryRenderer());
		Modern.onThemeChange(tree, () -> {
			tree.setUI(new BasicTreeUI());
			tree.setBackground(NightTheme.getCardBackground());
		});
		return tree;
	}

	private static JScrollPane scroll(JTree tree) {
		JScrollPane scroll = new JScrollPane(tree);
		Modern.onThemeChange(scroll, () -> {
			scroll.setUI(new BasicScrollPaneUI());
			scroll.setBorder(createLineBorder(NightTheme.getBorder()));
			scroll.getViewport().setBackground(NightTheme.getCardBackground());
		});
		Modern.slim(scroll);
		return scroll;
	}

	private static class EntryRenderer extends JLabel implements TreeCellRenderer {

		EntryRenderer() {
			setOpaque(false);
			setIconTextGap(7);
			setBorder(createEmptyBorder(0, 2, 0, 6));
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
			Object object = ((DefaultMutableTreeNode) value).getUserObject();
			if (!(object instanceof Entry)) {
				setText("");
				setIcon(null);
				return this;
			}
			Entry e = (Entry) object;
			boolean folder = !isFile(e);
			Color color;
			String note = "";
			switch (e.kind) {
			case NEW_FOLDER:
				color = NightTheme.getSuccess();
				note = "new";
				break;
			case REMOVED_FOLDER:
				color = NightTheme.getDanger();
				note = "goes away";
				break;
			case RENAMED:
				color = NightTheme.getAccent();
				break;
			case FOLLOWS:
				color = NightTheme.getDimForeground();
				note = "follows the folder";
				break;
			default:
				color = NightTheme.getForeground();
			}

			String name = escapeHTML(e.name);
			if (e.kind == Kind.REMOVED_FOLDER) {
				name = "<s>" + name + "</s>";
			}
			String hint = note.isEmpty() ? "" : "&nbsp;&nbsp;<font color='" + toHex(NightTheme.getDimForeground()) + "'>" + note + "</font>";
			setText("<html><font color='" + toHex(color) + "'>" + name + "</font>" + hint + "</html>");
			setFont(Modern.font(12.5f, folder ? Font.BOLD : Font.PLAIN));
			setIcon(Glyph.of(folder ? Glyph.Shape.FOLDER : Glyph.Shape.FILE, 15, folder ? (e.kind == Kind.NEW_FOLDER ? NightTheme::getSuccess : e.kind == Kind.REMOVED_FOLDER ? NightTheme::getDanger : NightTheme::getAccent) : NightTheme::getDimForeground));
			return this;
		}
	}

}
