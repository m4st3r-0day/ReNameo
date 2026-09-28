package net.renameo.ui.rename;

import static net.renameo.Logging.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.logging.Level;

import javax.swing.AbstractAction;

import net.renameo.ResourceManager;
import net.renameo.similarity.EpisodeMetrics;
import net.renameo.similarity.Match;
import net.renameo.similarity.Matcher;
import net.renameo.util.ui.ProgressMonitor;

class MatchAction extends AbstractAction {

	private final RenameModel model;

	public MatchAction(RenameModel model) {
		this.model = model;

		// initialize with default values
		setMatchMode(false);
	}

	public void setMatchMode(boolean strict) {
		putValue(NAME, "Match");
		putValue(SMALL_ICON, ResourceManager.getIcon(strict ? "action.match.strict" : "action.match"));
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		if (model.names().isEmpty() || model.files().isEmpty()) {
			return;
		}

		Matcher<Object, File> matcher = new Matcher<Object, File>(model.values(), model.candidates(), false, EpisodeMetrics.defaultSequence(true));
		setEnabled(false);
		ProgressMonitor.runTask("Finding optimal alignment", null, (message, progress, cancelled) -> {
			message.accept(String.format("Checking %d combinations …", matcher.remainingCandidates().size() * matcher.remainingValues().size()));
			return matcher.match();
		}, matches -> {
			setEnabled(true);
			if (matches == null) {
				return;
			}

			// put new data into model
			model.clear();
			model.addAll(matches);

			// insert objects that could not be matched at the end of the model
			model.addAll(matcher.remainingValues(), matcher.remainingCandidates());
		}, error -> {
			setEnabled(true);
			log.log(Level.WARNING, error.getMessage(), error);
		});
	}

}
