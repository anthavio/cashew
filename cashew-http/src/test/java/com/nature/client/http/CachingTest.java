package com.nature.client.http;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.MemcachedClient;

import org.apache.commons.codec.binary.Base64;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.nature.client.http.ResponseExtractor.ExtractedResponse;
import com.nature.client.http.cache.CacheEntry;
import com.nature.client.http.cache.CachedResponse;
import com.nature.client.http.cache.CachingSender;
import com.nature.client.http.cache.EHRequestCache;
import com.nature.client.http.cache.RequestCache;
import com.nature.client.http.cache.SimpleRequestCache;
import com.nature.client.http.cache.SpyRequestCache;
import com.thimbleware.jmemcached.CacheImpl;
import com.thimbleware.jmemcached.Key;
import com.thimbleware.jmemcached.LocalCacheElement;
import com.thimbleware.jmemcached.MemCacheDaemon;
import com.thimbleware.jmemcached.storage.CacheStorage;
import com.thimbleware.jmemcached.storage.hash.ConcurrentLinkedHashMap;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingTest {

	private JokerServer server = new JokerServer();

	private CacheManager ehCacheManager;

	MemCacheDaemon<LocalCacheElement> memcached;

	@BeforeClass
	public void setup() throws Exception {
		this.server.start();
	}

	@AfterClass
	public void destroy() throws Exception {
		this.server.stop();

		if (ehCacheManager != null) {
			ehCacheManager.shutdown();
		}
		if (memcached != null && memcached.isRunning()) {
			memcached.stop();
		}
	}

	private EHRequestCache<CachedResponse> buildEhCache() {
		if (ehCacheManager == null) {
			ehCacheManager = CacheManager.create();
			Cache ehCache = new Cache("EHCache", 5000, false, false, 0, 0);
			ehCacheManager.addCache(ehCache);
		}
		EHRequestCache<CachedResponse> cache = new EHRequestCache<CachedResponse>("EHCache",
				ehCacheManager.getCache("EHCache"));
		return cache;
	}

	private SpyRequestCache<CachedResponse> buildMemcache() throws IOException {
		if (memcached == null) {
			memcached = new MemCacheDaemon<LocalCacheElement>();

			int maxItems = 5000;
			long maxBytes = 10 * 1024 * 1024;
			CacheStorage<Key, LocalCacheElement> storage = ConcurrentLinkedHashMap.create(
					ConcurrentLinkedHashMap.EvictionPolicy.FIFO, maxItems, maxBytes);
			memcached.setCache(new CacheImpl(storage));
			memcached.setBinary(false);
			memcached.setAddr(new InetSocketAddress(11211));
			memcached.setIdleTime(1000);
			memcached.setVerbose(true);
			memcached.start();
		}

		MemcachedClient client = new MemcachedClient(AddrUtil.getAddresses("localhost:11211"));
		SpyRequestCache<CachedResponse> cache = new SpyRequestCache<CachedResponse>("whatever", client, 1, TimeUnit.SECONDS);
		return cache;
	}

	private CachingSender newCachedSender() {
		String url = "http://localhost:" + this.server.getHttpPort();
		//HttpSender sender = new SimpleHttpSender(url);
		HttpSender sender = new HttpClient4Sender(url);
		SimpleRequestCache<CachedResponse> cache = new SimpleRequestCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);
		return csender;
	}

	public void testSameRequestDifferentSender() throws IOException {

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
	}

	@Test
	public void testSimpleCache() throws Exception {
		RequestCache<CachedResponse> cache;
		cache = new SimpleRequestCache<CachedResponse>();
		testCache(cache);
	}

	@Test
	public void testEhCache() throws Exception {
		RequestCache<CachedResponse> cache;
		cache = buildEhCache();
		//cache = buildMemcache();
		testCache(cache);
	}

	@Test
	public void testMemcached() throws Exception {
		RequestCache<CachedResponse> cache;
		cache = buildMemcache();
		testCache(cache);
	}

	@Test
	public void testHttpCaching() throws Exception {
		CachingSender csender = newCachedSender();
		//http headers will allow to cache reponse for 1 second
		SenderRequest request = new GetRequest().addParameter("docache", 1);
		//keep original count of request executed on server
		final int requestCount = server.getRequestCount();

		ExtractedResponse<String> extract1 = csender.extract(request, ResponseExtractor.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 1);//count + 1
		assertThat(extract1.getResponse().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract1.getResponse()).isInstanceOf(CachedResponse.class); //is cached

		ExtractedResponse<String> extract2 = csender.extract(request, ResponseExtractor.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 1); //count is same as before
		assertThat(extract2.getResponse().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract2.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		assertThat(extract2.getResponse()).isSameAs(extract1.getResponse()); //1,2 are same object!
		assertThat(extract2.getExtracted()).isEqualTo(extract1.getExtracted()); //1,2 extracted are equal

		Thread.sleep(1300); //let the cache entry expire

		ExtractedResponse<String> extract3 = csender.extract(request, ResponseExtractor.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 2);//count + 2
		assertThat(extract3.getResponse().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract3.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		assertThat(extract3.getResponse()).isNotSameAs(extract1.getResponse()); //not equal anymore!

		ExtractedResponse<String> extract4 = csender.extract(request, ResponseExtractor.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 2);//count + 2
		assertThat(extract4.getResponse().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract4.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		assertThat(extract4.getResponse()).isSameAs(extract3.getResponse()); //3,4 are same object!
		assertThat(extract4.getExtracted()).isEqualTo(extract3.getExtracted()); //3,4 extracted are equal
		assertThat(extract4.getExtracted()).isEqualTo(extract3.getExtracted()); //1,4 extracted are equal

		//Thread.sleep(1300); //let the request end properly

		csender.close();
	}

	@Test
	public void testHttpETag() throws Exception {
		CachingSender csender = newCachedSender();

		//keep original count of request executed on server
		final int requestCount = server.getRequestCount();

		//http headers will use ETag
		SenderRequest request1 = new GetRequest().addParameter("doetag", null);
		ExtractedResponse<String> extract1 = csender.extract(request1, ResponseExtractor.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 1);//count + 1
		assertThat(extract1.getResponse().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract1.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		//System.out.println(extract1.getExtracted());
		//System.out.println(extract1.getResponse());

		Thread.sleep(100); //

		SenderRequest request2 = new GetRequest().addParameter("doetag", null);
		ExtractedResponse<String> extract2 = csender.extract(request2, ResponseExtractor.STRING);
		assertThat(server.getRequestCount()).isEqualTo(requestCount + 2);//count + 2 (304 NOT_MODIFIED)
		assertThat(extract2.getResponse().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(extract2.getResponse()).isInstanceOf(CachedResponse.class); //is cached
		//System.out.println(extract2.getExtracted());
		//System.out.println(extract2.getResponse());
		//assertThat(extract2.getResponse()).isSameAs(extract1.getResponse()); //1,2 are same object!
		assertThat(extract2.getExtracted()).isEqualTo(extract1.getExtracted()); //1,2 extracted are equal

		Thread.sleep(1300); //let the request end properly

		csender.close();
	}

	private void testCache(RequestCache<CachedResponse> cache) throws InterruptedException {
		CachedResponse response = new CachedResponse(200, "Choroso", null, new Date().toString());
		String cacheKey = String.valueOf(System.currentTimeMillis());
		//hard ttl is 2 seconds
		Boolean added = cache.set(cacheKey, new CacheEntry<CachedResponse>(response, 2, 1));
		assertThat(added).isTrue();

		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		assertThat(entry.getValue().getAsString()).isEqualTo(response.getAsString());
		assertThat(entry.isSoftExpired()).isFalse();
		assertThat(entry.isHardExpired()).isFalse();

		Thread.sleep(1100); //after soft ttl

		entry = cache.get(cacheKey);
		assertThat(entry.getValue().getAsString()).isEqualTo(response.getAsString());
		assertThat(entry.isSoftExpired()).isTrue();
		assertThat(entry.isHardExpired()).isFalse();

		Thread.sleep(1500); //after hard ttl

		entry = cache.get(cacheKey);
		assertThat(entry).isNull();
	}

	//@Test
	public void testFacebook() throws IOException {
		//https://developers.facebook.com/tools/explorer
		//Facebook uses ETag
		//ETag: "9ea8f5a5b1d659bc8358daad6f2e347f15f6e683"
		//Expires: Sat, 01 Jan 2000 00:00:00 GMT
		HttpSender sender = new HttpClient4Sender("https://graph.facebook.com");
		SimpleRequestCache<CachedResponse> cache = new SimpleRequestCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);

		GetRequest request = new GetRequest("/me/friends");
		request
				.addParameter(
						"access_token",
						"AAAAAAITEghMBAOK0zAh6obLGcPm5FuXt3OlqMWPmgudEF9KxrVBiNt6AjccUFCSoZAxRr8ZBvGZBi9sOdZBxebQZBJzVMwRKzIWCLZCjzxGV0cvAMkDjXu");
		//request.addParameter("fields", "id,name");
		ExtractedResponse<String> response = csender.extract(request, ResponseExtractor.STRING);
		assertThat(response.getResponse()).isInstanceOf(CachedResponse.class);

		ExtractedResponse<String> response2 = csender.extract(request, ResponseExtractor.STRING);
		assertThat(response2.getResponse()).isInstanceOf(CachedResponse.class);
		assertThat(response2.getResponse()).isEqualTo(response.getResponse());
		System.out.println(response.getExtracted());
	}

	//https://code.google.com/apis/console
	//@Test
	public void testGoogleApi() throws Exception {

		//https://developers.google.com/maps/documentation/staticmaps/
		//Google maps uses Expires
		//Cache-Control: public, max-age=86400
		//Date: Tue, 26 Feb 2013 16:11:16 GMT
		//Expires: Wed, 27 Feb 2013 16:11:16 GMT
		HttpSender sender = new HttpClient4Sender("http://maps.googleapis.com/maps/api/staticmap");
		SimpleRequestCache<CachedResponse> cache = new SimpleRequestCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);
		GetRequest request = new GetRequest();
		request.addParameter("key", "AIzaSyCgNUVqbYTyIP_f4Ew2wJXSZ9XjIQ8F5w8");
		request.addParameter("center", "51.477222,0");
		request.addParameter("size", "10x10");
		request.addParameter("sensor", false);
		ExtractedResponse<String> extract = sender.extract(request, ResponseExtractor.STRING);
		System.out.println(extract.getExtracted());

		if (true)
			return;

		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		keyStore.load(new FileInputStream("/Users/martin.vanek/Downloads/google_privatekey.p12"),
				"notasecret".toCharArray());
		PrivateKey privateKey = (PrivateKey) keyStore.getKey("privatekey", "notasecret".toCharArray());
		String header = Base64.encodeBase64String("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
		System.out.println(header);
		long iat = System.currentTimeMillis() / 1000;
		long exp = iat + 60 * 60;
		String data = "{" //
				+ "\"iss\":\"164620382615@developer.gserviceaccount.com\","//
				+ "\"scope\":\"https://www.googleapis.com/auth/prediction\","//
				+ "\"aud\":\"https://accounts.google.com/o/oauth2/token\"," //
				+ "\"exp\":" + exp + "," + "\"iat\":" + iat//
				+ "}";
		String claimset = Base64.encodeBase64String(data.getBytes());
		String content = header + "." + claimset;

		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(privateKey);
		signature.update(content.getBytes());
		byte[] sign = signature.sign();
		String digsig = Base64.encodeBase64String(sign);
		String assertion = content + "." + digsig;
		System.out.println(assertion);
		PostRequest request2 = new HttpClient4Sender("https://accounts.google.com/o/oauth2/token").newPostRequest();
		request2.addParameter("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
		request2.addParameter("assertion", assertion);
		ExtractedResponse<String> x = request2.extract(ResponseExtractor.STRING);
		System.out.println(x.getExtracted());
	}

	//@Test
	public void testGoogle() throws IOException {
		//google protect itself from being cached
		//Expires: -1
		//Cache-Control: private, max-age=0
		HttpSender sender = new HttpClient4Sender("http://www.google.co.uk");
		SimpleRequestCache<CachedResponse> cache = new SimpleRequestCache<CachedResponse>();
		CachingSender csender = new CachingSender(sender, cache);

		SenderResponse response = csender.execute(new GetRequest());
		assertThat(response.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(response).isNotInstanceOf(CachedResponse.class); //not cached!
		response.close();

		//enforce static caching
		SenderResponse response2 = csender.execute(new GetRequest(), 5, TimeUnit.MINUTES);
		assertThat(response2.getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(response2).isInstanceOf(CachedResponse.class);
		response2.close();

		SenderResponse response3 = csender.execute(new GetRequest(), 5, TimeUnit.MINUTES);
		assertThat(response3).isEqualTo(response2); //must be same instance 
		response3.close();
	}
}
