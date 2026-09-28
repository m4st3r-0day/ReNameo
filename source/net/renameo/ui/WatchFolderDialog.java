package net.renameo.ui;

import static net.renameo.util.ui.SwingUI.*;

import java.awt.Dialog.ModalityType;
import java.awt.Font;
import java.awt.Window;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;
import net.renameo.StandardRenameAction;
import net.renameo.UserFiles;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;

/**
 * Settings of the watch folder: which folder, how files are named and what happens to them.
 */
public class WatchFolderDialog extends JDialog {

	private static final StandardRenameAction[] ACTIONS = { StandardRenameAction.MOVE, StandardRenameAction.COPY, StandardRenameAction.HARDLINK, StandardRenameAction.SYMLINK };

	public static void show(Window owner) {
		new WatchFolderDialog(owner).setVisible(true);
	}

	private WatchFolderDialog(Window owner) {
		super(owner, "Watch folder", ModalityType.APPLICATION_MODAL);

		JCheckBox enabled = new JCheckBox("Rename new files automatically", Boolean.parseBoolean(WatchFolder.enabled.getValue()));
		enabled.setOpaque(false);
		enabled.setForeground(NightTheme.getForeground());
		enabled.setFont(Modern.font(13f, Font.PLAIN));

		JTextField folder = Modern.field(new JTextField(18));
		folder.setText(WatchFolder.folder.getValue());
		JButton browse = Modern.button(newAction("Choose …", evt -> {
			File selected = UserFiles.showOpenDialogSelectFolder(WatchFolder.getFolder(), "Folder to watch", evt);
			if (selected != null) {
				folder.setText(selected.getPath());
			}
		}), null, Modern.Style.SECONDARY);

		JComboBox<WatchFolder.Naming> naming = new JComboBox<WatchFolder.Naming>(WatchFolder.Naming.values());
		naming.setSelectedItem(WatchFolder.getNaming());

		JComboBox<String> action = new JComboBox<String>();
		for (StandardRenameAction it : ACTIONS) {
			action.addItem(it.getDisplayName());
		}
		for (int i = 0; i < ACTIONS.length; i++) {
			if (ACTIONS[i] == WatchFolder.getAction()) {
				action.setSelectedIndex(i);
			}
		}

		JSpinner interval = new JSpinner(new SpinnerNumberModel(WatchFolder.getIntervalMinutes(), 1, 1440, 1));
		JTextField minutes = ((JSpinner.DefaultEditor) interval.getEditor()).getTextField();
		minutes.setBackground(NightTheme.getFieldBackground());
		minutes.setForeground(NightTheme.getForeground());
		minutes.setCaretColor(NightTheme.getForeground());
		minutes.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		interval.setBorder(BorderFactory.createLineBorder(NightTheme.getBorder()));
		JLabel status = Modern.label(" ", 12f, Font.PLAIN, true);

		JPanel content = new JPanel(new MigLayout("insets 24 26 20 26, fillx, wrap 1", "[fill, grow]"));
		content.setBackground(NightTheme.getBackground());

		JLabel title = Modern.label("Watch folder", 18f, Font.BOLD, false);
		title.setIcon(Glyph.of(Glyph.Shape.FOLDER, 22, NightTheme::getAccent));
		title.setIconTextGap(10);
		content.add(title);
		content.add(Modern.label("<html><body style='width: 330px'>While ReNameo is open, video, audio and subtitle files that arrive in this folder are renamed on their own. Files changed in the last 2 minutes are left alone, so downloads can finish. Everything can be undone from History.</body></html>", 12.5f, Font.PLAIN, true), "gapbottom 12");

		content.add(enabled, "gapbottom 8");

		content.add(Modern.label("Folder", 12f, Font.BOLD, true));
		JPanel folderRow = new JPanel(new MigLayout("insets 0, fillx", "[grow, fill][]"));
		folderRow.setOpaque(false);
		folderRow.add(folder, "h 34!");
		folderRow.add(browse, "h 34!");
		content.add(folderRow, "gapbottom 8");

		content.add(Modern.label("Names", 12f, Font.BOLD, true));
		content.add(naming, "h 32!, gapbottom 8");
		content.add(Modern.label("Action", 12f, Font.BOLD, true));
		content.add(action, "h 32!, gapbottom 8");

		JPanel intervalRow = new JPanel(new MigLayout("insets 0", "[][]"));
		intervalRow.setOpaque(false);
		intervalRow.add(Modern.label("Check every", 12f, Font.BOLD, true));
		intervalRow.add(interval, "w 70!");
		intervalRow.add(Modern.label("minutes", 12f, Font.PLAIN, true));
		content.add(intervalRow);
		content.add(status, "gaptop 4");

		JButton cancel = Modern.button(newAction("Cancel", e -> dispose()), null, Modern.Style.SECONDARY);
		JButton save = Modern.button(newAction("Save", e -> {
			String path = folder.getText().trim();
			if (enabled.isSelected() && (path.isEmpty() || !new File(path).isDirectory())) {
				status.setText("Please choose an existing folder.");
				status.setForeground(NightTheme.getDanger());
				return;
			}

			WatchFolder.enabled.setValue(Boolean.toString(enabled.isSelected()));
			WatchFolder.folder.setValue(path);
			WatchFolder.naming.setValue(((WatchFolder.Naming) naming.getSelectedItem()).name());
			WatchFolder.action.setValue(ACTIONS[action.getSelectedIndex()].name());
			WatchFolder.interval.setValue(interval.getValue().toString());
			WatchFolder.restart();

			String detail = enabled.isSelected() ? String.format("%s · every %s min", new File(path).getName(), interval.getValue()) : "Off";
			SwingEventBus.getInstance().post(new AppEvents.Toast("Watch folder", detail, AppEvents.Status.Kind.READY));
			dispose();
		}), null, Modern.Style.PRIMARY);

		JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "push[][]"));
		buttons.setOpaque(false);
		buttons.add(cancel);
		buttons.add(save);
		content.add(buttons, "gaptop 12");

		setContentPane(content);
		getRootPane().setDefaultButton(save);
		installAction(content, KeyStroke.getKeyStroke("ESCAPE"), newAction("Cancel", e -> dispose()));
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}

}
