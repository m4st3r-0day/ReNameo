package net.renameo.ui.rename;

import static net.renameo.Logging.*;
import static net.renameo.media.XattrMetaInfo.*;

import java.awt.Component;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

import net.renameo.History;
import net.renameo.History.Element;
import net.renameo.History.Sequence;
import net.renameo.HistorySpooler;
import net.renameo.Settings;
import net.renameo.StandardRenameAction;
import net.renameo.ui.AppEvents;
import net.renameo.util.PreferencesMap.PreferencesEntry;
import net.renameo.util.ui.SwingEventBus;

/**
 * Undo the most recent rename, also after a restart: the persistent rename history knows where every file came from.
 */
final class RenameUndo {

	// dates (millis) of history sequences that have already been undone
	private static final PreferencesEntry<String> undone = Settings.forPackage(RenameUndo.class).entry("rename.undone").defaultValue("");

	private RenameUndo() {
		throw new UnsupportedOperationException();
	}

	static Sequence getLastSequence() {
		try {
			History history = HistorySpooler.getInstance().getCompleteHistory();
			Set<String> done = getUndone();
			List<Sequence> sequences = history.sequences();
			for (int i = sequences.size() - 1; i >= 0; i--) {
				Sequence sequence = sequences.get(i);
				if (sequence.date() != null && !done.contains(String.valueOf(sequence.date().getTime()))) {
					return sequence;
				}
			}
		} catch (Exception e) {
			debug.warning("Failed to read rename history: " + e);
		}
		return null;
	}

	static void undoLast(Component parent) {
		Sequence sequence = getLastSequence();
		if (sequence == null || sequence.elements().isEmpty()) {
			SwingEventBus.getInstance().post(new AppEvents.Toast("Nothing to undo", "The rename history is empty.", AppEvents.Status.Kind.WARNING));
			return;
		}

		int count = sequence.elements().size();
		String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(sequence.date());
		String message = String.format("Undo the last rename of %d %s (%s)?", count, count == 1 ? "file" : "files", when);
		if (JOptionPane.showConfirmDialog(parent, message, "Undo Last Rename", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
			return;
		}

		int reverted = 0;
		List<String> failed = new ArrayList<String>();
		for (Element element : sequence.elements()) {
			File original = new File(element.dir(), element.from());
			File current = new File(element.to());
			if (!current.isAbsolute()) {
				current = new File(element.dir(), element.to());
			}
			try {
				File restored = StandardRenameAction.revert(current, original);
				xattr.clear(restored);
				reverted++;
			} catch (Exception e) {
				failed.add(current.getName());
				debug.warning(String.format("Failed to revert %s: %s", current, e));
			}
		}

		markUndone(sequence);

		if (failed.isEmpty()) {
			SwingEventBus.getInstance().post(new AppEvents.Toast(String.format("%d %s restored", reverted, reverted == 1 ? "file" : "files"), "The last rename has been undone.", AppEvents.Status.Kind.READY));
		} else {
			SwingEventBus.getInstance().post(new AppEvents.Toast(String.format("%d of %d files restored", reverted, count), String.format("%d could not be restored (moved or deleted?)", failed.size()), AppEvents.Status.Kind.WARNING));
		}
	}

	private static Set<String> getUndone() {
		String value = undone.getValue();
		return new LinkedHashSet<String>(value == null || value.isEmpty() ? new ArrayList<String>() : Arrays.asList(value.split(",")));
	}

	private static void markUndone(Sequence sequence) {
		Set<String> done = getUndone();
		done.add(String.valueOf(sequence.date().getTime()));
		undone.setValue(String.join(",", done));
	}

}
