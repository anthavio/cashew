package com.nature.client.http;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.nature.client.http.async.ExecutorServiceBuilder;
import com.nature.client.http.cache.CachingExtractor;
import com.nature.client.http.cache.CachingExtractorRequest;
import com.nature.client.http.cache.SimpleRequestCache;

/**
 * 
 * @author martin.vanek
 *
 */
public class ExtractionTest {

	private ThreadPoolExecutor executor;

	@BeforeClass
	public void setup() throws Exception {
		this.executor = (ThreadPoolExecutor) new ExecutorServiceBuilder().setCorePoolSize(0).setMaximumPoolSize(1)
				.setMaximumQueueSize(0).build();

	}

	@AfterClass
	public void destroy() throws Exception {
		this.executor.shutdown();
	}

	//@Test
	public void syncUpdateExtraction() throws Exception {
		JokerServer server = new JokerServer().start();
		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());
		SenderRequest request = new GetRequest();
		ResponseExtractor<String> extractor = ResponseExtractor.STRING;
		CachingExtractorRequest<String> cerequest = new CachingExtractorRequest<String>(request, extractor, 2, 1,
				TimeUnit.SECONDS);

		final int initialCount = server.getRequestCount();

		String extract1 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);

		Thread.sleep(501);
		//taken from cache
		String extract2 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1); //same
		assertThat(extract2).isEqualTo(extract1); //same

		Thread.sleep(501);
		//taken from server
		String extract3 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); //new
		assertThat(extract3).isNotEqualTo(extract1); //different

		server.stop(); //stop server from responding

		Thread.sleep(1001); //after soft expiration
		String extract4 = cextractor.extract(cerequest);
		assertThat(extract4).isEqualTo(extract3); //same from cache

		Thread.sleep(1001); //after hard expiration
		try {
			cextractor.extract(cerequest);
			Assert.fail("Preceding line must throw IOException");
		} catch (IOException iox) {
			//this is what we expect
		}
	}

	//@Test
	public void asyncUpdateExtraction() throws Exception {
		JokerServer server = new JokerServer().start();
		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());
		SenderRequest request = new GetRequest();
		ResponseExtractor<String> extractor = ResponseExtractor.STRING;
		CachingExtractorRequest<String> cerequest = new CachingExtractorRequest<String>(request, extractor, 2, 1,
				TimeUnit.SECONDS, true); //asynchronous updates!

		final int initialCount = server.getRequestCount();

		String extract1 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);

		Thread.sleep(1001); //after soft expiration

		//taken from cache
		String extract2 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1); //same
		assertThat(extract2).isEqualTo(extract1); //same

		Thread.sleep(100); //wait for async thread to update from server
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); //plus 1

		//from cache again - but different value
		String extract3 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2);//same
		assertThat(extract3).isNotEqualTo(extract2); //updated in cache asyncronously

		server.stop(); //stop server from responding

		Thread.sleep(1000); //after soft expiration

		//from cache - async update check should fail in the background not afecting us
		String extract4 = cextractor.extract(cerequest);
		assertThat(extract4).isEqualTo(extract3);

		Thread.sleep(1000); //after hard expiration

		try {
			cextractor.extract(cerequest);
			Assert.fail("Preceding line must throw IOException");
		} catch (IOException iox) {
			//this is what we expect
		}
	}

	@Test
	public void asyncNonparallelExtraction() throws Exception {
		JokerServer server = new JokerServer().start();
		CachingExtractor cextractor = newExtractorSender(server.getHttpPort());
		SenderRequest request = new GetRequest();
		ResponseExtractor<String> extractor = ResponseExtractor.STRING;
		CachingExtractorRequest<String> cerequest = new CachingExtractorRequest<String>(request, extractor, 2, 1,
				TimeUnit.SECONDS, true); //asynchronous updates!

		//request must spent sime time in server
		cerequest.getRequest().addParameter("sleep", 1);

		final int initialCount = server.getRequestCount();

		String extract1 = cextractor.extract(cerequest);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 1);
		Thread.sleep(1001); //after soft expiry

		assertThat(executor.getActiveCount()).isEqualTo(0);
		String extract2 = cextractor.extract(cerequest); //this request should start async update
		assertThat(extract2).isEqualTo(extract1);

		Thread.sleep(100); //async resfresh hit server

		assertThat(executor.getActiveCount()).isEqualTo(1);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); // plus 1
		//next 900 (1000-100) millis same request will be discarded because of running refresh

		assertThat(executor.getActiveCount()).isEqualTo(1);
		assertThat(executor.getQueue().size()).isEqualTo(0);

		String extract3 = cextractor.extract(cerequest); //discarded request 
		assertThat(extract3).isEqualTo(extract1);
		assertThat(server.getRequestCount()).isEqualTo(initialCount + 2); // same as before
		assertThat(executor.getActiveCount()).isEqualTo(1);
		assertThat(executor.getQueue().size()).isEqualTo(0);

		Thread.sleep(1000); //refresh thread finished

		assertThat(executor.getActiveCount()).isEqualTo(0);
		assertThat(executor.getQueue().size()).isEqualTo(0);

		String extract4 = cextractor.extract(cerequest); //from cache 
		assertThat(extract4).isNotEqualTo(extract3);
	}

	private CachingExtractor newExtractorSender(int port) {
		String url = "http://localhost:" + port;
		HttpSender sender = new SimpleHttpSender(url);
		//HttpSender sender = new HttpClient4Sender(url);
		SimpleRequestCache<Serializable> cache = new SimpleRequestCache<Serializable>();
		CachingExtractor csender = new CachingExtractor(sender, cache);
		csender.setExecutor(executor);
		return csender;
	}
}
