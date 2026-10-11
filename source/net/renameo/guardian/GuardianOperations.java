package net.renameo.guardian;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.renameo.RenameAction;
import net.renameo.cli.CmdlineOperations;
import net.renameo.cli.ConflictAction;
import net.renameo.cli.ExecCommand;
import net.renameo.similarity.Match;
import net.renameo.util.FileUtilities;

/**
 * The command line renaming, with two additions for the guardian: plan without touching anything, and remember every move that really happened (folders that
 * follow included), so each correction can be undone.
 */
class GuardianOperations extends CmdlineOperations {

	private final boolean planOnly;

	/** planned renames: file => absolute new path */
	final Map<File, File> planned = new LinkedHashMap<File, File>();

	/** moves that happened: old path => new path */
	final Map<File, File> moved = new LinkedHashMap<File, File>();

	GuardianOperations(boolean planOnly) throws Exception {
		this.planOnly = planOnly;
	}

	@Override
	protected List<File> renameAll(Map<File, File> renameMap, RenameAction renameAction, ConflictAction conflictAction, List<Match<File, ?>> matches, ExecCommand exec) throws Exception {
		if (planOnly) {
			renameMap.forEach((from, to) -> planned.put(from, FileUtilities.resolve(from, to)));
			return new ArrayList<File>(planned.values());
		}
		return super.renameAll(renameMap, renameAction, conflictAction, matches, exec);
	}

	@Override
	protected void writeHistory(RenameAction action, Map<File, File> log, List<Match<File, ?>> matches) {
		moved.putAll(log);
		super.writeHistory(action, log, matches);
	}

}
