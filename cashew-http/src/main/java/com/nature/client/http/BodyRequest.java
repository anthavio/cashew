package com.nature.client.http;

import java.io.IOException;
import java.io.InputStream;

import com.nature.client.http.HttpSender.Multival;

/**
 * Base class for POST and PUT Requests
 * 
 * @author martin.vanek
 *
 */
public abstract class BodyRequest extends SenderRequest {

	private InputStream bodyStream;

	protected BodyRequest(HttpSender sender, Method method, Multival parameters) {
		super(sender, method, parameters);
	}

	protected BodyRequest(HttpSender sender, Method method, String urlPath, Multival parameters) {
		super(sender, method, urlPath, parameters);
	}

	protected BodyRequest(HttpSender sender, Method method, String urlPath) {
		super(sender, method, urlPath);
	}

	protected BodyRequest(HttpSender sender, Method method) {
		super(sender, method);
	}

	@Override
	public boolean hasBody() {
		return this.bodyStream != null;
	}

	public SenderRequest setBodyString(String string, String contentType) {
		if (Cutils.isBlank(string)) {
			throw new IllegalArgumentException("Body content is blank");
		}
		setBodyStream(new StringWrappingStream(string), contentType);
		return this;
	}

	public SenderRequest setBodyStream(InputStream stream, String contentType) {
		if (stream == null) {
			throw new IllegalArgumentException("Body stream is null");
		}
		this.bodyStream = stream;

		setHeader("Content-Type", contentType);
		return this;
	}

	/*
		public SenderRequest setBodyStream(InputStream stream) {
			if (stream == null) {
				throw new IllegalArgumentException("Body stream is null");
			}
			this.bodyStream = stream;
			return this;
		}
	*/
	public InputStream getBodyStream() {
		return this.bodyStream;
	}

	/**
	 * Just a fake Stream...
	 */
	class StringWrappingStream extends InputStream {

		private String string;

		public StringWrappingStream(String string) {
			this.string = string;
		}

		public String getString() {
			return this.string;
		}

		@Override
		public void close() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public int read() throws IOException {
			throw new UnsupportedOperationException();
		}

	}
}
