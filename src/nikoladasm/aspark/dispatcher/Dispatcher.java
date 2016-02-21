package nikoladasm.aspark.dispatcher;

import static io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaders.Names.IF_MODIFIED_SINCE;
import static io.netty.handler.codec.http.HttpHeaders.Names.LAST_MODIFIED;
import static nikoladasm.aspark.ASparkUtil.isEqualHttpMethod;
import static nikoladasm.aspark.ASparkUtil.mimeType;
import static nikoladasm.aspark.HttpMethod.GET;
import static nikoladasm.aspark.Routable.DEFAULT_RESPONSE_TRANSFORMER;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import nikoladasm.aspark.HttpMethod;
import nikoladasm.aspark.RequestImpl;
import nikoladasm.aspark.ResponseImpl;
import nikoladasm.aspark.dispatcher.StaticResourceLocation.StaticResource;

public class Dispatcher {

	private static final int HTTP_CACHE_SECONDS = 60;

	private RoutesList routes;
	private FiltersList before;
	private FiltersList after;
	private StaticResourceLocation location;
	private StaticResourceLocation externalLocation;
	private Properties mimeTypes;

	public Dispatcher(
			RoutesList routes,
			FiltersList before,
			FiltersList after,
			StaticResourceLocation location,
			StaticResourceLocation externalLocation,
			Properties mimeTypes) {
		this.routes = routes;
		this.before = before;
		this.after = after;
		this.location = location;
		this.externalLocation = externalLocation;
		this.mimeTypes = (mimeTypes == null) ? new Properties() : mimeTypes;
	}
	
	public Dispatcher(Properties mimeTypes) {
		this(new RoutesList(), new FiltersList(), new FiltersList(), null, null, mimeTypes);
	}
	
	public Dispatcher() {
		this(new RoutesList(), new FiltersList(), new FiltersList(), null, null, null);
	}
	
	public RoutesList routes() {
		return routes;
	}
	
	public FiltersList before() {
		return before;
	}
	
	public FiltersList after() {
		return after;
	}
	
	public void mimeTypes(Properties mimeTypes) {
		this.mimeTypes = mimeTypes;
	}
	
	public void location(StaticResourceLocation location) {
		this.location = location;
	}
	
	public StaticResourceLocation location() {
		return location;
	}
	
	public void externalLocation(StaticResourceLocation externalLocation) {
		this.externalLocation = externalLocation;
	}
	
	public StaticResourceLocation externalLocation() {
		return externalLocation;
	}
	
	public boolean process(
			RequestImpl request,
			ResponseImpl response) throws Exception {
		String acceptType = request.acceptType();
		HttpMethod httpMethod = request.method();
		String path = request.pathInfo();
		FiltersList.FilterConfig config = FiltersList.createConfig(path, acceptType);
		for (Filter filter : before.filteredList(FiltersList.filter(config))) {
			request.parameterNamesMap(filter.parameterNamesMap());
			request.startWithWildcard(filter.startWithWildcard());
			request.parameterMatcher(config.parameterMatcher);
			filter.handler().handle(request, response);
		}
		boolean routeFound =
			processRoutes(
					request,
					response);
		if (!routeFound) {
			routeFound = processStaticResources(
					request,
					response,
					httpMethod);
		}
		for (Filter filter : after.filteredList(FiltersList.filter(config))) {
			request.parameterNamesMap(filter.parameterNamesMap());
			request.startWithWildcard(filter.startWithWildcard());
			request.parameterMatcher(config.parameterMatcher);
			filter.handler().handle(request, response);
		}
		if (!routeFound) {
			response.transformer(DEFAULT_RESPONSE_TRANSFORMER);
			response.status(404);
		}
		return routeFound;
	}
	
	private boolean processRoutes(
			RequestImpl request,
			ResponseImpl response) throws Exception {
		String acceptType = request.acceptType();
		HttpMethod httpMethod = request.method();
		String path = request.pathInfo();
		do {
			request.rewrite(null);
			RoutesList.FilterConfig config = RoutesList.createConfig(path, acceptType, httpMethod);
			for (Route route : routes.filteredList(RoutesList.filter(config))) {
				request.parameterNamesMap(route.parameterNamesMap());
				request.startWithWildcard(route.startWithWildcard());
				request.parameterMatcher(config.parameterMatcher);
				response.transformer(route.responseTransformer());
				Object body = route.handler().handle(request, response);
				response.body(body);
				if (request.rewritePath() != null) {
					path = request.rewritePath();
					request.path(path);
					break;
				}
				return true;
			}
		} while (request.rewritePath() != null);
		return false;
	}
	
	private boolean processStaticResources(
			RequestImpl request,
			ResponseImpl response,
			HttpMethod requestMethod) throws IOException {
		if (!isEqualHttpMethod(requestMethod, GET))
			return false;
		if (this.location != null) {
			StaticResource resource = this.location.getClassResource(request.pathInfo());
			if (resource.stream() != null) {
				response.inputStream(resource.stream());
				response.header(CONTENT_TYPE, mimeType(resource.fullPath(), mimeTypes));
				return true;
			}
		}
		if (this.externalLocation != null) {
			StaticResource resource = this.externalLocation.getFileResource(request.pathInfo());
			if (resource.stream() == null) return false;
			File file = new File(resource.fullPath());
			String ifModifiedSince = request.headers(IF_MODIFIED_SINCE);
			if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
				long ifModifiedSinceDateSeconds =
					ZonedDateTime.parse(ifModifiedSince, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond();
				long fileLastModifiedSeconds = file.lastModified() / 1000;
				if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
					response.transformer(DEFAULT_RESPONSE_TRANSFORMER);
					response.status(304);
					return true;
				}
			}
			response.inputStream(resource.stream());
			response.header(CONTENT_TYPE, mimeType(resource.fullPath(), mimeTypes));
			final String cacheExpires =
				DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(HTTP_CACHE_SECONDS));
			response.header(EXPIRES, cacheExpires);
			response.header(CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
			final String lastModified =
				DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT")));
			response.header(LAST_MODIFIED, lastModified);
			return true;
		}
		return false;
	}
}
