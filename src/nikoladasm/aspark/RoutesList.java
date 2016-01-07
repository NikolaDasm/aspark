package nikoladasm.aspark;

import static nikoladasm.aspark.ASparkUtil.isAcceptContentType;
import static nikoladasm.aspark.ASparkUtil.isEqualHttpMethod;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.regex.Matcher;

import nikoladasm.common.FilterableWrapper;

public class RoutesList implements Iterable<Route> {

	public static class FilterConfig {
		public String path;
		public String acceptType;
		public HttpMethod requestMethod;
		public Matcher parameterMatcher;
	}
	
	public static final Function<Route,Boolean> DEFAULT_FILTER = (filter) -> true;

	private ConcurrentLinkedQueue<Route> routes;
	
	public static FilterConfig createConfig(String path, String acceptType, HttpMethod requestMethod) {
		FilterConfig config = new FilterConfig();
		config.path = path;
		config.acceptType = acceptType;
		config.requestMethod = requestMethod;
		return config;
	}
	
	public static Function<Route,Boolean> filter(FilterConfig config) {
		return route -> {
			config.parameterMatcher = route.pathPattern().matcher(config.path);
			return isEqualHttpMethod(config.requestMethod, route.httpMethod()) &&
				isAcceptContentType(config.acceptType, route.acceptedType()) &&
				config.parameterMatcher.matches();
		};
	}
	
	public RoutesList() {
		routes = new ConcurrentLinkedQueue<>();
	}
	
	public void addLast(Route route) {
		routes.add(route);
	}

	public void clear() {
		routes.clear();
	}
	
	@Override
	public Iterator<Route> iterator() {
		return routes.iterator();
	}

	public Iterable<Route> filteredList(Function<Route,Boolean> filter) {
		return new FilterableWrapper<>(routes, filter);
	}
}
