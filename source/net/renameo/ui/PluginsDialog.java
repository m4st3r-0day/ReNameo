package net.renameo.ui;

import static net.renameo.util.ui.SwingUI.*;

import java.awt.Dialog.ModalityType;
import java.awt.Font;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;

import net.miginfocom.swing.MigLayout;
import net.renameo.plugins.Plugins;
import net.renameo.plugins.Plugins.Plugin;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;

/**
 * Lists the plugins in the plugins folder, with their description or load error, and turns them on and off.
 */
public class PluginsDialog extends JDialog {

	public static void show(Window owner) {
		new PluginsDialog(owner).setVisible(true);
	}

	private final JPanel list = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[fill, grow]"));

	private PluginsDialog(Window owner) {
		super(owner, "Plugins", ModalityType.APPLICATION_MODAL);

		JPanel content = new JPanel(new MigLayout("insets 24 26 20 26, fillx, wrap 1", "[fill, grow]", "[][][fill, grow][]"));
		content.setBackground(NightTheme.getBackground());

		JLabel title = Modern.label("Plugins", 18f, Font.BOLD, false);
		title.setIcon(Glyph.of(Glyph.Shape.SLIDERS, 22, NightTheme::getAccent));
		title.setIconTextGap(10);
		content.add(title);
		content.add(Modern.label("<html><body style='width: 360px'>Plugins are Groovy scripts in the plugins folder. They can react to every rename (e.g. tell your media server to rescan) and add values to formats, like <b>{plugin.name}</b>. Only install plugins you trust: they run with your rights.</body></html>", 12.5f, Font.PLAIN, true), "gapbottom 12");

		list.setOpaque(false);
		JScrollPane scroll = new JScrollPane(list);
		Modern.slim(scroll);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setViewportBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		content.add(scroll, "hmin 40, hmax 320");
		fill();

		JButton folder = Modern.button(newAction("Open folder", e -> openURI(Plugins.getFolder().toURI().toString())), null, Modern.Style.SECONDARY);
		JButton reload = Modern.button(newAction("Reload", e -> {
			Plugins.load();
			fill();
			pack();
			SwingEventBus.getInstance().post(new AppEvents.Toast("Plugins reloaded", Plugins.list().size() + " found", AppEvents.Status.Kind.READY));
		}), null, Modern.Style.SECONDARY);
		JButton close = Modern.button(newAction("Close", e -> dispose()), null, Modern.Style.PRIMARY);

		JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "[][]push[]"));
		buttons.setOpaque(false);
		buttons.add(folder);
		buttons.add(reload);
		buttons.add(close);
		content.add(buttons, "gaptop 12");

		setContentPane(content);
		getRootPane().setDefaultButton(close);
		installAction(content, KeyStroke.getKeyStroke("ESCAPE"), newAction("Close", e -> dispose()));
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}

	private void fill() {
		list.removeAll();

		if (Plugins.list().isEmpty()) {
			list.add(Modern.label("<html><body style='width: 360px'>No plugins yet. Put a <b>.groovy</b> file into " + Plugins.getFolder() + " and press Reload.</body></html>", 12f, Font.PLAIN, true));
		}

		for (Plugin plugin : Plugins.list()) {
			JCheckBox enabled = new JCheckBox(plugin.name, plugin.enabled);
			enabled.setOpaque(false);
			enabled.setForeground(NightTheme.getForeground());
			enabled.setFont(Modern.font(13f, Font.BOLD));
			enabled.addActionListener(e -> {
				Plugins.setEnabled(plugin, enabled.isSelected());
				Plugins.load();
				fill();
				pack();
			});
			list.add(enabled, "gaptop 6");

			String text = plugin.error != null ? plugin.error : !plugin.enabled ? "Turned off" : plugin.description.isEmpty() ? plugin.file.getName() : plugin.description;
			JLabel detail = Modern.label("<html><body style='width: 330px'>" + escapeHTML(text) + "</body></html>", 11.5f, Font.PLAIN, true);
			if (plugin.error != null) {
				detail.setForeground(NightTheme.getDanger());
			}
			list.add(detail, "gapleft 24");
		}

		list.revalidate();
		list.repaint();
	}

}
