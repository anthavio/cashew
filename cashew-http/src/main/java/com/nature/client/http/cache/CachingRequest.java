package com.nature.client.http.cache;

import java.util.concurrent.TimeUnit;

import com.nature.client.http.SenderRequest;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingRequest {

	private final SenderRequest request;

	private final long hardTtl; //seconds

	private final long softTtl; //seconds

	private final boolean asyncUpdate;

	public CachingRequest(SenderRequest request, long hardTtl, TimeUnit unit) {
		this(request, hardTtl, hardTtl, unit, false); //hardTtl = softTtl
	}

	public CachingRequest(SenderRequest request, long hardTtl, TimeUnit unit, boolean asyncUpdate) {
		this(request, hardTtl, hardTtl, unit, asyncUpdate);
	}

	public CachingRequest(SenderRequest request, long hardTtl, long softTtl, TimeUnit unit) {
		this(request, hardTtl, softTtl, unit, false);
	}

	public CachingRequest(SenderRequest request, long hardTtl, long softTtl, TimeUnit unit, boolean asyncUpdate) {
		if (request == null) {
			throw new IllegalArgumentException("null request");
		}
		this.request = request;

		hardTtl = unit.toSeconds(hardTtl);
		if (hardTtl <= 0) {
			throw new IllegalArgumentException("hardTtl " + hardTtl + " must be >= 1 second ");
		}
		this.hardTtl = hardTtl;

		softTtl = unit.toSeconds(softTtl);
		if (hardTtl < softTtl) {
			throw new IllegalArgumentException("hardTtl " + hardTtl + " must be >= softTtl " + softTtl);
		}
		this.softTtl = softTtl;

		this.asyncUpdate = asyncUpdate;
	}

	public SenderRequest getRequest() {
		return request;
	}

	public long getHardTtl() {
		return hardTtl;
	}

	public long getSoftTtl() {
		return softTtl;
	}

	public boolean isAsyncUpdate() {
		return asyncUpdate;
	}

	@Override
	public String toString() {
		return "CachingRequest [request=" + request + ", hardTtl=" + hardTtl + ", softTtl=" + softTtl + ", asyncUpdate="
				+ asyncUpdate + "]";
	}

}
