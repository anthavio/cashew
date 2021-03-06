package com.nature.client.http;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fest.assertions.api.Fail;
import org.testng.annotations.Test;

import com.nature.client.http.Authentication.Scheme;
import com.nature.client.http.HttpSender.Multival;
import com.nature.client.http.SenderRequest.Method;

/**
 * 
 * @author martin.vanek
 *
 */
public class SimpleTest {

	@Test
	public void requestUrl() {
		SimpleHttpSender sender = new SimpleHttpSender("www.somewhere.com");
		assertThat(sender.getConfig().getUrl().toString()).isEqualTo("http://www.somewhere.com/"); //add prefix and suffix
		//SimpleHttpSender sender = null;

		GetRequest rGet = new GetRequest("path");
		String[] paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path");
		assertThat(paq[1]).isNull();

		rGet.addParameter("p1", null);
		paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path?p1=");
		assertThat(paq[1]).isEqualTo("p1=");

		rGet.addParameter("p2", 1);
		paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path?p1=&p2=1");
		assertThat(paq[1]).isEqualTo("p1=&p2=1");

		rGet.addParameter(";m1", null); //add matrix parameter
		paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path;m1=?p1=&p2=1");
		assertThat(paq[1]).isEqualTo("p1=&p2=1");

		rGet.addParameter(";m2", 2); //add matrix parameter
		paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path;m1=;m2=2?p1=&p2=1");
		assertThat(paq[1]).isEqualTo("p1=&p2=1");

		//now with POST request

		PostRequest rPost = new PostRequest("path");
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path");
		assertThat(paq[1]).isNull();

		rPost.addParameter("p1", null);
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path");
		assertThat(paq[1]).isEqualTo("p1=");

		rPost.addParameter("p2", 1);
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path");
		assertThat(paq[1]).isEqualTo("p1=&p2=1");

		rPost.addParameter(";m1", null); //add matrix parameter
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path;m1=");
		assertThat(paq[1]).isEqualTo("p1=&p2=1");

		rPost.addParameter(";m2", 2); //add matrix parameter
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path;m1=;m2=2");
		assertThat(paq[1]).isEqualTo("p1=&p2=1");

		//setting custom POST body moves query parameters from body to path 
		rPost.setBodyStream(new ByteArrayInputStream(new byte[0]), "application/json; charset=utf-8");
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path;m1=;m2=2?p1=&p2=1");
		assertThat(paq[1]).isEqualTo("p1=&p2=1");
	}

	@Test
	public void requestParams() {
		SenderRequest request = new GetRequest();
		assertThat(request.getMethod()).isEqualTo(Method.GET);
		assertThat(request.getUrlPath()).isNull();
		assertThat(request.getParameters()).isNull();
		assertThat(request.getHeaders()).isNull();

		request = new PostRequest();
		assertThat(request.getMethod()).isEqualTo(Method.POST);
		assertThat(request.getUrlPath()).isNull();
		assertThat(request.getParameters()).isNull();
		assertThat(request.getHeaders()).isNull();

		Multival parameters = new Multival(
				Arrays.asList(new String[][] { { "p1", "y" }, { "p2", "a", "b", "c" }, { "p3" } }));
		Map<String, List<String>> mHeaders = new HashMap<String, List<String>>();
		mHeaders.put("h1", Arrays.asList("v1"));
		mHeaders.put("h2", Arrays.asList("v21", "v22", "v23"));
		mHeaders.put("h3", Collections.EMPTY_LIST);
		Multival headers = new Multival(mHeaders);
		String path = "/path/to/somewhere?pname=pvalue";
		request = new PostRequest(path, parameters).setHeaders(headers);
		assertThat(request.getMethod()).isEqualTo(Method.POST);
		assertThat(request.getUrlPath()).isEqualTo(path);

		assertThat(request.getParameters()).isEqualTo(parameters);
		assertThat(request.getParameters().size()).isEqualTo(3);
		assertThat(request.getParameters().names().size()).isEqualTo(3);

		assertThat(request.getParameters().get("p1")).hasSize(1);
		assertThat(request.getParameters().getFirst("p1")).isEqualTo("y");
		assertThat(request.getParameters().getLast("p1")).isEqualTo("y");
		assertThat(request.getParameters().get("p2")).hasSize(3);
		assertThat(request.getParameters().getFirst("p2")).isEqualTo("a");
		assertThat(request.getParameters().getLast("p2")).isEqualTo("c");
		assertThat(request.getParameters().get("p3")).hasSize(1);
		assertThat(request.getParameters().getFirst("p3")).isEqualTo("");
		assertThat(request.getParameters().getLast("p3")).isEqualTo("");

		assertThat(request.getHeaders()).isEqualTo(headers);
		assertThat(request.getHeaders().size()).isEqualTo(3);
		assertThat(request.getHeaders().names().size()).isEqualTo(3);

		assertThat(request.getHeaders().get("h1")).hasSize(1);
		assertThat(request.getHeaders().getFirst("h1")).isEqualTo("v1");
		assertThat(request.getHeaders().getLast("h1")).isEqualTo("v1");

		assertThat(request.getHeaders().get("h2")).hasSize(3);
		assertThat(request.getHeaders().getFirst("h2")).isEqualTo("v21");
		assertThat(request.getHeaders().getLast("h2")).isEqualTo("v23");

		assertThat(request.getHeaders().get("h3")).hasSize(1);
		assertThat(request.getHeaders().getFirst("h3")).isEqualTo("");
		assertThat(request.getHeaders().getLast("h3")).isEqualTo("");

		//parameteres allow duplicit names
		request.addParameter("param", "something");
		assertThat(request.getParameters().get("param")).hasSize(1);
		request.addParameter("param", "something");
		assertThat(request.getParameters().get("param")).hasSize(2);

		//header do not allow duplicit names
		request.setHeader("header", "something");
		assertThat(request.getHeaders().get("header")).hasSize(1);
		request.setHeader("header", "something");
		assertThat(request.getHeaders().get("header")).hasSize(1);
	}

	@Test
	public void simpleDefauts() {
		String url = "http://www.hostname.com:8080/path/to/somewhere";
		SimpleHttpSender sender = new SimpleHttpSender(url);
		assertThat(sender.getConfig().getUrl().toString()).isEqualTo(url);
		assertThat(sender.getConfig().getEncoding()).isEqualTo("UTF-8");
		assertThat(sender.getConfig().getAuthentication()).isNull();

		//default authentication is BASIC and preepmtive
		Authentication authentication = new Authentication("user", "pass");
		assertThat(authentication.getScheme()).isEqualTo(Authentication.Scheme.BASIC);
		assertThat(authentication.getPreemptive()).isEqualTo(true);
	}

	@Test
	public void http4() {
		String url = "http://www.hostname.com:8080/path/to/somewhere";
		HttpClient4Config config = new HttpClient4Config(url);
		Authentication authentication = new Authentication(Scheme.DIGEST, "user", "pass", false);
		config.setAuthentication(authentication);
		HttpClient4Sender sender = new HttpClient4Sender(config);

		assertThat(sender.getConfig().getUrl().toString()).isEqualTo(url);
		assertThat(sender.getConfig().getEncoding()).isEqualTo("UTF-8");
		assertThat(sender.getConfig().getAuthentication().getPreemptive()).isFalse();
	}

	@Test
	public void senderBadParameters() {
		SimpleHttpSender sender;
		try {
			sender = new SimpleHttpSender((String) null);
			Fail.fail("Previous statemet must throw IllegalArgumentException");
		} catch (IllegalArgumentException iax) {
			//ok
		}
		try {
			sender = new SimpleHttpSender((HttpSenderConfig) null);
			Fail.fail("Previous statemet must throw IllegalArgumentException");
		} catch (IllegalArgumentException iax) {
			//ok
		}
		try {
			sender = new SimpleHttpSender("");
			Fail.fail("Previous statemet must throw IllegalArgumentException");
		} catch (IllegalArgumentException iax) {
			//ok
		}
		try {
			sender = new SimpleHttpSender("http:///");
			Fail.fail("Previous statemet must throw IllegalArgumentException");
		} catch (IllegalArgumentException iax) {
			//ok
		}
	}

}
