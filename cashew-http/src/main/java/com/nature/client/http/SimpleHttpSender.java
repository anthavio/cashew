package com.nature.client.http;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nature.client.http.Authentication.Scheme;
import com.nature.client.http.BodyRequest.StringWrappingStream;

/**
 * 
 * @author martin.vanek
 *
 */
public class SimpleHttpSender extends HttpSender {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpSenderConfig config;

	private String basicAuthHeader;

	public SimpleHttpSender(String baseUrl) {
		this(new HttpSenderConfig(baseUrl));
	}

	public SimpleHttpSender(HttpSenderConfig config) {
		super(config);
		this.config = config;

		if (config.getAuthentication() != null) {
			final Authentication authentication = config.getAuthentication();
			if (authentication.getScheme() == Scheme.BASIC && authentication.getPreemptive()) {
				//we can use preemptive shortcut only for basic authentication
				byte[] bytes = (authentication.getUsername() + ":" + authentication.getPassword()).getBytes(Charset
						.forName(config.getEncoding()));
				String encoded = Base64.encodeBase64String(bytes);
				this.basicAuthHeader = "Basic " + encoded;
			} else {
				//for other authentication schemas use standard java Authenticator
				//http://docs.oracle.com/javase/7/docs/technotes/guides/net/http-auth.html
				Authenticator.setDefault(new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(authentication.getUsername(), authentication.getPassword().toCharArray());
					}
				});
			}
		}
	}

	@Override
	public void close() {
		//nothing
	}

	@Override
	public SenderResponse doExecute(SenderRequest request, String path, String query) throws IOException {

		URL url = new URL(this.config.getUrl().getProtocol(), this.config.getUrl().getHost(), this.config.getUrl()
				.getPort(), path);

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setConnectTimeout(this.config.getConnectTimeout());
		connection.setReadTimeout(this.config.getReadTimeout());
		connection.setInstanceFollowRedirects(false);
		connection.setUseCaches(false);
		connection.setDoOutput(true);
		connection.setDoInput(true);

		Multival headers = request.getHeaders();

		if (headers != null && headers.size() > 0) {
			for (String name : headers) {
				List<String> values = headers.get(name);
				for (String value : values) {
					//this.logger.debug(name + ": " + value);
					connection.setRequestProperty(name, value);
				}
			}
		}

		if (request.getFirstHeader("Accept-Charset") == null) {
			connection.setRequestProperty("Accept-Charset", config.getEncoding());
		}

		if (request.getFirstHeader("Accept") == null && config.getAcceptType() != null) {
			connection.setRequestProperty("Accept", config.getAcceptType());
		}

		if (this.basicAuthHeader != null) {
			this.logger.debug("Authorization: " + this.basicAuthHeader);
			connection.setRequestProperty("Authorization", this.basicAuthHeader);
		}

		if (config.getCompress()) {
			connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
		}

		connection.setRequestMethod(request.getMethod().toString());
		switch (request.getMethod()) {
		case GET:
		case DELETE:
			if (this.logger.isDebugEnabled()) {
				logHeaders("Request", connection.getRequestProperties());
			}
			break;
		case POST:
		case PUT:
			//set "Content-Type" if not explicitly set by parameters
			if (headers == null || headers.get("Content-Type") == null) {
				connection.setRequestProperty("Content-Type",
						"application/x-www-form-urlencoded; charset=" + this.config.getEncoding());
			}

			if (request.hasBody()) {
				InputStream stream = ((BodyRequest) request).getBodyStream();
				if (stream instanceof StringWrappingStream) {
					String string = ((StringWrappingStream) stream).getString();
					byte[] dataBytes = string.getBytes(this.config.getCharset());
					writeBytes(connection, dataBytes);
				} else {
					writeStream(connection, stream);
				}
			} else if (query != null && query.length() != 0) {
				//POST/PUT without body but with parameters
				byte[] dataBytes = query.getBytes(this.config.getCharset());
				writeBytes(connection, dataBytes);
			}
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}

		int responseCode;
		try {
			responseCode = connection.getResponseCode();
		} catch (SocketTimeoutException stx) {
			throw translateException(stx, null);
		}

		String responseMessage = connection.getResponseMessage();

		Map<String, List<String>> headerFields = connection.getHeaderFields();
		if (this.logger.isDebugEnabled()) {
			if (this.logger.isDebugEnabled()) {
				logHeaders("Response", headerFields);
			}
		}
		Multival responseHeaders = new Multival(headerFields);

		InputStream inputStream = null;
		try {
			inputStream = connection.getInputStream();
		} catch (IOException iox) {
			return new SimpleHttpResponse(responseCode, responseMessage, responseHeaders, connection.getErrorStream(),
					connection);
		}

		return new SimpleHttpResponse(responseCode, responseMessage, responseHeaders, inputStream, connection);
	}

	private void writeStream(HttpURLConnection connection, InputStream input) throws IOException {
		DataOutputStream output = null;
		try {
			output = new DataOutputStream(connection.getOutputStream());
			byte[] buffer = new byte[512];
			int read = -1;
			while ((read = input.read(buffer)) != -1) {
				output.write(buffer, 0, read);
			}
			output.flush();
			output.close();//XXX really close?
		} catch (IOException iox) {
			throw translateException(iox, output);
		}
	}

	private void writeBytes(HttpURLConnection connection, byte[] dataBytes) throws IOException {
		connection.setRequestProperty("Content-Length", Integer.toString(dataBytes.length));
		if (this.logger.isDebugEnabled()) {
			logHeaders("Request", connection.getRequestProperties());
		}
		DataOutputStream output = null;
		try {
			output = new DataOutputStream(connection.getOutputStream());
			output.write(dataBytes);
			output.flush();
			output.close(); //XXX really close?
		} catch (IOException iox) {
			throw translateException(iox, output);
		}
	}

	private IOException translateException(Exception exception, OutputStream output) throws IOException {

		if (output != null) {
			try {
				output.close();
			} catch (Exception xxx) {
				//ignore
			}
		}

		if (exception instanceof SocketTimeoutException) {
			if (exception.getMessage().equals("connect timed out")) {
				ConnectException cx = new ConnectException("Connect timeout " + this.config.getConnectTimeout() + " ms");
				cx.setStackTrace(exception.getStackTrace());
				throw cx;
			} else if (exception.getMessage().equals("Read timed out")) {
				SocketTimeoutException stx = new SocketTimeoutException("Read timeout " + this.config.getReadTimeout() + " ms");
				stx.setStackTrace(exception.getStackTrace());
				throw stx;
			} else {
				throw (SocketTimeoutException) exception;
			}
			//} else if(exception instanceof ConnectException) {
			//java.net.ConnectException: Connection refused
		} else if (exception instanceof IOException) {
			throw (IOException) exception;
		} else {
			IOException iox = new IOException(exception.getMessage());
			iox.setStackTrace(exception.getStackTrace());
			throw iox;
		}
	}

	private void logHeaders(String string, Map<String, List<String>> requestProperties) {
		this.logger.debug(string + " Headers");
		String direction = string.equals("Request") ? ">> " : "<< ";
		for (Entry<String, List<String>> entry : requestProperties.entrySet()) {
			List<String> values = entry.getValue();
			for (String value : values) {
				this.logger.debug(direction + entry.getKey() + ": " + value);
			}
		}
	}

	@Override
	public String toString() {
		return "SimpleHttpSender [" + config.getUrl() + "]";
	}
}
