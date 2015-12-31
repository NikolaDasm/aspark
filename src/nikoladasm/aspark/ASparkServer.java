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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import nikoladasm.aspark.StaticResourceLocation.StaticResource;
import io.netty.handler.codec.http.websocketx.*;

import static nikoladasm.aspark.ASparkUtil.*;
import static nikoladasm.aspark.HttpMethod.*;
import static nikoladasm.aspark.ASparkInstance.*;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class ASparkServer {
	
	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(nikoladasm.aspark.ASparkServer.class);

	private static final int DEFAULT_MAX_CONTENT_LENGTH = 20480;
	private static final int HTTP_CACHE_SECONDS = 60;
	private static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER_ATTR_KEY =
		AttributeKey.valueOf("HANDSHAKER");
	private static final AttributeKey<WebSocketHandler> WEBSOCKET_HANDLER_ATTR_KEY =
			AttributeKey.valueOf("WEBSOCKET_HANDLER");
	private static final AttributeKey<WebSocketContextImpl> WEBSOCKET_CONTEXT_ATTR_KEY =
			AttributeKey.valueOf("WEBSOCKET_CONTEXT");
	private String ipAddress;
	private int port;
	private ConcurrentLinkedQueue<Route> routes;
	private ConcurrentLinkedQueue<Filter> before;
	private ConcurrentLinkedQueue<Filter> after;
	private StaticResourceLocation location;
	private StaticResourceLocation externalLocation;
	private ExceptionMap exceptionMap;
	private WebSocketMap webSockets;
	private int maxContentLength;
	private SSLContext sslContext;
	private CountDownLatch latch;
	private String serverName;
	private Properties mimeTypes;
	
	private Channel channel;
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	
	private Executor pool;

	private class ServerInitializer extends ChannelInitializer<SocketChannel> {
		
		@Override
		public void initChannel(SocketChannel channel) throws Exception {
			ChannelPipeline pipeline = channel.pipeline();
			if (sslContext != null) {
				SSLEngine sslEngine = sslContext.createSSLEngine();
				sslEngine.setUseClientMode(false);
				sslEngine.setWantClientAuth(true);
				sslEngine.setEnabledProtocols(sslEngine.getSupportedProtocols());
				sslEngine.setEnabledCipherSuites(sslEngine.getSupportedCipherSuites());
				sslEngine.setEnableSessionCreation(true);
				SslHandler sslHandler = new SslHandler(sslEngine);
				pipeline.addLast("ssl", sslHandler);
			}
			pipeline.addLast("httpCodec", new HttpServerCodec());
			pipeline.addLast("inflater", new HttpContentDecompressor());
			pipeline.addLast("deflater", new HttpContentCompressor());
			pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
			pipeline.addLast("aggregator", new HttpObjectAggregator(maxContentLength));
			pipeline.addLast("handler", new ServerHandler());
		}
	}
	
	class ServerHandler extends SimpleChannelInboundHandler<Object> {

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (msg instanceof FullHttpRequest) {
				handleHttpRequest(ctx, (FullHttpRequest)msg);
			} else if (msg instanceof WebSocketFrame) {
				handleWebSocketFrame(ctx, (WebSocketFrame)msg);
			}
		}
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			LOG.warn("Unexpected exception", cause);
			ctx.close();
		}
	}
	
	private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest nettyRequest) throws Exception {
		boolean decoderResult = nettyRequest.getDecoderResult().isSuccess();
		HttpVersion version = nettyRequest.getProtocolVersion();
		boolean keepAlive = isKeepAlive(nettyRequest);
		if (decoderResult) {
			String uri = QueryStringDecoder.decodeComponent(nettyRequest.getUri(), CharsetUtil.UTF_8);
			QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
			String path = sanitizePath(queryStringDecoder.path());
			String httpMethodOverrideName = nettyRequest.headers().get("X-HTTP-Method-Override");
			String httpMethodName =
				(httpMethodOverrideName == null) ? nettyRequest.getMethod().name() : httpMethodOverrideName;
			HttpMethod requestMethod =
					HttpMethod.valueOf(httpMethodName.toUpperCase());
			HttpMethod originalRequestMethod =
					HttpMethod.valueOf(nettyRequest.getMethod().name().toUpperCase());
			String acceptType = nettyRequest.headers().get(ACCEPT);
			Map<String, List<String>> postAttr = getPostAttributes(originalRequestMethod, nettyRequest);
			RequestImpl request = new RequestImpl(nettyRequest,
					queryStringDecoder,
					originalRequestMethod,
					requestMethod,
					postAttr,
					path,
					port,
					ipAddress,
					version);
			ResponseImpl response = new ResponseImpl(ctx,
					version,
					keepAlive,
					requestMethod,
					serverName);
			pool.execute(() -> {
				try {
					boolean processed =
					WebSocketHandshake(
							originalRequestMethod,
							path,
							nettyRequest,
							ctx);
					if (processed) return;
					processRequest(
							path,
							acceptType,
							request,
							response,
							requestMethod);
					response.send();
				} catch (Exception e) {
					LOG.warn("Exception ", e);
					if (e instanceof HaltException) {
						sendResponse(ctx,
								version,
								HttpResponseStatus.valueOf(((HaltException) e).status()),
								keepAlive,
								((HaltException) e).body());
						return;
					}
					ExceptionHandler handler = exceptionMap.get(e.getClass());
					if (handler != null) {
						handler.handle(e, request, response);
						try {
							if (response.inputStream() != null) {
								response.inputStream().close();
								response.inputStream(null);
								if (response.transformer() == null)
									response.transformer(DEFAULT_RESPONSE_TRANSFORMER);
							}
							response.send();
						} catch (Exception exc) {
							sendResponse(ctx, version, INTERNAL_SERVER_ERROR, keepAlive, null);
						}
						return;
					}
					sendResponse(ctx, version, INTERNAL_SERVER_ERROR, keepAlive, null);
				}
			});
		} else {
			sendResponse(ctx, version, BAD_REQUEST, keepAlive, null);
		}
	}
	
	private void sendResponse(
			ChannelHandlerContext ctx,
			HttpVersion version,
			HttpResponseStatus status,
			boolean keepAlive,
			String body) {
		FullHttpResponse response =
			new DefaultFullHttpResponse ((version == null) ? HTTP_1_1 : version,
					status,
					Unpooled.copiedBuffer((body == null) ? "" : body, CharsetUtil.UTF_8));
		response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
		response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
		if (!keepAlive) {
			ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
		} else {
			response.headers().set(CONNECTION, KEEP_ALIVE);
			ctx.channel().writeAndFlush(response);
		}
	}
	
	private Map<String, List<String>> getPostAttributes(HttpMethod requestMethod,
			FullHttpRequest request) {
		if (!requestMethod.equals(POST)) return null;
		final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
		final Map<String, List<String>> map = new HashMap<String, List<String>>();
		try {
			for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
				if (data.getHttpDataType() == HttpDataType.Attribute) {
					Attribute attribute = (Attribute) data;
					List<String> list = map.get(attribute.getName());
					if (list == null) {
						list = new LinkedList<String>();
						map.put(attribute.getName(), list);
					}
					list.add(attribute.getValue());
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Cannot parse http request data", e);		} finally {
			decoder.destroy();
		}
		return Collections.unmodifiableMap(map);
	}
	
	private boolean processRequest(
			String path,
			String acceptType,
			RequestImpl request,
			ResponseImpl response,
			HttpMethod requestMethod) throws Exception {
		for (Filter filter : before) {
			Matcher parameterMatcher = filter.pathPattern().matcher(path);
			if(isAcceptContentType(acceptType, filter.acceptedType()) &&
				parameterMatcher.matches()) {
				request.parameterNamesMap(filter.parameterNamesMap());
				request.startWithWildcard(filter.startWithWildcard());
				request.parameterMatcher(parameterMatcher);
				filter.handler().handle(request, response);
			}
		}
		boolean routeFound =
			processRoutes(
					path,
					acceptType,
					request,
					response,
					requestMethod);
		if (!routeFound) {
			routeFound = processStaticResources(
					path,
					request,
					response,
					requestMethod);
		}
		for (Filter filter : after) {
			Matcher parameterMatcher = filter.pathPattern().matcher(path);
			if(isAcceptContentType(acceptType, filter.acceptedType()) &&
				parameterMatcher.matches()) {
				request.parameterNamesMap(filter.parameterNamesMap());
				request.startWithWildcard(filter.startWithWildcard());
				request.parameterMatcher(parameterMatcher);
				filter.handler().handle(request, response);
			}
		}
		if (!routeFound) {
			response.transformer(DEFAULT_RESPONSE_TRANSFORMER);
			response.status(404);
		}
		return routeFound;
	}
	
	private boolean processRoutes(
			String path,
			String acceptType,
			RequestImpl request,
			ResponseImpl response,
			HttpMethod requestMethod) throws Exception {
		for (Route route : routes) {
			Matcher parameterMatcher = route.pathPattern().matcher(path);
			if(isEqualHttpMethod(requestMethod, route.httpMethod()) &&
				isAcceptContentType(acceptType, route.acceptedType()) &&
				parameterMatcher.matches()) {
				request.parameterNamesMap(route.parameterNamesMap());
				request.startWithWildcard(route.startWithWildcard());
				request.parameterMatcher(parameterMatcher);
				response.transformer(route.responseTransformer());
				Object body = route.handler().handle(request, response);
				response.body(body);
				return true;
			}
		}
		return false;
	}
	
	private boolean processStaticResources(
			String path,
			RequestImpl request,
			ResponseImpl response,
			HttpMethod requestMethod) throws IOException {
		if (!isEqualHttpMethod(requestMethod, GET))
			return false;
		if (this.location != null) {
			StaticResource resource = this.location.getClassResource(path);
			if (resource.stream() != null) {
				response.inputStream(resource.stream());
				response.header(CONTENT_TYPE, mimeType(resource.fullPath(), mimeTypes));
				return true;
			}
		}
		if (this.externalLocation != null) {
			StaticResource resource = this.externalLocation.getFileResource(path);
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
	
	private boolean WebSocketHandshake(
			HttpMethod method,
			String path,
			FullHttpRequest req,
			ChannelHandlerContext ctx) {
		if (method.equals(GET)) {
			final WebSocketHandler wsHandler = webSockets.handler(path);
			if (wsHandler == null) return false;
			Channel channel = ctx.channel();
			final WebSocketServerHandshakerFactory wsFactory =
				new WebSocketServerHandshakerFactory(
					getWebSocketLocation(channel.pipeline(), req, path), null, true);
			final WebSocketServerHandshaker handshaker =
				wsFactory.newHandshaker(req);
			if (handshaker == null) {
				WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(channel);
			} else {
				final ChannelFuture handshakeFuture =
					handshaker.handshake(channel, req);
				handshakeFuture.addListener((future) -> {
					if (!future.isSuccess()) {
						ctx.fireExceptionCaught(future.cause());
					} else {
						channel.attr(WEBSOCKET_HANDLER_ATTR_KEY).set(wsHandler);
						channel.attr(HANDSHAKER_ATTR_KEY).set(handshaker);
						WebSocketContextImpl wsContext =
							new WebSocketContextImpl(channel);
						channel.attr(WEBSOCKET_CONTEXT_ATTR_KEY).set(wsContext);
						wsHandler.onConnect(wsContext);
					}
				});
			}
			return true;
		}
		return false;
	}
	
	private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
		WebSocketContextImpl wctx = 
				ctx.channel().attr(WEBSOCKET_CONTEXT_ATTR_KEY).get();
		WebSocketHandler wsHandler =
			ctx.channel().attr(WEBSOCKET_HANDLER_ATTR_KEY).get();
		if (frame instanceof CloseWebSocketFrame) {
			WebSocketServerHandshaker handshaker =
				ctx.channel().attr(HANDSHAKER_ATTR_KEY).get();
			if (handshaker != null) {
				frame.retain();
				handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame);
				if (wsHandler != null) {
					ctx.channel().attr(WEBSOCKET_HANDLER_ATTR_KEY).remove();
					ctx.channel().attr(WEBSOCKET_CONTEXT_ATTR_KEY).remove();
					String reason = ((CloseWebSocketFrame) frame).reasonText();
					int statusCode = ((CloseWebSocketFrame) frame).statusCode();
					wsHandler.onClose(wctx, statusCode, reason);
				}
			} else {
				ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
			}
			return;
		}
		if (wsHandler == null) return;
		if (frame instanceof PingWebSocketFrame) {
			frame.content().retain();
			ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content()));
			return;
		}
		if (frame instanceof PongWebSocketFrame) {
			return;
		}
		if (frame instanceof TextWebSocketFrame) {
			wctx.textFrameBegin(true);
			String request = ((TextWebSocketFrame) frame).text();
			if (frame.isFinalFragment()) {
				wsHandler.onMessage(wctx, request);
			} else {
				wctx.stringBuilder().append(request);
			}
			return;
		}
		if (frame instanceof BinaryWebSocketFrame) {
			wctx.textFrameBegin(false);
			byte[] request = new byte[((BinaryWebSocketFrame) frame).content().readableBytes()];
			((BinaryWebSocketFrame) frame).content().readBytes(request);
			if (frame.isFinalFragment()) {
				wsHandler.onMessage(wctx, request);
			} else {
				wctx.frameBuffer().writeBytes(request);
			}
			return;
		}
		if (frame instanceof ContinuationWebSocketFrame) {
			if (wctx.textFrameBegin()) {
				String request = ((ContinuationWebSocketFrame) frame).text();
				wctx.stringBuilder().append(request);
				if (frame.isFinalFragment()) {
					wsHandler.onMessage(wctx, wctx.stringBuilder().toString());
					wctx.stringBuilder(new StringBuilder());
				}
			} else {
				byte[] request = new byte[((BinaryWebSocketFrame) frame).content().readableBytes()];
				((BinaryWebSocketFrame) frame).content().readBytes(request);
				wctx.frameBuffer().writeBytes(request);
				if (frame.isFinalFragment()) {
					request = new byte[wctx.frameBuffer().readableBytes()];
					wctx.frameBuffer().readBytes(request);
					wsHandler.onMessage(wctx, request);
					wctx.frameBuffer().clear();
				}
			}
			return;
		}
	}
	
	private static String getWebSocketLocation(ChannelPipeline cp, HttpRequest req, String path) {
		String protocol = "ws";
		if (cp.get(SslHandler.class) != null) protocol = "wss";
		return protocol + "://" + req.headers().get(HOST) + path;
	}
	
	ASparkServer(CountDownLatch latch,
			Executor pool,
			String ipAddress,
			int port,
			ConcurrentLinkedQueue<Route> routes,
			ConcurrentLinkedQueue<Filter> beforeFilters,
			ConcurrentLinkedQueue<Filter> afterFilters,
			StaticResourceLocation location,
			StaticResourceLocation externalLocation,
			ExceptionMap exceptionMap,
			WebSocketMap webSockets,
			SSLContext sslContext,
			int maxContentLength,
			String serverName,
			Properties mimeTypes) {
		this.pool = pool;
		this.latch = latch;
		this.ipAddress = ipAddress;
		this.port = port;
		this.routes = routes;
		this.before = beforeFilters;
		this.after = afterFilters;
		this.location = location;
		this.externalLocation = externalLocation;
		this.exceptionMap = exceptionMap;
		this.webSockets = webSockets;
		this.sslContext = sslContext;
		this.maxContentLength = maxContentLength;
		this.serverName = serverName;
		this.mimeTypes = (mimeTypes == null) ? new Properties() : mimeTypes;
		start();
	}
	
	ASparkServer(CountDownLatch latch,
			Executor pool,
			String ipAddress,
			int port,
			ConcurrentLinkedQueue<Route> routes,
			ConcurrentLinkedQueue<Filter> beforeFilters,
			ConcurrentLinkedQueue<Filter> afterFilters,
			StaticResourceLocation location,
			StaticResourceLocation externalLocation,
			ExceptionMap exceptionMap,
			WebSocketMap webSockets,
			SSLContext sslContext,
			String serverName,
			Properties mimeTypes) {
		this(
				latch,
				pool,
				ipAddress,
				port,
				routes,
				beforeFilters,
				afterFilters,
				location,
				externalLocation,
				exceptionMap,
				webSockets,
				sslContext,
				DEFAULT_MAX_CONTENT_LENGTH,
				serverName,
				mimeTypes);
	}
	
	ASparkServer(CountDownLatch latch,
			Executor pool,
			String ipAddress,
			int port,
			ConcurrentLinkedQueue<Route> routes,
			ConcurrentLinkedQueue<Filter> beforeFilters,
			ConcurrentLinkedQueue<Filter> afterFilters,
			StaticResourceLocation location,
			StaticResourceLocation externalLocation,
			ExceptionMap exceptionMap,
			WebSocketMap webSockets,
			int maxContentLength,
			String serverName,
			Properties mimeTypes) {
		this(
				latch,
				pool,
				ipAddress,
				port,
				routes,
				beforeFilters,
				afterFilters,
				location,
				externalLocation,
				exceptionMap,
				webSockets,
				null,
				maxContentLength,
				serverName,
				mimeTypes);
	}
	
	ASparkServer(CountDownLatch latch,
			Executor pool,
			String ipAddress,
			int port,
			ConcurrentLinkedQueue<Route> routes,
			ConcurrentLinkedQueue<Filter> beforeFilters,
			ConcurrentLinkedQueue<Filter> afterFilters,
			StaticResourceLocation location,
			StaticResourceLocation externalLocation,
			ExceptionMap exceptionMap,
			WebSocketMap webSockets,
			String serverName,
			Properties mimeTypes) {
		this(
				latch,
				pool,
				ipAddress,
				port,
				routes,
				beforeFilters, 
				afterFilters,
				location,
				externalLocation,
				exceptionMap,
				webSockets,
				null,
				DEFAULT_MAX_CONTENT_LENGTH,
				serverName,
				mimeTypes);
	}
	
	private void start() {
		bossGroup = new NioEventLoopGroup();
		workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap server = new ServerBootstrap();
			server.group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.childHandler(new ServerInitializer())
				.option(ChannelOption.SO_BACKLOG, 1024)
				.option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.TCP_NODELAY, true)
				.childOption(ChannelOption.SO_KEEPALIVE, true)
				.childOption(ChannelOption.TCP_NODELAY, true);
			channel =
				server.bind(new InetSocketAddress(ipAddress, port)).sync().channel();
			latch.countDown();
			LOG.info("Netty server started");
		} catch (InterruptedException e) {
			LOG.error("Unexpected exception", e);
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}
	
	public void stop() {
		if (channel == null) return;
		channel.close().syncUninterruptibly();
		bossGroup.shutdownGracefully();
		workerGroup.shutdownGracefully();
		LOG.info("Netty server stopped");
	}
	
	public void await() {
		if (channel == null) return;
		try {
			channel.closeFuture().sync();
		} catch (InterruptedException e) {
			LOG.warn("Unexpected exception", e);
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}
}
