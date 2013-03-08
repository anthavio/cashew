package com.nature.client.http;

import java.io.InputStream;
import java.net.HttpURLConnection;

import com.nature.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class SimpleHttpResponse extends SenderResponse {

	private transient HttpURLConnection connection;

	public SimpleHttpResponse(int code, String message, Multival headers, InputStream stream, HttpURLConnection connection) {
		super(code, message, headers, stream);
		if (connection == null) {
			throw new IllegalArgumentException("Null connection");
		}
		this.connection = connection;
	}

	@Override
	public void close() {
		if (connection != null) {
			connection.disconnect();
		}
	}

	public HttpURLConnection getConnection() {
		return connection;
	}
}
