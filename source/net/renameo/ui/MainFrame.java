package net.renameo.ui;

import static java.awt.event.InputEvent.*;
import static java.awt.event.KeyEvent.*;
import static java.util.Arrays.*;
import static javax.swing.BorderFactory.*;
import static javax.swing.KeyStroke.*;
import static javax.swing.ScrollPaneConstants.*;
import static net.renameo.Logging.*;
import static net.renameo.Settings.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dialog.ModalExclusionType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.google.common.eventbus.Subscribe;

import net.renameo.CacheManager;
import net.renameo.Settings;
import net.renameo.cli.GroovyPad;
import net.renameo.util.PreferencesMap.PreferencesEntry;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;
import net.renameo.util.ui.ProgressLayer;
import net.renameo.util.ui.ToastLayer;
import net.miginfocom.swing.MigLayout;

public class MainFrame extends JFrame {

	/**
	 * Client property a panel sets to contribute components to the right side of the page header.
	 */
	public static final String PAGE_ACTIONS = "page.actions";

	/**
	 * Client property a panel sets to override the page header subtitle.
	 */
	public static final String PAGE_SUBTITLE = "page.subtitle";

	/**
	 * Client property a panel sets to a {@code List<Action>} offered by the command palette.
	 */
	public static final String PAGE_COMMANDS = "page.commands";

	private static final PreferencesEntry<String> persistentSidebarCollapsed = Settings.forPackage(MainFrame.class).entry("ui.sidebar.collapsed").defaultValue("false");

	private static final PreferencesEntry<String> persistentSelectedPanel = Settings.forPackage(MainFrame.class).entry("panel.selected").defaultValue("0");

	private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().startsWith("mac");

	private static final Map<String, String[]> PAGES = new HashMap<String, String[]>();

	static {
		PAGES.put("Rename", new String[] { "Rename Media Files", "Match files with online metadata and rename them automatically." });
		PAGES.put("Episodes", new String[] { "Episode Lists", "Browse complete episode lists for any series." });
		PAGES.put("Subtitles", new String[] { "Subtitles", "Search, download and match subtitles for your media." });
		PAGES.put("SFV", new String[] { "Checksums", "Create and verify SFV, MD5 and SHA checksum files." });
		PAGES.put("Filter", new String[] { "Filter & Inspect", "Inspect media attributes, extract archives and organize files." });
		PAGES.put("List", new String[] { "List Generator", "Generate lists of names from a pattern." });
	}

	private final PanelSelectionList selectionList;
	private final PanelBuilder[] builders;
	private JComponent sidebar;
	private JButton sidebarSettings;
	private JComponent currentPanel;
	private final JPanel panelHost = new JPanel(new CardLayout());
	private final PageHeader pageHeader = new PageHeader();
	private final StatusPill statusPill = new StatusPill();
	private ToastLayer toasts;

	public MainFrame(PanelBuilder[] panels) {
		super(getApplicationName());

		if (MAC) {
			// draw our own top bar underneath the native traffic lights
			// keep the native title bar (macOS moves the window with it), just blend it with the app bar below
			getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
			getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
			Modern.onThemeChange(getRootPane(), () -> setBackground(NightTheme.getHeaderColor()));
		}

		builders = panels;
		selectionList = new PanelSelectionList(panels);
		panelHost.setOpaque(false);

		JComponent main = new JPanel(new BorderLayout());
		main.setOpaque(false);
		main.setBorder(createEmptyBorder(18, 24, 16, 24));
		main.add(pageHeader, BorderLayout.NORTH);
		main.add(panelHost, BorderLayout.CENTER);
		main.setMinimumSize(new Dimension(0, 0));

		JComponent root = new JPanel(new BorderLayout()) {

			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(NightTheme.getBackground());
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		root.add(new TopBar(), BorderLayout.NORTH);
		root.add(createSidebar(), BorderLayout.WEST);
		root.add(main, BorderLayout.CENTER);
		setContentPane(root);
		toasts = ToastLayer.install(getRootPane());
		ProgressLayer.install(getRootPane());

		// restore selected panel
		int restoreIndex;
		try {
			restoreIndex = Integer.parseInt(persistentSelectedPanel.getValue());
		} catch (Exception e) {
			restoreIndex = 0;
		}
		if (restoreIndex >= 0 && restoreIndex < panels.length) {
			selectionList.setSelectedValue(panels[restoreIndex], false);
		}

		// show initial panel
		showPanel(selectionList.getSelectedValue());

		selectionList.addSelectionListener(() -> {
			PanelBuilder selected = selectionList.getSelectedValue();
			if (selected == null)
				return;
			showPanel(selected);
			int idx = asList(panels).indexOf(selected);
			if (idx >= 0) {
				persistentSelectedPanel.setValue(Integer.toString(idx));
			}
		});

		setSize(1360, 820);
		setMinimumSize(new Dimension(1040, 620));

		installAction(getRootPane(), getKeyStroke(VK_DELETE, CTRL_DOWN_MASK | SHIFT_DOWN_MASK), newAction("Clear Cache", evt -> {
			withWaitCursor(getRootPane(), () -> {
				CacheManager.getInstance().clearAll();
				log.info("Cache has been cleared");
			});
		}));

		installAction(getRootPane(), getKeyStroke(VK_F5, 0), newAction("Run", evt -> {
			withWaitCursor(getRootPane(), () -> {
				GroovyPad pad = new GroovyPad();

				pad.addWindowListener(new WindowAdapter() {

					@Override
					public void windowOpened(WindowEvent e) {
						setVisible(false);
					};

					@Override
					public void windowClosing(WindowEvent e) {
						setVisible(true);
					};
				});

				pad.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
				pad.setModalExclusionType(ModalExclusionType.TOOLKIT_EXCLUDE);
				pad.setLocationByPlatform(true);
				pad.setVisible(true);
			});
		}));

		installAction(this.getRootPane(), getKeyStroke(VK_F1, 0), newAction("Help", evt -> openURI(getEmbeddedHelpURL())));

		SwingEventBus.getInstance().register(this);
	}

	@Subscribe
	public void selectPanel(PanelBuilder panel) {
		selectionList.setSelectedValue(panel, false);
	}

	@Subscribe
	public void status(AppEvents.Status status) {
		statusPill.setStatus(status.text, status.kind);
	}

	@Subscribe
	public void toast(AppEvents.Toast toast) {
		Color tint = toast.kind == AppEvents.Status.Kind.WARNING ? NightTheme.getWarning() : toast.kind == AppEvents.Status.Kind.BUSY ? NightTheme.getAccent() : NightTheme.getSuccess();
		toasts.show(toast.title, toast.detail, tint);
	}

	private JComponent createSidebar() {
		JButton settings = Modern.button(newAction("Settings", evt -> showSettingsPopup((JComponent) evt.getSource())), Glyph.of(Glyph.Shape.SETTINGS, 18, NightTheme::getDimForeground), Modern.Style.GHOST);
		settings.setHorizontalAlignment(SwingConstants.LEFT);
		settings.setIconTextGap(12);
		sidebarSettings = settings;

		sidebar = new JPanel(new BorderLayout()) {

			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(NightTheme.getSidebarBackground());
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(NightTheme.getBorder());
				g.fillRect(getWidth() - 1, 0, 1, getHeight());
			}
		};
		sidebar.setPreferredSize(new Dimension(224, 0));
		sidebar.setBorder(createEmptyBorder(14, 12, 14, 12));
		sidebar.add(selectionList, BorderLayout.CENTER);
		selectionList.setMinimumSize(new Dimension(0, 0));

		JPanel footer = new JPanel(new MigLayout("insets 0, fillx", "[fill, grow]"));
		footer.setOpaque(false);
		footer.add(settings, "h 40!");
		sidebar.add(footer, BorderLayout.SOUTH);

		setSidebarCollapsed(Boolean.parseBoolean(persistentSidebarCollapsed.getValue()));
		return sidebar;
	}

	/**
	 * Compact mode: icons only, for more room when working on hundreds of files.
	 */
	private void setSidebarCollapsed(boolean collapsed) {
		persistentSidebarCollapsed.setValue(String.valueOf(collapsed));
		sidebar.setPreferredSize(new Dimension(collapsed ? 68 : 224, 0));
		sidebar.setBorder(collapsed ? createEmptyBorder(14, 8, 14, 8) : createEmptyBorder(14, 12, 14, 12));
		selectionList.setCollapsed(collapsed);
		sidebarSettings.setHideActionText(collapsed);
		sidebarSettings.setHorizontalAlignment(collapsed ? SwingConstants.CENTER : SwingConstants.LEFT);
		sidebarSettings.setToolTipText(collapsed ? "Settings" : null);

		// relayout the whole window, not only the sidebar, so the content takes the freed space right away
		JComponent root = (JComponent) getContentPane();
		root.revalidate();
		root.repaint();
	}

	private List<Action> getCommands() {
		List<Action> commands = new ArrayList<Action>();
		if (currentPanel != null && currentPanel.getClientProperty(PAGE_COMMANDS) instanceof List) {
			for (Object it : (List<?>) currentPanel.getClientProperty(PAGE_COMMANDS)) {
				commands.add((Action) it);
			}
		}
		for (PanelBuilder builder : builders) {
			commands.add(newAction("Go to " + builder.getName(), evt -> selectionList.setSelectedValue(builder, false)));
		}
		commands.add(newAction("Toggle sidebar", evt -> setSidebarCollapsed(!Boolean.parseBoolean(persistentSidebarCollapsed.getValue()))));
		commands.add(newAction("Toggle night mode", evt -> NightTheme.setNightMode(!NightTheme.isNightMode())));
		commands.add(newAction("Open settings", evt -> showSettingsPopup(sidebarSettings)));
		commands.add(newAction("Open user guide", evt -> openURI(getEmbeddedHelpURL())));
		commands.add(newAction("API keys", evt -> ApiKeysDialog.show(this, false)));
		commands.add(newAction("Watch folder", evt -> WatchFolderDialog.show(this)));
		commands.add(newAction("Update offline index", evt -> OfflineIndexAction.run(this)));
		commands.add(newAction("Plugins", evt -> PluginsDialog.show(this)));
		return commands;
	}

	private static void showSettingsPopup(JComponent source) {
		SettingsPopup popup = new SettingsPopup();
		// the sidebar button sits at the bottom of the window, so the popover opens above it
		popup.show(source, 0, -popup.getPreferredSize().height - 6);
	}

	private void showPanel(PanelBuilder selectedBuilder) {
		if (selectedBuilder == null)
			return;

		JComponent selectedPanel = null;

		for (Component component : panelHost.getComponents()) {
			JComponent panel = (JComponent) component;
			PanelBuilder builder = (PanelBuilder) panel.getClientProperty(PanelBuilder.class.getName());
			if (builder != null) {
				if (builder.equals(selectedBuilder)) {
					selectedPanel = panel;
				} else if (panel.isVisible()) {
					SwingEventBus.getInstance().unregister(panel);
				}
			}
		}

		if (selectedPanel == null) {
			selectedPanel = selectedBuilder.create();
			selectedPanel.putClientProperty(PanelBuilder.class.getName(), selectedBuilder);
			JComponent panel = selectedPanel;
			Modern.onThemeChange(panel, () -> Modern.modernize(panel));
			panelHost.add(selectedPanel, selectedBuilder.getName());
		}

		String[] page = PAGES.getOrDefault(selectedBuilder.getName(), new String[] { selectedBuilder.getName(), "" });
		Object subtitle = selectedPanel.getClientProperty(PAGE_SUBTITLE);
		pageHeader.setPage(page[0], subtitle != null ? subtitle.toString() : page[1], (JComponent) selectedPanel.getClientProperty(PAGE_ACTIONS));
		currentPanel = selectedPanel;

		((CardLayout) panelHost.getLayout()).show(panelHost, selectedBuilder.getName());
		SwingEventBus.getInstance().register(selectedPanel);
		statusPill.setStatus("Ready", AppEvents.Status.Kind.READY);
	}

	/**
	 * Title, subtitle and the primary actions of the active panel.
	 */
	private static class PageHeader extends JPanel {

		private final JLabel title = Modern.label("", 22f, Font.BOLD, false);
		private final JLabel subtitle = Modern.label("", 13f, Font.PLAIN, true);
		private final JPanel actions = new JPanel(new BorderLayout());

		PageHeader() {
			super(new MigLayout("insets 0 0 16 0, fillx", "[grow][]", "[]2[]"));
			setOpaque(false);
			actions.setOpaque(false);
			add(title, "cell 0 0");
			add(subtitle, "cell 0 1");
			add(actions, "cell 1 0 1 2, aligny center");
		}

		void setPage(String titleText, String subtitleText, JComponent pageActions) {
			title.setText(titleText);
			subtitle.setText(subtitleText);
			subtitle.setVisible(subtitleText != null && subtitleText.length() > 0);
			actions.removeAll();
			if (pageActions != null) {
				actions.add(pageActions, BorderLayout.CENTER);
			}
			revalidate();
			repaint();
		}
	}

	/**
	 * Application bar: brand, global search, quick actions and status. Doubles as the window drag area on macOS.
	 */
	private class TopBar extends JPanel {

		TopBar() {
			super(new MigLayout("insets 6 16 10 16, fill", "[][][]push[360:520:560]push[][]", "[center]"));
			setOpaque(false);

			JLabel brand = Modern.label(getApplicationName(), 15f, Font.BOLD, false);
			brand.setIcon(new LogoIcon(26));
			brand.setIconTextGap(10);
			JLabel version = Modern.label(getApplicationVersion(), 12f, Font.PLAIN, true);

			Modern.SearchField search = new Modern.SearchField("Search files or type a command…", MAC ? "⌘K" : "Ctrl K");
			search.addActionListener(evt -> SwingEventBus.getInstance().post(new AppEvents.Search(search.getText().trim(), true)));
			new CommandPalette(MainFrame.this.getRootPane(), search, MainFrame.this::getCommands);

			JButton toggleSidebar = Modern.iconButton(newAction("Toggle Sidebar", evt -> setSidebarCollapsed(!Boolean.parseBoolean(persistentSidebarCollapsed.getValue()))), Glyph.Shape.SIDEBAR, "Show / hide sidebar labels (" + (MAC ? "⌘\\" : "Ctrl \\") + ")");
			installAction(MainFrame.this.getRootPane(), getKeyStroke(VK_BACK_SLASH, MAC ? META_DOWN_MASK : CTRL_DOWN_MASK), toggleSidebar.getAction());
			installAction(search, getKeyStroke(VK_ESCAPE, 0), newAction("Clear Search", evt -> search.setText("")));
			installAction(MainFrame.this.getRootPane(), getKeyStroke(VK_K, MAC ? META_DOWN_MASK : CTRL_DOWN_MASK), newAction("Search", evt -> {
				search.requestFocusInWindow();
				search.selectAll();
			}));

			JButton help = Modern.iconButton(newAction("Help", evt -> openURI(getEmbeddedHelpURL())), Glyph.Shape.HELP, "User guide (F1)");

			add(brand);
			add(version, "gapleft 4");
			add(toggleSidebar, "gapleft 14");
			add(search, "growx, h 34!");
			add(help, "gapright 10");
			add(statusPill);

		}

		@Override
		protected void paintComponent(Graphics g) {
			g.setColor(NightTheme.getHeaderColor());
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(NightTheme.getBorder());
			g.fillRect(0, getHeight() - 1, getWidth(), 1);
		}
	}

	/**
	 * Rounded app mark: play glyph on a blue to violet gradient.
	 */
	private static class LogoIcon implements Icon {

		private final int size;

		LogoIcon(int size) {
			this.size = size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2d = Modern.smooth(g);
			g2d.setPaint(new GradientPaint(x, y, NightTheme.getAccent(), x + size, y + size, NightTheme.getSecondaryAccent()));
			g2d.fill(new RoundRectangle2D.Float(x, y, size, size, size * 0.42f, size * 0.42f));
			Glyph.of(Glyph.Shape.PLAY, (int) (size * 0.62), Color.WHITE).paintIcon(c, g2d, x + (int) (size * 0.21), y + (int) (size * 0.19));
			g2d.dispose();
		}

		@Override
		public int getIconWidth() {
			return size;
		}

		@Override
		public int getIconHeight() {
			return size;
		}
	}

	/**
	 * Compact status indicator: colored dot plus a short message.
	 */
	private static class StatusPill extends JComponent {

		private String text = "Ready";
		private AppEvents.Status.Kind kind = AppEvents.Status.Kind.READY;
		private float pulse = 0;
		private final Timer timer = new Timer(60, evt -> {
			pulse = (pulse + 0.08f) % 2f;
			repaint();
		});

		StatusPill() {
			setFont(Modern.font(12.5f, Font.PLAIN));
		}

		void setStatus(String text, AppEvents.Status.Kind kind) {
			this.text = text;
			this.kind = kind;
			if (kind == AppEvents.Status.Kind.BUSY) {
				timer.start();
			} else {
				timer.stop();
			}
			revalidate();
			repaint();
		}

		@Override
		public Dimension getPreferredSize() {
			FontMetrics fm = getFontMetrics(getFont());
			// fixed width, so the search field in the middle never shifts when the status text changes
			return new Dimension(Math.max(132, fm.stringWidth(text) + 40), 30);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = Modern.smooth(g);
			g2d.setColor(NightTheme.getCardBackground());
			g2d.fill(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, getHeight(), getHeight()));
			g2d.setColor(NightTheme.getBorder());
			g2d.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, getHeight(), getHeight()));

			Color dot = kind == AppEvents.Status.Kind.BUSY ? NightTheme.getAccent() : kind == AppEvents.Status.Kind.WARNING ? NightTheme.getWarning() : NightTheme.getSuccess();
			float cy = getHeight() / 2f;
			if (kind == AppEvents.Status.Kind.BUSY) {
				float halo = pulse < 1 ? pulse : 2 - pulse;
				g2d.setColor(Modern.alpha(dot, (int) (90 * (1 - halo))));
				float r = 4 + 4 * halo;
				g2d.fill(new Ellipse2D.Float(16 - r, cy - r, 2 * r, 2 * r));
			}
			g2d.setColor(dot);
			g2d.fill(new Ellipse2D.Float(12, cy - 4, 8, 8));

			g2d.setFont(getFont());
			g2d.setColor(NightTheme.getForeground());
			FontMetrics fm = g2d.getFontMetrics();
			g2d.drawString(text, 28, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
			g2d.dispose();
		}
	}

	private static class PanelSelectionList extends JPanel {

		private final PanelBuilder[] builders;
		private final JList toolsList;
		private final JList utilitiesList;
		private Runnable selectionListener;
		private boolean updating = false;

		PanelSelectionList(PanelBuilder[] builders) {
			super(new MigLayout("insets 0, gapy 2, wrap 1, fillx", "[fill, grow]"));
			this.builders = builders;
			setOpaque(false);

			PanelBuilder[] tools = stream(builders).filter(this::isMainTool).toArray(PanelBuilder[]::new);
			PanelBuilder[] utilities = stream(builders).filter(b -> !isMainTool(b)).toArray(PanelBuilder[]::new);

			toolsList = createList(tools);
			utilitiesList = createList(utilities);

			JLabel mediaTools = Modern.caption("Media Tools");
			captions.add(mediaTools);
			add(mediaTools, "gapleft 12, gapbottom 6, hidemode 3");
			add(toolsList, "wmin 0");

			if (utilities.length > 0) {
				JComponent divider = new JComponent() {

					@Override
					protected void paintComponent(Graphics g) {
						g.setColor(NightTheme.getBorder());
						g.fillRect(8, getHeight() / 2, getWidth() - 16, 1);
					}
				};
				add(divider, "h 21!, gaptop 6");
				JLabel utilitiesCaption = Modern.caption("Utilities");
				captions.add(utilitiesCaption);
				add(utilitiesCaption, "gapleft 12, gapbottom 6, hidemode 3");
				add(utilitiesList, "wmin 0");
			}
		}

		private final List<JComponent> captions = new ArrayList<JComponent>();

		void setCollapsed(boolean collapsed) {
			for (JComponent caption : captions) {
				caption.setVisible(!collapsed);
			}
			for (JList list : new JList[] { toolsList, utilitiesList }) {
				list.putClientProperty("collapsed", collapsed);
				list.setToolTipText(collapsed ? "" : null);
				list.setFixedCellWidth(collapsed ? 50 : -1); // cell sizes computed with the labels would keep the list wide
				list.setCellRenderer(list.getCellRenderer());
				list.revalidate();
				list.repaint();
			}
			revalidate();
		}

		private boolean isMainTool(PanelBuilder b) {
			String name = b.getName();
			return "Rename".equals(name) || "Episodes".equals(name) || "Subtitles".equals(name);
		}

		private JList createList(PanelBuilder[] items) {
			DefaultListModel model = new DefaultListModel();
			for (PanelBuilder b : items) {
				model.addElement(b);
			}
			JList list = new JList(model) {

				@Override
				public String getToolTipText(MouseEvent e) {
					int index = locationToIndex(e.getPoint());
					return Boolean.TRUE.equals(getClientProperty("collapsed")) && index >= 0 ? ((PanelBuilder) getModel().getElementAt(index)).getName() : null;
				}
			};
			list.setCellRenderer(new PanelCellRenderer());
			list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			list.setFixedCellHeight(42);
			list.setOpaque(false);
			list.setBorder(createEmptyBorder());

			list.getSelectionModel().addListSelectionListener(e -> {
				if (e.getValueIsAdjusting() || updating)
					return;
				if (list.getSelectedIndex() >= 0) {
					updating = true;
					(list == toolsList ? utilitiesList : toolsList).clearSelection();
					updating = false;
				}
				fireSelectionChanged();
			});

			list.addMouseMotionListener(new MouseMotionAdapter() {

				@Override
				public void mouseMoved(MouseEvent e) {
					setHovered(list, list.locationToIndex(e.getPoint()));
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					setHovered(list, list.locationToIndex(e.getPoint()));
				}
			});

			list.addMouseListener(new MouseAdapter() {

				@Override
				public void mouseExited(MouseEvent e) {
					setHovered(list, -1);
				}
			});

			new DropTarget(list, new DragDropListener(list));
			return list;
		}

		private void setHovered(JList list, int index) {
			Integer current = (Integer) list.getClientProperty("hover.index");
			if (!Objects.equals(current, index)) {
				list.putClientProperty("hover.index", index);
				list.repaint();
			}
		}

		private void fireSelectionChanged() {
			if (selectionListener != null) {
				selectionListener.run();
			}
		}

		public void addSelectionListener(Runnable listener) {
			this.selectionListener = listener;
		}

		public PanelBuilder getSelectedValue() {
			for (JList list : new JList[] { toolsList, utilitiesList }) {
				Object value = list.getSelectedValue();
				if (value instanceof PanelBuilder) {
					return (PanelBuilder) value;
				}
			}
			return null;
		}

		public void setSelectedValue(PanelBuilder panel, boolean shouldScroll) {
			updating = true;
			try {
				for (JList list : new JList[] { toolsList, utilitiesList }) {
					for (int i = 0; i < list.getModel().getSize(); i++) {
						Object element = list.getModel().getElementAt(i);
						if (element instanceof PanelBuilder && element.equals(panel)) {
							list.setSelectedIndex(i);
							JList other = (list == toolsList) ? utilitiesList : toolsList;
							other.clearSelection();
						}
					}
				}
			} finally {
				updating = false;
			}
			fireSelectionChanged();
		}

		public void setSelectedIndex(int builderIndex) {
			if (builderIndex >= 0 && builderIndex < builders.length) {
				setSelectedValue(builders[builderIndex], false);
			}
		}

		private class DragDropListener extends DropTargetAdapter {

			private final JList target;
			private boolean selectEnabled = false;
			private Timer dragEnterTimer;

			DragDropListener(JList target) {
				this.target = target;
			}

			@Override
			public void dragOver(DropTargetDragEvent dtde) {
				if (selectEnabled) {
					int index = target.locationToIndex(dtde.getLocation());
					if (index >= 0 && index < target.getModel().getSize() && index != target.getSelectedIndex()) {
						updating = true;
						target.setSelectedIndex(index);
						JList other = (target == toolsList) ? utilitiesList : toolsList;
						other.clearSelection();
						updating = false;
						fireSelectionChanged();
					}
				}
			}

			@Override
			public void dragEnter(final DropTargetDragEvent dtde) {
				dragEnterTimer = invokeLater(300, () -> {
					selectEnabled = true;

					if (Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
						Desktop.getDesktop().requestForeground(true);
					} else {
						SwingUtilities.getWindowAncestor(((DropTarget) dtde.getSource()).getComponent()).toFront();
					}
				});
			}

			@Override
			public void dragExit(DropTargetEvent dte) {
				selectEnabled = false;

				if (dragEnterTimer != null) {
					dragEnterTimer.stop();
				}
			}

			@Override
			public void drop(DropTargetDropEvent dtde) {
			}
		}
	}

	private static class PanelCellRenderer extends JLabel implements ListCellRenderer<Object> {

		private static final Map<Icon, Icon> scaled = new HashMap<Icon, Icon>();

		private boolean selected = false;
		private boolean hovered = false;
		private boolean collapsed = false;

		PanelCellRenderer() {
			setHorizontalTextPosition(SwingConstants.RIGHT);
			setHorizontalAlignment(SwingConstants.LEFT);
			setIconTextGap(12);
			setBorder(createEmptyBorder(0, 12, 0, 12));
		}

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			if (value instanceof PanelBuilder) {
				PanelBuilder panel = (PanelBuilder) value;
				collapsed = Boolean.TRUE.equals(list.getClientProperty("collapsed"));
				setText(collapsed ? null : panel.getName());
				setHorizontalAlignment(collapsed ? SwingConstants.CENTER : SwingConstants.LEFT);
				setBorder(collapsed ? createEmptyBorder() : createEmptyBorder(0, 12, 0, 12));
				setIcon(scaled.computeIfAbsent(panel.getIcon(), i -> scaleIcon(i, 20)));
				selected = isSelected;
				hovered = Objects.equals(list.getClientProperty("hover.index"), index) && !isSelected;
				setFont(Modern.font(14f, selected ? Font.BOLD : Font.PLAIN));
				setForeground(selected ? NightTheme.getForeground() : hovered ? NightTheme.getForeground() : Modern.mix(NightTheme.getForeground(), NightTheme.getDimForeground(), 0.35f));
			}
			setOpaque(false);
			return this;
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = Modern.smooth(g);

			if (selected || hovered) {
				int size = getHeight() - 4;
				RoundRectangle2D shape = collapsed ? new RoundRectangle2D.Double((getWidth() - size) / 2.0, 2, size, size, 12, 12) : new RoundRectangle2D.Double(0, 2, getWidth(), getHeight() - 4, 12, 12);
				g2d.setColor(selected ? Modern.alpha(NightTheme.getAccent(), NightTheme.isNightMode() ? 46 : 30) : NightTheme.getCardHover());
				g2d.fill(shape);
			}

			if (selected && !collapsed) {
				g2d.setColor(NightTheme.getAccent());
				g2d.fill(new RoundRectangle2D.Double(0, 12, 3, getHeight() - 24, 3, 3));
			}

			g2d.dispose();
			super.paintComponent(g);
		}

		private static Icon scaleIcon(Icon icon, int baseSize) {
			if (icon == null) {
				return null;
			}
			int large = baseSize * 2;
			BufferedImage big = new BufferedImage(large, large, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = big.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			if (icon instanceof ImageIcon) {
				g.drawImage(((ImageIcon) icon).getImage(), 0, 0, large, large, null);
			} else {
				int pad = Math.max(0, (large - icon.getIconWidth()) / 2);
				icon.paintIcon(null, g, pad, pad);
			}
			g.dispose();

			BufferedImage smallImg = new BufferedImage(baseSize, baseSize, BufferedImage.TYPE_INT_ARGB);
			Graphics2D sg = smallImg.createGraphics();
			sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			sg.drawImage(big, 0, 0, baseSize, baseSize, null);
			sg.dispose();

			return new ImageIcon(new BaseMultiResolutionImage(smallImg, big));
		}
	}
}
