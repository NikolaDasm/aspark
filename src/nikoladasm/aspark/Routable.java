/*
 *  ASpark
 *  Copyright (C) 2015  Nikolay Platov
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nikoladasm.aspark;

import static java.util.Objects.requireNonNull;
import static nikoladasm.aspark.HttpMethod.*;
import static java.nio.charset.StandardCharsets.UTF_8;

abstract class Routable {

	private static final String DEFAULT_ACCEPT_TYPE = "*/*";
	private static final String ALL_PATHS = "/*";

	public static final RequestTransformer<String> DEFAULT_REQUEST_TRANSFORMER =
		body -> new String(body, UTF_8);
		
	public static final ResponseTransformer DEFAULT_RESPONSE_TRANSFORMER =
		body -> body;
		
	public static final ViewEngine DEFAULT_VIEW_ENGINE =
		(modelAndView) -> modelAndView.render();
			
	private volatile ResponseTransformer defaultResponseTransformer =
		DEFAULT_RESPONSE_TRANSFORMER;
	
	private volatile String defaultAcceptedType = DEFAULT_ACCEPT_TYPE;
	
	public synchronized void defaultResponseTransformer(ResponseTransformer responseTransformer) {
		requireNonNull(responseTransformer,"Response transformer can't be null");
		defaultResponseTransformer = responseTransformer;
	}
	
	public synchronized ResponseTransformer defaultResponseTransformer() {
		return defaultResponseTransformer;
	}
	
	public synchronized void defaultAcceptType(String acceptedType) {
		requireNonNull(acceptedType,"Accepted type can't be null");
		defaultAcceptedType = acceptedType;
	}
	
	public synchronized String defaultAcceptedType() {
		return defaultAcceptedType;
	}
	
	public abstract void addRoute(HttpMethod httpMethod,
			String path,
			String acceptedType,
			RouteHandler handler,
			ResponseTransformer responseTransformer);
	
	public abstract void addFilter(boolean before,
			String path,
			String acceptedType,
			FilterHandler handler);
	
	public void before(FilterHandler handler) {
		addFilter(true, ALL_PATHS, defaultAcceptedType, handler);
	}

	public void before(String path, FilterHandler handler) {
		addFilter(true, path, defaultAcceptedType, handler);
	}
	
	public void before(String path, String acceptType, FilterHandler handler) {
		addFilter(true, path, acceptType, handler);
	}

	public void after(FilterHandler handler) {
		addFilter(false, ALL_PATHS, defaultAcceptedType, handler);
	}

	public void after(String path, FilterHandler handler) {
		addFilter(false, path, defaultAcceptedType, handler);
	}
	
	public void after(String path, String acceptType, FilterHandler handler) {
		addFilter(false, path, acceptType, handler);
	}

	public void get(String path, RouteHandler handler) {
		addRoute(GET,
				path,
				defaultAcceptedType,
				handler,
				defaultResponseTransformer);
	}

	public void get(String path, String acceptType, RouteHandler handler) {
		addRoute(GET,
				path,
				acceptType,
				handler,
				defaultResponseTransformer);
	}

	public void get(String path, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(GET,
				path,
				defaultAcceptedType,
				handler,
				transformer);
	}

	public void get(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(GET,
				path,
				acceptType,
				handler,
				transformer);
	}

	public void post(String path, RouteHandler handler) {
		addRoute(POST,
				path,
				defaultAcceptedType,
				handler,
				defaultResponseTransformer);
	}
	
	public void post(String path, String acceptType, RouteHandler handler) {
		addRoute(POST,
				path,
				acceptType,
				handler,
				defaultResponseTransformer);
	}

	public void post(String path, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(POST,
				path,
				defaultAcceptedType,
				handler,
				transformer);
	}

	public void post(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(POST,
				path,
				acceptType,
				handler,
				transformer);
	}

	public void put(String path, RouteHandler handler) {
		addRoute(PUT,
				path,
				defaultAcceptedType,
				handler,
				defaultResponseTransformer);
	}

	public void put(String path, String acceptType, RouteHandler handler) {
		addRoute(PUT,
				path,
				acceptType,
				handler,
				defaultResponseTransformer);
	}

	public void put(String path, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(PUT,
				path,
				defaultAcceptedType,
				handler,
				transformer);
	}

	public void put(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(PUT,
				path,
				acceptType,
				handler,
				transformer);
	}

	public void delete(String path, RouteHandler handler) {
		addRoute(DELETE,
				path,
				defaultAcceptedType,
				handler,
				defaultResponseTransformer);
	}

	public void delete(String path, String acceptType, RouteHandler handler) {
		addRoute(DELETE,
				path,
				acceptType,
				handler,
				defaultResponseTransformer);
	}

	public void delete(String path, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(DELETE,
				path,
				defaultAcceptedType,
				handler,
				transformer);
	}

	public void delete(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(DELETE,
				path,
				acceptType,
				handler,
				transformer);
	}

	public void head(String path, RouteHandler handler) {
		addRoute(HEAD,
				path,
				defaultAcceptedType,
				handler,
				defaultResponseTransformer);
	}

	public void head(String path, String acceptType, RouteHandler handler) {
		addRoute(HEAD,
				path,
				acceptType,
				handler,
				defaultResponseTransformer);
	}

	public void head(String path, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(HEAD,
				path,
				defaultAcceptedType,
				handler,
				transformer);
	}

	public void head(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(HEAD,
				path,
				acceptType,
				handler,
				transformer);
	}

	public void connect(String path, RouteHandler handler) {
		addRoute(CONNECT,
				path,
				defaultAcceptedType,
				handler,
				defaultResponseTransformer);
	}

	public void connect(String path, String acceptType, RouteHandler handler) {
		addRoute(CONNECT,
				path,
				acceptType,
				handler,
				defaultResponseTransformer);
	}

	public void connect(String path, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(CONNECT,
				path,
				defaultAcceptedType,
				handler,
				transformer);
	}

	public void connect(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(CONNECT,
				path,
				acceptType,
				handler,
				transformer);
	}

	public void trace(String path, RouteHandler handler) {
		addRoute(TRACE,
				path,
				defaultAcceptedType,
				handler,
				defaultResponseTransformer);
	}

	public void trace(String path, String acceptType, RouteHandler handler) {
		addRoute(TRACE,
				path,
				acceptType,
				handler,
				defaultResponseTransformer);
	}

	public void trace(String path, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(TRACE,
				path,
				defaultAcceptedType,
				handler,
				transformer);
	}

	public void trace(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(TRACE,
				path,
				acceptType,
				handler,
				transformer);
	}

	public void options(String path, RouteHandler handler) {
		addRoute(OPTIONS,
				path,
				defaultAcceptedType,
				handler,
				defaultResponseTransformer);
	}

	public void options(String path, String acceptType, RouteHandler handler) {
		addRoute(OPTIONS,
				path,
				acceptType,
				handler,
				defaultResponseTransformer);
	}

	public void options(String path, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(OPTIONS,
				path,
				defaultAcceptedType,
				handler,
				transformer);
	}

	public void options(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(OPTIONS,
				path,
				acceptType,
				handler,
				transformer);
	}

	public void patch(String path, RouteHandler handler) {
		addRoute(PATCH,
				path,
				defaultAcceptedType,
				handler,
				defaultResponseTransformer);
	}

	public void patch(String path, String acceptType, RouteHandler handler) {
		addRoute(PATCH,
				path,
				acceptType,
				handler,
				defaultResponseTransformer);
	}

	public void patch(String path, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(PATCH,
				path,
				defaultAcceptedType,
				handler,
				transformer);
	}

	public void patch(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		addRoute(PATCH,
				path,
				acceptType,
				handler,
				transformer);
	}
}
