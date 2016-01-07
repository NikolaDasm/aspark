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

import static nikoladasm.aspark.ASparkUtil.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import nikoladasm.aspark.HttpMethod;
import nikoladasm.aspark.server.ASparkServer;

import static java.util.Objects.requireNonNull;

public class ASparkInstance extends Routable {
	
	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(nikoladasm.aspark.ASparkInstance.class);

	public static final int ASPARK_DEFAULT_PORT = 4568;

	private static final String DEFAULT_SERVER_NAME = "ASpark - powered by Netty";
	
	private static final String BEFORE_MAPPING_ERROR_MESSAGE =
		"This must be done before route mapping has begun";
	private static final String[] DEFAULT_STATIC_RESOURCE_INDEX = {"index.html", "index.htm"};
	private static final String PROTOCOL = "TLS";
	private static final String KEY_TYPE = "JKS";
	private static final String MIME_TYPES_PROPERTY_FILE = "resources/mime-types.properties";
	
	private volatile String ipAddress = "0.0.0.0";
	private volatile int port = ASPARK_DEFAULT_PORT;
	private volatile ExecutorService pool;
	private volatile int maxThreads;
	
	private boolean started;
	private RoutesList routes;
	private FiltersList before;
	private FiltersList after;
	private volatile ASparkServer server;
	private volatile CountDownLatch latch = new CountDownLatch(1);
	private ExceptionMap exceptionMap;
	private WebSocketMap webSockets;
	private volatile StaticResourceLocation location;
	private volatile StaticResourceLocation externalLocation;
	private volatile SSLContext sslContext;
	private volatile String serverName = DEFAULT_SERVER_NAME;
	private Properties mimeTypes;

	public ASparkInstance() {
		routes = new RoutesList();
		before = new FiltersList();
		after = new FiltersList();
		exceptionMap = new ExceptionMap();
		maxThreads = Runtime.getRuntime().availableProcessors();
		webSockets = new WebSocketMap();
	}
	
	public synchronized void threadPool(int maxThreads) {
		this.maxThreads = maxThreads;
	}
	
	public synchronized void ipAddress(String ipAddress) {
		if (started)
			throw new ASparkException(BEFORE_MAPPING_ERROR_MESSAGE);
		this.ipAddress = ipAddress;
	}

	public String ipAddress() {
		return ipAddress;
	}

	public synchronized void port(int port) {
		if (started)
			throw new ASparkException(BEFORE_MAPPING_ERROR_MESSAGE);
		this.port = port;	
	}

	public int port() {
		return port;
	}

	public synchronized void secure(String keystoreFile,
			String keystorePassword,
			String truststoreFile,
			String truststorePassword) {
		if (started)
			throw new ASparkException(BEFORE_MAPPING_ERROR_MESSAGE);
		requireNonNull(keystoreFile,"Must provide a keystore file to run secured");
		try {
			KeyStore ks = KeyStore.getInstance(KEY_TYPE);
			InputStream ksIs = new FileInputStream(keystoreFile);
			ks.load(ksIs, keystorePassword.toCharArray());
			ksIs.close();
			KeyManagerFactory kmf =
				KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, keystorePassword.toCharArray());
			KeyStore ts;
			if (truststoreFile == null) truststoreFile = "";
			File tsf = new File(truststoreFile);
			if (tsf.exists() && !tsf.isDirectory()) { 
				ts = KeyStore.getInstance(KEY_TYPE);
				InputStream tsIs = new FileInputStream(tsf);
				ts.load(tsIs, truststorePassword.toCharArray());
				tsIs.close();
			} else {
				ts = ks;
			}
			TrustManagerFactory tmf =
				TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ts);
			sslContext = SSLContext.getInstance(PROTOCOL);
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (Exception e) {
			e.printStackTrace();
			sslContext = null;
			throw new ASparkException("Failed to initialize SSLContext", e);
		}
	}
	
	private void loadMimeTypes() {
		if (mimeTypes != null) return;
		mimeTypes = new Properties();
		InputStream is = this.getClass().getResourceAsStream(MIME_TYPES_PROPERTY_FILE);
		if (is == null)
			is = this.getClass().getClassLoader().getResourceAsStream(MIME_TYPES_PROPERTY_FILE);
		if (is == null) throw new ASparkException("File \"mime-types.properties\" not found.");
		try {
			mimeTypes.load(is);
		} catch (IOException e) {
			throw new ASparkException("Could not load file \"mime-types.properties\"");
		}
	}
	
	public synchronized void staticFileLocation(String folder) {
		staticFileLocation(folder, DEFAULT_STATIC_RESOURCE_INDEX);
	}
	
	public synchronized void staticFileLocation(String folder, String[] indexFiles) {
		if (started)
			throw new ASparkException(BEFORE_MAPPING_ERROR_MESSAGE);
		requireNonNull(folder,"Path can't be null");
		location = new StaticResourceLocation(folder, indexFiles);
		loadMimeTypes();
	}
	
	public void staticFileLocationACL(String path, boolean allow) {
		if (location == null)
			throw new ASparkException("Static file location not set");
		ACLEntry entry = new ACLEntry(buildPathPattern(path), allow);
		location.aclEntry(entry);
	}
	
	public synchronized void externalStaticFileLocation(String externalFolder) {
		externalStaticFileLocation(externalFolder, DEFAULT_STATIC_RESOURCE_INDEX);
	}
	
	public synchronized void externalStaticFileLocation(String externalFolder, String[] indexFiles) {
		if (started)
			throw new ASparkException(BEFORE_MAPPING_ERROR_MESSAGE);
		requireNonNull(externalFolder,"Path can't be null");
		externalLocation = new StaticResourceLocation(externalFolder, indexFiles);
		loadMimeTypes();
	}
	
	public void externalStaticFileLocationACL(String path, boolean allow) {
		if (externalLocation == null)
			throw new ASparkException("External static file location not set");
		ACLEntry entry = new ACLEntry(buildPathPattern(path), allow);
		externalLocation.aclEntry(entry);
	}
	
	public void exception(Class<? extends Exception> exceptionClass, ExceptionHandler handler) {
		exceptionMap.put(exceptionClass, handler);
	}
	
	public void halt() {
		throw new HaltException();	}
	
	public void halt(int status) {
		throw new HaltException(status);	}
	
	public void halt(String body) {
		throw new HaltException(body);	}
	
	public void halt(int status, String body) {
		throw new HaltException(status, body);	}
	
	public synchronized void init() {
		if(!started) {
			pool = Executors.newFixedThreadPool(maxThreads);
			server = new ASparkServer(
					latch,
					pool,
					ipAddress,
					port,
					routes,
					before,
					after,
					location,
					externalLocation,
					exceptionMap,
					webSockets,
					sslContext,
					serverName,
					mimeTypes);
			new Thread(() -> {
				server.start();
			}).start();
			started = true;
		}
	}
	
	public void awaitInitialization() {
		try {
			latch.await();
			if (server == null || !server.isStarted()) {
				started = false;
				throw new ASparkException("Netty server not started");
			}
		} catch (InterruptedException e) {
			LOG.info("Interrupted by another thread");
		}
	}
	
	public synchronized boolean isStarted() {
		started = server != null && server.isStarted();
		return started;
	}
	
	@Override
	public void addRoute(
			HttpMethod httpMethod,
			String path,
			String acceptedType,
			RouteHandler handler,
			ResponseTransformer responseTransformer) {
		init();
		requireNonNull(httpMethod,"Http method can't be null");
		requireNonNull(path,"Path can't be null");
		requireNonNull(acceptedType,"Accepted type can't be null");
		requireNonNull(handler,"Handler can't be null");
		requireNonNull(responseTransformer,"Response transformer can't be null");
		Map<String, Integer> parameterNamesMap = new HashMap<>();
		Boolean startWithWildcard = new Boolean(false);
		Pattern pathPattern = buildParameterizedPathPattern(path, parameterNamesMap, startWithWildcard);
		Route route = new Route(httpMethod,
				pathPattern,
				parameterNamesMap,
				startWithWildcard,
				acceptedType,
				handler,
				responseTransformer);
		routes.addLast(route);
	}

	@Override
	public void addFilter(boolean before,
			String path,
			String acceptedType,
			FilterHandler handler) {
		init();
		requireNonNull(path,"Path can't be null");
		requireNonNull(acceptedType,"Accepted type can't be null");
		requireNonNull(handler,"Handler can't be null");
		Map<String, Integer> parameterNamesMap = new HashMap<>();
		Boolean startWithWildcard = new Boolean(false);
		Pattern pathPattern = buildParameterizedPathPattern(path, parameterNamesMap, startWithWildcard);
		Filter filter = new Filter(pathPattern,
				parameterNamesMap,
				startWithWildcard,
				acceptedType,
				handler);
		if (before)
			this.before.addLast(filter);
		else
			after.addLast(filter);
	}
	
	public synchronized void stop() {
		if (server != null) {
			server.stop();
			pool.shutdown();
			try {
				pool.awaitTermination(15, TimeUnit.SECONDS);
				pool.shutdownNow();
			} catch (InterruptedException e) {
				LOG.error("Could not stop thread pool", e);
				pool.shutdownNow();
			}
			latch = new CountDownLatch(1);
			started = false;
		}
	}
	
	public void await() {
		server.await();
	}
	
	public synchronized void clearRoutes() {
		if (started)
			throw new ASparkException(BEFORE_MAPPING_ERROR_MESSAGE);
		routes.clear();
	}

	public synchronized void clearBefore() {
		if (started)
			throw new ASparkException(BEFORE_MAPPING_ERROR_MESSAGE);
		before.clear();
	}

	public synchronized void clearAfter() {
		if (started)
			throw new ASparkException(BEFORE_MAPPING_ERROR_MESSAGE);
		after.clear();
	}

	public void webSocket(String path, WebSocketHandler handler) {
		requireNonNull(path,"Path can't be null");
		requireNonNull(handler,"Handler can't be null");
		webSockets.add(path, handler);
	}
	
	public void serverName(String serverName) {
		requireNonNull(serverName,"Server name can't be null;");
		this.serverName = serverName;
	}
	
	public String serverName() {
		return serverName;
	}
}
