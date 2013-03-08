package com.nature.client.http;

import java.io.IOException;

import com.nature.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class UserApiClientTest {

	public static void main(String[] args) {
		JokerServer ps = new JokerServer();
		//ps.stop();
		SimpleHttpSender sender = new SimpleHttpSender("http://test-www.nature.com/api/users");
		Multival params = new Multival();
		params.add("login", "aqyhyu.lhbur@dhhkys.jcc&|-1cLWZVIUeq2k&|0");
		Multival headers = new Multival();
		try {

			SimpleHttpSender s = new SimpleHttpSender("http://local.nature.com:6666/");

			Multival p = new Multival();
			p.add("wait", "5");
			SenderResponse call = s.execute(new GetRequest("", p));
			System.out.println(call);

			SenderRequest request = new PostRequest("/auth", params);
			SenderResponse response = sender.execute(request);
			System.out.println(response);
			//String string = IOUtils.toString(response.getStream());
			//System.out.println(string);
			response.close();

			SenderRequest request2 = new GetRequest("/email/martin.vanek@nature.com", params).setHeaders(headers);
			SenderResponse response2 = sender.execute(request2);
			System.out.println(response2);

			HttpClient4Sender hs = new HttpClient4Sender("http://www.google.com");
			SenderRequest hrq = new GetRequest("/");
			SenderResponse hrs = hs.execute(hrq);
			System.out.println(hrs);
			hrs.close();
			hrs = hs.execute(hrq);

			SimpleHttpSender sender2 = new SimpleHttpSender("http://www.google.com");
			SenderResponse response3 = sender2.execute(new GetRequest());
			System.out.println(response3);
			//String string2 = IOUtils.toString(response2.getStream());
			//System.out.println(string2);
			/*
			URL url = new URL("http://www.google.com/cesta/z/mesta?pnam=pval");
			System.out.println(url.getProtocol());
			System.out.println(url.getHost());
			System.out.println(url.getPort());
			System.out.println(url.getPath());
			System.out.println(url.getQuery());
			System.out.println(url.getRef());
			System.out.println(url.getAuthority());
			System.out.println(url.getUserInfo());
			*/
		} catch (IOException iox) {
			iox.printStackTrace();
		} finally {
			try {
				ps.stop();
			} catch (Exception x) {
			}
		}

	}
}
