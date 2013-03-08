package com.nature.client.http.cache;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nature.client.http.HttpHeaderUtil;
import com.nature.client.http.HttpSender.Multival;
import com.nature.client.http.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachedResponse extends SenderResponse implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(CachedResponse.class.getName());

	private static final long serialVersionUID = 1L;

	private byte[] contentBinary;

	private String contentString;

	private static final InputStream DUMMY_STREAM = new ByteArrayInputStream(new byte[0]);

	protected CachedResponse() {
		super();
		//serialization
	}

	public CachedResponse(SenderResponse response) throws IOException {
		super(response.getCode(), response.getMessage(), response.getHeaders(), DUMMY_STREAM);
		if (response.isBinaryContent()) {
			contentBinary = HttpHeaderUtil.readAsBytes(response);
		} else {
			contentString = HttpHeaderUtil.readAsString(response);
		}
	}

	public CachedResponse(int code, String message, Multival headers, String data) {
		super(code, message, headers, DUMMY_STREAM);
		this.contentString = data;
	}

	public CachedResponse(int code, String message, Multival headers, byte[] data) {
		super(code, message, headers, DUMMY_STREAM);
		this.contentBinary = data;
	}

	@Override
	public void close() throws IOException {
		//nothing
	}

	@Override
	public InputStream getStream() {
		if (contentBinary != null) {
			return new ByteArrayInputStream(contentBinary);
		} else {
			logger.warn("Inefficient conversion from string to bytes");
			return new ByteArrayInputStream(contentString.getBytes(getCharset()));
		}
	}

	@Override
	public Reader getReader() {
		if (contentBinary != null) {
			logger.warn("Inefficient conversion from bytes to string");
			return new StringReader(new String(contentBinary, getCharset()));
		} else {
			return new StringReader(contentString);
		}
	}

	public byte[] getAsBytes() {
		if (contentBinary != null) {
			return contentBinary;
		} else {
			logger.warn("Inefficient conversion from string to bytes");
			return contentString.getBytes(getCharset());
		}
	}

	public String getAsString() {
		if (contentBinary != null) {
			logger.warn("Inefficient conversion from bytes to string");
			return new String(contentBinary, getCharset());
		} else {
			return contentString;
		}
	}

	@Override
	public String toString() {
		return "CachedResponse#" + hashCode() + "{" + getCode() + ", " + getMessage() + "}";
	}

}
