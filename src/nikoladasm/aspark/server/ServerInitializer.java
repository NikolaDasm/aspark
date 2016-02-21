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

import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import nikoladasm.aspark.ExceptionMap;
import nikoladasm.aspark.WebSocketMap;
import nikoladasm.aspark.dispatcher.Dispatcher;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {

	private SSLContext sslContext;
	private int maxContentLength;
	
	private String ipAddress;
	private int port;
	private Dispatcher dispatcher;
	private ExceptionMap exceptionMap;
	private WebSocketMap webSockets;
	private String serverName;
	private Executor pool;
	
	public ServerInitializer(SSLContext sslContext,
			int maxContentLength,
			String ipAddress,
			int port,
			Dispatcher dispatcher,
			ExceptionMap exceptionMap,
			WebSocketMap webSockets,
			String serverName,
			Executor pool) {
		this.sslContext = sslContext;
		this.maxContentLength = maxContentLength;
		this.ipAddress = ipAddress;
		this.port = port;
		this.dispatcher = dispatcher;
		this.exceptionMap = exceptionMap;
		this.webSockets = webSockets;
		this.serverName = serverName;
		this.pool = pool;
	}
	
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
		ServerHandler serverHandler = new ServerHandler(
				ipAddress,
				port,
				dispatcher,
				exceptionMap,
				webSockets,
				serverName,
				pool);
		pipeline.addLast("handler", serverHandler);
	}

}
