package net.renameo.ui;

import static net.renameo.Logging.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Font;
import java.awt.Window;
import java.util.logging.Level;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import net.miginfocom.swing.MigLayout;
import net.renameo.Settings;
import net.renameo.Updates;
import net.renameo.Updates.Release;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;

/**
 * Tells about a new version, with its release notes and a link to the download.
 */
public class UpdateDialog extends JDialog {

	/**
	 * Ask GitHub in the background. When started by the user, also say so if there is nothing new or GitHub can't be reached.
	 */
	public static void check(Window owner, boolean manual) {
		newSwingWorker(Updates::fetchLatest, release -> {
			if (Updates.isWanted(release, manual)) {
				show(owner, release);
			} else if (manual) {
				SwingEventBus.getInstance().post(new AppEvents.Toast(Settings.getApplicationName() + " is up to date", "Version " + Settings.getApplicationVersion(), AppEvents.Status.Kind.READY));
			}
		}, error -> {
			if (manual) {
				SwingEventBus.getInstance().post(new AppEvents.Toast("Couldn't check for updates", "GitHub could not be reached. Please try again later.", AppEvents.Status.Kind.WARNING));
			}
			debug.log(Level.WARNING, "Update check failed: " + error);
		}).execute();
	}

	public static void show(Window owner, Release release) {
		new UpdateDialog(owner, release).setVisible(true);
	}

	private UpdateDialog(Window owner, Release release) {
		super(owner, "Update", ModalityType.APPLICATION_MODAL);

		JPanel content = new JPanel(new MigLayout("insets 24 26 20 26, fillx, wrap 1", "[fill, grow]"));
		content.setBackground(NightTheme.getBackground());

		JLabel title = Modern.label(Settings.getApplicationName() + " " + release.version + " is available", 18f, Font.BOLD, false);
		title.setIcon(Glyph.of(Glyph.Shape.DOWNLOAD, 22, NightTheme::getAccent));
		title.setIconTextGap(10);
		content.add(title);
		content.add(Modern.label("<html><body style='width: 400px'>You have version " + Settings.getApplicationVersion() + ". Download the new one from the release page and replace the app: settings, keys, history and plugins are kept.</body></html>", 12.5f, Font.PLAIN, true), "wmin 0, gapbottom 12");

		if (!release.notes.trim().isEmpty()) {
			content.add(Modern.caption("What's new"), "gapbottom 4");
			JTextArea notes = new JTextArea(plainText(release.notes));
			notes.setEditable(false);
			notes.setLineWrap(true);
			notes.setWrapStyleWord(true);
			notes.setFont(Modern.font(12.5f, Font.PLAIN));
			notes.setForeground(NightTheme.getForeground());
			notes.setBackground(NightTheme.getFieldBackground());
			notes.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
			notes.setCaretPosition(0);
			JScrollPane scroll = new JScrollPane(notes);
			Modern.slim(scroll);
			scroll.setBorder(BorderFactory.createLineBorder(NightTheme.getBorder()));
			content.add(scroll, "h 60:200:260, w 460, wmin 0");
		}

		JCheckBox automatic = new JCheckBox("Check for updates automatically", Boolean.parseBoolean(Updates.automatic.getValue()));
		automatic.setOpaque(false);
		automatic.setForeground(NightTheme.getForeground());
		automatic.setFont(Modern.font(12.5f, Font.PLAIN));
		automatic.addActionListener(e -> Updates.automatic.setValue(String.valueOf(automatic.isSelected())));
		content.add(automatic, "gaptop 10");

		JButton skip = Modern.button(newAction("Skip this version", e -> {
			Updates.skipped.setValue(release.version);
			dispose();
		}), null, Modern.Style.GHOST);
		JButton later = Modern.button(newAction("Later", e -> dispose()), null, Modern.Style.SECONDARY);
		JButton download = Modern.button(newAction("Download", e -> {
			openURI(release.url);
			dispose();
		}), null, Modern.Style.PRIMARY);

		JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "[]push[][]"));
		buttons.setOpaque(false);
		buttons.add(skip);
		buttons.add(later);
		buttons.add(download);
		content.add(buttons, "gaptop 12");

		setContentPane(content);
		getRootPane().setDefaultButton(download);
		installAction(content, KeyStroke.getKeyStroke("ESCAPE"), newAction("Later", e -> dispose()));
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}

	/**
	 * Release notes are Markdown: show them as plain text, without the markup.
	 */
	static String plainText(String markdown) {
		return markdown.replace("\r", "") //
				.replaceAll("(?m)^[ \\t]*```.*$\\n?", "") // code fences, also indented in lists
				.replaceAll("(?m)^#{1,6}\\s*", "") // headings
				.replaceAll("\\*\\*(.+?)\\*\\*", "$1") // bold
				.replaceAll("(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])", "$1") // italic
				.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1") // links
				.replaceAll("`([^`]+)`", "$1") // inline code
				.replaceAll("(?m)^- ", "• ") // bullets
				.replaceAll("\\n{3,}", "\n\n").trim();
	}

}
