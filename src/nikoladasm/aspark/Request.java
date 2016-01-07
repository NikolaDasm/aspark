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
import java.util.Set;

import nikoladasm.sattributemap.*;

public interface Request extends SAttributeMap {

	Map<String, String> params();
	String params(String param);
	ParamsMap paramsMap();
	ParamsMap paramsMap(String name);
	String[] splat();
	String requestMethod();
	String method();
	String host();
	String userAgent();
	int port();
	String pathInfo();
	String contentType();
	String ip();
	String body();
	<T> T body(RequestTransformer<T> transformer) throws Exception;
	byte[] bodyAsBytes();
	int contentLength();
	String queryParams(String queryParam);
	String[] queryParamsValues(String queryParam);
	Set<String> queryParams();
	String queryString();
	ParamsMap queryMap();
	ParamsMap queryMap(String key);
	String postParams(String postParam);
	String[] postParamsValues(String postParam);
	Set<String> postParams();
	ParamsMap postMap();
	ParamsMap postMap(String key);
	String headers(String header);
	Set<String> headers();
	Map<String, String> cookies();
	String cookie(String name);
	long cookieMaxAge(String name);
	String protocol();
}