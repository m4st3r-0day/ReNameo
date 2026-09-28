package net.renameo.ui;

/**
 * Messages exchanged between the main window chrome and the active panel via {@link net.renameo.util.ui.SwingEventBus}.
 */
public final class AppEvents {

	private AppEvents() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Posted by the global search field. {@code next} is set when the user asks for the next hit (Enter).
	 */
	public static class Search {

		public final String query;
		public final boolean next;

		public Search(String query, boolean next) {
			this.query = query;
			this.next = next;
		}
	}

	/**
	 * Short, non-modal notification shown in the corner of the main window.
	 */
	public static class Toast {

		public final String title;
		public final String detail;
		public final Status.Kind kind;

		public Toast(String title, String detail, Status.Kind kind) {
			this.title = title;
			this.detail = detail;
			this.kind = kind;
		}
	}

	/**
	 * Posted by panels to update the status indicator in the top bar.
	 */
	public static class Status {

		public enum Kind {
			READY, BUSY, WARNING
		}

		public final String text;
		public final Kind kind;

		public Status(String text, Kind kind) {
			this.text = text;
			this.kind = kind;
		}
	}

}
