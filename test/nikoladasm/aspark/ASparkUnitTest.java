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

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.After;
import org.junit.AfterClass;

import static org.hamcrest.CoreMatchers.*;

import static nikoladasm.aspark.ASpark.*;
import static nikoladasm.aspark.Routable.DEFAULT_VIEW_ENGINE;
import static java.nio.charset.StandardCharsets.UTF_8;

import nikoladasm.commons.dydamictypedmap.*;
import nikoladasm.simplehttpclient.HttpResponse;
import nikoladasm.simplehttpclient.SimpleHttpClient;

import org.junit.BeforeClass;
import org.junit.Test;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;

import static java.util.Collections.synchronizedList;
import static java.nio.file.StandardCopyOption.*;

public class ASparkUnitTest {

	private static SimpleHttpClient C = new SimpleHttpClient();
	
	private static final String IP_ADDRESS = "127.0.0.1";
	private static final int PORT = 50123;
	private static final String PATH = "http://"+IP_ADDRESS+":"+PORT;
	
	private static final String BODY_CONTENT = "the body content";
	
	private static final String NOT_FOUND_BRO = "Not found bro";
	
	private static final String CONTENT = "the content that will be compressed/decompressed";
	
	private static String AUTHOR = "FOO";
	private static String TITLE = "BAR";
	private static String NEW_TITLE = "SPARK";
	
	private String beforeBody = null;
	private String routeBody = null;
	private String afterBody = null;
	
	private String bookId;
	
	private static Map<String, Book> books = new HashMap<String, Book>();
	
	private HttpResponse clientResponse;
	private nikoladasm.simplehttpclient.ResponseTransformer<String> clResTr =
		(res, body) -> {
			clientResponse = res;
			String enc = res.header("Content-Encoding");
			if (enc != null && enc.equals("gzip")) {
				ByteArrayInputStream is = new ByteArrayInputStream(body);
				GZIPInputStream gis = new GZIPInputStream(is);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				byte[] buf = new byte[1024];
				int n;
				while ((n = gis.read(buf)) != -1) {
					os.write(buf, 0, n);
				}
				return new String(os.toByteArray(), UTF_8);
			}
			return new String(body, UTF_8);
		};
	
	private static File tmpExternalFile;
	
	public static final List<String> events = synchronizedList(new ArrayList<>());
		
	private static class Book {
		
		private String author;
		private String title;
		
		public Book(String author, String title) {
			this.author = author;
			this.title = title;
		}
		public String getAuthor() {return author;}
		public String getTitle() {return title;}
		public void setAuthor(String author) {this.author = author;}
		public void setTitle(String title) {this.title = title;}
	}
	
	private static class BaseException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	public static class NotFoundException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	private static class SubclassOfBaseException extends BaseException {
		private static final long serialVersionUID = 1L;
	}
	
	private static WebSocketHandler wsHandler = new WebSocketHandler() {
		
		@Override
		public void onConnect(WebSocketContext wctx) {
			events.add("onConnect");
		}
		
		@Override
		public void onClose(WebSocketContext wctx, int statusCode, String reason) {
			events.add(String.format("onClose: %s %s", statusCode, reason));
		}
		
		@Override
		public void onMessage(WebSocketContext wctx, String msg) {
			events.add("onMessage: " + msg);
		}
		
	};
	
	private static class TestWebSocketClient extends WebSocketClient {

		private final CountDownLatch closeLatch;
		
		public TestWebSocketClient(Draft d , URI uri) {
			super(uri, d);
			closeLatch = new CountDownLatch(1);
		}
		
		public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
			return closeLatch.await(duration, unit);
		}
		
		@Override
		public void onClose(int arg0, String arg1, boolean arg2) {
			closeLatch.countDown();
		}

		@Override
		public void onError(Exception ex) {
			System.out.println( "Error: " );
			ex.printStackTrace();
		}

		@Override
		public void onMessage(String message) {
		}
		
		@Override
		public void onOpen(ServerHandshake arg0) {
			this.getConnection().send("Hi ASpark!");
			this.getConnection().close(1000, "Bye!");
		}
	}
	
	@BeforeClass
	public static void setup() throws IOException {
		setupWebServerParams();
		setupStaticFiles();
		setupGeneral();
		setupBookAndHeaders();
		setupCookiesPath();
		awaitInitialization();
	}
	
	public static void setupWebServerParams() {
		InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
		ipAddress(IP_ADDRESS);
		port(PORT);
	}
	
	private static void setupStaticFiles() throws IOException {
		tmpExternalFile = new File(System.getProperty("java.io.tmpdir"), "externalFile.html");
		FileWriter writer = new FileWriter(tmpExternalFile);
		writer.write("Content of external file");
		writer.flush();
		writer.close();
		new File(System.getProperty("java.io.tmpdir")+"/pages").mkdir();
		Files.copy(Paths.get("resources/pages/index.html"), Paths.get(System.getProperty("java.io.tmpdir")+"/pages"), REPLACE_EXISTING);
		staticFileLocation("/resources/public");
		externalStaticFileLocation(System.getProperty("java.io.tmpdir"));
	}
	
	private static void setupGeneral() {
		exception(UnsupportedOperationException.class, (exception, request, response) -> {
			response.body("Exception handled");
		});
		
		exception(BaseException.class, (exception, request, response) -> {
			response.body("Exception handled");
		});
		
		exception(NotFoundException.class, (exception, request, response) -> {
			response.status(404);
			response.body(NOT_FOUND_BRO);
		});
	}
	
	public static void setupBookAndHeaders() {
		before((request, response) -> {
			response.header("FOZ", "BAZ");
		});
		
		post("/books", (request, response) -> {
			String author = request.queryParams("author");
			String title = request.queryParams("title");
			Book book = new Book(author, title);
			Random random = new Random();
			int id = random.nextInt(Integer.MAX_VALUE);
			books.put(String.valueOf(id), book);
			response.status(201);
			return id;
		});

		get("/books/:id", (request, response) -> {
			Book book = books.get(request.params(":id"));
			if (book != null) {
				return "Title: " + book.getTitle() + ", Author: " + book.getAuthor();
			} else {
				response.status(404);
				return "Book not found";
			}
		});

		put("/books/:id", (request, response) -> {
			String id = request.params(":id");
			Book book = books.get(id);
			if (book != null) {
				String newAuthor = request.queryParams("author");
				String newTitle = request.queryParams("title");
				if (newAuthor != null) {
					book.setAuthor(newAuthor);
				}
				if (newTitle != null) {
					book.setTitle(newTitle);
				}
				return "Book with id '" + id + "' updated";
			} else {
				response.status(404);
				return "Book not found";
			}
		});

		delete("/books/:id", (request, response) -> {
			String id = request.params(":id");
			Book book = books.remove(id);
			if (book != null) {
				return "Book with id '" + id + "' deleted";
			} else {
				response.status(404);
				return "Book not found";
			}
		});

		get("/books", (request, response) -> {
			String ids = "";
			for (String id : books.keySet()) {
				ids += id + " ";
			}
			return ids;
		});

		after((request, response) -> {
			response.header("FOO", "BAR");
		});
	}
	
	public static void setupCookiesPath() {
		post("/assertNoCookies", (request, response) -> {
			if (!request.cookies().isEmpty()) {
				halt(500);
			}
			return "";
		});
		
		post("/setCookie", (request, response) -> {
			response.cookie(request.queryParams("cookieName"), request.queryParams("cookieValue"));
			return "";
		});
		
		post("/assertHasCookie", (request, response) -> {
			String cookieValue = request.cookie(request.queryParams("cookieName"));
			if (!request.queryParams("cookieValue").equals(cookieValue)) {
				halt(500);
			}
			return "";
		});
		
		post("/removeCookie", (request, response) -> {
			String cookieName = request.queryParams("cookieName");
			String cookieValue = request.cookie(cookieName);
			if (!request.queryParams("cookieValue").equals(cookieValue)) {
				halt(500);
			}
			response.removeCookie(cookieName);
			return "";
		});
	}
	
	@AfterClass
	public static void down() {
		stop();
		if (tmpExternalFile != null) {
			tmpExternalFile.delete();
		}
	}
	
	@After
	public void clear() {
		books.clear();
		if (clientResponse != null && clientResponse.cookie() != null)
			clientResponse.cookie().clear();
	}
	
	@Test
	public void shouldBeGetIpAddress() {
		assertThat(ipAddress(),is(equalTo(IP_ADDRESS)));
	}
	
	@Test(expected=ASparkException.class)
	public void shouldBeExceptionDuringSetIpAddress() {
		ipAddress("192.168.0.1");
	}
	
	@Test
	public void shouldBeGetPort() {
		assertThat(port(),is(equalTo(PORT)));
	}
	
	@Test(expected=ASparkException.class)
	public void shouldBeExceptionDuringSetPort() {
		port(2222);
	}
	
	@Test
	public void shouldBeProcessSimpleGetRequest() {
		get("/simplegetrequest", (request, response) -> {
			return "Get answer";
		});
		
		assertThat(C.get(PATH+"/simplegetrequest"), is(equalTo("Get answer")));
	}
	
	@Test
	public void shouldBePostRequestBodyWithFilters() {
		String testPath = "/shouldbepostrequestbodywithfilters";
		
		before(testPath, (req, res) -> {
			beforeBody = req.body();
		});
		
		post(testPath, (req, res) -> {
			routeBody = req.body();
			return req.body();
		});
		
		after(testPath, (req, res) -> {
			afterBody = req.body();
		});
		
		String resSrt = C.post(PATH+testPath, BODY_CONTENT, clResTr);
		assertThat(resSrt, is(equalTo(BODY_CONTENT)));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(beforeBody, is(equalTo(BODY_CONTENT)));
		assertThat(routeBody, is(equalTo(BODY_CONTENT)));
		assertThat(afterBody, is(equalTo(BODY_CONTENT)));
	}

	@Test
	public void shouldBeAcceptTypeFilter() throws Exception {
		before("/accepttypefilter", "application/xml", (request, response) -> {
			halt(401, "<project><task></task></project>");
		});
		
		before("/accepttypefilter", "application/json", (request, response) -> {
			halt(401, "{\"project\": \"project_name\"}");
		});
		
		String resSrt = C.get(PATH+"/accepttypefilter", (request, body) -> {
			request.header("Accept", "application/json");
			return new byte[0];
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(401)));
		assertThat(resSrt, is(equalTo("{\"project\": \"project_name\"}")));
	}
	
	@Test
	public void shouldBeAcceptTypeRoute() throws Exception {
		get("/accepttyperoute", "application/json", (request, response) -> {
			return "{\"project\": \"project_name\"}";
		});
		
		get("/accepttyperoute", (request, response) -> {
			return "Project - project name";
		});
		
		String resSrt = C.get(PATH+"/accepttyperoute", (request, body) -> {
			request.header("Accept", "application/json");
			return new byte[0];
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("{\"project\": \"project_name\"}")));
	}
	
	@Test
	public void shouldBeRenderView() throws Exception {
		get("/renderview", (request, response) -> {
			String model = "Header";
			return modelAndView(() -> {
				return "<head>"+model+"</head>";
			});
		}, DEFAULT_VIEW_ENGINE);
		
		String resSrt = C.get(PATH+"/renderview", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("<head>Header</head>")));
	}
	
	@Test
	public void shouldBeBinarySerialization() {
		get("/binaryserialization", (request, response) -> {
			return "Binary message".getBytes(UTF_8);
		});
		
		String resSrt = C.get(PATH+"/binaryserialization", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("Binary message")));
	}
	
	@Test
	public void shouldBeByteBufferSerialization() {
		get("/bytebufferserialization", (request, response) -> {
			return ByteBuffer.wrap("ByteBuff message".getBytes(UTF_8));
		});
		
		String resSrt = C.get(PATH+"/bytebufferserialization", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("ByteBuff message")));
	}
	
	@Test
	public void shouldBeInputStreamSerialization() {
		get("/inputstreamserialization", (request, response) -> {
			return new ByteArrayInputStream("InputStream message".getBytes(UTF_8));
		});
		
		String resSrt = C.get(PATH+"/inputstreamserialization", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("InputStream message")));
	}
	
	@Test
	public void shouldBeProcessHeadRequestOnGetRoute() throws Exception {
		get("/headrequest", (request, response) -> {
			return "Answer";
		});
		
		String resSrt = C.head(PATH+"/headrequest", "", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("")));
	}
	
	@Test
	public void shouldBeGetHeaderAfterFilter() throws Exception {
		get("/afterfilter", (request, response) -> {
			return "";
		});
		
		after("/afterfilter", (request, response) -> {
			response.header("after", "filter");
		});
		
		C.get(PATH+"/afterfilter", clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.header("after"), is(containsString("filter")));
	}
	
	@Test
	public void shouldBeGetRoot() throws Exception {
		get("/", (request, response) -> {
			return "root";
		});
		
		String resSrt = C.get(PATH+"/", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("root")));
	}
	
	@Test
	public void shouldBeGetParamAndWild() throws Exception {
		get("/paramandwild/:param/stuff/*", (request, response) -> {
			return "paramandwild: " + request.params(":param") + request.splat()[0];
		});
		
		String resSrt = C.get(PATH+"/paramandwild/thedude/stuff/andits", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("paramandwild: thedudeandits")));
	}
	
	@Test
	public void shouldBeGetParamValue() throws Exception {
		get("/param/:param", (request, response) -> {
			return "echo: " + request.params(":param");
		});
		
		String resSrt = C.get(PATH+"/param/param_value", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("echo: param_value")));
	}
	
	@Test
	public void shouldBeGetPolyglotParam() throws Exception {
		get("/param_p/:param", (request, response) -> {
			return "echo: " + request.params(":param");
		});
		
		String polyglot = "жξ Ä 聊";
		String encoded = "%D0%B6%CE%BE%20%C3%84%20%E8%81%8A";
		String resSrt = C.get(PATH+"/param_p/" + encoded, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("echo: " + polyglot)));
	}
	
	@Test
	public void shouldBeGetParamValueWithCamelCase() throws Exception {
		get("/param_u/:param", (request, response) -> {
			return "echo: " + request.params(":param");
		});
		
		final String camelCased = "ThisIsCamelCaseValue";
		String resSrt = C.get(PATH+"/param_u/" + camelCased, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("echo: " + camelCased)));
	}
	
	@Test
	public void shouldBeGetTwoRoutesWithDifferentCaseButSameName() throws Exception {
		String lowerCasedRoutePart = "param";
		String upperCasedRoutePart = "PARAM";
		
		registerEchoRoute(lowerCasedRoutePart);
		registerEchoRoute(upperCasedRoutePart);
		assertEchoRoute(lowerCasedRoutePart);
		assertEchoRoute(upperCasedRoutePart);
	}
	
	private void registerEchoRoute(final String routePart) {
		get("/tworoutes/" + routePart + "/:param", (request, response) -> {
			return routePart + " route: " + request.params(":param");
		});
	}
	 
	private void assertEchoRoute(String routePart) throws Exception {
		final String expected = "expected";
		String resSrt = C.get(PATH+"/tworoutes/" + routePart + "/" + expected, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo(routePart + " route: " + expected)));
	}
 
	@Test
	public void shouldBeUnauthorized() throws Exception {
		before("/secretcontent/*", (request, response) -> {
			halt(401, "Go Away!");
		});
		
		C.get(PATH+"/secretcontent/info", clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(401)));
	}
	
	@Test
	public void shouldBeApplayDenyACL() throws Exception {
		before("*.abc", (request, response) -> {
			halt(401, "Empty");
		});
		
		C.get(PATH+"/123.abc", clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(401)));
	}
	
	@Test
	public void shouldBeNotFound() throws Exception {
		C.get(PATH+"/no/resource", clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(404)));
	}
	
	@Test
	public void shouldBePost() throws Exception {
		post("/poster", (request, response) -> {
			String body = request.body();
			response.status(201);
			return "Body was: " + body;
		});
		
		String resSrt = C.post(PATH+"/poster", "post message", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(201)));
		assertThat(resSrt, is(containsString("post message")));
	}
	
	@Test
	public void shouldBeDeleteViaPostWithMethodOverrideHeader() throws IOException {
		delete("/delete_via_post", (request, response) -> {
			response.status(201);
			return "Method override worked";
		});
		
		post("/delete_via_post", (request, response) -> {
			return "Method override didn't work";
		});
		
		String resSrt = C.post(PATH+"/delete_via_post", "", (request, body) -> {
			request.header("X-HTTP-Method-Override", "DELETE");
			return body.getBytes(UTF_8);
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(201)));
		assertThat(resSrt, is(containsString("Method override worked")));
	}
	
	@Test
	public void shouldBePatch() throws Exception {
		patch("/patcher", (request, response) -> {
			String body = request.body();
			response.status(200);
			return "Body was: " + body;
		});
		
		String resSrt = C.post(PATH+"/patcher", "patch message", (request, body) -> {
			request.header("X-HTTP-Method-Override", "PATCH");
			return body.getBytes("UTF-8");
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString("Body was: patch message")));

		MediaType contentType = MediaType.parse("text/plain; charset=utf-8");
		RequestBody body = RequestBody.create(contentType, "".getBytes(UTF_8));
		Request request = new Request.Builder()
			.method("PATCH", body)
			.url(PATH+"/patcher")
			.build();
		OkHttpClient client = new OkHttpClient();
		com.squareup.okhttp.Response response = client.newCall(request).execute();
		assertThat(response, is(notNullValue()));
		assertThat(response.body().string(), is(notNullValue()));
		assertThat(response.code(), is(equalTo(200)));
	}
	
	@Test
	public void shouldBeExceptionMapper() throws Exception {
		get("/throwexception", (request, response) -> {
			throw new UnsupportedOperationException();
		});
		
		String resSrt = C.get(PATH+"/throwexception", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(resSrt, is(equalTo("Exception handled")));
	}
	
	@Test
	public void shouldBeInheritanceExceptionMapper() throws Exception {
		get("/throwsubclassofbaseexception", (request, response) -> {
			throw new SubclassOfBaseException();
		});
		
		String resSrt = C.get(PATH+"/throwsubclassofbaseexception", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(resSrt, is(equalTo("Exception handled")));
	}
	
	@Test
	public void shouldBeNotFoundExceptionMapper() throws Exception {
		get("/thrownotfound", (request, response) -> {
			throw new NotFoundException();
		});
		
		String resSrt = C.get(PATH+"/thrownotfound", clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(404)));
		assertThat(resSrt, is(notNullValue()));
		assertThat(resSrt, is(equalTo(NOT_FOUND_BRO)));
	}
	
	@Test
	public void shouldBeWebSocketConversation() throws Exception {
		webSocket("/ws", wsHandler);
		
		String uri = "ws://"+IP_ADDRESS+":"+PORT+"/ws";
		Draft d = new Draft_17();
		TestWebSocketClient client = new TestWebSocketClient(d, URI.create(uri) );
		new Thread(client).start();
		client.awaitClose(30, TimeUnit.SECONDS);
		client.close();
		assertThat(events.size(), is(equalTo(3)));
		assertThat(events.get(0), is(equalTo("onConnect")));
		assertThat(events.get(1), is(equalTo("onMessage: Hi ASpark!")));
		assertThat(events.get(2), is(equalTo("onClose: 1000 Bye!")));
	}

	@Test
	public void shouldBeGzipCompression() throws Exception {
		post("/zipped", (request, response) -> {
			return request.body();
		});
		
		String resSrt = C.post(PATH+"/zipped", CONTENT, (request, body) -> {
			request.header("Accept-Encoding", "gzip");
			request.header("Content-Encoding", "gzip");
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			GZIPOutputStream gos = new GZIPOutputStream(os);
			gos.write(body.getBytes("UTF-8"));
			gos.close();
			return os.toByteArray();
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo(CONTENT)));
	}
	
	@Test
	public void shouldBeStaticFile() throws Exception {
		String resSrt = C.get(PATH+"/css/style.css", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("Content of css file")));
	}
	
	@Test
	public void shouldBeStaticFileWithGzipCompression() throws Exception {
		String resSrt = C.get(PATH+"/css/style.css", (request, body) -> {
			request.header("Accept-Encoding", "gzip");
			return new byte[0];
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("Content of css file")));
	}
	
	@Test
	public void shouldBeExternalStaticFile() throws Exception {
		String resSrt = C.get(PATH+"/externalFile.html", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("Content of external file")));
	}
	
	@Test
	public void shouldBeStaticFilePagesIndexHtml() throws Exception {
		String resSrt = C.get(PATH+"/pages/", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("<html><body>Hello Static World!</body></html>")));
	}

	@Test
	public void shouldBeQueryParamsMap() throws Exception {
		get("/queryparamsmap", (request, response) -> {
			String firstname = request.queryMap("user").get("firstname").values()[0];
			String lastname = request.queryMap("user").get("lastname").values()[0];
			return "First name \""+firstname+"\", last name \""+lastname+"\"";
		});
		String resSrt = C.get(PATH+"/queryparamsmap?user[firstname]=best&user[lastname]=user", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("First name \"best\", last name \"user\"")));
	}
	
	@Test
	public void shouldBeGetHttpMethods() {
		post("/httpmethod", (request, response) -> {
			return "Original http method: "+request.originalMethod()+". Route http method: "+request.method();
		});
		
		String resSrt = C.get(PATH+"/httpmethod", (request, body) -> {
			request.header("X-HTTP-Method-Override", "POST");
			return new byte[0];
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString("Original http method: GET. Route http method: POST")));
	}
	
	@Test
	public void shouldBeGetUserAgent() {
		get("/useragent", (request, response) -> {
			return request.userAgent();
		});
		
		String resSrt = C.get(PATH+"/useragent", (request, body) -> {
			request.header("User-Agent", "IE");
			return new byte[0];
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString("IE")));
	}
	
	@Test
	public void shouldBeGetpathInfo() {
		get("/pathinfo", (request, response) -> {
			return request.pathInfo();
		});
		
		String resSrt = C.get(PATH+"/pathinfo", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString("/pathinfo")));
	}
	
	@Test
	public void shouldBeGetContentType() {
		get("/contenttype", (request, response) -> {
			return request.contentType();
		});
		
		String resSrt = C.get(PATH+"/contenttype", (request, body) -> {
			request.header("Content-Type", "text/plain");
			return new byte[0];
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString("text/plain")));
	}
	
	@Test
	public void shouldBeGetContentLength() {
		post("/contentlength", (request, response) -> {
			return new Integer(request.contentLength()).toString();
		});
		
		String resSrt = C.post(PATH+"/contentlength", "abc", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString("3")));
	}
	
	@Test
	public void shouldBeGetRequestAttrubute() {
		before("/requestattrubute", (request, response) -> {
			DydamicTypedKey<String> key = DydamicTypedKey.valueOf(String.class, "str");
			request.value(key).set("Test attribute");
		});
		post("/requestattrubute", (request, response) -> {
			DydamicTypedKey<String> key = DydamicTypedKey.valueOf(String.class, "str");
			return request.value(key).get();
		});
		
		String resSrt = C.post(PATH+"/requestattrubute", "abc", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString("Test attribute")));
	}
	
	@Test
	public void shouldBeSetContentType() {
		get("/contenttype1", (request, response) -> {
			response.type("text/mixed");
			return null;
		});
		
		C.get(PATH+"/contenttype1", clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(clientResponse.header("Content-Type"), is(equalTo("text/mixed")));
	}
	
	@Test
	public void shouldBeRedirect() {
		get("/redirect", (request, response) -> {
			response.redirect(PATH+"/");;
			return new byte[0];
		});
		
		String resSrt = C.get(PATH+"/redirect", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString("root")));
	}
	
	@Test
	public void shouldBeGetParamValueImParamsMap() throws Exception {
		get("/parammap/:param", (request, response) -> {
			return "echo: " + request.paramsMap(":param").value();
		});
		
		String resSrt = C.get(PATH+"/parammap/param_value", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("echo: param_value")));
	}
	
	@Test
	public void shouldBePostParamsMap() throws Exception {
		post("/postparamsmap", (request, response) -> {
			String firstname = request.postMap("user").get("firstname").values()[0];
			String lastname = request.postMap("user").get("lastname").values()[0];
			return "First name \""+firstname+"\", last name \""+lastname+"\"";
		});
		String rqbody = "user[firstname]=best&user[lastname]=user";
		String resSrt = C.post(PATH+"/postparamsmap", rqbody, (request, body) -> {
			request.header("Content-Type", "application/x-www-form-urlencoded");
			return body.getBytes(UTF_8);
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("First name \"best\", last name \"user\"")));
	}
	
	//////////////////////////////////////////////////////////////////////////////
	
	@Test
	public void shouldBeCanCreateBook() {
		String testPath = "/books?author=" + AUTHOR + "&title=" + TITLE;
		String resSrt = C.post(PATH+testPath, "", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(Integer.valueOf(resSrt), is(not(0)));
		assertThat(clientResponse.status(), is(equalTo(201)));
	}
	
	@Test
	public void shouldBeCanListBooks() {
		String preparePath = "/books?author=" + AUTHOR + "&title=" + TITLE;
		bookId = C.post(PATH+preparePath, "", clResTr);
		String resSrt = C.get(PATH+"/books", clResTr).trim();
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(Integer.valueOf(resSrt), is(not(0)));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString(bookId)));
	}
	
	@Test
	public void shouldBeCanGetBook() {
		String preparePath = "/books?author=" + AUTHOR + "&title=" + TITLE;
		bookId = C.post(PATH+preparePath, "", clResTr);
		String resSrt = C.get(PATH+"/books/" + bookId, clResTr).trim();
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString(AUTHOR)));
		assertThat(resSrt, is(containsString(TITLE)));
		assertThat(clientResponse.header("FOZ"), is(equalTo("BAZ")));
		assertThat(clientResponse.header("FOO"), is(equalTo("BAR")));
	}
	
	@Test
	public void shouldBeCanUpdateBook() {
		String preparePath = "/books?author=" + AUTHOR + "&title=" + TITLE;
		bookId = C.post(PATH+preparePath, "", clResTr);
		String testPath = "/books/" + bookId + "?title=" + NEW_TITLE;
		String resSrt = C.put(PATH+testPath, "", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString(bookId)));
		assertThat(resSrt, is(containsString("updated")));		
		resSrt = C.get(PATH+"/books/" + bookId, clResTr).trim();
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString(AUTHOR)));
		assertThat(resSrt, is(containsString(NEW_TITLE)));
	}
	
	@Test
	public void shouldBeCanDeleteBook() {
		String preparePath = "/books?author=" + AUTHOR + "&title=" + TITLE;
		bookId = C.post(PATH+preparePath, "", clResTr);
		String resSrt = C.delete(PATH+"/books/" + bookId, "", clResTr).trim();
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(containsString(bookId)));
		assertThat(resSrt, is(containsString("deleted")));		
	}
	
	@Test
	public void wontFindBook() throws IOException {
		C.get(PATH+"/books/" + 345, clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(404)));
	}
	
	//////////////////////////////////////////////////////////////////////////////
	
	@Test
	public void shouldBeEmptyCookies() {
		C.post(PATH+"/assertNoCookies", "", clResTr);
		assertThat(clientResponse.status(), is(equalTo(200)));
	}
	
	@Test
	public void shouldBeCreateCookie() {
		String cookieName = "testCookie";
		String cookieValue = "testCookieValue";
		String preparePath = "/setCookie?cookieName=" + cookieName + "&cookieValue=" + cookieValue;
		C.post(PATH+preparePath, "", clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(clientResponse.cookie().get(cookieName), is(equalTo(cookieValue)));
		preparePath = "/assertHasCookie?cookieName=" + cookieName + "&cookieValue=" + cookieValue;
		C.post(PATH+preparePath, "", (request, body) -> {
			request.cookie(cookieName, clientResponse.cookie().get(cookieName));
			return body.toString().getBytes("UTF-8");
		}, clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
	}
	
	@Test
	public void shouldBeRemoveCookie() {
		String cookieName = "testCookie";
		String cookieValue = "testCookieValue";
		String preparePath = "/setCookie?cookieName=" + cookieName + "&cookieValue=" + cookieValue;
		C.post(PATH+preparePath, "", clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(clientResponse.cookie().get(cookieName), is(equalTo(cookieValue)));
		preparePath = "/removeCookie?cookieName=" + cookieName + "&cookieValue=" + cookieValue;
		C.post(PATH+preparePath, "", (request, body) -> {
			request.cookie(cookieName, clientResponse.cookie().get(cookieName));
			return body.toString().getBytes("UTF-8");
		}, clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		C.post(PATH+"/assertNoCookies", "", clResTr);
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
	}
	
}
