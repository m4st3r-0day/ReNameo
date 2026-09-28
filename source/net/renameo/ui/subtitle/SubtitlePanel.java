package net.renameo.ui.subtitle;

import static net.renameo.Logging.*;
import static net.renameo.Settings.*;
import static net.renameo.ui.LanguageComboBoxModel.*;
import static net.renameo.util.FileUtilities.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Cursor;
import java.awt.Font;
import java.awt.Dialog.ModalityType;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.google.common.eventbus.Subscribe;

import net.renameo.Language;
import net.renameo.ResourceManager;
import net.renameo.Settings;
import net.renameo.WebServices;
import net.renameo.media.MediaDetection;
import net.renameo.ui.AbstractSearchPanel;
import net.renameo.ui.LanguageComboBox;
import net.renameo.ui.SelectDialog;
import net.renameo.ui.subtitle.SubtitleDropTarget.DropAction;
import net.renameo.ui.transfer.FileTransferable;
import net.renameo.ui.AppEvents;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.LabelProvider;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SimpleLabelProvider;
import net.renameo.util.ui.SwingEventBus;
import net.renameo.web.OpenSubtitlesRestClient;
import net.renameo.web.SearchResult;
import net.renameo.web.SubtitleDescriptor;
import net.renameo.web.SubtitleProvider;
import net.renameo.web.SubtitleSearchResult;
import net.renameo.web.VideoHashSubtitleService;
import net.miginfocom.swing.MigLayout;

public class SubtitlePanel extends AbstractSearchPanel<SubtitleProvider, SubtitlePackage> {

	private LanguageComboBox languageComboBox = new LanguageComboBox(ALL_LANGUAGES, getSettings());

	public SubtitlePanel() {
		historyPanel.setColumnHeader(0, "Search");
		historyPanel.setColumnHeader(1, "Number of Subtitles");

		// add after text field
		add(languageComboBox, "gap 12px, h 36!", 1);
		add(Modern.iconButton(setUserAction, Glyph.Shape.USER, "OpenSubtitles login"), "gap 6px, h 36!, w 36!", 2);

		// drop tiles at the top right corner: drop video files to download, subtitle files to upload
		uploadDropTarget.setToolTipText("Drop subtitle files here to upload them to OpenSubtitles");
		downloadDropTarget.setToolTipText("Drop video files here to download matching subtitles");
		add(uploadDropTarget, "width 44px!, height 44px!, gap before 16px", 4);
		add(downloadDropTarget, "width 44px!, height 44px!, gap before 8px", 5);
	}

	@Subscribe
	public void handle(Transferable transferable) throws Exception {
		SubtitleDropTarget target = downloadDropTarget;
		List<File> files = FileTransferable.getFilesFromTransferable(transferable);

		if (files != null && files.size() > 0 && target.getDropAction(files) != DropAction.Cancel) {
			target.handleDrop(files);
		}
	}

	private final SubtitleDropTarget uploadDropTarget = new SubtitleDropTarget.Upload() {

		@Override
		public OpenSubtitlesRestClient getSubtitleService() {
			return WebServices.OpenSubtitles;
		};
	};

	private final SubtitleDropTarget downloadDropTarget = new SubtitleDropTarget.Download() {

		public Locale getLocale() {
			return languageComboBox.getModel().getSelectedItem() == ALL_LANGUAGES ? Locale.ROOT : languageComboBox.getModel().getSelectedItem().getLocale();
		}

		@Override
		public VideoHashSubtitleService[] getVideoHashSubtitleServices() {
			return WebServices.getVideoHashSubtitleServices(getLocale());
		}

		@Override
		public SubtitleProvider[] getSubtitleProviders() {
			return WebServices.getSubtitleProviders(getLocale());
		}

		@Override
		public OpenSubtitlesRestClient getSubtitleService() {
			return WebServices.OpenSubtitles;
		};

		@Override
		public Locale getQueryLanguage() {
			// use currently selected language for drop target
			return languageComboBox.getModel().getSelectedItem() == ALL_LANGUAGES ? null : languageComboBox.getModel().getSelectedItem().getLocale();
		}
	};

	@Override
	protected Collection<String> getHistory(SubtitleProvider engine) throws Exception {
		List<String> names = new ArrayList<String>();
		for (SubtitleSearchResult it : MediaDetection.releaseInfo.getOpenSubtitlesIndex()) {
			names.add(it.toString());
		}
		return names;
	};

	@Override
	protected SubtitleProvider[] getSearchEngines() {
		return WebServices.getSubtitleProviders(getLocale());
	}

	@Override
	protected LabelProvider<SubtitleProvider> getSearchEngineLabelProvider() {
		return SimpleLabelProvider.forClass(SubtitleProvider.class);
	}

	@Override
	protected Settings getSettings() {
		return Settings.forPackage(SubtitlePanel.class);
	}

	@Override
	protected SubtitleRequestProcessor createRequestProcessor() {
		SubtitleProvider provider = searchTextField.getSelectButton().getSelectedValue();

		if (provider instanceof OpenSubtitlesRestClient && !((OpenSubtitlesRestClient) provider).hasApiKey()) {
			log.info(String.format("%s: Please enter your API key first.", ((OpenSubtitlesRestClient) provider).getName()));

			// automatically open login dialog
			SwingUtilities.invokeLater(() -> setUserAction.actionPerformed(new ActionEvent(searchTextField, 0, "login")));

			return null;
		}

		// parse query
		String query = searchTextField.getText();
		int season = seasonFilter.match(query);
		query = seasonFilter.remove(query).trim();
		int episode = episodeFilter.match(query);
		query = episodeFilter.remove(query).trim();

		Language language = languageComboBox.getModel().getSelectedItem();
		return new SubtitleRequestProcessor(new SubtitleRequest(provider, query, season, episode, language));
	}

	private final QueryFilter<Integer> seasonFilter = new QueryFilter<Integer>("season", s -> s == null ? -1 : Integer.parseInt(s));
	private final QueryFilter<Integer> episodeFilter = new QueryFilter<Integer>("episode", s -> s == null ? -1 : Integer.parseInt(s));

	protected static class QueryFilter<T> {

		private final Pattern pattern;
		private final Function<String, T> parser;

		public QueryFilter(String key, Function<String, T> parser) {
			this.pattern = Pattern.compile("(?:" + key + "):(\\w+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
			this.parser = parser;
		}

		public T match(String s) {
			Matcher m = pattern.matcher(s);
			if (m.find()) {
				return parser.apply(m.group(m.groupCount()));
			}
			return parser.apply(null);
		}

		public String remove(String s) {
			return pattern.matcher(s).replaceAll("");
		}
	}

	protected static class SubtitleRequest extends Request {

		private final SubtitleProvider provider;
		private final Language language;
		private final int season;
		private final int episode;

		public SubtitleRequest(SubtitleProvider provider, String searchText, int season, int episode, Language language) {
			super(searchText);
			this.season = season;
			this.episode = episode;
			this.provider = provider;
			this.language = language;
		}

		public SubtitleProvider getProvider() {
			return provider;
		}

		public Locale getLanguage() {
			return language == ALL_LANGUAGES ? null : language.getLocale();
		}

		public int[][] getEpisodeFilter() {
			return season >= 0 && episode >= 0 ? new int[][] { new int[] { season, episode } } : season >= 0 ? new int[][] { new int[] { season, -1 } } : null;
		}
	}

	protected static class SubtitleRequestProcessor extends RequestProcessor<SubtitleRequest, SubtitlePackage> {

		public SubtitleRequestProcessor(SubtitleRequest request) {
			super(request, new SubtitleDownloadComponent());
		}

		@Override
		public Collection<SubtitleSearchResult> search() throws Exception {
			return request.getProvider().search(request.getSearchText());
		}

		@Override
		public SubtitleSearchResult getSearchResult() {
			return (SubtitleSearchResult) super.getSearchResult();
		}

		@Override
		public Collection<SubtitlePackage> fetch() throws Exception {
			List<SubtitlePackage> packages = new ArrayList<SubtitlePackage>();

			for (SubtitleDescriptor subtitle : request.getProvider().getSubtitleList(getSearchResult(), request.getEpisodeFilter(), request.getLanguage())) {
				packages.add(new SubtitlePackage(request.getProvider(), subtitle));
			}

			return packages;
		}

		@Override
		public URI getLink() {
			return request.getProvider().getSubtitleListLink(getSearchResult(), request.getLanguage());
		}

		@Override
		public void process(Collection<SubtitlePackage> subtitles) {
			getComponent().setLanguageVisible(request.getLanguage() == null);
			getComponent().getPackageModel().addAll(subtitles);
		}

		@Override
		public SubtitleDownloadComponent getComponent() {
			return (SubtitleDownloadComponent) super.getComponent();
		}

		@Override
		public String getStatusMessage(Collection<SubtitlePackage> result) {
			return (result.isEmpty()) ? "No subtitles found" : String.format("%d subtitles", result.size());
		}

		@Override
		public Icon getIcon() {
			return request.provider.getIcon();
		}

		@Override
		protected void configureSelectDialog(SelectDialog<SearchResult> selectDialog) {
			super.configureSelectDialog(selectDialog);
			selectDialog.getMessageLabel().setText("Select a Show / Movie:");
		}

	}

	protected final Action setUserAction = new AbstractAction("Login", ResourceManager.getIcon("action.user")) {

		@Override
		public void actionPerformed(ActionEvent evt) {
			createLoginDialog().setVisible(true);
		}
	};

	/**
	 * OpenSubtitles sign in, styled like the rest of the app.
	 */
	JDialog createLoginDialog() {
		JDialog dialog = new JDialog(getWindow(SubtitlePanel.this), "OpenSubtitles", ModalityType.APPLICATION_MODAL);

		JTextField apiKey = Modern.field(new JTextField(18));
		JTextField user = Modern.field(new JTextField(18));
		JPasswordField pass = (JPasswordField) Modern.field(new JPasswordField(18));
		JLabel status = Modern.label(" ", 12f, Font.PLAIN, true);

		// restore values (the password stays in the keychain)
		apiKey.setText(WebServices.getOpenSubtitlesApiKey());
		user.setText(WebServices.getLogin(WebServices.LOGIN_OPENSUBTITLES)[0]);

		boolean registered = !user.getText().isEmpty();
		JLabel link = Modern.label(registered ? "Upgrade your account for a higher download quota" : "No account yet? Create one for free", 12.5f, Font.PLAIN, false);
		link.setForeground(NightTheme.getAccent());
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(mouseClicked(e -> openURI(registered ? "https://www.opensubtitles.com/en/vip" : OpenSubtitlesRestClient.REGISTER_PAGE.toString())));

		JLabel keyLink = Modern.label("Get a free API key", 12f, Font.PLAIN, false);
		keyLink.setForeground(NightTheme.getAccent());
		keyLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		keyLink.addMouseListener(mouseClicked(e -> openURI(OpenSubtitlesRestClient.API_KEY_PAGE.toString())));

		JButton cancel = Modern.button(newAction("Cancel", e -> dialog.dispose()), null, Modern.Style.SECONDARY);
		JButton signIn = Modern.button(newAction("Sign In", e -> {
			String key = apiKey.getText().trim();
			String name = user.getText().trim();
			String password = new String(pass.getPassword());

			if (key.isEmpty()) {
				showStatus(status, "Please enter your API key (opensubtitles.com → API consumers).", true);
				apiKey.requestFocusInWindow();
				return;
			}

			// an empty user name signs out and forgets the stored login
			if (name.isEmpty()) {
				WebServices.setOpenSubtitlesApiKey(key);
				WebServices.setLogin(WebServices.LOGIN_OPENSUBTITLES, null, null);
				dialog.dispose();
				return;
			}
			if (password.isEmpty()) {
				showStatus(status, "Please enter your password.", true);
				pass.requestFocusInWindow();
				return;
			}

			showStatus(status, "Signing in …", false);
			dialog.getRootPane().getDefaultButton().setEnabled(false);
			newSwingWorker(() -> {
				OpenSubtitlesRestClient osdb = new OpenSubtitlesRestClient(getApplicationName(), getApplicationVersion());
				osdb.setApiKey(key);
				osdb.setCredentials(name, password);
				osdb.login();
				Map<?, ?> info = osdb.getUserInfo();
				osdb.logout();
				return info;
			}, info -> {
				WebServices.setOpenSubtitlesApiKey(key);
				WebServices.setLogin(WebServices.LOGIN_OPENSUBTITLES, name, password);
				Object remaining = info == null ? null : info.get("remaining_downloads");
				Object allowed = info == null ? null : info.get("allowed_downloads");
				String quota = remaining == null ? name : String.format("%s · %s of %s downloads left today", name, remaining, allowed);
				log.info("OpenSubtitles: " + quota);
				SwingEventBus.getInstance().post(new AppEvents.Toast("Signed in to OpenSubtitles", quota, AppEvents.Status.Kind.READY));
				dialog.dispose();
			}, error -> {
				dialog.getRootPane().getDefaultButton().setEnabled(true);
				showStatus(status, "Sign in failed: " + error.getMessage(), true);
			}).execute();
		}), null, Modern.Style.PRIMARY);

		JPanel content = new JPanel(new MigLayout("insets 24 26 20 26, fillx, wrap 1", "[fill, grow]"));
		content.setBackground(NightTheme.getBackground());

		JLabel icon = new JLabel(Glyph.of(Glyph.Shape.USER, 22, NightTheme::getAccent));
		JLabel title = Modern.label("OpenSubtitles", 18f, Font.BOLD, false);
		title.setIcon(icon.getIcon());
		title.setIconTextGap(10);
		content.add(title);
		content.add(Modern.label("Sign in with your opensubtitles.com account to download subtitles.", 12.5f, Font.PLAIN, true), "gapbottom 14");

		JPanel keyRow = new JPanel(new MigLayout("insets 0, fillx", "[]push[]"));
		keyRow.setOpaque(false);
		keyRow.add(Modern.label("API Key", 12f, Font.BOLD, true));
		keyRow.add(keyLink);
		content.add(keyRow);
		content.add(apiKey, "h 34!, gapbottom 8");

		content.add(Modern.label("Username", 12f, Font.BOLD, true));
		content.add(user, "h 34!, gapbottom 8");
		content.add(Modern.label("Password", 12f, Font.BOLD, true));
		content.add(pass, "h 34!");
		content.add(status, "gaptop 6");
		content.add(link, "gaptop 2");

		JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "push[][]"));
		buttons.setOpaque(false);
		buttons.add(cancel);
		buttons.add(signIn);
		content.add(buttons, "gaptop 16");

		dialog.setContentPane(content);
		dialog.getRootPane().setDefaultButton(signIn);
		installAction(content, KeyStroke.getKeyStroke("ESCAPE"), newAction("Cancel", e -> dialog.dispose()));
		dialog.pack();
		dialog.setSize(Math.max(420, dialog.getWidth()), dialog.getHeight());
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(getWindow(SubtitlePanel.this));
		(apiKey.getText().isEmpty() ? apiKey : registered ? pass : user).requestFocusInWindow();
		return dialog;
	}

	private static void showStatus(JLabel status, String text, boolean error) {
		status.setText(text);
		status.setForeground(error ? NightTheme.getDanger() : NightTheme.getDimForeground());
	}

}
