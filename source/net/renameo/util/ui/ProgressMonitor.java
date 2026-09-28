package net.renameo.util.ui;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.SwingWorker;

/**
 * Runs a long task in the background and shows its progress in the main window ({@link ProgressLayer}). Results are delivered on the event dispatch thread.
 */
public class ProgressMonitor {

	/**
	 * @param done
	 *            receives the result, or null if the task was cancelled
	 * @param error
	 *            receives the failure (never a cancellation)
	 */
	public static <T> void runTask(String title, String header, ProgressWorker<T> worker, Consumer<T> done, Consumer<Throwable> error) {
		ProgressLayer layer = ProgressLayer.getMain();
		AtomicBoolean cancelled = new AtomicBoolean(false);

		SwingWorker<T, Object[]> task = new SwingWorker<T, Object[]>() {

			@Override
			protected T doInBackground() throws Exception {
				return worker.call(message -> publish(new Object[] { message, -1d }), (value, total) -> publish(new Object[] { null, total > 0 ? (double) value / total : -1d }), cancelled::get);
			}

			@Override
			protected void process(List<Object[]> chunks) {
				if (layer != null) {
					for (Object[] it : chunks) {
						layer.update((String) it[0], (Double) it[1]);
					}
				}
			}

			@Override
			protected void done() {
				if (layer != null) {
					layer.finish();
				}
				try {
					T result = get();
					done.accept(cancelled.get() ? null : result);
				} catch (ExecutionException e) {
					if (e.getCause() instanceof CancellationException) {
						done.accept(null);
					} else {
						error.accept(e.getCause());
					}
				} catch (Exception e) {
					error.accept(e);
				}
			}
		};

		if (layer != null) {
			layer.start(title, header, () -> cancelled.set(true));
		}
		task.execute();
	}

	@FunctionalInterface
	public interface ProgressWorker<T> {
		T call(Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) throws Exception;
	}

	private ProgressMonitor() {
		throw new UnsupportedOperationException();
	}

}
