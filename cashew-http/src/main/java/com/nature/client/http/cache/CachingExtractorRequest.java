package com.nature.client.http.cache;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import com.nature.client.http.ResponseExtractor;
import com.nature.client.http.SenderRequest;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingExtractorRequest<T extends Serializable> extends CachingRequest {

	private final ResponseExtractor<T> extractor;

	public CachingExtractorRequest(SenderRequest request, ResponseExtractor<T> extractor, long hardTtl, TimeUnit unit) {
		this(request, extractor, hardTtl, hardTtl, unit, false); //hardTtl = softTtl
	}

	public CachingExtractorRequest(SenderRequest request, ResponseExtractor<T> extractor, long hardTtl, TimeUnit unit,
			boolean asyncUpdate) {
		this(request, extractor, hardTtl, hardTtl, unit, asyncUpdate);
	}

	public CachingExtractorRequest(SenderRequest request, ResponseExtractor<T> extractor, long hardTtl, long softTtl,
			TimeUnit unit) {
		this(request, extractor, hardTtl, softTtl, unit, false);
	}

	public CachingExtractorRequest(SenderRequest request, ResponseExtractor<T> extractor, long hardTtl, long softTtl,
			TimeUnit unit, boolean asyncUpdate) {
		super(request, hardTtl, softTtl, unit, asyncUpdate);
		if (request == null) {
			throw new IllegalArgumentException("null request");
		}

		if (extractor == null) {
			throw new IllegalArgumentException("null extractor");
		}
		this.extractor = extractor;
	}

	public ResponseExtractor<T> getExtractor() {
		return extractor;
	}

}
