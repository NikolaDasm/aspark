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

package nikoladasm.aspark.server;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import nikoladasm.aspark.ExceptionHandler;
import nikoladasm.aspark.ExceptionMap;
import nikoladasm.aspark.HaltException;
import nikoladasm.aspark.HttpMethod;
import nikoladasm.aspark.RequestImpl;
import nikoladasm.aspark.ResponseImpl;
import nikoladasm.aspark.WebSocketContextImpl;
import nikoladasm.aspark.WebSocketHandler;
import nikoladasm.aspark.WebSocketMap;
import nikoladasm.aspark.dispatcher.Dispatcher;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import static nikoladasm.aspark.ASparkUtil.*;
import static nikoladasm.aspark.HttpMethod.GET;
import static nikoladasm.aspark.HttpMethod.POST;
import static nikoladasm.aspark.ASparkInstance.DEFAULT_RESPONSE_TRANSFORMER;

public class ServerHandler extends SimpleChannelInboundHandler<Object> {
	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(nikoladasm.aspark.server.ServerHandler.class);

	private static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER_ATTR_KEY =
		AttributeKey.valueOf("HANDSHAKER");
	private static final AttributeKey<WebSocketHandler> WEBSOCKET_HANDLER_ATTR_KEY =
			AttributeKey.valueOf("WEBSOCKET_HANDLER");
	private static final AttributeKey<WebSocketContextImpl> WEBSOCKET_CONTEXT_ATTR_KEY =
			AttributeKey.valueOf("WEBSOCKET_CONTEXT");
	
	private String ipAddress;
	private int port;
	private Dispatcher dispatcher;
	private ExceptionMap exceptionMap;
	private WebSocketMap webSockets;
	private String serverName;
	private Executor pool;

	public ServerHandler(
			String ipAddress,
			int port,
			Dispatcher dispatcher,
			ExceptionMap exceptionMap,
			WebSocketMap webSockets,
			String serverName,
			Executor pool) {
		this.ipAddress = ipAddress;
		this.port = port;
		this.dispatcher = dispatcher;
		this.exceptionMap = exceptionMap;
		this.webSockets = webSockets;
		this.serverName = serverName;
		this.pool = pool;
	}
	
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
			HttpMethod httpMethod =
				HttpMethod.valueOf(httpMethodName.toUpperCase());
			HttpMethod originalHttpMethod =
				HttpMethod.valueOf(nettyRequest.getMethod().name().toUpperCase());
			Map<String, List<String>> postAttr = getPostAttributes(originalHttpMethod, nettyRequest);
			RequestImpl request = new RequestImpl(nettyRequest,
					queryStringDecoder,
					originalHttpMethod,
					httpMethod,
					postAttr,
					path,
					port,
					ipAddress,
					version);
			ResponseImpl response = new ResponseImpl(ctx,
					version,
					keepAlive,
					httpMethod,
					serverName);
			pool.execute(() -> {
				try {
					boolean processed =
					WebSocketHandshake(
							originalHttpMethod,
							path,
							nettyRequest,
							ctx);
					if (processed) return;
					dispatcher.process(
							request,
							response);
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
		if (keepAlive)
			response.headers().set(CONNECTION, KEEP_ALIVE);
		ChannelFuture lastContentFuture = ctx.channel().writeAndFlush(response);
		if (!keepAlive || HTTP_1_0.equals(version))
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
	}
	
	private boolean isDecodeableContent(String contentType) {
		if (contentType == null || contentType.isEmpty()) return false;
		return contentType.startsWith("multipart/form-data") ||
				contentType.startsWith("application/x-www-form-urlencoded");
	}
	
	private Map<String, List<String>> getPostAttributes(HttpMethod requestMethod,
			FullHttpRequest request) {
		final Map<String, List<String>> map = new HashMap<String, List<String>>();
		if (!requestMethod.equals(POST)) return map;
		if (!isDecodeableContent(request.headers().get(CONTENT_TYPE))) return map;
		final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
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
}
