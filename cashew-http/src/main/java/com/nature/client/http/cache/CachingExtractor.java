package com.nature.client.http.cache;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nature.client.http.HttpSender;
import com.nature.client.http.ResponseExtractor.ExtractedResponse;
import com.nature.client.http.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingExtractor {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final RequestCache<Serializable> cache;

	private final HttpSender sender;

	private ExecutorService executor;

	private Map<String, CachingExtractorRequest<?>> updating = new HashMap<String, CachingExtractorRequest<?>>();

	//private ReadWriteLock lock = new ReentrantReadWriteLock();

	public CachingExtractor(HttpSender sender, RequestCache<Serializable> cache, ExecutorService executor) {
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

	public CachingExtractor(HttpSender sender, RequestCache<Serializable> cache) {
		this(sender, cache, null);
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
	public RequestCache<Serializable> getCache() {
		return cache;
	}

	/**
	 * Extracted response version. Response is extracted, then closed and result is returned to caller
	 * Static caching based on specified amount and unit
	 */
	public <T extends Serializable> T extract(CachingExtractorRequest<T> request) throws IOException {
		if (request.isAsyncUpdate() && this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		String cacheKey = sender.getCacheKey(request.getRequest());
		CacheEntry<Serializable> entry = cache.get(cacheKey);
		if (entry != null) {
			if (!entry.isSoftExpired()) {
				return (T) entry.getValue(); //nice hit
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
							executor.execute(new CacheUpdateRunner<T>(request));
							updating.put(cacheKey, request);
						}
					}
					//return soft expired value
					logger.debug("Request soft expired value returned " + cacheKey);
					return (T) entry.getValue();
				} else { //sync update
					logger.debug("Request sync refresh start " + cacheKey);
					try {
						updating.put(cacheKey, request);
						ExtractedResponse<T> extract = sender.extract(request.getRequest(), request.getExtractor());
						entry = new CacheEntry<Serializable>(extract.getExtracted(), request.getHardTtl(), request.getSoftTtl());
						cache.set(cacheKey, entry);
						return extract.getExtracted();
					} catch (Exception x) {
						logger.warn("Request refresh failed for " + request, x);
						//bugger - return soft expired value
						logger.debug("Request soft expired value returned " + cacheKey);
						return (T) entry.getValue();
					} finally {
						updating.remove(cacheKey);
					}
				}
			}
		} else { //entry is null -> execute request, extract response and put it into cache
			ExtractedResponse<T> extracted = sender.extract(request.getRequest(), request.getExtractor());
			entry = new CacheEntry<Serializable>(extracted.getExtracted(), request.getHardTtl(), request.getSoftTtl());
			cache.set(cacheKey, entry);
			return extracted.getExtracted();
		}
	}

	/**
	 * Close undelying sender and cache XXX really? sender may be used somewhere else!
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

		private final CachingExtractorRequest<T> request;

		public CacheUpdateRunner(CachingExtractorRequest<T> request) {
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
				T extracted = request.getExtractor().extract(response);
				CacheEntry<Serializable> entry = new CacheEntry<Serializable>(extracted, request.getHardTtl(),
						request.getSoftTtl());
				cache.set(cacheKey, entry);
			} catch (Exception x) {
				logger.warn("Failed to update request " + request.getRequest(), x);
			} finally {
				updating.remove(cacheKey);
			}
		}
	}

}
