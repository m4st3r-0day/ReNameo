package net.renameo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@FunctionalInterface
public interface Resource<R> {

	R get() throws Exception;

	default Resource<R> memoize() {
		return new MemoizedResource<R>(this);
	}

	default <T> Resource<T> transform(Function<R, T> function) {
		return new TransformedResource<R, T>(this, function);
	}

	static <T> Resource<T> lazy(Resource<T> resource) {
		return resource.memoize();
	}

	/**
	 * Make every memoized resource compute its value again on next use, e.g. after the offline index was updated.
	 */
	static void invalidateAll() {
		MemoizedResource.GENERATION.incrementAndGet();
	}

}

class MemoizedResource<R> implements Resource<R> {

	static final AtomicInteger GENERATION = new AtomicInteger();

	private final Resource<R> resource;
	private R value;
	private int generation;

	public MemoizedResource(Resource<R> resource) {
		this.resource = resource;
	}

	@Override
	public synchronized R get() throws Exception {
		int current = GENERATION.get();
		if (value == null || generation != current) {
			value = resource.get();
			generation = current;
		}
		return value;
	}
}

class TransformedResource<R, T> implements Resource<T> {

	private final Resource<R> resource;
	private final Function<R, T> function;

	public TransformedResource(Resource<R> resource, Function<R, T> function) {
		this.resource = resource;
		this.function = function;
	}

	@Override
	public T get() throws Exception {
		return function.apply(resource.get());
	}

}
