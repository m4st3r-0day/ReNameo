package net.renameo.ui.rename;

import static java.awt.event.KeyEvent.*;
import static java.util.Collections.*;
import static java.util.Comparator.*;
import static javax.swing.KeyStroke.*;
import static javax.swing.SwingUtilities.*;
import static net.renameo.Logging.*;
import static net.renameo.Settings.*;
import static net.renameo.media.MediaDetection.*;
import static net.renameo.util.ExceptionUtilities.*;
import static net.renameo.util.FileUtilities.*;
import static net.renameo.util.ui.LoadingOverlayPane.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.BasicStroke;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;

import com.google.common.eventbus.Subscribe;

import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.ListSelection;
import ca.odell.glazedlists.swing.DefaultEventSelectionModel;
import net.renameo.ApplicationFolder;
import net.renameo.History;
import net.renameo.HistorySpooler;
import net.renameo.InvalidResponseException;
import net.renameo.Language;
import net.renameo.ResourceManager;
import net.renameo.Settings;
import net.renameo.StandardRenameAction;
import net.renameo.UserFiles;
import net.renameo.WebServices;
import net.renameo.format.ExpressionFileFormat;
import net.renameo.format.MediaBindingBean;
import net.renameo.media.MetaAttributes;
import net.renameo.platform.mac.MacAppUtilities;
import net.renameo.similarity.Match;
import net.renameo.ui.AppEvents;
import net.renameo.ui.MainFrame;
import net.renameo.ui.SelectDialog;
import net.renameo.ui.rename.FormatDialog.Mode;
import net.renameo.ui.rename.RenameModel.FormattedFuture;
import net.renameo.ui.transfer.BackgroundFileTransferablePolicy;
import net.renameo.ui.transfer.TransferablePolicy;
import net.renameo.ui.transfer.TransferablePolicy.TransferAction;
import net.renameo.util.AlphanumComparator;
import net.renameo.util.PreferencesMap.PreferencesEntry;
import net.renameo.util.ui.ActionPopup;
import net.renameo.util.ui.DefaultFancyListCellRenderer;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.LanguageChipIcon;
import net.renameo.util.ui.LoadingOverlayPane;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;
import net.renameo.vfs.FileInfo;
import net.renameo.vfs.SimpleFileInfo;
import net.renameo.web.AudioTrack;
import net.renameo.web.AudioTrackFormat;
import net.renameo.web.Episode;
import net.renameo.web.EpisodeFormat;
import net.renameo.web.EpisodeListProvider;
import net.renameo.web.Movie;
import net.renameo.web.MovieFormat;
import net.renameo.web.MovieIdentificationService;
import net.renameo.web.MusicIdentificationService;
import net.renameo.web.SortOrder;
import net.miginfocom.swing.MigLayout;

public class RenamePanel extends JComponent {

	public static final String MATCH_MODE_OPPORTUNISTIC = "Opportunistic";
	public static final String MATCH_MODE_STRICT = "Strict";

	protected final RenameModel renameModel = new RenameModel();

	protected final RenameList<FormattedFuture> namesList = new RenameList<FormattedFuture>(renameModel.names());

	protected final RenameList<File> filesList = new RenameList<File>(renameModel.files());

	protected final MatchAction matchAction = new MatchAction(renameModel);

	protected final RenameAction renameAction = new RenameAction(renameModel);

	private static final PreferencesEntry<String> persistentEpisodeFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.episode");
	private static final PreferencesEntry<String> persistentMovieFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.movie");
	private static final PreferencesEntry<String> persistentMusicFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.music");
	private static final PreferencesEntry<String> persistentFileFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.file");

	private static final PreferencesEntry<String> persistentLastFormatState = Settings.forPackage(RenamePanel.class).entry("rename.last.format.state").defaultValue(Mode.Episode.name());
	private static final PreferencesEntry<String> persistentPreferredMatchMode = Settings.forPackage(RenamePanel.class).entry("rename.match.mode").defaultValue(MATCH_MODE_OPPORTUNISTIC);
	private static final PreferencesEntry<String> persistentPreferredLanguage = Settings.forPackage(RenamePanel.class).entry("rename.language").defaultValue("en");
	private static final PreferencesEntry<String> persistentPreferredEpisodeOrder = Settings.forPackage(RenamePanel.class).entry("rename.episode.order").defaultValue("Airdate");

	private static final Map<String, Preset> persistentPresets = Settings.forPackage(RenamePanel.class).node("presets").asMap(Preset.class);

	private static final PreferencesEntry<String> persistentServiceOrder = Settings.forPackage(RenamePanel.class).entry("rename.service.order");
	private static final String PREF_HIDE_UNAVAILABLE = "rename.service.hideUnavailable";

	private static boolean isHideUnavailable() {
		return Boolean.parseBoolean(Settings.forPackage(RenamePanel.class).get(PREF_HIDE_UNAVAILABLE, "false"));
	}

	private static void setHideUnavailable(boolean hide) {
		Settings.forPackage(RenamePanel.class).put(PREF_HIDE_UNAVAILABLE, String.valueOf(hide));
	}

	public RenamePanel() {
		namesList.setTitle("New Names");
		namesList.setTransferablePolicy(new NamesListTransferablePolicy(renameModel.values()));

		filesList.setTitle("Original Files");
		filesList.setTransferablePolicy(new FilesListTransferablePolicy(renameModel.files()));

		// restore icon indicating current match mode
		matchAction.setMatchMode(isMatchModeStrict());

		// earlier naming profiles moved files into a library folder, now they rename in place
		for (PreferencesEntry<String> format : Arrays.asList(persistentEpisodeFormat, persistentMovieFormat, persistentMusicFormat)) {
			Matcher legacy = LEGACY_LIBRARY_FORMAT.matcher(String.valueOf(format.getValue()));
			if (legacy.matches()) {
				format.setValue(inPlaceExpression(legacy.group(1) != null ? legacy.group(1) : legacy.group(2)));
			}
		}
		// the naming profiles of episodes and movies now also tidy the folder of the series or movie
		for (PreferencesEntry<String> format : Arrays.asList(persistentEpisodeFormat, persistentMovieFormat)) {
			Matcher inPlace = IN_PLACE_FORMAT.matcher(String.valueOf(format.getValue()));
			if (inPlace.matches()) {
				format.setValue(tidyExpression(inPlace.group(1)));
			}
		}

		try {
			// restore custom episode formatter
			renameModel.useFormatter(Episode.class, new ExpressionFormatter(persistentEpisodeFormat.getValue(), EpisodeFormat.SeasonEpisode, Episode.class));
		} catch (Exception e) {
			// use default formatter
		}

		try {
			// restore custom movie formatter
			renameModel.useFormatter(Movie.class, new ExpressionFormatter(persistentMovieFormat.getValue(), MovieFormat.NameYear, Movie.class));
		} catch (Exception e) {
			// use default movie formatter
			renameModel.useFormatter(Movie.class, new MovieFormatter());
		}

		try {
			// restore custom music formatter
			renameModel.useFormatter(AudioTrack.class, new ExpressionFormatter(persistentMusicFormat.getValue(), new AudioTrackFormat(), AudioTrack.class));
		} catch (Exception e) {
			// use default formatter
		}

		try {
			// restore custom music formatter
			renameModel.useFormatter(File.class, new ExpressionFormatter(persistentFileFormat.getValue(), new FileNameFormat(), File.class));
		} catch (Exception e) {
			// make sure to put File formatter at position 3
			renameModel.useFormatter(File.class, new FileNameFormatter());
		} finally {
			// use default filename formatter
			renameModel.useFormatter(FileInfo.class, new FileNameFormatter());
		}

		RenameListCellRenderer cellrenderer = new RenameListCellRenderer(renameModel, ApplicationFolder.UserHome.get());

		namesList.getListComponent().setCellRenderer(cellrenderer);
		filesList.getListComponent().setCellRenderer(cellrenderer);

		DefaultEventSelectionModel<Match<Object, File>> selectionModel = new DefaultEventSelectionModel<Match<Object, File>>(renameModel.matches());
		selectionModel.setSelectionMode(ListSelection.MULTIPLE_INTERVAL_SELECTION);

		// use the same selection model for both lists to synchronize selection
		namesList.getListComponent().setSelectionModel(selectionModel);
		filesList.getListComponent().setSelectionModel(selectionModel);

		// synchronize viewports
		new ScrollPaneSynchronizer(namesList, filesList);

		// delete items from both lists
		Action removeAction = newAction("Exclude Selected Items", ResourceManager.getIcon("dialog.cancel"), evt -> {
			RenameList list = null;
			boolean deleteCell;

			if (evt.getSource() instanceof JButton) {
				list = filesList;
				deleteCell = isShiftOrAltDown(evt);
			} else {
				list = ((RenameList) evt.getSource());
				deleteCell = isShiftOrAltDown(evt);
			}

			int index = list.getListComponent().getSelectedIndex();
			if (index >= 0) {
				if (deleteCell) {
					EventList eventList = list.getModel();
					if (index < eventList.size()) {
						list.getModel().remove(index);
					}
				} else {
					renameModel.matches().remove(index);
				}
				int maxIndex = list.getModel().size() - 1;
				if (index > maxIndex) {
					index = maxIndex;
				}
				if (index >= 0) {
					list.getListComponent().setSelectedIndex(index);
				}
			}
		});
		namesList.setRemoveAction(removeAction);
		filesList.setRemoveAction(removeAction);

		// create fetch popup
		ActionPopup fetchPopup = createFetchPopup();
		Action fetchPopupAction = new ShowPopupAction("Fetch Metadata", ResourceManager.getIcon("action.fetch"));
		JPopupMenu contextMenu = createContextMenu(fetchPopup);
		filesList.getListComponent().setComponentPopupMenu(contextMenu);
		namesList.getListComponent().setComponentPopupMenu(contextMenu);

		// primary actions in the page header
		JButton matchButton = Modern.button(matchAction, Glyph.of(Glyph.Shape.MATCH, 17, Color.WHITE), Modern.Style.PRIMARY);
		matchButton.setComponentPopupMenu(fetchPopup);
		matchButton.setToolTipText("Match files to the names in the list, or pick a data source");

		JButton fetchButton = Modern.button(fetchPopupAction, Glyph.of(Glyph.Shape.DOWNLOAD, 17, NightTheme::getForeground), Modern.Style.SECONDARY);
		fetchButton.setComponentPopupMenu(fetchPopup);

		JButton formatButton = Modern.button(newAction("Format", evt -> showFormatEditor(null)), Glyph.of(Glyph.Shape.EYE, 17, NightTheme::getForeground), Modern.Style.SECONDARY);
		formatButton.setToolTipText("Edit the naming scheme with a live preview");

		JPanel pageActions = new JPanel(new MigLayout("insets 0, gap 10"));
		pageActions.setOpaque(false);
		pageActions.add(matchButton);
		pageActions.add(fetchButton);
		pageActions.add(formatButton);
		pageActions.add(Modern.iconButton(new ShowPresetsPopupAction(), Glyph.Shape.BOOKMARK, "Presets (1–9)"), "gapleft 4");
		pageActions.add(Modern.iconButton(undoAction, Glyph.Shape.UNDO, "Undo last rename (⌘Z)"), "gapleft 0");
		pageActions.add(Modern.iconButton(openHistoryAction, Glyph.Shape.HISTORY, "Rename history"), "gapleft 0");
		pageActions.add(Modern.iconButton(toggleInspectorAction, Glyph.Shape.INSPECTOR, "Show / hide the metadata inspector (⌘I)"), "gapleft 0");
		putClientProperty(MainFrame.PAGE_ACTIONS, pageActions);
		putClientProperty(Modern.CUSTOM, true);

		// with nothing to match against yet, Match identifies the files automatically (use Fetch Metadata to pick a specific source)
		matchButton.addActionListener(evt -> {
			if (renameModel.names().isEmpty() && autoDetectAction != null && autoDetectAction.isEnabled()) {
				autoDetectAction.actionPerformed(evt);
			}
		});

		// rename options popup, attached to the start button
		ActionPopup settingsPopup = createSettingsPopup();
		JButton renameButton = Modern.button(renameAction, Glyph.of(Glyph.Shape.PLAY, 15, Color.WHITE), Modern.Style.VIOLET);
		renameButton.setComponentPopupMenu(settingsPopup);
		JButton renameOptionsButton = Modern.button(new ShowPopupAction("Rename Options", null), Glyph.of(Glyph.Shape.CHEVRON_DOWN, 15, Color.WHITE), Modern.Style.VIOLET);
		renameOptionsButton.setHideActionText(true);
		renameOptionsButton.setToolTipText("Rename options: extension handling and move / copy / link");
		renameOptionsButton.setComponentPopupMenu(settingsPopup);
		renameButton.setToolTipText("Preview every change, then apply (⌘↵)");

		// list tools
		JPanel fileTools = filesList.getButtonPanel();
		fileTools.add(Modern.iconButton(removeAction, Glyph.Shape.MINUS_CIRCLE, "Exclude selected item"), 0);
		fileTools.add(Modern.iconButton(clearFilesAction, Glyph.Shape.TRASH, "Clear all"), 1);

		JPanel nameTools = namesList.getButtonPanel();

		// reveal file location on double click
		filesList.getListComponent().addMouseListener(mouseClicked(evt -> {
			if (evt.getClickCount() == 2) {
				getWindow(evt.getSource()).setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
				try {
					JList list = (JList) evt.getSource();
					if (list.getSelectedIndex() >= 0) {
						UserFiles.revealFiles(list.getSelectedValuesList());
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, e.getMessage(), e);
				} finally {
					getWindow(evt.getSource()).setCursor(Cursor.getDefaultCursor());
				}
			}
		}));

		// open format editor for the selected match on double click
		namesList.getListComponent().addMouseListener(mouseClicked(evt -> {
			if (evt.getClickCount() == 2) {
				editSelectedMatch();
			}
		}));

		BackgroundFileTransferablePolicy<?> transferablePolicy = (BackgroundFileTransferablePolicy<?>) filesList.getTransferablePolicy();
		transferablePolicy.addPropertyChangeListener(evt -> {
			if (BackgroundFileTransferablePolicy.LOADING_PROPERTY.equals(evt.getPropertyName())) {
				filesList.firePropertyChange(LoadingOverlayPane.LOADING_PROPERTY, (boolean) evt.getOldValue(), (boolean) evt.getNewValue());
			}
		});

		JComponent filesEmpty = createDropZone();
		filesEmpty.setTransferHandler(filesList.getTransferHandler());
		JComponent namesEmpty = createHint(Glyph.Shape.LINK, "No proposed names yet", "Add files, then choose Match or Fetch Metadata.");
		namesEmpty.setTransferHandler(namesList.getTransferHandler());

		filesCard = createListCard("Original Files", filesCount, fileTools, new LoadingOverlayPane(filesList, filesList, "37px", "30px"), filesEmpty);
		namesCard = createListCard("Proposed Names", namesCount, nameTools, new LoadingOverlayPane(namesList, namesList, "37px", "30px"), namesEmpty);

		Action editAction = newAction("Open in Format Editor", evt -> editSelectedMatch());
		metadataPanel = new MetadataPanel(renameModel, selectionModel, editAction, changeMatchAction);

		setLayout(new MigLayout("insets 0, fill, gap 14", "[0:0:n, fill, grow, sg list][0:0:n, fill, grow, sg list][280!, fill]", "[0:0:n, fill, grow][]"));
		add(filesCard, "grow, wmin 0, hmin 0");
		add(namesCard, "grow, wmin 0, hmin 0");
		add(metadataPanel, "grow, hmin 0, wrap, hidemode 3");
		add(createStatusBar(renameButton, renameOptionsButton), "span 3, growx, wmin 0");

		metadataPanel.setVisible(Boolean.parseBoolean(persistentInspectorVisible.getValue()));
		putClientProperty(MainFrame.PAGE_COMMANDS, createCommands(fetchPopupAction));

		// install F2 and 1..9 keystroke actions
		SwingUtilities.invokeLater(this::installKeyStrokeActions);
	}

	private AutoCompleteAction autoDetectAction;
	private MetadataPanel metadataPanel;

	private static final PreferencesEntry<String> persistentInspectorVisible = Settings.forPackage(RenamePanel.class).entry("ui.inspector.visible").defaultValue("true");

	private final Action undoAction = newAction("Undo Last Rename", evt -> RenameUndo.undoLast(this));

	private final Action toggleInspectorAction = newAction("Toggle Inspector", evt -> {
		boolean visible = !metadataPanel.isVisible();
		metadataPanel.setVisible(visible);
		persistentInspectorVisible.setValue(String.valueOf(visible));
		revalidate();
	});

	private final Action changeMatchAction = newAction("Change Match …", evt -> changeMatch());

	private final Action matchSelectedAction = newAction("Match Selected", evt -> matchSelected(null));

	private final Action renameSelectedAction = newAction("Rename Selected …", evt -> renameAction.preview(evt, new LinkedHashSet<File>(getSelectedFiles())));

	private final Action clearMatchAction = newAction("Clear Match", evt -> clearSelected());

	private final Action revealAction = newAction("Reveal in Finder", evt -> {
		List<File> selected = getSelectedFiles();
		if (selected.size() > 0) {
			UserFiles.revealFiles(selected);
		}
	});

	private final Action setLanguageAction = newAction("Set Language …", evt -> {
		List<Language> languages = new ArrayList<Language>(Language.preferredLanguages());
		languages.addAll(Language.availableLanguages());
		Object choice = JOptionPane.showInputDialog(getWindow(this), "Fetch names for the selected files in:", "Set Language", JOptionPane.PLAIN_MESSAGE, null, languages.stream().distinct().toArray(), Language.getLanguage(persistentPreferredLanguage.getValue()));
		if (choice instanceof Language) {
			matchSelected(((Language) choice).getLocale());
		}
	});

	private final Action quickLookAction = newAction("Quick Look", evt -> {
		List<File> selected = getSelectedFiles();
		if (selected.size() > 0) {
			try {
				List<String> command = new ArrayList<String>(Arrays.asList("qlmanage", "-p"));
				selected.stream().limit(10).map(File::getPath).forEach(command::add);
				new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			} catch (Exception e) {
				debug.warning("Quick Look failed: " + e);
			}
		}
	});

	private JPopupMenu createContextMenu(ActionPopup fetchPopup) {
		JPopupMenu menu = new JPopupMenu();
		menu.add(matchSelectedAction);
		menu.add(changeMatchAction);
		menu.add(setLanguageAction);
		menu.add(clearMatchAction);
		menu.addSeparator();
		menu.add(renameSelectedAction);
		menu.add(quickLookAction);
		menu.add(revealAction);
		menu.addSeparator();
		menu.add(newAction("Fetch Metadata …", evt -> {
			JComponent invoker = (JComponent) menu.getInvoker();
			fetchPopup.show(invoker, invoker.getWidth() / 3, 40);
		}));
		return menu;
	}

	/**
	 * Files of the selected rows, in list order.
	 */
	private List<File> getSelectedFiles() {
		List<File> selected = new ArrayList<File>();
		for (int i : filesList.getListComponent().getSelectedIndices()) {
			if (i < renameModel.files().size()) {
				selected.add(renameModel.files().get(i));
			}
		}
		return selected;
	}

	/**
	 * Replace the matches of some files, keeping unmatched files at the end of the list (the model requires matched pairs first).
	 */
	private void replaceMatches(Map<File, Object> results) {
		EventList<Match<Object, File>> matches = renameModel.matches();
		for (File file : results.keySet()) {
			for (int i = 0; i < matches.size(); i++) {
				if (file.equals(matches.get(i).getCandidate())) {
					matches.remove(i);
					break;
				}
			}
		}
		int insert = 0;
		while (insert < matches.size() && matches.get(insert).getValue() != null) {
			insert++;
		}
		for (Map.Entry<File, Object> it : results.entrySet()) {
			matches.add(insert++, new Match<Object, File>(it.getValue(), it.getKey()));
		}
	}

	private void matchSelected(Locale locale) {
		List<File> selected = getSelectedFiles();
		if (selected.isEmpty()) {
			return;
		}
		Locale effective = locale != null ? locale : Language.getLanguage(persistentPreferredLanguage.getValue()).getLocale();
		SortOrder order = SortOrder.forName(persistentPreferredEpisodeOrder.getValue());
		Window window = getWindow(this);

		namesList.firePropertyChange(LOADING_PROPERTY, false, true);
		newSwingWorker(() -> {
			Map<File, Object> results = new LinkedHashMap<File, Object>();
			for (Match<File, ?> m : new AutoDetectMatcher().match(selected, isMatchModeStrict(), order, effective, true, window)) {
				results.put(m.getValue(), m.getCandidate());
			}
			return results;
		}, results -> {
			replaceMatches(results);
			int missing = selected.size() - results.size();
			SwingEventBus.getInstance().post(new AppEvents.Toast(String.format("%d of %d files matched", results.size(), selected.size()), missing > 0 ? "Use Change Match for the others." : null, missing > 0 ? AppEvents.Status.Kind.WARNING : AppEvents.Status.Kind.READY));
		}, error -> {
			log.warning(error.getMessage());
		}, () -> namesList.firePropertyChange(LOADING_PROPERTY, true, false)).execute();
	}

	private void changeMatch() {
		List<File> selected = getSelectedFiles();
		if (selected.isEmpty()) {
			SwingEventBus.getInstance().post(new AppEvents.Toast("Select a file first", "Then choose Change Match to search the right title.", AppEvents.Status.Kind.WARNING));
			return;
		}
		Map<File, Object> results = new ManualMatchDialog(getWindow(this), selected, Language.getLanguage(persistentPreferredLanguage.getValue()).getLocale()).showDialog();
		if (results != null && results.size() > 0) {
			replaceMatches(results);
		}
	}

	private void clearSelected() {
		EventList<Match<Object, File>> matches = renameModel.matches();
		for (File file : getSelectedFiles()) {
			for (int i = 0; i < matches.size(); i++) {
				if (file.equals(matches.get(i).getCandidate()) && matches.get(i).getValue() != null) {
					matches.remove(i);
					matches.add(new Match<Object, File>(null, file));
					break;
				}
			}
		}
	}

	/**
	 * Commands offered by the command palette (⌘K) while this panel is shown.
	 */
	private List<Action> createCommands(Action fetchAction) {
		List<Action> commands = new ArrayList<Action>();
		commands.add(named(autoDetectAction, "Match files automatically"));
		commands.add(named(fetchAction, "Fetch metadata (choose source)"));
		commands.add(named(renameAction, "Preview and rename"));
		commands.add(renameSelectedAction);
		commands.add(matchSelectedAction);
		commands.add(changeMatchAction);
		commands.add(setLanguageAction);
		commands.add(clearMatchAction);
		commands.add(undoAction);
		commands.add(named(openHistoryAction, "Open rename history"));
		commands.add(named(filesList.getLoadAction(), "Add files"));
		commands.add(named(clearFilesAction, "Clear list"));
		commands.add(newAction("Edit format", evt -> showFormatEditor(null)));
		commands.add(newAction("Naming profile: Plex", evt -> applyLibraryLayout("plex", "Plex", evt)));
		commands.add(newAction("Naming profile: Jellyfin", evt -> applyLibraryLayout("jellyfin", "Jellyfin", evt)));
		commands.add(newAction("Naming profile: Emby", evt -> applyLibraryLayout("emby", "Emby", evt)));
		commands.add(newAction("Naming profile: Kodi", evt -> applyLibraryLayout("kodi", "Kodi", evt)));
		commands.add(newAction("Naming profile: Windows-friendly", evt -> applyNamingProfile(NamingProfile.WINDOWS)));
		commands.add(newAction("Naming profile: macOS-friendly", evt -> applyNamingProfile(NamingProfile.MACOS)));
		commands.add(newAction("Open destination folder", evt -> openDestinationFolder()));
		commands.add(quickLookAction);
		commands.add(revealAction);
		commands.add(toggleInspectorAction);
		return commands;
	}

	private static Action named(Action action, String name) {
		return newAction(name, evt -> action.actionPerformed(evt));
	}

	private void openDestinationFolder() {
		try {
			Map<File, File> map = renameModel.getRenameMap();
			for (Map.Entry<File, File> it : map.entrySet()) {
				File target = it.getValue().isAbsolute() ? it.getValue() : new File(it.getKey().getParentFile(), it.getValue().getPath());
				for (File f = target.getParentFile(); f != null; f = f.getParentFile()) {
					if (f.isDirectory()) {
						java.awt.Desktop.getDesktop().open(f);
						return;
					}
				}
			}
		} catch (Exception e) {
			log.warning("Nothing to open yet: " + e.getMessage());
		}
	}

	private final Modern.Badge filesCount = new Modern.Badge("0 files");
	private final Modern.Badge namesCount = new Modern.Badge("0 items");
	private JComponent filesCard;
	private JComponent namesCard;

	private final JLabel addedLabel = statusItem(Glyph.Shape.FILE, NightTheme::getAccent);
	private final JLabel matchedLabel = statusItem(Glyph.Shape.CHECK_CIRCLE, NightTheme::getSuccess);
	private final JLabel warningLabel = statusItem(Glyph.Shape.WARNING, NightTheme::getWarning);
	private final JLabel readyLabel = Modern.label("", 12f, Font.PLAIN, true);
	private final ProgressLine progressLine = new ProgressLine();

	private boolean matching = false;

	private static JLabel statusItem(Glyph.Shape shape, Supplier<Color> color) {
		JLabel label = Modern.label("", 12.5f, Font.PLAIN, false);
		label.setIcon(Glyph.of(shape, 16, color));
		label.setIconTextGap(8);
		return label;
	}

	private void editSelectedMatch() {
		int index = namesList.getListComponent().getSelectedIndex();
		if (index >= 0 && index < renameModel.size() && renameModel.hasComplement(index)) {
			Match<Object, File> match = renameModel.getMatch(index);
			Map<File, Object> context = renameModel.getMatchContext(match);

			if (match.getValue() != null) {
				MediaBindingBean sample = new MediaBindingBean(match.getValue(), match.getCandidate(), context);
				showFormatEditor(sample);
				return;
			}
		}
		showFormatEditor(null);
	}

	private JComponent createListCard(String title, Modern.Badge badge, JComponent tools, JComponent list, JComponent emptyState) {
		Modern.Card card = new Modern.Card(new MigLayout("insets 14 8 8 8, fill", "[0:0:n, fill, grow]", "[]8[0:0:n, fill, grow]"));

		JPanel header = new JPanel(new MigLayout("insets 0 8 0 4, fillx", "[][]push[]", "[center]"));
		header.setOpaque(false);
		header.add(Modern.label(title, 15f, Font.BOLD, false));
		header.add(badge, "gapleft 10");
		header.add(tools);
		card.add(header, "wrap, h 32!, wmin 0");

		JPanel stack = new JPanel(new CardLayout());
		stack.setOpaque(false);
		stack.add(list, "list");
		stack.add(emptyState, "empty");
		card.add(stack, "grow, wmin 0, hmin 0");
		card.putClientProperty("stack", stack);
		return card;
	}

	private static void showCard(JComponent card, boolean empty) {
		JPanel stack = (JPanel) card.getClientProperty("stack");
		((CardLayout) stack.getLayout()).show(stack, empty ? "empty" : "list");
	}

	private JComponent createDropZone() {
		JPanel zone = new JPanel(new MigLayout("insets 24, fill, wrap 1", "[0:0:n, center, grow]", "push[]14[]6[]2[]18[]push")) {

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = Modern.smooth(g);
				RoundRectangle2D shape = new RoundRectangle2D.Float(6, 4, getWidth() - 12, getHeight() - 10, 16, 16);
				g2d.setColor(Modern.alpha(NightTheme.getAccent(), NightTheme.isNightMode() ? 12 : 8));
				g2d.fill(shape);
				g2d.setColor(Modern.alpha(NightTheme.getAccent(), 110));
				g2d.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 6f, 6f }, 0f));
				g2d.draw(shape);
				g2d.dispose();
			}
		};
		zone.setOpaque(false);

		JLabel icon = new JLabel(Glyph.of(Glyph.Shape.CLOUD_UPLOAD, 48, NightTheme::getAccent));
		JLabel title = Modern.label("Drag & drop media files here", 15f, Font.BOLD, false);
		JLabel hint = Modern.label("Movies, TV shows, music and subtitles", 12f, Font.PLAIN, true);
		JLabel formats = Modern.label("MKV · MP4 · AVI · SRT · ASS and more", 12f, Font.PLAIN, true);

		JButton browse = Modern.button(filesList.getLoadAction(), Glyph.of(Glyph.Shape.FOLDER, 15, NightTheme::getForeground), Modern.Style.SECONDARY);
		browse.setText("Browse Files");
		browse.setHideActionText(false);

		zone.add(icon);
		zone.add(title);
		zone.add(hint);
		zone.add(formats);
		zone.add(browse);
		return zone;
	}

	private static JComponent createHint(Glyph.Shape shape, String title, String text) {
		JPanel panel = new JPanel(new MigLayout("insets 24, fill, wrap 1", "[center, grow]", "push[]12[]4[]push"));
		panel.setOpaque(false);
		panel.add(new JLabel(Glyph.of(shape, 34, NightTheme::getDimForeground)));
		panel.add(Modern.label(title, 14f, Font.BOLD, false));
		panel.add(Modern.label(text, 12f, Font.PLAIN, true));
		return panel;
	}

	private JComponent createStatusBar(JButton renameButton, JButton renameOptionsButton) {
		Modern.Card bar = new Modern.Card(new MigLayout("insets 8 16 8 8, fillx", "[]20[]20[]push[200:360:480, fill]push[]12[]1[]", "[center]"));
		bar.add(addedLabel);
		bar.add(matchedLabel);
		bar.add(warningLabel);
		bar.add(progressLine, "h 6!");
		bar.add(readyLabel);
		bar.add(renameButton, "h 36!");
		bar.add(renameOptionsButton, "h 36!, w 36!");

		// keep counters in sync with the model
		renameModel.matches().addListEventListener(evt -> updateStatusBar());
		renameModel.values().addListEventListener(evt -> updateStatusBar());
		renameModel.files().addListEventListener(evt -> updateStatusBar());

		// show progress whenever the names list is loading matches
		namesList.addPropertyChangeListener(LOADING_PROPERTY, evt -> setMatching((Boolean) evt.getNewValue()));

		updateStatusBar();
		return bar;
	}

	private void updateStatusBar() {
		int total = renameModel.matches().size();
		int files = renameModel.files().size();
		int names = renameModel.names().size();
		int matched = 0;
		int warnings = 0;

		for (int i = 0; i < total; i++) {
			if (renameModel.hasComplement(i)) {
				matched++;
				try {
					if (RenameListCellRenderer.getMatchProbablity(renameModel.getMatch(i)) < RenameListCellRenderer.LIKELY) {
						warnings++;
					}
				} catch (Exception e) {
					// ignore metric failures for exotic objects
				}
			}
		}

		addedLabel.setText(files == 1 ? "1 file added" : files + " files added");
		matchedLabel.setText(matched + " matched");
		warningLabel.setText(warnings == 1 ? "1 needs review" : warnings + " need review");
		warningLabel.setVisible(warnings > 0);

		filesCount.setText(files == 1 ? "1 file" : files + " files");
		namesCount.setText(names == 1 ? "1 item" : names + " items");

		readyLabel.setText(matching ? "Matching…" : matched > 0 ? "Ready to rename " + (matched == 1 ? "1 file" : matched + " files") : files > 0 ? "Choose Match to find names" : "Add files to begin");
		renameAction.setEnabled(matched > 0 && !matching);

		progressLine.setProgress(total == 0 ? 0 : matched / (float) Math.max(files, total));

		if (filesCard != null) {
			showCard(filesCard, files == 0);
			showCard(namesCard, names == 0 && !matching);
		}

		if (matching) {
			postStatus("Matching…", AppEvents.Status.Kind.BUSY);
		} else if (warnings > 0) {
			postStatus(warnings == 1 ? "1 to review" : warnings + " to review", AppEvents.Status.Kind.WARNING);
		} else {
			postStatus("Ready", AppEvents.Status.Kind.READY);
		}
	}

	private void postStatus(String text, AppEvents.Status.Kind kind) {
		if (isShowing()) {
			SwingEventBus.getInstance().post(new AppEvents.Status(text, kind));
		}
	}

	private void setMatching(boolean matching) {
		this.matching = matching;
		progressLine.setIndeterminate(matching);
		updateStatusBar();
	}

	@Subscribe
	public void search(AppEvents.Search search) {
		if (search.query.isEmpty()) {
			return;
		}

		String query = search.query.toLowerCase(Locale.ROOT);
		JList list = filesList.getListComponent();
		int size = renameModel.size();
		int start = search.next ? list.getSelectedIndex() + 1 : 0;

		for (int n = 0; n < size; n++) {
			int i = (start + n) % size;
			Match<Object, File> match = renameModel.getMatch(i);
			String file = match.getCandidate() != null ? match.getCandidate().getName() : "";
			String value = match.getValue() != null ? match.getValue().toString() : "";
			if (file.toLowerCase(Locale.ROOT).contains(query) || value.toLowerCase(Locale.ROOT).contains(query)) {
				list.setSelectedIndex(i);
				list.ensureIndexIsVisible(i);
				return;
			}
		}
	}

	/**
	 * Thin rounded progress track with an indeterminate sweep while matching.
	 */
	private static class ProgressLine extends JComponent {

		private float progress = 0;
		private float sweep = 0;
		private final javax.swing.Timer timer = new javax.swing.Timer(30, evt -> {
			sweep = (sweep + 0.02f) % 1.4f;
			repaint();
		});

		void setProgress(float progress) {
			this.progress = Math.max(0, Math.min(1, progress));
			repaint();
		}

		void setIndeterminate(boolean indeterminate) {
			if (indeterminate) {
				timer.start();
			} else {
				timer.stop();
			}
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = Modern.smooth(g);
			int w = getWidth();
			int h = getHeight();
			g2d.setColor(NightTheme.getControlColor());
			g2d.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));

			g2d.setPaint(new java.awt.GradientPaint(0, 0, NightTheme.getAccent(), w, 0, NightTheme.getSecondaryAccent()));
			if (timer.isRunning()) {
				float x = (sweep - 0.4f) * w;
				g2d.setClip(new RoundRectangle2D.Float(0, 0, w, h, h, h));
				g2d.fill(new RoundRectangle2D.Float(x, 0, w * 0.4f, h, h, h));
			} else if (progress > 0) {
				g2d.fill(new RoundRectangle2D.Float(0, 0, Math.max(h, w * progress), h, h, h));
			}
			g2d.dispose();
		}
	}

	private static boolean isTyping() {
		return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent;
	}

	private void installKeyStrokeActions() {
		int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_Z, menu), newAction("Undo", evt -> {
			if (!isTyping()) {
				undoAction.actionPerformed(evt);
			}
		}));
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_O, menu), filesList.getLoadAction());
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_M, menu), newAction("Match", evt -> {
			if (getSelectedFiles().size() > 1 && renameModel.names().size() > 0) {
				matchSelectedAction.actionPerformed(evt);
			} else if (autoDetectAction != null && autoDetectAction.isEnabled()) {
				autoDetectAction.actionPerformed(evt);
			}
		}));
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_ENTER, menu), newAction("Rename", evt -> renameAction.actionPerformed(evt)));
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_I, menu), toggleInspectorAction);
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_SPACE, 0), newAction("Quick Look", evt -> {
			if (!isTyping()) {
				quickLookAction.actionPerformed(evt);
			}
		}));

		// manual force name via F2
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_F2, 0), newAction("Force Name", evt -> {
			withWaitCursor(evt.getSource(), () -> {
				if (namesList.getModel().isEmpty()) {
					// match to xattr metadata object or the file itself
					Map<File, Object> xattr = WebServices.XattrMetaData.match(renameModel.files(), false);

					renameModel.clear();
					renameModel.addAll(xattr.values(), xattr.keySet());
				} else {
					int index = namesList.getListComponent().getSelectedIndex();
					if (index >= 0) {
						File file = (File) filesList.getListComponent().getModel().getElementAt(index);
						Object object = namesList.getListComponent().getModel().getElementAt(index);

						String string = showInputDialog("Enter Name:", object.toString(), "Enter Name", RenamePanel.this);
						if (string != null && string.length() > 0) {
							renameModel.matches().set(index, new Match<Object, File>(string + '.' + getExtension(file), file));
						}
					}
				}
			});
		}));

		// map 1..9 number keys to presets
		for (int presetNumber = 1; presetNumber <= 9; presetNumber++) {
			int index = presetNumber - 1;

			installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(Character.forDigit(presetNumber, 10), 0), newAction("Preset " + presetNumber, evt -> {
				if (isTyping()) {
					return;
				}
				try {
					List<Preset> presets = getPresets();

					if (index < presets.size()) {
						new ApplyPresetAction(presets.get(index)).actionPerformed(evt);
					} else {
						new ShowPresetsPopupAction().actionPerformed(evt);
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, e, e::getMessage);
				}
			}));
		}

		// copy debug information (paths and objects)
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_F7, 0), newAction("Copy Debug Information", evt -> {
			withWaitCursor(evt.getSource(), () -> {
				String text = getDebugInfo();
				if (text.length() > 0) {
					copyToClipboard(text);
					log.info("Match model has been copied to clipboard");
				} else {
					log.warning("Match model is empty");
				}
			});
		}));
	}

	private boolean isMatchModeStrict() {
		return MATCH_MODE_STRICT.equalsIgnoreCase(persistentPreferredMatchMode.getValue());
	}

	private ActionPopup createPresetsPopup() {
		ActionPopup actionPopup = new ActionPopup("Presets", ResourceManager.getIcon("action.script"));

		List<Preset> presets = getPresets();
		if (presets.size() > 0) {
			for (Preset preset : presets) {
				actionPopup.add(new ApplyPresetAction(preset));
			}
			actionPopup.addSeparator();
		}

		actionPopup.add(newAction("Edit Presets", ResourceManager.getIcon("script.add"), evt -> {
			Window window = getWindow(evt.getSource());

			Action newPreset = newAction("New Preset …", ResourceManager.getIcon("script.add"), a -> {
				Optional.ofNullable(JOptionPane.showInputDialog(window, "Preset Name:", a.getActionCommand(), JOptionPane.PLAIN_MESSAGE, null, null, "My Preset")).map(Object::toString).map(String::trim).filter(s -> s.length() > 0).ifPresent(n -> {
					showPresetEditor(new Preset(n, null, null, null, null, null, null, null, null), window);
				});
			});

			List<Object> options = new ArrayList<Object>(presets);
			options.add(newPreset);

			SelectDialog<Object> selectDialog = new SelectDialog<Object>(window, options) {

				@Override
				protected void configureValue(DefaultFancyListCellRenderer render, Object value) {
					if (value instanceof Preset) {
						Preset preset = (Preset) value;
						render.setIcon(preset.getIcon());
					} else if (value instanceof Action) {
						Action action = (Action) value;
						render.setText((String) action.getValue(Action.NAME));
						render.setIcon((Icon) action.getValue(Action.SMALL_ICON));
					}
				}
			};

			selectDialog.setTitle("Edit Presets");
			selectDialog.setLocation(getOffsetLocation(selectDialog.getOwner()));
			selectDialog.setMinimumSize(new Dimension(250, 250));
			selectDialog.pack();
			selectDialog.setVisible(true);

			Object selection = selectDialog.getSelectedValue();
			if (selection instanceof Preset) {
				Preset preset = (Preset) selection;
				showPresetEditor(preset, window);
			} else if (selection instanceof Action) {
				Action action = (Action) selection;
				action.actionPerformed(new ActionEvent(newPreset, ActionEvent.ACTION_PERFORMED, "New Preset …"));
			}
		}));

		return actionPopup;
	}

	private ActionPopup createFetchPopup() {
		ActionPopup actionPopup = new ActionPopup("Fetch & Match Data", ResourceManager.getIcon("action.fetch"));

		actionPopup.addDescription(new JLabel("Smart Mode:"));
		AutoCompleteAction auto = new AutoCompleteAction("Autodetect", ResourceManager.getIcon("action.auto"), AutoDetectMatcher::new);
		decorateServiceAction(auto, "Autodetect", "Recommended: detects movies, episodes and music file by file", true);
		actionPopup.add(auto);
		autoDetectAction = auto;

		actionPopup.addSeparator();
		actionPopup.addDescription(new JLabel("Episode Mode:"));

		// create actions for match popup episode list completion
		for (EpisodeListProvider db : sortByPreference(Arrays.asList(WebServices.getEpisodeListProviders()))) {
			AutoCompleteAction action = new AutoCompleteAction(db.getName(), db.getIcon(), () -> new EpisodeListMatcher(db, db == WebServices.AniDB));
			decorateServiceAction(action, db.getName(), "Series and episodes", isServiceConfigured(db.getName()));
			actionPopup.add(action);
		}

		actionPopup.addSeparator();
		actionPopup.addDescription(new JLabel("Movie Mode:"));

		// create action for movie name completion
		for (MovieIdentificationService it : sortByPreference(Arrays.asList(WebServices.getMovieIdentificationServices()))) {
			AutoCompleteAction action = new AutoCompleteAction(it.getName(), it.getIcon(), () -> new MovieMatcher(it));
			decorateServiceAction(action, it.getName(), "Movies", isServiceConfigured(it.getName()));
			actionPopup.add(action);
		}

		actionPopup.addSeparator();
		actionPopup.addDescription(new JLabel("Music Mode:"));
		for (MusicIdentificationService it : sortByPreference(Arrays.asList(WebServices.getMusicIdentificationServices()))) {
			AutoCompleteAction action = new AutoCompleteAction(it.getName(), it.getIcon(), () -> new MusicMatcher(it));
			decorateServiceAction(action, it.getName(), "Music tags (AcoustID)", isServiceConfigured(it.getName()));
			actionPopup.add(action);
		}


		actionPopup.addSeparator();
		actionPopup.addDescription(new JLabel("Options:"));

		actionPopup.add(newAction("Edit Format", ResourceManager.getIcon("action.format"), evt -> showFormatEditor(null)));

		actionPopup.add(newAction("Preferences", ResourceManager.getIcon("action.preferences"), evt -> showMatchPreferences()));

		return actionPopup;
	}

	/**
	 * Reorder services so that the ones configured first are offered at the top.
	 */
	private <T> List<T> sortByPreference(List<T> services) {
		if (isHideUnavailable()) {
			services = services.stream().filter(s -> isServiceConfigured(getServiceName(s))).collect(Collectors.toList());
		}

		String order = persistentServiceOrder.getValue();
		if (order == null || order.isEmpty()) {
			return services;
		}

		final List<String> preferred = Arrays.asList(order.split(","));

		List<T> sorted = new ArrayList<T>(services);
		sorted.sort((a, b) -> {
			int pa = preferred.indexOf(getServiceName(a));
			int pb = preferred.indexOf(getServiceName(b));
			pa = pa < 0 ? Integer.MAX_VALUE : pa;
			pb = pb < 0 ? Integer.MAX_VALUE : pb;
			return Integer.compare(pa, pb);
		});

		return sorted;
	}

	private static String getServiceName(Object service) {
		return String.valueOf(service);
	}

	/**
	 * Services that require credentials we don't have are marked as unavailable so they can be hidden.
	 */
	private boolean isServiceConfigured(String name) {
		if (name == null) {
			return true;
		}

		if (name.contains("OpenSubtitles")) {
			return !isBlank(getApiKey("opensubtitles"));
		}

		if (name.contains("TVDB")) {
			return false; // the TheTVDB v2 API has been shut down and v4 requires a paid key
		}

		if (name.contains("AniDB")) {
			return !isBlank(getApiKey("anidb"));
		}

		return true;
	}

	private static boolean isBlank(String string) {
		return string == null || string.trim().isEmpty();
	}

	/**
	 * Adds a tooltip that explains what a service does and whether it is ready to use.
	 */
	private void decorateServiceAction(Action action, String name, String description, boolean configured) {
		StringBuilder tip = new StringBuilder(description);
		tip.append(" · ").append(name);

		if (!configured) {
			tip.append(" · not available (service discontinued or API key missing)");
		}

		action.putValue(Action.SHORT_DESCRIPTION, "<html>" + tip.toString().replace("&", "&amp;") + "</html>");

		if (!configured) {
			action.putValue(Action.NAME, action.getValue(Action.NAME) + "  (unavailable)");
			action.setEnabled(false);
		}
	}

	private void showMatchPreferences() {
		String[] modes = new String[] { MATCH_MODE_OPPORTUNISTIC, MATCH_MODE_STRICT };
		JComboBox modeCombo = new JComboBox(modes);

		List<Language> languages = new ArrayList<Language>();
		languages.addAll(Language.preferredLanguages()); // add preferred languages first
		languages.addAll(Language.availableLanguages()); // then others

		JComboBox orderCombo = new JComboBox(SortOrder.values());
		JList languageList = new JList(languages.toArray());
		languageList.setCellRenderer(new DefaultListCellRenderer() {

			@Override
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value != null) {
					setText(((Language) value).getName());
					setIcon(new LanguageChipIcon(((Language) value).getCode(), isSelected));
				}
				return this;
			}
		});

		JCheckBox hideUnavailable = new JCheckBox("Hide unavailable services", isHideUnavailable());

		// service priority: a simple "most used first" list
		List<String> allServices = new ArrayList<String>();
		for (EpisodeListProvider s : WebServices.getEpisodeListProviders()) {
			allServices.add(s.getName());
		}
		for (MovieIdentificationService s : WebServices.getMovieIdentificationServices()) {
			allServices.add(s.getName());
		}
		for (MusicIdentificationService s : WebServices.getMusicIdentificationServices()) {
			allServices.add(s.getName());
		}

		String savedOrder = persistentServiceOrder.getValue();
		if (savedOrder != null) {
			Arrays.stream(savedOrder.split(",")).filter(s -> !s.isEmpty()).forEach(s -> allServices.remove(s));
			allServices.addAll(0, Arrays.asList(savedOrder.split(",")));
		}

		JList serviceList = new JList(allServices.toArray());
		serviceList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		// move selected services up/down to define the priority
		JButton up = new JButton(newAction("Move Up", ResourceManager.getIcon("action.up"), evt -> moveService(serviceList, -1)));
		JButton down = new JButton(newAction("Move Down", ResourceManager.getIcon("action.down"), evt -> moveService(serviceList, 1)));

		// restore current preference values
		try {
			modeCombo.setSelectedItem(persistentPreferredMatchMode.getValue());
			orderCombo.setSelectedItem(SortOrder.forName(persistentPreferredEpisodeOrder.getValue()));

			String selectedLanguage = persistentPreferredLanguage.getValue();
			languages.stream().filter(l -> l.getCode().equals(selectedLanguage)).findFirst().ifPresent(l -> languageList.setSelectedValue(l, true));
		} catch (Exception e) {
			debug.log(Level.WARNING, e.getMessage(), e);
		}

		JScrollPane spModeCombo = new JScrollPane(modeCombo, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		spModeCombo.setBorder(new CompoundBorder(new TitledBorder("Match Mode"), spModeCombo.getBorder()));
		JScrollPane spLanguageList = new JScrollPane(languageList);
		spLanguageList.setBorder(new CompoundBorder(new TitledBorder("Language"), spLanguageList.getBorder()));
		JScrollPane spOrderCombo = new JScrollPane(orderCombo, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		spOrderCombo.setBorder(new CompoundBorder(new TitledBorder("Episode Order"), spOrderCombo.getBorder()));
		JScrollPane spServiceList = new JScrollPane(serviceList);
		spServiceList.setBorder(new CompoundBorder(new TitledBorder("Service Priority (top = first)"), spServiceList.getBorder()));

		// fix background issues on OSX
		spModeCombo.setOpaque(false);
		spLanguageList.setOpaque(false);
		spOrderCombo.setOpaque(false);
		spServiceList.setOpaque(false);

		JPanel servicePanel = new JPanel(new MigLayout("insets 0, fill", "[grow, fill][center, 0px]"));
		servicePanel.add(spServiceList, "grow, hmin 80px");
		JPanel orderButtons = new JPanel(new MigLayout("insets 0, flowy, gapy 2px"));
		orderButtons.add(up, "grow");
		orderButtons.add(down, "grow");
		servicePanel.add(orderButtons, "gapx 3px 0");

		JPanel message = new JPanel(new MigLayout("fill, flowy, insets 0"));
		message.add(spModeCombo, "grow, hmin 24px");
		message.add(spLanguageList, "grow, hmin 50px");
		message.add(spOrderCombo, "grow, hmin 24px");
		message.add(servicePanel, "grow, hmin 80px");
		message.add(hideUnavailable, "gaptop 6px");

		JOptionPane pane = new JOptionPane(message, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		pane.createDialog(getWindowAncestor(RenamePanel.this), "Match Preferences").setVisible(true);

		if (pane.getValue() != null && pane.getValue().equals(JOptionPane.OK_OPTION)) {
			persistentPreferredMatchMode.setValue((String) modeCombo.getSelectedItem());
			persistentPreferredLanguage.setValue(((Language) languageList.getSelectedValue()).getCode());
			persistentPreferredEpisodeOrder.setValue(((SortOrder) orderCombo.getSelectedItem()).name());
			setHideUnavailable(hideUnavailable.isSelected());

			StringBuilder order = new StringBuilder();
			DefaultListModel model = (DefaultListModel) serviceList.getModel();
			for (int i = 0; i < model.size(); i++) {
				Object service = model.get(i);
				if (order.length() > 0) {
					order.append(',');
				}
				order.append(service);
			}
			persistentServiceOrder.setValue(order.toString());

			// update UI
			matchAction.setMatchMode(isMatchModeStrict());
		}
	}

	private static void moveService(JList serviceList, int offset) {
		int index = serviceList.getSelectedIndex();
		int target = index + offset;

		if (index < 0 || target < 0 || target >= serviceList.getModel().getSize()) {
			return;
		}

		DefaultListModel model = (DefaultListModel) serviceList.getModel();
		Object value = model.remove(index);
		model.add(target, value);
		serviceList.setSelectedIndex(target);
	}

	private ActionPopup createSettingsPopup() {
		ActionPopup actionPopup = new ActionPopup("Rename Options", ResourceManager.getIcon("action.settings"));

		actionPopup.addDescription(new JLabel("Extension:"));
		actionPopup.add(new SetRenameMode(false, "Preserve", ResourceManager.getIcon("action.extension.preserve")));
		actionPopup.add(new SetRenameMode(true, "Override", ResourceManager.getIcon("action.extension.override")));

		actionPopup.addSeparator();

		actionPopup.addDescription(new JLabel("Action:"));
		for (StandardRenameAction action : Preset.getSupportedActions()) {
			actionPopup.add(new SetRenameAction(action));
		}

		actionPopup.addSeparator();

		JCheckBox artwork = new JCheckBox("Download poster & fanart", Boolean.parseBoolean(RenameAction.persistentFetchArtwork.getValue()));
		artwork.setOpaque(false);
		artwork.setForeground(NightTheme.getForeground());
		artwork.setToolTipText("Images come from TheMovieDB and are saved next to the renamed files");
		artwork.addActionListener(evt -> RenameAction.persistentFetchArtwork.setValue(String.valueOf(artwork.isSelected())));
		actionPopup.addDescription(new JLabel("After Rename:"));
		actionPopup.addAction(artwork);

		actionPopup.addSeparator();

		actionPopup.addDescription(new JLabel("Naming Profile:"));
		actionPopup.add(newAction("Plex", Glyph.of(Glyph.Shape.FOLDER, 16, NightTheme::getWarning), evt -> applyLibraryLayout("plex", "Plex", evt)));
		actionPopup.add(newAction("Jellyfin", Glyph.of(Glyph.Shape.FOLDER, 16, NightTheme::getSecondaryAccent), evt -> applyLibraryLayout("jellyfin", "Jellyfin", evt)));
		actionPopup.add(newAction("Emby", Glyph.of(Glyph.Shape.FOLDER, 16, NightTheme::getSuccess), evt -> applyLibraryLayout("emby", "Emby", evt)));
		actionPopup.add(newAction("Kodi", Glyph.of(Glyph.Shape.FOLDER, 16, NightTheme::getAccent), evt -> applyLibraryLayout("kodi", "Kodi", evt)));
		actionPopup.add(newAction("Windows-friendly (rename in place)", Glyph.of(Glyph.Shape.FILE, 16, NightTheme::getDimForeground), evt -> applyNamingProfile(NamingProfile.WINDOWS)));
		actionPopup.add(newAction("macOS-friendly (rename in place)", Glyph.of(Glyph.Shape.FILE, 16, NightTheme::getDimForeground), evt -> applyNamingProfile(NamingProfile.MACOS)));
		actionPopup.add(newAction("Custom Format …", Glyph.of(Glyph.Shape.PENCIL, 16, NightTheme::getDimForeground), evt -> showFormatEditor(null)));

		return actionPopup;
	}

	/**
	 * Names that stay in their current folder and are safe for the given file system: Windows forbids ":" in names, the Finder shows it as "/", so the macOS profile uses the look-alike "꞉" instead.
	 */
	enum NamingProfile {
		WINDOWS("Windows-friendly", " - "), MACOS("macOS-friendly", "꞉ ");

		final String label;
		final String colon;

		NamingProfile(String label, String colon) {
			this.label = label;
			this.colon = colon;
		}

		String movie() {
			return String.format("{n.colon('%s')} ({y})", colon);
		}

		String episode() {
			return String.format("{n.colon('%s')} - {s00e00} - {t.colon('%s')}", colon, colon);
		}
	}

	private void applyNamingProfile(NamingProfile profile) {
		try {
			renameModel.useFormatter(Episode.class, new ExpressionFormatter(profile.episode(), EpisodeFormat.SeasonEpisode, Episode.class));
			renameModel.useFormatter(Movie.class, new ExpressionFormatter(profile.movie(), MovieFormat.NameYear, Movie.class));
			persistentEpisodeFormat.setValue(profile.episode());
			persistentMovieFormat.setValue(profile.movie());
			SwingEventBus.getInstance().post(new AppEvents.Toast("Naming profile: " + profile.label, "Files are renamed where they are.", AppEvents.Status.Kind.READY));
		} catch (Exception e) {
			log.log(Level.WARNING, e, e::getMessage);
		}
	}


	/**
	 * Use the Plex or Jellyfin folder structure for episodes, movies and music, as file names while the files stay in their current folder.
	 */
	private static final Pattern LEGACY_LIBRARY_FORMAT = Pattern.compile("^(?:[^{}]+/\\{(plex|jellyfin|emby|kodi)\\}|\\{(plex|jellyfin|emby|kodi)\\.library\\('.*'\\)\\})$");

	/**
	 * File name by the media server convention, while the file stays in its current folder.
	 */
	private static String inPlaceExpression(String binding) {
		return "{" + binding + ".name}";
	}

	/**
	 * Like the file name only, but the folder of the series or movie is renamed in place and episodes go into season folders.
	 */
	private static String tidyExpression(String binding) {
		return "{" + binding + ".tidy}";
	}

	private static final Pattern IN_PLACE_FORMAT = Pattern.compile("^\\{(plex|jellyfin|emby|kodi)\\.name\\}$");

	private void applyLibraryLayout(String binding, String server, ActionEvent evt) {
		String expression = tidyExpression(binding);
		String music = inPlaceExpression(binding);

		try {
			renameModel.useFormatter(Episode.class, new ExpressionFormatter(expression, EpisodeFormat.SeasonEpisode, Episode.class));
			renameModel.useFormatter(Movie.class, new ExpressionFormatter(expression, MovieFormat.NameYear, Movie.class));
			renameModel.useFormatter(AudioTrack.class, new ExpressionFormatter(music, new AudioTrackFormat(), AudioTrack.class));
			persistentEpisodeFormat.setValue(expression);
			persistentMovieFormat.setValue(expression);
			persistentMusicFormat.setValue(music);
			SwingEventBus.getInstance().post(new AppEvents.Toast("Naming profile: " + server, "Folders get the series or movie name, episodes go into season folders", AppEvents.Status.Kind.READY));
		} catch (Exception e) {
			log.log(Level.WARNING, e, e::getMessage);
		}
	}

	private Mode getFormatEditorMode(MediaBindingBean binding) {
		if (binding != null) {
			if (binding.getInfoObject() instanceof Episode) {
				return Mode.Episode;
			} else if (binding.getInfoObject() instanceof Movie) {
				return Mode.Movie;
			} else if (binding.getInfoObject() instanceof AudioTrack) {
				return Mode.Music;
			} else if (binding.getInfoObject() instanceof File) {
				return Mode.File;
			} else {
				throw new IllegalArgumentException("Cannot format class: " + binding.getInfoObjectType()); // ignore objects that cannot be formatted
			}
		}

		try {
			return Mode.valueOf(persistentLastFormatState.getValue()); // restore previous mode
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::getMessage);
		}

		return Mode.Episode; // default to Episode mode
	}

	private void showFormatEditor(MediaBindingBean binding) {
		withWaitCursor(this, () -> {
			FormatDialog dialog = new FormatDialog(getWindowAncestor(RenamePanel.this), getFormatEditorMode(binding), binding, binding != null);
			dialog.setLocation(getOffsetLocation(dialog.getOwner()));
			dialog.setVisible(true);

			if (dialog.submit()) {
				switch (dialog.getMode()) {
				case Episode:
					renameModel.useFormatter(Episode.class, new ExpressionFormatter(dialog.getFormat().getExpression(), EpisodeFormat.SeasonEpisode, Episode.class));
					persistentEpisodeFormat.setValue(dialog.getFormat().getExpression());
					break;
				case Movie:
					renameModel.useFormatter(Movie.class, new ExpressionFormatter(dialog.getFormat().getExpression(), MovieFormat.NameYear, Movie.class));
					persistentMovieFormat.setValue(dialog.getFormat().getExpression());
					break;
				case Music:
					renameModel.useFormatter(AudioTrack.class, new ExpressionFormatter(dialog.getFormat().getExpression(), new AudioTrackFormat(), AudioTrack.class));
					persistentMusicFormat.setValue(dialog.getFormat().getExpression());
					break;
				case File:
					renameModel.useFormatter(File.class, new ExpressionFormatter(dialog.getFormat().getExpression(), new FileNameFormat(), File.class));
					persistentFileFormat.setValue(dialog.getFormat().getExpression());
					break;
				}

				if (binding == null) {
					persistentLastFormatState.setValue(dialog.getMode().name());
				}
			}
		});
	}

	private void showPresetEditor(Preset preset, Window owner) {
		try {
			PresetEditor presetEditor = new PresetEditor(owner);
			presetEditor.setPreset(preset);

			presetEditor.setLocation(getOffsetLocation(presetEditor.getOwner()));
			presetEditor.setVisible(true);

			switch (presetEditor.getResult()) {
			case SET:
				persistentPresets.put(preset.getName(), presetEditor.getPreset());
				break;
			case DELETE:
				persistentPresets.remove(preset.getName());
				break;
			case CANCEL:
				break;
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::toString);
		}
	}

	private List<Preset> getPresets() {
		// load Presets and ensure Preset order on all platforms (e.g. Windows Registry Preferences are sorted alphabetically, but the same is not guaranteed for other platforms)
		List<Preset> presets = new ArrayList<Preset>(persistentPresets.values());
		presets.sort(comparing(Preset::getName, new AlphanumComparator(Locale.getDefault())));
		return presets;
	}

	private String getDebugInfo() throws Exception {
		StringBuilder sb = new StringBuilder();

		for (Match<Object, File> m : renameModel.matches()) {
			String f = getStructurePathTail(m.getCandidate()).getPath();
			Object v = m.getValue();

			// convert FastFile items
			if (v instanceof File) {
				v = new SimpleFileInfo(getStructurePathTail((File) v).getPath(), ((File) v).length());
			}

			sb.append(f).append('\t').append(MetaAttributes.toJson(v)).append('\n');
		}

		return sb.toString();
	}

	private final Action clearFilesAction = newAction("Clear All", ResourceManager.getIcon("action.clear"), evt -> {
		if (isShiftOrAltDown(evt)) {
			renameModel.files().clear();
		} else {
			renameModel.clear();
		}
	});

	private final Action openHistoryAction = newAction("Open History", ResourceManager.getIcon("action.report"), evt -> {
		try {
			History model = HistorySpooler.getInstance().getCompleteHistory();

			HistoryDialog dialog = new HistoryDialog(getWindow(RenamePanel.this));
			dialog.setLocationRelativeTo(RenamePanel.this);
			dialog.setModel(model);

			// show and block
			dialog.setVisible(true);
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause(getRootCause(e)));
		}
	});

	@Subscribe
	public void handle(Transferable transferable) throws Exception {
		for (TransferablePolicy handler : new TransferablePolicy[] { filesList.getTransferablePolicy(), namesList.getTransferablePolicy() }) {
			if (handler != null && handler.accept(transferable)) {
				handler.handleTransferable(transferable, TransferAction.PUT);
				return;
			}
		}
	}

	private static class ShowPopupAction extends AbstractAction {

		public ShowPopupAction(String name, Icon icon) {
			super(name, icon);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			JComponent source = (JComponent) e.getSource();
			source.getComponentPopupMenu().show(source, 0, source.getHeight() + 6);
		}
	};

	private class ShowPresetsPopupAction extends AbstractAction {

		public ShowPresetsPopupAction() {
			super("Presets", ResourceManager.getIcon("action.script"));
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			try {
				JComponent source = (JComponent) evt.getSource();
				createPresetsPopup().show(source, -3, source.getHeight() + 4);
			} catch (Exception e) {
				debug.log(Level.WARNING, e, e::toString);
			}
		}
	};

	private class ApplyPresetAction extends AutoCompleteAction {

		private Preset preset;

		public ApplyPresetAction(Preset preset) {
			super(preset.getName(), preset.getIcon(), preset::getAutoCompleteMatcher);
			this.preset = preset;
		}

		@Override
		public List<File> getFiles(ActionEvent evt) {
			File inputFolder = preset.getInputFolder();

			if (inputFolder == null) {
				return super.getFiles(evt); // default behaviour
			}

			if (isMacSandbox()) {
				if (!MacAppUtilities.askUnlockFolders(getWindow(RenamePanel.this), singleton(inputFolder))) {
					return emptyList();
				}
			}

			try {
				List<File> selection = onSecondaryLoop(preset::selectFiles); // run potentially long-running operations on secondary EDT

				if (selection.size() > 0) {
					renameModel.clear();
					renameModel.files().addAll(selection);
					return selection;
				}

				log.info("No files have been selected.");
			} catch (Exception e) {
				log.log(Level.WARNING, e, e::toString);
			}

			return null; // cancel operation
		}

		@Override
		public boolean isStrict(ActionEvent evt) {
			return preset.getMatchMode() != null ? MATCH_MODE_STRICT.equals(preset.getMatchMode()) : super.isStrict(evt);
		}

		@Override
		public SortOrder getSortOrder(ActionEvent evt) {
			return preset.getSortOrder() != null ? preset.getSortOrder() : super.getSortOrder(evt);
		}

		@Override
		public Locale getLocale(ActionEvent evt) {
			return preset.getLanguage() != null ? preset.getLanguage().getLocale() : super.getLocale(evt);
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			SwingWorker<ExpressionFormatter, Void> worker = newSwingWorker(() -> {
				ExpressionFileFormat format = preset.getFormat();

				if (format != null && preset.getDatasource() != null) {
					switch (Mode.getMode(preset.getDatasource())) {
					case Episode:
						return new ExpressionFormatter(format, EpisodeFormat.SeasonEpisode, Episode.class);
					case Movie:
						return new ExpressionFormatter(format, MovieFormat.NameYear, Movie.class);
					case Music:
						return new ExpressionFormatter(format, new AudioTrackFormat(), AudioTrack.class);
					case File:
						return new ExpressionFormatter(format, new FileNameFormat(), File.class);
					}
				}

				return null;
			}, formatter -> {
				if (formatter != null) {
					renameModel.useFormatter(formatter.getTargetClass(), formatter);
				}

				if (preset.getRenameAction() != null) {
					new SetRenameAction(preset.getRenameAction()).actionPerformed(evt);
				}

				super.actionPerformed(evt);
			});

			// auto-match in progress
			namesList.firePropertyChange(LOADING_PROPERTY, false, true);
			worker.execute();
		}
	}

	private class SetRenameMode extends AbstractAction {

		private final boolean activate;

		private SetRenameMode(boolean activate, String name, Icon icon) {
			super(name, icon);
			this.activate = activate;
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			renameModel.setPreserveExtension(!activate);

			// display changed state
			filesList.repaint();
		}
	}

	private class SetRenameAction extends AbstractAction {

		private final StandardRenameAction action;

		public SetRenameAction(StandardRenameAction action) {
			super(action.getDisplayName(), ResourceManager.getIcon("rename.action." + action.name().toLowerCase()));
			this.action = action;
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			if (action == StandardRenameAction.MOVE) {
				renameAction.resetValues();
			} else {
				renameAction.putValue(RenameAction.RENAME_ACTION, action);
				renameAction.putValue(NAME, this.getValue(NAME));
				renameAction.putValue(SMALL_ICON, ResourceManager.getIcon("action." + action.name().toLowerCase()));
			}
		}
	}

	private class AutoCompleteAction extends AbstractAction {

		protected final Supplier<AutoCompleteMatcher> matcher;

		public AutoCompleteAction(String name, Icon icon, Supplier<AutoCompleteMatcher> matcher) {
			super(name, icon);

			// create matcher when required
			this.matcher = matcher;

			// disable action while episode list matcher is working
			namesList.addPropertyChangeListener(LOADING_PROPERTY, evt -> {
				// disable action while loading is in progress
				setEnabled(!(Boolean) evt.getNewValue());
			});
		}

		public List<File> getFiles(ActionEvent evt) {
			return renameModel.files();
		}

		public boolean isStrict(ActionEvent evt) {
			return isMatchModeStrict();
		}

		public SortOrder getSortOrder(ActionEvent evt) {
			return SortOrder.forName(persistentPreferredEpisodeOrder.getValue());
		}

		public Locale getLocale(ActionEvent evt) {
			return Language.getLanguage(persistentPreferredLanguage.getValue()).getLocale();
		}

		private boolean isAutoDetectionEnabled(ActionEvent evt) {
			return !isShiftOrAltDown(evt); // skip name auto-detection if SHIFT is pressed
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			// clear names list
			renameModel.values().clear();

			// select files
			List<File> files = getFiles(evt);
			if (files == null) {
				namesList.firePropertyChange(LOADING_PROPERTY, true, false);
				return;
			}

			List<File> remainingFiles = new LinkedList<File>(files);
			boolean strict = isStrict(evt);
			SortOrder order = getSortOrder(evt);
			Locale locale = getLocale(evt);
			boolean autodetection = isAutoDetectionEnabled(evt);

			if (isMacSandbox()) {
				if (!MacAppUtilities.askUnlockFolders(getWindow(RenamePanel.this), remainingFiles)) {
					namesList.firePropertyChange(LOADING_PROPERTY, true, false);
					return;
				}
			}

			SwingWorker<List<Match<File, ?>>, Void> worker = new SwingWorker<List<Match<File, ?>>, Void>() {

				@Override
				protected List<Match<File, ?>> doInBackground() throws Exception {
					List<Match<File, ?>> matches = matcher.get().match(remainingFiles, strict, order, locale, autodetection, getWindow(RenamePanel.this));

					// remove matched files
					for (Match<File, ?> match : matches) {
						remainingFiles.remove(match.getValue());
					}

					return matches;
				}

				@Override
				protected void done() {
					try {
						List<Match<Object, File>> matches = new ArrayList<Match<Object, File>>();

						for (Match<File, ?> match : get()) {
							matches.add(new Match<Object, File>(match.getCandidate(), match.getValue()));
						}

						renameModel.clear();
						renameModel.addAll(matches);

						// add remaining file entries
						renameModel.files().addAll(remainingFiles);
					} catch (Exception e) {
						// ignore cancellation exception
						if (findCause(e, CancellationException.class) != null) {
							return;
						}

						// common error message
						if (findCause(e, InvalidResponseException.class) != null) {
							log.log(Level.WARNING, findCause(e, InvalidResponseException.class).getMessage());
							return;
						}

						// generic error message
						log.log(Level.WARNING, e, cause(getRootCause(e)));
					} finally {
						// auto-match finished
						namesList.firePropertyChange(LOADING_PROPERTY, true, false);
					}
				}
			};

			// auto-match in progress
			namesList.firePropertyChange(LOADING_PROPERTY, false, true);
			worker.execute();
		}

	}

}
