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

import java.util.Map;
import java.util.regex.Pattern;

public final class Route {
	private HttpMethod httpMethod;
	private Pattern pathPattern;
	private Map<String, Integer> parameterNamesMap;
	private String acceptedType;
	private RouteHandler handler;
	private ResponseTransformer responseTransformer;
	
	public Route(HttpMethod httpMethod,
			Pattern pathPattern,
			Map<String, Integer> parameterNamesMap,
			String acceptedType,
			RouteHandler handler,
			ResponseTransformer responseTransformer) {
		this.httpMethod = httpMethod;
		this.pathPattern = pathPattern;
		this.parameterNamesMap = parameterNamesMap;
		this.acceptedType = acceptedType;
		this.handler = handler;
		this.responseTransformer = responseTransformer;
	}

	public HttpMethod httpMethod() {
		return httpMethod;
	}

	public Pattern pathPattern() {
		return pathPattern;
	}

	public Map<String, Integer> parameterNamesMap() {
		return parameterNamesMap;
	}

	public String acceptedType() {
		return acceptedType;
	}

	public RouteHandler handler() {
		return handler;
	}
	
	public ResponseTransformer responseTransformer() {
		return responseTransformer;
	}
}
