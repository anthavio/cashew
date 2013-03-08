package com.nature.client.http.cache;

import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nature.client.http.Cutils;
import com.nature.client.http.HttpDateUtil;
import com.nature.client.http.HttpHeaderUtil;
import com.nature.client.http.HttpSender;
import com.nature.client.http.ResponseExtractor;
import com.nature.client.http.ResponseExtractor.ExtractedResponse;
import com.nature.client.http.SenderRequest;
import com.nature.client.http.SenderResponse;

/**
 * Sender warpper that caches Responses as they are recieved from remote server
 * 
 * @author martin.vanek
 *
 */
public class CachingSender {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpSender sender;

	private final RequestCache<CachedResponse> cache;

	private Map<String, CachingRequest> updating = new HashMap<String, CachingRequest>();

	private ExecutorService executor;

	public CachingSender(HttpSender sender, RequestCache<CachedResponse> cache) {
		this(sender, cache, null);
	}

	public CachingSender(HttpSender sender, RequestCache<CachedResponse> cache, ExecutorService executor) {
		if (sender == null) {
			throw new IllegalArgumentException("sender is null");
		}
		this.sender = sender;

		if (cache == null) {
			throw new IllegalArgumentException("cache is null");
		}
		this.cache = cache;

		this.executor = executor; //can be null
	}

	public ExecutorService getExecutor() {
		return executor;
	}

	public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	/**
	 * @return underlying sender
	 */
	public HttpSender getSender() {
		return sender;
	}

	/**
	 * @return underlying cache
	 */
	public RequestCache<CachedResponse> getCache() {
		return cache;
	}

	public SenderResponse execute(CachingRequest request) throws IOException {
		if (request.isAsyncUpdate() && this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		String cacheKey = sender.getCacheKey(request.getRequest());
		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		if (entry != null) {
			if (!entry.isSoftExpired()) {
				return entry.getValue(); //nice hit
			} else {
				logger.debug("Request soft expired " + cacheKey);
				//soft expired -  refresh needed
				if (request.isAsyncUpdate()) {
					//start asynchronous update for request this cache entry
					synchronized (updating) {
						if (updating.containsKey(cacheKey)) {
							logger.debug("Request is already being refreshed " + cacheKey);
						} else {
							logger.debug("Request async refresh start " + cacheKey);
							executor.execute(new CacheUpdateRunner<Serializable>(request));
							updating.put(cacheKey, request);
						}
					}
					//return soft expired value
					logger.debug("Request soft expired value returned " + cacheKey);
					return entry.getValue();
				} else { //sync update
					logger.debug("Request sync refresh start " + cacheKey);
					try {
						updating.put(cacheKey, request);
						SenderResponse response = sender.execute(request.getRequest());
						CachedResponse cached = new CachedResponse(response);
						entry = new CacheEntry<CachedResponse>(cached, request.getHardTtl(), request.getSoftTtl());
						cache.set(cacheKey, entry);
						return cached;
					} catch (Exception x) {
						logger.warn("Request refresh failed for " + request, x);
						//bugger - return soft expired value
						logger.debug("Request soft expired value returned " + cacheKey);
						return entry.getValue();
					} finally {
						updating.remove(cacheKey);
					}
				}
			}
		} else { //entry is null -> execute request, extract response and put it into cache
			SenderResponse response = sender.execute(request.getRequest());
			CachedResponse cached = new CachedResponse(response);
			entry = new CacheEntry<CachedResponse>(cached, request.getHardTtl(), request.getSoftTtl());
			cache.set(cacheKey, entry);
			return cached;
		}
	}

	/**
	 * Static caching based on specified ttl and unit
	 */
	public SenderResponse execute(SenderRequest request, long ttl, TimeUnit unit) throws IOException {
		if (request == null) {
			throw new IllegalArgumentException("request is null");
		}
		String cacheKey = sender.getCacheKey(request);
		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		if (entry != null) {
			if (entry.isSoftExpired()) {
				logger.debug("Request soft expired " + cacheKey);
			} else {
				return entry.getValue();
			}
		}

		SenderResponse response = sender.execute(request);
		CachedResponse cached = new CachedResponse(response);
		cache.set(cacheKey, cached, ttl, unit);

		return cached;
	}

	/**
	 * Caching based on HTTP headers values
	 * 
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 */
	public <T extends Serializable> ExtractedResponse<T> extract(SenderRequest request, ResponseExtractor<T> extractor)
			throws IOException {
		if (extractor == null) {
			throw new IllegalArgumentException("Extractor is null");
		}
		SenderResponse response = execute(request);
		try {
			//XXX extract when response code != 200 ?
			T extracted = extractor.extract(response);
			return new ExtractedResponse<T>(response, extracted);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Caching based on HTTP headers values (ETag, Last-Modified, Cache-Control, Expires)
	 */
	public SenderResponse execute(SenderRequest request) throws IOException {
		if (request == null) {
			throw new IllegalArgumentException("request is null");
		}
		String cacheKey = sender.getCacheKey(request);
		CacheEntry<CachedResponse> entry = cache.get(cacheKey);
		if (entry != null) {
			if (!entry.isSoftExpired()) {
				return entry.getValue(); //cache hit and not soft expired
			} else {
				//soft expired - verify freshness
				if (entry.getServerTag() != null) {//ETag
					request.setHeader("If-None-Match", entry.getServerTag()); //XXX this modifies request so hashCode will change as well
				}
				if (entry.getServerDate() != null) {//Last-Modified
					request.setHeader("If-Modified-Since", HttpDateUtil.formatDate(entry.getServerDate()));
				}
			}
		} else if (request.getFirstHeader("If-None-Match") != null) {
			throw new IllegalStateException("Cannot use request ETag without holding cached response");
		}
		SenderResponse response = sender.execute(request);

		if (response.getCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
			//only happen when we sent Etag => we have CacheEntry
			//entry.setServerDate(response.getFirstHeader("Date"));
			return entry.getValue();
		}

		if (response.getCode() == HttpURLConnection.HTTP_OK) {
			CacheEntry<CachedResponse> entryNew = HttpHeaderUtil.buildCacheEntry(response);
			if (entryNew != null) {
				cache.set(cacheKey, entryNew);
				return entryNew.getValue();
			} else {
				logger.info("Response http headers disallows caching");
				return response;
			}

		} else {
			//Other then HTTP 200 OK responses are NOT cached
			return response;
		}
	}

	/*
	private int defaultAmount = 5;

	private TimeUnit defaultUnit = TimeUnit.MINUTES;
	
	public void setDefault(int amount, TimeUnit unit) {
		if (amount <= 0) {
			throw new IllegalArgumentException("amount must be positive number");
		}
		this.defaultAmount = amount;
		if (unit == null) {
			throw new IllegalArgumentException("unit is null");
		}
		this.defaultUnit = unit;
	}
	*/

	/**
	 * Close undelying sender and cache 
	 * XXX really? sender may be used somewhere else!
	 */
	public void close() {
		try {
			this.sender.close();
		} catch (IOException iox) {
			//ignore
		}
		if (this.cache != null) {
			this.cache.destroy();
		}
	}

	private class CacheUpdateRunner<T extends Serializable> implements Runnable {

		private final CachingRequest request;

		public CacheUpdateRunner(CachingRequest request) {
			if (request == null) {
				throw new IllegalArgumentException("request is null");
			}
			this.request = request;
		}

		@Override
		public void run() {
			String cacheKey = sender.getCacheKey(request.getRequest());
			try {
				SenderResponse response = sender.execute(request.getRequest());
				CachedResponse cached = new CachedResponse(response);
				CacheEntry<CachedResponse> entry = new CacheEntry<CachedResponse>(cached, request.getHardTtl(),
						request.getSoftTtl());
				cache.set(cacheKey, entry);
			} catch (Exception x) {
				logger.warn("Failed to update request " + cacheKey, x);
			} finally {

				updating.remove(cacheKey);
			}
		}
	}

}
