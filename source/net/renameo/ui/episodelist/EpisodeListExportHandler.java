package net.renameo.ui.episodelist;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.List;

import javax.swing.JComponent;

import net.renameo.ui.ReNameoList;
import net.renameo.ui.ReNameoListExportHandler;
import net.renameo.ui.transfer.ArrayTransferable;
import net.renameo.ui.transfer.ClipboardHandler;
import net.renameo.ui.transfer.CompositeTranserable;
import net.renameo.util.StringUtilities;
import net.renameo.web.Episode;

class EpisodeListExportHandler extends ReNameoListExportHandler<Episode> implements ClipboardHandler {

	public EpisodeListExportHandler(ReNameoList<Episode> list) {
		super(list);
	}

	@Override
	public Transferable createTransferable(JComponent c) {
		Transferable episodeArray = export(list, true);
		Transferable textFile = super.createTransferable(c);

		return new CompositeTranserable(episodeArray, textFile);
	}

	@Override
	public void exportToClipboard(JComponent c, Clipboard clipboard, int action) throws IllegalStateException {
		ArrayTransferable<Episode> episodeData = export(list, false);
		StringSelection stringSelection = new StringSelection(StringUtilities.join(episodeData.getArray(), System.lineSeparator()));

		clipboard.setContents(new CompositeTranserable(episodeData, stringSelection), null);
	}

	public ArrayTransferable<Episode> export(ReNameoList<?> list, boolean forceAll) {
		Episode[] selection = ((List<?>) list.getListComponent().getSelectedValuesList()).stream().map(Episode.class::cast).toArray(Episode[]::new);

		if (forceAll || selection.length == 0) {
			selection = list.getModel().stream().map(Episode.class::cast).toArray(Episode[]::new);
		}

		return new ArrayTransferable<Episode>(selection);
	}

}
