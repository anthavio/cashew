package com.nature.client.http;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class AuthenticationTest {

	private JokerServer server = new JokerServer();

	@BeforeClass
	public void setup() throws Exception {
		this.server.start();
	}

	@AfterClass
	public void destroy() throws Exception {
		this.server.stop();
	}

	@Test
	public void simple() throws IOException {

		System.setProperty("http.keepAlive", "false");

		String url = "http://localhost:" + this.server.getHttpPort();
		SimpleHttpSender sender = new SimpleHttpSender(url);

		SenderRequest request = sender.newGetRequest();
		request.addParameter("x", "y");

		SenderResponse response = sender.execute(request);
		//can access unprotected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		request = sender.newGetRequest("/basic");
		response = sender.execute(request);
		//can't access BASIC protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		request = sender.newGetRequest("/digest");
		response = sender.execute(request);
		//can't access DIGEST protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		//setup BASIC authentication

		HttpSenderConfig config = new HttpSenderConfig(url);
		Authentication authentication = new Authentication("lajka", "haf!haf!");
		//authentication.setPreemptive(false);
		config.setAuthentication(authentication);
		sender = new SimpleHttpSender(config);
		request = sender.newGetRequest("/basic");
		response = sender.execute(request);
		//can access BASIC protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		sender = new SimpleHttpSender(config);
		request = sender.newGetRequest("/digest");
		response = sender.execute(request);
		//can access DIGEST protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		//setup DIGEST authentication

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "lajka", "haf!haf!");
		config.setAuthentication(digest);
		sender = new SimpleHttpSender(config);
		request = sender.newGetRequest("/basic");
		response = sender.execute(request);
		//can access BASIC protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		sender = new SimpleHttpSender(config);
		request = sender.newGetRequest("/digest");
		response = sender.execute(request);
		//can access DIGEST protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
	}

	@Test
	public void http3() throws IOException {
		SenderRequest request = new GetRequest();
		request.addParameter("x", "y");

		String url = "http://localhost:" + this.server.getHttpPort();
		SenderResponse response = new HttpClient3Sender(url).execute(request);
		//can access unprotected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		request = new GetRequest("/basic");
		response = new HttpClient3Sender(url).execute(request);
		//can't access BASIC protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		request = new GetRequest("/digest");
		response = new HttpClient3Sender(url).execute(request);
		//can't access DIGEST protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		//setup BASIC authentication

		HttpClient3Config config = new HttpClient3Config(url);
		Authentication authentication = new Authentication("lajka", "haf!haf!");
		//XXX authentication.setPreemptive(false);
		config.setAuthentication(authentication);
		request = new GetRequest("/basic");
		response = new HttpClient3Sender(config).execute(request);
		//can access BASIC protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		request = new GetRequest("/digest");
		response = new HttpClient3Sender(config).execute(request);
		//can access DIGEST protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		//setup DIGEST authentication

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "lajka", "haf!haf!");
		config.setAuthentication(digest);
		request = new GetRequest("/basic");
		response = new HttpClient3Sender(config).execute(request);
		//can access BASIC protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		request = new GetRequest("/digest");
		response = new HttpClient3Sender(config).execute(request);
		//can access DIGEST protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
	}

	@Test
	public void http4() throws IOException {
		SenderRequest request = new GetRequest();
		request.addParameter("x", "y");

		String url = "http://localhost:" + this.server.getHttpPort();
		SenderResponse response = new HttpClient4Sender(url).execute(request);
		//can access unprotected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		request = new GetRequest("/basic");
		response = new HttpClient4Sender(url).execute(request);
		//can't access BASIC protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		request = new GetRequest("/digest");
		response = new HttpClient4Sender(url).execute(request);
		//can't access DIGEST protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);

		//setup BASIC authentication

		HttpClient4Config config = new HttpClient4Config(url);
		Authentication basic = new Authentication("lajka", "haf!haf!");
		//XXX authentication.setPreemptive(false);
		config.setAuthentication(basic);
		request = new GetRequest("/basic");
		response = new HttpClient4Sender(config).execute(request);
		//can access BASIC protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		request = new GetRequest("/digest");
		response = new HttpClient4Sender(config).execute(request);
		//can access DIGEST protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		//setup DIGEST authentication

		Authentication digest = new Authentication(Authentication.Scheme.DIGEST, "lajka", "haf!haf!");
		config.setAuthentication(digest);
		request = new GetRequest("/basic");
		response = new HttpClient4Sender(config).execute(request);
		//can access BASIC protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

		request = new GetRequest("/digest");
		response = new HttpClient4Sender(config).execute(request);
		//can access DIGEST protected
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
	}

}
