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

import static nikoladasm.aspark.ASpark.*;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.AfterClass;

import static org.hamcrest.CoreMatchers.*;

import org.junit.BeforeClass;
import org.junit.Test;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import nikoladasm.simplehttpclient.HttpResponse;
import nikoladasm.simplehttpclient.SimpleHttpClient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class ASparkSslUnitTest {

	private static SimpleHttpClient C = new SimpleHttpClient();
	
	private static final String IP_ADDRESS = "127.0.0.1";
	private static final int PORT = 50123;
	private static final String SSL_PATH = "https://"+IP_ADDRESS+":"+PORT+"/ssl";
	
	private HttpResponse clientResponse;
	private nikoladasm.simplehttpclient.ResponseTransformer<String> clResTr =
		(res, body) -> {
			clientResponse = res;
			return new String(body, UTF_8);
		};
		
	

	private static void setupStaticFiles() throws IOException {
		new File(System.getProperty("java.io.tmpdir")+"/ssl/pages").mkdirs();
		Files.copy(Paths.get("resources/pages/index.html"), Paths.get(System.getProperty("java.io.tmpdir")+"/ssl/pages"), REPLACE_EXISTING);
		externalStaticFileLocation(System.getProperty("java.io.tmpdir"));
	}
		
	@BeforeClass
	public static void setup() throws IOException {
		InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
		ipAddress(IP_ADDRESS);
		port(PORT);
		secure("resources/keystore.jks", "password", null, null);
		setupStaticFiles();
		init();
		awaitInitialization();
	}
	
	@AfterClass
	public static void down() {
		stop();
	}
	
	@Test
	public void shouldBeGetParamValue() throws Exception {
		get("/ssl/param/:param", (request, response) -> {
			return "echo: " + request.params(":param");
		});
		
		String resSrt = C.get(SSL_PATH+"/param/param_value", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("echo: param_value")));
	}
	
	@Test
	public void shouldBeDeleteViaPostWithMethodOverrideHeader() throws IOException {
		delete("/ssl/delete_via_post", (request, response) -> {
			response.status(201);
			return "Method override worked";
		});
		
		post("/ssl/delete_via_post", (request, response) -> {
			return "Method override didn't work";
		});
		
		String resSrt = C.post(SSL_PATH+"/delete_via_post", "", (request, body) -> {
			request.header("X-HTTP-Method-Override", "DELETE");
			return body.getBytes(UTF_8);
		}, clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(201)));
		assertThat(resSrt, is(containsString("Method override worked")));
	}

	@Test
	public void shouldBeStaticFilePagesIndexHtml() throws Exception {
		String resSrt = C.get(SSL_PATH+"/pages/", clResTr);
		assertThat(resSrt, is(notNullValue()));
		assertThat(clientResponse, is(notNullValue()));
		assertThat(clientResponse.status(), is(equalTo(200)));
		assertThat(resSrt, is(equalTo("<html><body>Hello Static World!</body></html>")));
	}
}
