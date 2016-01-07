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

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import nikoladasm.aspark.ExceptionMap;
import nikoladasm.aspark.FiltersList;
import nikoladasm.aspark.RoutesList;
import nikoladasm.aspark.StaticResourceLocation;
import nikoladasm.aspark.WebSocketMap;

public class ASparkServer {
	
	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(nikoladasm.aspark.server.ASparkServer.class);

	private static final int DEFAULT_MAX_CONTENT_LENGTH = 20480;
	private String ipAddress;
	private int port;
	private RoutesList routes;
	private FiltersList before;
	private FiltersList after;
	private StaticResourceLocation location;
	private StaticResourceLocation externalLocation;
	private ExceptionMap exceptionMap;
	private WebSocketMap webSockets;
	private int maxContentLength;
	private SSLContext sslContext;
	private CountDownLatch latch;
	private String serverName;
	private Properties mimeTypes;
	
	private volatile Channel channel;
	private volatile EventLoopGroup bossGroup;
	private volatile EventLoopGroup workerGroup;
	private volatile boolean started;
	
	private Executor pool;

	public ASparkServer(CountDownLatch latch,
			Executor pool,
			String ipAddress,
			int port,
			RoutesList routes,
			FiltersList beforeFilters,
			FiltersList afterFilters,
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
	}
	
	public ASparkServer(CountDownLatch latch,
			Executor pool,
			String ipAddress,
			int port,
			RoutesList routes,
			FiltersList beforeFilters,
			FiltersList afterFilters,
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
	
	public ASparkServer(CountDownLatch latch,
			Executor pool,
			String ipAddress,
			int port,
			RoutesList routes,
			FiltersList beforeFilters,
			FiltersList afterFilters,
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
	
	public ASparkServer(CountDownLatch latch,
			Executor pool,
			String ipAddress,
			int port,
			RoutesList routes,
			FiltersList beforeFilters,
			FiltersList afterFilters,
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
	
	public void start() {
		bossGroup = new NioEventLoopGroup();
		workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap server = new ServerBootstrap();
			server.group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.childHandler(new ServerInitializer(
						sslContext,
						maxContentLength,
						ipAddress,
						port,
						routes,
						before,
						after,
						location,
						externalLocation,
						exceptionMap,
						webSockets,
						serverName,
						mimeTypes,
						pool))
				.option(ChannelOption.SO_BACKLOG, 1024)
				.option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.TCP_NODELAY, true)
				.childOption(ChannelOption.SO_KEEPALIVE, true)
				.childOption(ChannelOption.TCP_NODELAY, true);
			channel =
				server.bind(new InetSocketAddress(ipAddress, port)).sync().channel();
			started = true;
			latch.countDown();
			LOG.info("Netty server started");
		} catch (InterruptedException e) {
			LOG.error("Unexpected exception", e);
			bossGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
			workerGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
			started = false;
			latch.countDown();
		}
	}
	
	public boolean isStarted() {
		return started;
	}
	
	public void stop() {
		if (channel == null) return;
		channel.close().syncUninterruptibly();
		bossGroup.shutdownGracefully();
		workerGroup.shutdownGracefully();
		bossGroup.terminationFuture().syncUninterruptibly();
		workerGroup.terminationFuture().syncUninterruptibly();
		started = false;
		LOG.info("Netty server stopped");
	}
	
	public void await() {
		if (channel == null) return;
		try {
			channel.closeFuture().sync();
		} catch (InterruptedException e) {
			LOG.warn("Unexpected exception", e);
			bossGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
			workerGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
			started = false;
		}
	}
}
