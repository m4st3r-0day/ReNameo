package net.renameo.ui;

import static net.renameo.Settings.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicScrollPaneUI;

import net.miginfocom.swing.MigLayout;
import net.renameo.ApiKeys;
import net.renameo.ApiKeys.Service;
import net.renameo.ApiKeys.Source;
import net.renameo.ApplicationFolder;
import net.renameo.CacheManager;
import net.renameo.Language;
import net.renameo.StandardRenameAction;
import net.renameo.Updates;
import net.renameo.UserFiles;
import net.renameo.format.MediaBindingBean;
import net.renameo.guardian.Guardian;
import net.renameo.platform.mac.Keychain;
import net.renameo.plugins.Plugins;
import net.renameo.ui.SettingsControls.Divider;
import net.renameo.ui.SettingsControls.Swatch;
import net.renameo.ui.SettingsControls.Switch;
import net.renameo.ui.rename.RenamePanel;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.ScrollablePanel;
import net.renameo.util.ui.SwingEventBus;

/**
 * All settings on one page: sections on the left, their options on the right. Options apply as soon as they change.
 */
public class SettingsPanel extends JPanel {

	public enum Section {
		APPEARANCE("Appearance", Glyph.Shape.EYE), RENAMING("Renaming", Glyph.Shape.PENCIL), API_KEYS("API keys", Glyph.Shape.LINK), FOLDERS("Folders", Glyph.Shape.FOLDER), GUARDIAN("Guardian", Glyph.Shape.CHECK_CIRCLE), UPDATES("Updates", Glyph.Shape.ARROW_DOWN), ADVANCED("Advanced", Glyph.Shape.SLIDERS), ABOUT("About", Glyph.Shape.INFO);

		final String label;
		final Glyph.Shape icon;

		Section(String label, Glyph.Shape icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	private static Section requested = Section.APPEARANCE;
	private static java.lang.ref.WeakReference<SettingsPanel> instance = new java.lang.ref.WeakReference<SettingsPanel>(null);

	/**
	 * Show the Settings page at the given section.
	 */
	public static void open(Section section) {
		requested = section;
		SettingsPanel panel = instance.get();
		if (panel != null) {
			panel.show(section);
		}
		SwingEventBus.getInstance().post(new SettingsPanelBuilder());
	}

	private final JPanel nav = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 2", "[fill, 190!]"));
	private final JPanel content = new ScrollablePanel(new MigLayout("insets 4 8 24 8, fillx, wrap 1, hidemode 3", "[fill, grow, :680:]"));
	private Section current;

	public SettingsPanel() {
		super(new MigLayout("insets 0 0 20 0, fill", "[][fill, grow]", "[fill, grow]"));
		setOpaque(false);
		instance = new java.lang.ref.WeakReference<SettingsPanel>(this);

		nav.setOpaque(false);
		add(nav, "aligny top, gapright 16");

		content.setOpaque(false);
		JScrollPane scroll = new JScrollPane(content);
		Modern.onThemeChange(scroll, () -> {
			scroll.setUI(new BasicScrollPaneUI());
			scroll.setBorder(null);
			scroll.setOpaque(false);
			scroll.getViewport().setOpaque(false);
		});
		Modern.slim(scroll);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, "grow, wmin 0, hmin 0");

		putClientProperty(Modern.CUSTOM, true);
		Modern.onThemeChange(this, () -> show(current != null ? current : requested));
	}

	@Override
	public void addNotify() {
		super.addNotify();
		show(requested);
	}

	void show(Section section) {
		current = section;
		requested = section;

		nav.removeAll();
		for (Section s : Section.values()) {
			JButton b = Modern.button(newAction(s.label, e -> show(s)), Glyph.of(s.icon, 16, s == section ? NightTheme::getAccent : NightTheme::getDimForeground), s == section ? Modern.Style.SECONDARY : Modern.Style.GHOST);
			b.setHorizontalAlignment(JButton.LEFT);
			b.setIconTextGap(10);
			nav.add(b, "h 36!");
		}

		content.removeAll();
		JLabel title = Modern.label(section.label, 20f, Font.BOLD, false);
		content.add(title, "gapbottom 6");
		switch (section) {
		case APPEARANCE:
			appearance();
			break;
		case RENAMING:
			renaming();
			break;
		case API_KEYS:
			apiKeys();
			break;
		case FOLDERS:
			folders();
			break;
		case GUARDIAN:
			guardian();
			break;
		case UPDATES:
			updates();
			break;
		case ADVANCED:
			advanced();
			break;
		default:
			about();
		}
		nav.revalidate();
		nav.repaint();
		content.revalidate();
		content.repaint();
		SwingUtilities.invokeLater(() -> content.scrollRectToVisible(new java.awt.Rectangle(0, 0, 1, 1)));
	}

	// ---------------------------------------------------------------------------------------------
	// building blocks
	// ---------------------------------------------------------------------------------------------

	private void intro(String text) {
		content.add(paragraph(text, 12.5f), "wmin 0, gapbottom 10");
	}

	/**
	 * Text that wraps to the width of the page (an HTML label would be cut off).
	 */
	private static JComponent paragraph(String text, float size) {
		return new net.renameo.util.ui.WrappingText(text.replaceAll("</?b>", "").replace("&nbsp;", " "), size);
	}

	private void group(String title) {
		content.add(Modern.caption(title), "gaptop 16, gapbottom 2");
	}

	/**
	 * Title and explanation on the left, the control on the right.
	 */
	private JComponent row(String title, String description, JComponent control) {
		JPanel row = new JPanel(new MigLayout("insets 10 0 10 0, fillx", "[grow, fill][]", "[]2[]"));
		row.setOpaque(false);
		row.add(Modern.label(title, 13f, Font.PLAIN, false), "cell 0 0, wmin 0");
		if (description != null) {
			row.add(paragraph(description, 11.5f), "cell 0 1, wmin 0, growx");
		}
		if (control != null) {
			row.add(control, "cell 1 0 1 2, aligny center, gapleft 16");
		}
		content.add(row, "wmin 0");
		content.add(new Divider(), "h 1!");
		return row;
	}

	private void switchRow(String title, String description, boolean value, Consumer<Boolean> setter) {
		row(title, description, new Switch(value, setter));
	}

	private static JButton button(String name, Glyph.Shape icon, Consumer<ActionEvent> action) {
		return Modern.button(newAction(name, action), icon == null ? null : Glyph.of(icon, 15, NightTheme::getForeground), Modern.Style.SECONDARY);
	}

	private static JLabel link(String text, String url) {
		JLabel link = Modern.label(text, 12f, Font.PLAIN, false);
		link.setForeground(NightTheme.getAccent());
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(mouseClicked(e -> openURI(url)));
		return link;
	}

	private static String home(String path) {
		return path.replace(System.getProperty("user.home"), "~");
	}

	/**
	 * A folder field with a Choose button; the listener gets the chosen folder.
	 */
	private JComponent folderPicker(String path, String dialogTitle, Consumer<File> listener) {
		JTextField field = Modern.field(new JTextField(home(path)));
		field.setEditable(false);
		JPanel row = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[grow, fill][]"));
		row.setOpaque(false);
		row.add(field, "h 32!, wmin 0");
		row.add(button("Choose …", Glyph.Shape.FOLDER, evt -> {
			File folder = UserFiles.showOpenDialogSelectFolder(path.isEmpty() ? null : new File(path), dialogTitle, evt);
			if (folder != null) {
				field.setText(home(folder.getPath()));
				listener.accept(folder);
			}
		}), "h 32!");
		return row;
	}

	private static void toast(String title, String detail) {
		SwingEventBus.getInstance().post(new AppEvents.Toast(title, detail, AppEvents.Status.Kind.READY));
	}

	// ---------------------------------------------------------------------------------------------
	// sections
	// ---------------------------------------------------------------------------------------------

	private void appearance() {
		// the theme is changed after the click, it rebuilds every window
		switchRow("Dark theme", "Dark windows; off uses the light theme", NightTheme.isNightMode(), on -> SwingUtilities.invokeLater(() -> NightTheme.setNightMode(on)));
		switchRow("Compact rows", "Denser lists for large libraries", NightTheme.isCompact(), on -> SwingUtilities.invokeLater(() -> NightTheme.setCompact(on)));

		JPanel swatches = new JPanel(new MigLayout("insets 0, gap 6"));
		swatches.setOpaque(false);
		Color custom = NightTheme.getCustomAccent();
		for (int i = 0; i < NightTheme.ACCENT_PRESETS.length; i++) {
			Color color = NightTheme.ACCENT_PRESETS[i];
			boolean selected = custom == null ? i == 0 : color.equals(custom);
			swatches.add(new Swatch(color, NightTheme.ACCENT_PRESET_NAMES[i], selected, c -> SwingUtilities.invokeLater(() -> {
				NightTheme.setAccent(c);
				show(Section.APPEARANCE);
			})));
		}
		row("Accent color", "Buttons, selections and highlights", swatches);
	}

	private static final String[][] PROFILES = { { "plex", "Plex" }, { "jellyfin", "Jellyfin" }, { "emby", "Emby" }, { "kodi", "Kodi" }, { "windows", "Windows-friendly, file name only" }, { "macos", "macOS-friendly, file name only" } };

	private void renaming() {
		intro("How the <b>Rename</b> page names files. The same options are in its ⌄ menu.");

		JComboBox<String> profile = new JComboBox<String>();
		String currentProfile = RenamePanel.getNamingProfile();
		for (String[] p : PROFILES) {
			profile.addItem(p[1]);
		}
		if ("custom".equals(currentProfile)) {
			profile.addItem("Custom format");
		}
		for (int i = 0; i < PROFILES.length; i++) {
			if (PROFILES[i][0].equals(currentProfile)) {
				profile.setSelectedIndex(i);
			}
		}
		if ("custom".equals(currentProfile)) {
			profile.setSelectedIndex(PROFILES.length);
		}
		profile.addActionListener(e -> {
			int i = profile.getSelectedIndex();
			if (i >= 0 && i < PROFILES.length) {
				RenamePanel.setNamingProfile(PROFILES[i][0]);
			}
		});
		row("Naming profile", "Plex, Jellyfin, Emby and Kodi tidy up in place: the folder of a series or movie gets its proper name, episodes go into season folders. A custom format is made with Format on the Rename page.", profile);

		switchRow("Database ID in movie file names", "Dune (2021) [tmdbid-438631].mkv helps the media server pick the right movie; off: Dune (2021).mkv", MediaBindingBean.isMovieIds(), on -> {
			MediaBindingBean.persistentMovieIds.setValue(String.valueOf(on));
			RenamePanel.refreshMovieNames();
		});

		List<Language> languages = Language.availableLanguages().stream().sorted(Language.ALPHABETIC_ORDER).collect(Collectors.toList());
		JComboBox<Language> language = new JComboBox<Language>(languages.toArray(new Language[0]));
		language.setRenderer(new javax.swing.DefaultListCellRenderer() {

			@Override
			public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				return super.getListCellRendererComponent(list, value instanceof Language ? ((Language) value).getName() : value, index, isSelected, cellHasFocus);
			}
		});
		String code = RenamePanel.getTitleLanguage();
		for (Language l : languages) {
			if (l.getCode().equals(code)) {
				language.setSelectedItem(l);
			}
		}
		language.addActionListener(e -> {
			Language l = (Language) language.getSelectedItem();
			if (l != null) {
				RenamePanel.setTitleLanguage(l.getCode());
			}
		});
		row("Language of the titles", "Series, movie and episode titles are fetched in this language", language);

		switchRow("Download poster and fanart", "Images from TheMovieDB are saved next to the renamed files", Boolean.parseBoolean(RenamePanel.getArtworkOption().getValue()), on -> RenamePanel.getArtworkOption().setValue(String.valueOf(on)));
	}

	private void apiKeys() {
		intro("ReNameo looks up titles on TheMovieDB: create a free account there and paste its <b>API Key</b>. The other services are optional. For subtitles, sign in on the <b>Subtitles</b> page.");

		Map<Service, JTextField> fields = new EnumMap<Service, JTextField>(Service.class);
		for (Service service : Service.values()) {
			JTextField field = Modern.field(new JTextField(24));
			Source source = ApiKeys.getSource(service);
			if (source == Source.SAVED) {
				field.setText(ApiKeys.getSaved(service));
			}
			fields.put(service, field);

			JPanel head = new JPanel(new MigLayout("insets 0, fillx", "[]push[]"));
			head.setOpaque(false);
			head.add(Modern.label(service.title + (service.required ? "" : " (optional)"), 13f, Font.BOLD, false));
			head.add(link("Get a free key", service.signup));
			content.add(head, "gaptop 10");
			content.add(field, "h 34!");
			String note = source == Source.ENVIRONMENT ? "Using the " + service.environment + " environment variable." : source == Source.BUILT_IN ? "Using the key built into this copy of the app. Enter your own to replace it." : source == Source.SAVED ? (Keychain.isSupported() ? "Saved in the macOS Keychain." : "Saved in the app settings.") : service.required ? "Required for movies and TV shows." : " ";
			content.add(Modern.label(note, 11.5f, Font.PLAIN, true));
		}

		JLabel status = Modern.label(" ", 12f, Font.PLAIN, true);
		JButton[] holder = new JButton[1];
		JButton save = Modern.button(newAction("Save", e -> {
			JButton button = holder[0];
			String tmdb = fields.get(Service.TMDB).getText().trim();
			boolean check = !tmdb.isEmpty() && !tmdb.equals(ApiKeys.getSaved(Service.TMDB));
			status.setForeground(NightTheme.getDimForeground());
			status.setText(check ? "Checking the TheMovieDB key …" : "Saving …");
			button.setEnabled(false);
			newSwingWorker(() -> check ? ApiKeys.checkTheMovieDB(tmdb) : null, problem -> {
				button.setEnabled(true);
				if (problem != null) {
					status.setForeground(NightTheme.getDanger());
					status.setText("<html>" + escapeHTML(problem) + "</html>");
					return;
				}
				try {
					fields.forEach((service, field) -> ApiKeys.save(service, field.getText()));
					status.setForeground(NightTheme.getSuccess());
					status.setText("Saved" + (check ? ", the TheMovieDB key works" : ""));
				} catch (Exception x) {
					status.setForeground(NightTheme.getDanger());
					status.setText(x.getMessage());
				}
			}).execute();
		}), null, Modern.Style.PRIMARY);
		holder[0] = save;
		JPanel buttons = new JPanel(new MigLayout("insets 0, fillx", "[grow, fill][]"));
		buttons.setOpaque(false);
		buttons.add(status, "wmin 0");
		buttons.add(save);
		content.add(buttons, "gaptop 14");
	}

	private static final StandardRenameAction[] WATCH_ACTIONS = { StandardRenameAction.MOVE, StandardRenameAction.COPY, StandardRenameAction.HARDLINK, StandardRenameAction.SYMLINK };

	private void folders() {
		group("Library");
		File library = Guardian.getFolder();
		row("Library folder", "Your movies and series. The <b>Library</b> page and the <b>Guardian</b> look at this folder.", null);
		content.add(folderPicker(library == null ? "" : library.getPath(), "Library folder", folder -> {
			Guardian.folder.setValue(folder.getPath());
			toast("Library folder", folder.getName());
		}), "gapbottom 6");

		group("Watch folder");
		intro("While ReNameo is open, new video, audio and subtitle files that arrive in this folder (e.g. Downloads) are renamed on their own. Files changed in the last 2 minutes are left alone, so downloads can finish.");
		String[] path = { WatchFolder.folder.getValue() };
		Switch enabled = new Switch(WatchFolder.isEnabled(), on -> {
		});
		row("Rename new files automatically", null, enabled);
		content.add(folderPicker(path[0], "Folder to watch", folder -> path[0] = folder.getPath()), "gapbottom 6");

		JComboBox<WatchFolder.Naming> naming = new JComboBox<WatchFolder.Naming>(WatchFolder.Naming.values());
		naming.setSelectedItem(WatchFolder.getNaming());
		row("Names", null, naming);

		JComboBox<String> action = new JComboBox<String>();
		for (StandardRenameAction it : WATCH_ACTIONS) {
			action.addItem(it.getDisplayName());
			if (it == WatchFolder.getAction()) {
				action.setSelectedIndex(action.getItemCount() - 1);
			}
		}
		row("Action", null, action);

		JSpinner interval = Modern.spinner(new JSpinner(new SpinnerNumberModel(WatchFolder.getIntervalMinutes(), 1, 1440, 1)));
		row("Check every (minutes)", null, interval);

		JLabel status = Modern.label(" ", 12f, Font.PLAIN, true);
		JButton save = Modern.button(newAction("Save watch folder", e -> {
			if (enabled.isOn() && (path[0].isEmpty() || !new File(path[0]).isDirectory())) {
				status.setForeground(NightTheme.getDanger());
				status.setText("Please choose an existing folder.");
				return;
			}
			WatchFolder.enabled.setValue(Boolean.toString(enabled.isOn()));
			WatchFolder.folder.setValue(path[0]);
			WatchFolder.naming.setValue(((WatchFolder.Naming) naming.getSelectedItem()).name());
			WatchFolder.action.setValue(WATCH_ACTIONS[action.getSelectedIndex()].name());
			WatchFolder.interval.setValue(interval.getValue().toString());
			WatchFolder.restart();
			status.setForeground(NightTheme.getSuccess());
			status.setText(enabled.isOn() ? String.format("Watching %s every %s min", new File(path[0]).getName(), interval.getValue()) : "Watch folder is off");
		}), null, Modern.Style.PRIMARY);
		JPanel buttons = new JPanel(new MigLayout("insets 0, fillx", "[grow, fill][]"));
		buttons.setOpaque(false);
		buttons.add(status, "wmin 0");
		buttons.add(save);
		content.add(buttons, "gaptop 12");
	}

	private void guardian() {
		intro("The guardian keeps your library in order: it finds files whose names don't follow the naming profile and corrects them, or asks first. Every change is written down in its journal and can be undone. The first check of a library only suggests.");

		File library = Guardian.getFolder();
		row("Library folder", library == null ? "Not chosen yet: set it under <b>Folders</b>." : home(library.getPath()), button("Change …", Glyph.Shape.FOLDER, e -> show(Section.FOLDERS)));

		JComboBox<Guardian.Mode> mode = new JComboBox<Guardian.Mode>(Guardian.Mode.values());
		mode.setSelectedItem(Guardian.getMode());
		mode.addActionListener(e -> Guardian.mode.setValue(((Guardian.Mode) mode.getSelectedItem()).name()));
		row("What it may do", "<b>Only suggest</b>: changes nothing, you approve on the Guardian page. <b>Correct when certain</b>: corrects what two lookups agree on, suggests the rest. <b>Correct everything</b>: corrects all it finds. Everything is in the journal either way.", mode);

		JComboBox<String> profile = new JComboBox<String>(new String[] { "Plex", "Jellyfin", "Emby", "Kodi" });
		for (int i = 0; i < Guardian.PROFILES.length; i++) {
			if (Guardian.PROFILES[i].equals(Guardian.profile.getValue())) {
				profile.setSelectedIndex(i);
			}
		}
		profile.addActionListener(e -> Guardian.profile.setValue(Guardian.PROFILES[profile.getSelectedIndex()]));
		row("Naming", "The names the library should have", profile);

		switchRow("Check every day", "While ReNameo is open, at the hour below", Boolean.parseBoolean(Guardian.enabled.getValue()), on -> Guardian.enabled.setValue(String.valueOf(on)));

		JComboBox<String> hour = new JComboBox<String>();
		for (int h = 0; h < 24; h++) {
			hour.addItem(String.format("%02d:00", h));
		}
		try {
			hour.setSelectedIndex(Integer.parseInt(Guardian.hour.getValue()));
		} catch (Exception e) {
			hour.setSelectedIndex(3);
		}
		hour.addActionListener(e -> Guardian.hour.setValue(String.valueOf(hour.getSelectedIndex())));
		row("At", null, hour);

		JSpinner limit = Modern.spinner(new JSpinner(new SpinnerNumberModel(Guardian.getLimit(), 1, 10000, 10)));
		limit.addChangeListener(e -> Guardian.limit.setValue(limit.getValue().toString()));
		row("Most changes per check", "With more, nothing is changed and everything waits for you, in case something went wrong", limit);

		switchRow("Double-check names that look right", "Slower and asks TheMovieDB about every title, also the ones already named Name (Year)/Season 01/…", Boolean.parseBoolean(Guardian.thorough.getValue()), on -> Guardian.thorough.setValue(String.valueOf(on)));

		String last = Guardian.getLastRun();
		row("Last check", last == null ? "Never" : last, null);

		JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8"));
		buttons.setOpaque(false);
		buttons.add(button("Open the Guardian page", Glyph.Shape.CHECK_CIRCLE, e -> SwingEventBus.getInstance().post(new GuardianPanelBuilder())));
		JButton now = Modern.button(newAction("Check now", e -> {
			GuardianRunner.runNow();
			SwingEventBus.getInstance().post(new GuardianPanelBuilder());
		}), null, Modern.Style.PRIMARY);
		now.setEnabled(library != null);
		buttons.add(now);
		content.add(buttons, "gaptop 12");
	}

	private void updates() {
		switchRow("Check for updates automatically", "Once a day ReNameo asks GitHub whether there is a new version", Boolean.parseBoolean(Updates.automatic.getValue()), on -> Updates.automatic.setValue(String.valueOf(on)));
		row("Version", getApplicationName() + " " + getApplicationVersion(), button("Check now", Glyph.Shape.ARROW_DOWN, e -> UpdateDialog.check(SwingUtilities.getWindowAncestor(this), true)));
		row("What's new", "The changes of every version", button("Open", null, e -> openURI("https://github.com/" + Updates.REPOSITORY + "/blob/main/CHANGELOG.md")));
	}

	private void advanced() {
		File data = ApplicationFolder.AppData.get();
		row("Data folder", "Settings, history, cache, plugins and the guardian journal: " + home(data.getPath()), button("Open", Glyph.Shape.FOLDER, e -> openURI(data.toURI().toString())));
		File errors = ApplicationFolder.AppData.resolve("error.log");
		row("Error log", "Warnings and errors, handy when something goes wrong", button("Open", Glyph.Shape.FILE, e -> {
			if (errors.isFile()) {
				openURI(errors.toURI().toString());
			} else {
				toast("No errors so far", "There is no error log yet.");
			}
		}));
		row("Plugins folder", home(Plugins.getFolder().getPath()), button("Open", Glyph.Shape.FOLDER, e -> openURI(Plugins.getFolder().toURI().toString())));
		row("Offline index", "Popular titles known without asking TheMovieDB (e.g. Il Trono di Spade → Game of Thrones)", button("Update", Glyph.Shape.DOWNLOAD, e -> OfflineIndexAction.run(SwingUtilities.getWindowAncestor(this))));
		row("Cache", "Answers of the online databases, kept so the next lookup is fast", button("Clear", Glyph.Shape.TRASH, e -> {
			CacheManager.getInstance().clearAll();
			toast("Cache cleared", "Titles are looked up online again.");
		}));
	}

	private void about() {
		JLabel name = Modern.label(getApplicationName() + " " + getApplicationVersion(), 16f, Font.BOLD, false);
		content.add(name, "gaptop 4");
		intro("Clean, consistent names for movies, TV shows, anime, music and subtitles. Built on the open source code of FileBot 4.8.0 by Reinhard Pointner, used under the Modified Don't Be A Dick Public License. Metadata from TheMovieDB, TVmaze, AniDB, OMDb, Fanart.tv, AcoustID and OpenSubtitles.");
		JPanel links = new JPanel(new MigLayout("insets 0, gap 8"));
		links.setOpaque(false);
		links.add(button("User guide", Glyph.Shape.HELP, e -> openURI(getEmbeddedHelpURL())));
		links.add(button("GitHub", Glyph.Shape.LINK, e -> openURI("https://github.com/" + Updates.REPOSITORY)));
		content.add(links);
	}

}
