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

import nikoladasm.aspark.dispatcher.FilterHandler;
import nikoladasm.aspark.dispatcher.RouteHandler;

public final class ASpark {

	public static final RequestTransformer<String> DEFAULT_REQUEST_TRANSFORMER =
		ASparkInstance.DEFAULT_REQUEST_TRANSFORMER;
	
	public static final ResponseTransformer DEFAULT_RESPONSE_TRANSFORMER =
		ASparkInstance.DEFAULT_RESPONSE_TRANSFORMER;
	
	public static final ViewEngine DEFAULT_VIEW_ENGINE =
		ASparkInstance.DEFAULT_VIEW_ENGINE;
	
	private static class SingletonHolder {
		private static final ASparkInstance INSTANCE = new ASparkInstance();
	}
	
	private ASpark() {}
	
	private static ASparkInstance getInstance() {
		return SingletonHolder.INSTANCE;
	}
	
	public static void ipAddress(String ipAddress) {
		getInstance().ipAddress(ipAddress);
	}

	public static String ipAddress() {
		return getInstance().ipAddress();
	}

	public static void port(int port) {
		getInstance().port(port);
	}

	public static int port() {
		return getInstance().port();
	}

	public static void awaitInitialization() {
		getInstance().awaitInitialization();
	}
	
	public static boolean isStarted() {
		return getInstance().isStarted();
	}
	
	public static void stop() {
		getInstance().stop();
	}
	
	public static void init() {
		getInstance().init();
	}
	
	public static void await() {
		getInstance().await();
	}

	public static void halt() {
		getInstance().halt();
	}
	
	public static void halt(int status) {
		getInstance().halt(status);
	}
	
	public static void halt(String body) {
		getInstance().halt(body);
	}
	
	public static void halt(int status, String body) {
		getInstance().halt(status, body);
	}
	
	public static void staticFileLocation(String folder) {
		getInstance().staticFileLocation(folder);
	}

	public static void staticFileLocation(String folder, String[] indexFiles) {
		getInstance().staticFileLocation(folder, indexFiles);
	}
	
	public static void staticFileLocationACL(String path, boolean allow) {
		getInstance().staticFileLocationACL(path, allow);
	}
	
	public static void externalStaticFileLocation(String externalFolder) {
		getInstance().externalStaticFileLocation(externalFolder);
	}
	
	public static void externalStaticFileLocation(String externalFolder, String[] indexFiles) {
		getInstance().externalStaticFileLocation(externalFolder, indexFiles);
	}

	public static void externalStaticFileLocationACL(String path, boolean allow) {
		getInstance().externalStaticFileLocationACL(path, allow);
	}
	
	public static void exception(Class<? extends Exception> exceptionClass, ExceptionHandler handler) {
		getInstance().exception(exceptionClass, handler);
	}

	public static void webSocket(String path, WebSocketHandler handler) {
		getInstance().webSocket(path, handler);
	}
	
	public static void secure(String keystoreFile,
			String keystorePassword,
			String truststoreFile,
			String truststorePassword) {
		getInstance().secure(keystoreFile, keystorePassword, truststoreFile, truststorePassword);
	}
	
	public static void threadPool(int maxThreads) {
		getInstance().threadPool(maxThreads);
	}
	
	public static ModelAndView modelAndView(ModelAndView modelAndView) {
		return modelAndView;
	}

	public static void clearRoutes() {
		getInstance().clearRoutes();
	}
	
	public static void clearBefore() {
		getInstance().clearBefore();
	}
	
	public static void clearAfter() {
		getInstance().clearAfter();
	}

	public static void defaultResponseTransformer(ResponseTransformer responseTransformer) {
		getInstance().defaultResponseTransformer(responseTransformer);
	}
	
	public static void before(FilterHandler handler) {
		getInstance().before(handler);
	}

	public static void before(String path, FilterHandler handler) {
		getInstance().before(path, handler);
	}

	public static void before(String path, String acceptType, FilterHandler hendler) {
		getInstance().before(path, acceptType, hendler);
	}

	public static void after(FilterHandler handler) {
		getInstance().after(handler);
	}

	public static void after(String path, FilterHandler handler) {
		getInstance().after(path, handler);
	}

	public static void after(String path, String acceptType, FilterHandler hendler) {
		getInstance().after(path, acceptType, hendler);
	}

	public static void get(String path, RouteHandler handler) {
		getInstance().get(path, handler);
	}
	
	public static void get(String path, String acceptType, RouteHandler handler) {
		getInstance().get(path, acceptType, handler);
	}

	public static void get(String path, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().get(path, handler, transformer);
	}
	
	public static void get(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().get(path, acceptType, handler, transformer);
	}
	
	public static void post(String path, RouteHandler handler) {
		getInstance().post(path, handler);
	}

	public static void post(String path, String acceptType, RouteHandler handler) {
		getInstance().post(path, acceptType, handler);
	}

	public static void post(String path, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().post(path, handler, transformer);
	}
	
	public static void post(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().post(path, acceptType, handler, transformer);
	}
	
	public static void put(String path, RouteHandler handler) {
		getInstance().put(path, handler);
	}

	public static void put(String path, String acceptType, RouteHandler handler) {
		getInstance().put(path, acceptType, handler);
	}

	public static void put(String path, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().put(path, handler, transformer);
	}
	
	public static void put(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().put(path, acceptType, handler, transformer);
	}
	
	public static void delete(String path, RouteHandler handler) {
		getInstance().delete(path, handler);
	}
	
	public static void delete(String path, String acceptType, RouteHandler handler) {
		getInstance().delete(path, acceptType, handler);
	}

	public static void delete(String path, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().delete(path, handler, transformer);
	}
	
	public static void delete(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().delete(path, acceptType, handler, transformer);
	}
	
	public static void head(String path, RouteHandler handler) {
		getInstance().head(path, handler);
	}

	public static void head(String path, String acceptType, RouteHandler handler) {
		getInstance().head(path, acceptType, handler);
	}

	public static void head(String path, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().head(path, handler, transformer);
	}
	
	public static void head(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().head(path, acceptType, handler, transformer);
	}
	
	public static void connect(String path, RouteHandler handler) {
		getInstance().connect(path, handler);
	}

	public static void connect(String path, String acceptType, RouteHandler handler) {
		getInstance().connect(path, acceptType, handler);
	}

	public static void connect(String path, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().connect(path, handler, transformer);
	}
	
	public static void connect(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().connect(path, acceptType, handler, transformer);
	}
	
	public static void trace(String path, RouteHandler handler) {
		getInstance().trace(path, handler);
	}

	public static void trace(String path, String acceptType, RouteHandler handler) {
		getInstance().trace(path, acceptType, handler);
	}

	public static void trace(String path, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().trace(path, handler, transformer);
	}
	
	public static void trace(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().trace(path, acceptType, handler, transformer);
	}
	
	public static void options(String path, RouteHandler handler) {
		getInstance().options(path, handler);
	}

	public static void options(String path, String acceptType, RouteHandler handler) {
		getInstance().options(path, acceptType, handler);
	}

	public static void options(String path, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().options(path, handler, transformer);
	}
	
	public static void options(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().options(path, acceptType, handler, transformer);
	}
	
	public static void patch(String path, RouteHandler handler) {
		getInstance().patch(path, handler);
	}

	public static void patch(String path, String acceptType, RouteHandler handler) {
		getInstance().patch(path, acceptType, handler);
	}

	public static void patch(String path, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().patch(path, handler, transformer);
	}
	
	public static void patch(String path, String acceptType, RouteHandler handler, ResponseTransformer transformer) {
		getInstance().patch(path, acceptType, handler, transformer);
	}
}
