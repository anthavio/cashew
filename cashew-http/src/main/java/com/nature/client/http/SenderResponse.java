package com.nature.client.http;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;

import com.nature.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class SenderResponse implements Closeable, Serializable {

	private static final long serialVersionUID = 1L;

	private int code;

	private String message;

	private Multival headers;

	private transient InputStream stream;

	public SenderResponse(int code, String message, Multival headers, InputStream stream) {
		this.code = code;
		this.message = message;
		this.headers = headers;
		this.stream = stream; //null for 304 Not Modified
	}

	protected SenderResponse() {
		//for serialization
		this.code = 0;
		this.message = null;
		this.headers = null;
		this.stream = null;
	}

	public int getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public Multival getHeaders() {
		return headers;
	}

	public String getFirstHeader(String string) {
		if (headers != null) {
			return headers.getFirst(string);
		} else {
			return null;
		}
	}

	public InputStream getStream() {
		return stream;
	}

	public Reader getReader() {
		return new InputStreamReader(stream, getCharset());
	}

	public boolean isBinaryContent() {
		return !HttpHeaderUtil.isTextContent(this);
	}

	public Charset getCharset() {
		return HttpHeaderUtil.getCharset(getFirstHeader("Content-Type"));
	}

	@Override
	public String toString() {
		return "SenderResponse {" + code + ", " + message + "}";
	}

}
