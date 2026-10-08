package net.renameo.ui;

import static net.renameo.Logging.*;

import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import net.renameo.ApiKeys;
import net.renameo.WebServices;
import net.renameo.media.MediaDetection;
import net.renameo.media.ReleaseInfo;
import net.renameo.util.ui.ProgressMonitor;
import net.renameo.util.ui.SwingEventBus;
import net.renameo.web.TMDbIndexBuilder;

/**
 * Refresh the offline title index from TheMovieDB, in English plus the language of the system.
 */
public final class OfflineIndexAction {

	private OfflineIndexAction() {
		throw new UnsupportedOperationException();
	}

	public static void run(Window owner) {
		if (ApiKeys.isMissing(ApiKeys.Service.TMDB)) {
			ApiKeysDialog.show(owner, false);
			return;
		}

		List<Locale> languages = new ArrayList<Locale>();
		languages.add(Locale.US);
		Locale system = Locale.getDefault();
		if (!system.getLanguage().isEmpty() && !system.getLanguage().equals("en")) {
			languages.add(system.getCountry().isEmpty() ? Locale.forLanguageTag(system.getLanguage()) : system);
		}

		ProgressMonitor.runTask("Updating offline index", "TheMovieDB", (message, progress, cancelled) -> {
			return new TMDbIndexBuilder(WebServices.TheMovieDB).build(ReleaseInfo.getUserDataFile("x").getParentFile(), languages, progress, cancelled);
		}, result -> {
			if (result != null) {
				// use the new index right away
				MediaDetection.reloadOfflineIndex();
				String detail = String.format("%d series · %d movies", result.series, result.movies);
				SwingEventBus.getInstance().post(new AppEvents.Toast("Offline index updated", detail, AppEvents.Status.Kind.READY));
			}
		}, error -> {
			log.log(Level.WARNING, "Failed to update the offline index: " + error.getMessage(), error);
		});
	}

}
