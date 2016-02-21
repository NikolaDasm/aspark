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
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;

import static nikoladasm.aspark.HttpMethod.*;
import static nikoladasm.aspark.ASparkUtil.*;

public class ResponseImpl implements Response {
	
	private static final int DEFAULT_CHUNK_SIZE = 8192;
	
	private int status;
	private ChannelHandlerContext ctx;
	private HttpVersion version;
	private Map<String,String> headers;
	private Object body;
	private ResponseTransformer transformer;
	private boolean keepAlive;
	private Map<String, Cookie> cookies;
	private InputStream stream;
	private HttpMethod httpMethod;
	private String serverName;
	
	public ResponseImpl(ChannelHandlerContext ctx,
			HttpVersion version,
			boolean keepAlive,
			HttpMethod httpMethod,
			String serverName) {
		this.ctx = ctx;
		this.version = version;
		this.keepAlive = keepAlive;
		status = 200;
		headers = new HashMap<>();
		cookies = new HashMap<>();
		this.httpMethod = httpMethod;
		this.serverName = serverName;
	}
	
	public void transformer(ResponseTransformer transformer) {
		this.transformer = transformer;
	}
	
	public ResponseTransformer transformer() {
		return transformer;
	}
	
	public void inputStream(InputStream stream) {
		this.stream = stream;
	}

	public InputStream inputStream() {
		return stream;
	}

	public void send() throws Exception {
		if (stream != null && !HTTP_1_0.equals(version))
			sendChunked();
		else
			sendUnChunked();
	}
	
	private void sendChunked() {
		HttpResponse response =
			new DefaultHttpResponse(version, HttpResponseStatus.valueOf(status));
		setHeades(response);
		response.headers().set(TRANSFER_ENCODING, CHUNKED);
		cookies.forEach((name, cookie) ->
			response.headers().add(SET_COOKIE, ServerCookieEncoder.LAX.encode(cookie)));
		ctx.channel().write(response);
		Object body;
		if (!httpMethod.equals(HEAD)) {
			body = new HttpChunkedInput(new ChunkedStream(stream, DEFAULT_CHUNK_SIZE));
		} else {
			body = LastHttpContent.EMPTY_LAST_CONTENT;
		}
		writeObjectToChannel(body).addListener(channelFuture -> stream.close());
	}
	
	private void sendUnChunked() throws Exception {
		FullHttpResponse response =
			new DefaultFullHttpResponse(version, HttpResponseStatus.valueOf(status));
		setHeades(response);
		if (stream != null)
			sendStream(response);
		else
			sendByteArray(response);
		response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
		cookies.forEach((name, cookie) ->
			response.headers().add(SET_COOKIE, ServerCookieEncoder.LAX.encode(cookie)));
		if (httpMethod.equals(HEAD))
			response.content().clear();
		writeObjectToChannel(response);
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
	
	private ChannelFuture writeObjectToChannel(Object object) {
		ChannelFuture lastContentFuture = ctx.channel().writeAndFlush(object);
		if (!keepAlive || HTTP_1_0.equals(version))
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		return lastContentFuture;
	}
	
	private void setHeades(HttpResponse response) {
		headers.putIfAbsent(CONTENT_TYPE, "text/plain; charset=UTF-8");
		headers.putIfAbsent(SERVER, serverName);
		if(!headers.containsKey(DATE)) {
			final String httpDate =
				DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
			headers.put(DATE, httpDate);
		}
		headers.putIfAbsent(VARY, "Accept-Encoding");
		headers.forEach((key, value) ->
			response.headers().add(key, value));
		if (keepAlive)
			response.headers().set(CONNECTION, KEEP_ALIVE);
	}
	
	@Override
	public void status(int statusCode) {
		status = statusCode;
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
		status = 302;
		headers.put(CONNECTION, "close");
	}
	
	@Override
	public void redirect(String location, int httpStatusCode) {
		headers.put(LOCATION, location);
		status = httpStatusCode;
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
	
	@Override
	public void authenticateBasic(String realm) {
		status = 401;
		headers.put(WWW_AUTHENTICATE, "Basic realm=\""+realm+"\"");
	}
}
