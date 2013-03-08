package com.nature.client.http;

import static org.fest.assertions.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import com.nature.client.http.cache.CachedResponse;
import com.nature.client.http.cache.CachingSender;
import com.nature.client.http.cache.RequestCache;
import com.nature.client.http.cache.SimpleRequestCache;

/**
 * 
 * @author martin.vanek
 *
 */
public class DevelopmentTest {

	public static void main(String[] args) {
		JokerServer server = new JokerServer().start();
		try {

			HttpSender sender1 = new HttpClient4Sender("127.0.0.1:" + server.getHttpPort());
			HttpSender sender2 = new HttpClient4Sender("localhost:" + server.getHttpPort());
			//shared cache for 2 senders
			RequestCache<CachedResponse> cache = new SimpleRequestCache<CachedResponse>();
			CachingSender csender1 = new CachingSender(sender1, cache);
			CachingSender csender2 = new CachingSender(sender2, cache);
			GetRequest request1 = new GetRequest();
			GetRequest request2 = new GetRequest();

			SenderResponse response1 = csender1.execute(request1, 1, TimeUnit.SECONDS);
			SenderResponse response2 = csender2.execute(request2, 1, TimeUnit.SECONDS);
			assertThat(response2).isNotEqualTo(response1); //different sender - different host!

			//switch Request execution to different Sender
			SenderResponse response3 = csender1.execute(request2, 1, TimeUnit.SECONDS);
			assertThat(response3).isEqualTo(response1); //same sender
			assertThat(response3).isNotEqualTo(response2); //different sender - different host!

			SenderResponse response4 = csender2.execute(request1, 1, TimeUnit.SECONDS);
			assertThat(response4).isNotEqualTo(response1); //different sender - different host!
			assertThat(response4).isEqualTo(response2); //same sender

		} catch (Exception x) {
			x.printStackTrace();
		} finally {
			server.stop();
		}
	}
}
