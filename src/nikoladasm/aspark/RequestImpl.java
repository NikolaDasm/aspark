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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;

import nikoladasm.aspark.HttpMethod;
import nikoladasm.commons.dydamictypedmap.*;

import static nikoladasm.aspark.ASparkUtil.*;

public class RequestImpl implements Request {
	
	private FullHttpRequest request;
	private Map<String, String> params;
	private Map<String, Integer> parameterNamesMap;
	private ParamsMap paramsMap;
	private Boolean startWithWildcard;
	private Matcher parameterMatcher;
	private HttpMethod originalMethod;
	private HttpMethod method;
	private Map<String, List<String>> postAttr;
	private String path;
	private byte[] bodyAsBytes;
	private QueryStringDecoder queryStringDecoder;
	private Set<String> headers;
	private ParamsMap queryMap;
	private ParamsMap postMap;
	private Map<String, Cookie> fullCookies;
	private Map<String, String> cookies;
	private DydamicTypedMap attributeMap;
	private HttpHeaders nettyHeaders;
	private int port;
	private String ipAddress;
	private HttpVersion version;
	private String newPath;
	
	public RequestImpl(FullHttpRequest request,
			QueryStringDecoder queryStringDecoder,
			HttpMethod originalMethod,
			HttpMethod method,
			Map<String, List<String>> postAttr,
			String path,
			int port,
			String ipAddress,
			HttpVersion version) {
		this.request = request;
		this.queryStringDecoder = queryStringDecoder;
		this.originalMethod = originalMethod;
		this.method = method;
		this.postAttr = postAttr;
		this.nettyHeaders = request.headers();
		this.path = path;
		this.port = port;
		this.ipAddress = ipAddress;
		this.version = version;
		readBodyAsBytes();
	}
	
	public void parameterNamesMap(Map<String, Integer> parameterNamesMap) {
		this.parameterNamesMap = parameterNamesMap;
	}
	
	public void startWithWildcard(Boolean startWithWildcard) {
		this.startWithWildcard = startWithWildcard;
	}
	
	public void parameterMatcher(Matcher parameterMatcher) {
		this.parameterMatcher = parameterMatcher;
	}
	
	private int parameterIndex(int index) {
		return startWithWildcard ? index+1 : index;
	}
	
	@Override
	public Map<String, String> params() {
		if (params != null) return params;
		params = new HashMap<>();
		parameterNamesMap.forEach((name, index) -> {
			params.put(name, parameterMatcher.group(parameterIndex(index)));
		});
		return params;
	}
	
	@Override
	public String params(String param) {
		String name = (param.startsWith(":")) ? param : ":"+param; 
		if (params != null) return params.get(name);
		Integer index = parameterNamesMap.get(name);
		if (index == null) return null;
		return parameterMatcher.group(parameterIndex(index));
	}
	
	@Override
	public ParamsMap paramsMap() {
		initParamsMap();
		return paramsMap;
	}
	
	@Override
	public ParamsMap paramsMap(String param) {
		initParamsMap();
		String name = (param.startsWith(":")) ? param : ":"+param; 
		return paramsMap.get(name);
	}
	
	private void initParamsMap() {
		if (paramsMap == null) {
			paramsMap = parseUniqueParams(params());
		}
	}
	
	@Override
	public String[] splat() {
		if (startWithWildcard && parameterMatcher.group(0) != null)
			return parameterMatcher.group(0).split("/");
		if (parameterMatcher.groupCount() > parameterNamesMap.size())
			return parameterMatcher.group(parameterNamesMap.size()+1).split("/");
		else
			return null;
	}
	
	@Override
	public HttpMethod originalMethod() {
		return originalMethod;
	}
	
	@Override
	public HttpMethod method() {
		return method;
	}
	
	@Override
	public String host() {
		return nettyHeaders.get(HOST);
	}
	
	@Override
	public String userAgent() {
		return nettyHeaders.get(USER_AGENT);
	}
	
	@Override
	public int port() {
		return port;
	}
	
	@Override
	public String pathInfo() {
		return path;
	}
	
	@Override
	public String contentType() {
		return nettyHeaders.get(CONTENT_TYPE);
	}
	
	@Override
	public String acceptType() {
		return nettyHeaders.get(ACCEPT);
	}
	
	@Override
	public String ip() {
		return ipAddress;
	}
	
	@Override
	public String body() {
		return new String(bodyAsBytes, UTF_8);
	}
	
	@Override
	public <T> T body(RequestTransformer<T> transformer) throws Exception {
		return transformer.transform(bodyAsBytes);
	}
	
	@Override
	public byte[] bodyAsBytes() {
		return bodyAsBytes;
	}
	
	private void readBodyAsBytes() {
		bodyAsBytes = new byte[request.content().readableBytes()];
		request.content().readBytes(bodyAsBytes);
	}
	
	@Override
	public int contentLength() {
		return Integer.valueOf(nettyHeaders.get(CONTENT_LENGTH));
	}
	
	@Override
	public String queryParams(String queryParam) {
		List<String> valueList = queryStringDecoder.parameters().get(queryParam);
		return (valueList == null) ? null : valueList.get(0);
	}
	
	@Override
	public String[] queryParamsValues(String queryParam) {
		List<String> valueList = queryStringDecoder.parameters().get(queryParam);
		return (valueList == null) ? null : valueList.toArray(new String[valueList.size()]);
	}
	
	@Override
	public Set<String> queryParams() {
		return queryStringDecoder.parameters().keySet();
	}
	
	@Override
	public String queryString() {
		String uri = queryStringDecoder.uri();
		int beginQuery = uri.indexOf('?');
		if (beginQuery != -1)
			return uri.substring(beginQuery+1);
		return "";
	}
	
	@Override
	public ParamsMap queryMap() {
		initQueryMap();
		return queryMap;
	}
	
	@Override
	public ParamsMap queryMap(String key) {
		initQueryMap();
		return queryMap().get(key);
	}
	
	private void initQueryMap() {
		if (queryMap == null)
			queryMap = parseParams(queryStringDecoder.parameters());
	}
	
	@Override
	public String postParams(String postParam) {
		List<String> valueList = postAttr.get(postParam);
		return (valueList == null) ? null : valueList.get(0);
	}
	
	@Override
	public String[] postParamsValues(String postParam) {
		List<String> valueList = postAttr.get(postParam);
		return (valueList == null) ? null : valueList.toArray(new String[valueList.size()]);
	}
	
	@Override
	public Set<String> postParams() {
		return postAttr.keySet();
	}
	
	@Override
	public ParamsMap postMap() {
		initPostMap();
		return postMap;
	}
	
	@Override
	public ParamsMap postMap(String key) {
		initPostMap();
		return postMap().get(key);
	}
	
	private void initPostMap() {
		if (postMap == null)
			postMap = parseParams(postAttr);
	}
	
	@Override
	public String headers(String header) {
		return nettyHeaders.get(header);
	}
	
	@Override
	public Set<String> headers() {
		if (headers == null) {
			headers = new HashSet<>();
			nettyHeaders.forEach((entry) -> {
				headers.add(entry.getKey());
			});
		}
		return headers;
	}
	
	@Override
	public Map<String, String> cookies() {
		readCookie();
		return cookies;
	}
	
	@Override
	public String cookie(String name) {
		readCookie();
		return cookies.get(name);
	}
	
	@Override
	public long cookieMaxAge(String name) {
		readCookie();
		return fullCookies.get(name).maxAge();
	}
	
	private void readCookie() {
		if (cookies != null) return;
		fullCookies = new HashMap<>();
		cookies = new HashMap<>();
		String cookieString = nettyHeaders.get(COOKIE);
		if (cookieString != null) {
			Set<Cookie> cookies = ServerCookieDecoder.LAX.decode(cookieString);
			if (!cookies.isEmpty()) {
				for (Cookie cookie: cookies) {
					fullCookies.put(cookie.name(), cookie);
					this.cookies.put(cookie.name(), cookie.value());
				}
			}
			
		}
	}
	
	@Override
	public String protocol() {
		return version.text();
	}

	@Override
	public <T> DydamicTypedValue<T> value(DydamicTypedKey<T> key) {
		if (attributeMap == null)
			attributeMap = new DefaultDydamicTypedMap();
		return attributeMap.value(key);
	}

	@Override
	public void clear() {
		if (attributeMap != null)
			attributeMap.clear();
	}

	@Override
	public <T> boolean containsKey(DydamicTypedKey<T> key) {
		return (attributeMap == null) ? false : attributeMap.containsKey(key);
	}

	@Override
	public <T> void remove(DydamicTypedKey<T> key) {
		if (attributeMap != null)
			attributeMap.value(key).remove();
	}

	@Override
	public void rewrite(String newPath) {
		this.newPath = newPath;
	}
	
	public void path(String path) {
		this.path = path;
	}
	
	public String rewritePath() {
		return newPath;
	}
	
	@Override
	public String[] authorizationBasic() {
		String authorization = nettyHeaders.get(AUTHORIZATION);
		if (authorization == null) return null;
		String[] values = authorization.trim().split(" ");
		if (values.length != 2) return null;
		if ("Basic".equalsIgnoreCase(values[0].trim())) return null;
		String userPass = new String(Base64.getDecoder().decode(values[1].trim()), UTF_8);
		int delimiterIndex = userPass.indexOf(':');
		if (delimiterIndex < 0) return null;
		return new String[]{userPass.substring(0, delimiterIndex), userPass.substring(delimiterIndex+1)};
	}
}
