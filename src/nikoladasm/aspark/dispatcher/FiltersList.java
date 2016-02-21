package nikoladasm.aspark.dispatcher;

import static nikoladasm.aspark.ASparkUtil.isAcceptContentType;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.regex.Matcher;

import nikoladasm.common.FilterableWrapper;

public class FiltersList implements Iterable<Filter> {

	public static class FilterConfig {
		public String path;
		public String acceptType;
		public Matcher parameterMatcher;
	}
	
	public static final Function<Filter,Boolean> DEFAULT_FILTER = (filter) -> true;

	private ConcurrentLinkedQueue<Filter> filters;
	
	public static FilterConfig createConfig(String path, String acceptType) {
		FilterConfig config = new FilterConfig();
		config.path = path;
		config.acceptType = acceptType;
		return config;
	}
	
	public static Function<Filter,Boolean> filter(FilterConfig config) {
		return filter -> {
			config.parameterMatcher = filter.pathPattern().matcher(config.path);
			return isAcceptContentType(config.acceptType, filter.acceptedType()) &&
				config.parameterMatcher.matches();
		};
	}
	
	public FiltersList() {
		filters = new ConcurrentLinkedQueue<>();
	}
	
	public void addLast(Filter filter) {
		filters.add(filter);
	}

	public void clear() {
		filters.clear();
	}
	
	@Override
	public Iterator<Filter> iterator() {
		return filters.iterator();
	}

	public Iterable<Filter> filteredList(Function<Filter,Boolean> filter) {
		return new FilterableWrapper<>(filters, filter);
	}
}
