package net.renameo.ui;

import static net.renameo.util.ui.SwingUI.*;

import java.awt.Cursor;
import java.awt.Font;
import java.awt.Window;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import net.miginfocom.swing.MigLayout;
import net.renameo.ApiKeys;
import net.renameo.ApiKeys.Service;
import net.renameo.ApiKeys.Source;
import net.renameo.platform.mac.Keychain;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;

/**
 * Where users enter their own (free) API keys. TheMovieDB is required for movies and series, the others are optional.
 */
public class ApiKeysDialog extends JDialog {

	private final Map<Service, JTextField> fields = new EnumMap<Service, JTextField>(Service.class);
	private final JLabel status = Modern.label(" ", 12f, Font.PLAIN, true);

	public static void show(Window owner, boolean firstRun) {
		new ApiKeysDialog(owner, firstRun).setVisible(true);
	}

	private ApiKeysDialog(Window owner, boolean firstRun) {
		super(owner, "API keys", ModalityType.APPLICATION_MODAL);

		JPanel content = new JPanel(new MigLayout("insets 24 26 20 26, fillx, wrap 1", "[fill, grow]"));
		content.setBackground(NightTheme.getBackground());

		JLabel title = Modern.label(firstRun ? "Welcome to ReNameo" : "API keys", 18f, Font.BOLD, false);
		title.setIcon(Glyph.of(Glyph.Shape.LINK, 22, NightTheme::getAccent));
		title.setIconTextGap(10);
		content.add(title);
		content.add(Modern.label("<html><body style='width: 330px'>ReNameo looks up titles on TheMovieDB. Create a free account there and paste its <b>API Key</b> below. The other services are optional.</body></html>", 12.5f, Font.PLAIN, true), "gapbottom 14");

		for (Service service : Service.values()) {
			JTextField field = Modern.field(new JTextField(18));
			Source source = ApiKeys.getSource(service);
			if (source == Source.SAVED) {
				field.setText(ApiKeys.getSaved(service));
			}
			fields.put(service, field);

			JPanel label = new JPanel(new MigLayout("insets 0, fillx", "[]push[]"));
			label.setOpaque(false);
			label.add(Modern.label(service.title + (service.required ? "" : " (optional)"), 12f, Font.BOLD, true));
			label.add(link("Get a free key", service.signup));
			content.add(label);
			content.add(field, "h 34!");

			String note = note(service, source);
			content.add(Modern.label(note == null ? " " : note, 11f, Font.PLAIN, true), "gapbottom 6");
		}

		content.add(status, "gaptop 4");

		JButton cancel = Modern.button(newAction(firstRun ? "Later" : "Cancel", e -> dispose()), null, Modern.Style.SECONDARY);
		JButton save = Modern.button(newAction("Save", e -> save()), null, Modern.Style.PRIMARY);

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

	private static String note(Service service, Source source) {
		switch (source) {
		case ENVIRONMENT:
			return "Using the " + service.environment + " environment variable.";
		case BUILT_IN:
			return "Using the key built into this copy of the app. Enter your own to replace it.";
		case MISSING:
			return service.required ? "Required for movies and TV shows." : null;
		default:
			return Keychain.isSupported() ? "Saved in the macOS Keychain." : "Saved in the app settings.";
		}
	}

	private static JLabel link(String text, String url) {
		JLabel link = Modern.label(text, 12f, Font.PLAIN, false);
		link.setForeground(NightTheme.getAccent());
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(mouseClicked(e -> openURI(url)));
		return link;
	}

	private void save() {
		String tmdb = fields.get(Service.TMDB).getText().trim();
		boolean checkTmdb = !tmdb.isEmpty() && !tmdb.equals(ApiKeys.getSaved(Service.TMDB));

		// the built-in or environment key may be all there is
		if (tmdb.isEmpty() && ApiKeys.getSource(Service.TMDB) == Source.MISSING) {
			showStatus("Please enter your TheMovieDB API key.", true);
			fields.get(Service.TMDB).requestFocusInWindow();
			return;
		}

		showStatus(checkTmdb ? "Checking the TheMovieDB key …" : "Saving …", false);
		getRootPane().getDefaultButton().setEnabled(false);

		newSwingWorker(() -> checkTmdb ? ApiKeys.checkTheMovieDB(tmdb) : null, problem -> {
			if (problem != null) {
				getRootPane().getDefaultButton().setEnabled(true);
				showStatus(problem, true);
				fields.get(Service.TMDB).requestFocusInWindow();
				return;
			}
			try {
				fields.forEach((service, field) -> ApiKeys.save(service, field.getText()));
			} catch (Exception e) {
				getRootPane().getDefaultButton().setEnabled(true);
				showStatus(e.getMessage(), true);
				return;
			}
			SwingEventBus.getInstance().post(new AppEvents.Toast("API keys saved", "You can change them in Settings.", AppEvents.Status.Kind.READY));
			dispose();
		}).execute();
	}

	private void showStatus(String text, boolean error) {
		status.setText("<html><body style='width: 330px'>" + text + "</body></html>");
		status.setForeground(error ? NightTheme.getDanger() : NightTheme.getDimForeground());
		pack();
	}

}
