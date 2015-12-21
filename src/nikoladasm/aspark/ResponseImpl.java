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

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http.FullHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;

import static nikoladasm.aspark.ASparkUtil.*;
import static nikoladasm.aspark.HttpMethod.*;

public class ResponseImpl implements Response {
	
	private HttpResponseStatus status;
	private ChannelHandlerContext ctx;
	private HttpVersion version;
	private Map<String,String> headers;
	private Object body;
	private ResponseTransformer transformer;
	private boolean keepAlive;
	private Map<String, Cookie> cookies;
	private InputStream stream;
	private HttpMethod httpMethod;
	
	ResponseImpl(ChannelHandlerContext ctx,
			HttpVersion version,
			boolean keepAlive,
			HttpMethod httpMethod) {
		this.ctx = ctx;
		this.version = version;
		this.keepAlive = keepAlive;
		status = HttpResponseStatus.valueOf(200);
		headers = new HashMap<>();
		cookies = new HashMap<>();
		this.httpMethod = httpMethod;
	}
	
	public void transformer(ResponseTransformer transformer) {
		this.transformer = transformer;
	}
	
	public void inputStream(InputStream stream) {
		this.stream = stream;
	}

	public void send() throws Exception {
		FullHttpResponse response =
			new DefaultFullHttpResponse(version, status);
		headers.forEach((key, value) ->
			response.headers().add(key, value));
		cookies.forEach((name, cookie) ->
			response.headers().add(SET_COOKIE, ServerCookieEncoder.LAX.encode(cookie)));
		if(!headers.containsKey(CONTENT_TYPE))
			response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
		if (stream != null)
			sendStream(response);
		else
			sendByteArray(response);
		response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
		if (httpMethod.equals(HEAD))
			response.content().clear();
		if (!keepAlive) {
			ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
		} else {
			response.headers().set(CONNECTION, KEEP_ALIVE);
			ctx.writeAndFlush(response);
		}
	}
	
	private void sendByteArray(FullHttpResponse response) throws Exception {
		if (transformer == null)
			throw new UnsupportedOperationException("Operation not support");
		if (body != null) {
			byte[] body = transformer.serialize(this.body);
			response.content().writeBytes(body);
		}
	}
	
	private void sendStream(FullHttpResponse response) throws IOException {
		copyStreamToByteBuf(stream, response.content());
		stream.close();
	}
	
	@Override
	public void status(int statusCode) {
		status = HttpResponseStatus.valueOf(statusCode);
	}
	
	@Override
	public void type(String contentType) {
		headers.put(CONTENT_TYPE, contentType);
	}
	
	@Override
	public void body(Object body) {
		this.body = body;
	}
	
	@Override
	public Object body() {
		return body;
	}
	
	@Override
	public void redirect(String location) {
		headers.put(LOCATION, location);
		status = HttpResponseStatus.valueOf(302);
		headers.put(CONNECTION, "close");
	}
	
	@Override
	public void redirect(String location, int httpStatusCode) {
		headers.put(LOCATION, location);
		status = HttpResponseStatus.valueOf(httpStatusCode);
		headers.put(CONNECTION, "close");
	}
	
	@Override
	public void header(String header, String value) {
		headers.put(header, value);
	}
	
	@Override
	public void cookie(String name, String value) {
		cookie(name, value, -1, false);
	}
	
	@Override
	public void cookie(String name, String value, int maxAge) {
		cookie(name, value, maxAge, false);
	}
	
	@Override
	public void cookie(String name, String value, int maxAge, boolean secured) {
		cookie(name, value, maxAge, secured, false);
	}
	
	@Override
	public void cookie(String name, String value, int maxAge, boolean secured, boolean httpOnly) {
		cookie("", name, value, maxAge, secured, httpOnly);
	}
	
	@Override
	public void cookie(String path, String name, String value, int maxAge, boolean secured) {
		cookie(path, name, value, maxAge, secured, false);
	}
	
	@Override
	public void cookie(String path, String name, String value, int maxAge, boolean secured, boolean httpOnly) {
		Cookie cookie = new DefaultCookie(name, value);
		cookie.setPath(path);
		cookie.setMaxAge(maxAge);
		cookie.setSecure(secured);
		cookie.setHttpOnly(httpOnly);
		cookies.put(name, cookie);
	}
	
	@Override
	public void removeCookie(String name) {
		Cookie cookie = new DefaultCookie(name, "");
		cookie.setMaxAge(0);
		cookies.put(name, cookie);
	}
}
