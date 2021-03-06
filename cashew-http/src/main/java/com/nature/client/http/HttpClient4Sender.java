package com.nature.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nature.client.http.BodyRequest.StringWrappingStream;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient4Sender extends HttpSender {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpClient httpClient;

	private final HttpClient4Config config;

	public HttpClient4Sender(String baseUrl) {
		this(new HttpClient4Config(baseUrl));
	}

	public HttpClient4Sender(HttpClient4Config config) {
		super(config);
		this.config = config;
		this.httpClient = config.buildHttpClient();
	}

	@Override
	public HttpClient4Config getConfig() {
		return config;
	}

	@Override
	public void close() {
		try {
			httpClient.getConnectionManager().shutdown();
		} catch (Exception x) {
			logger.warn("Exception while closing sender", x);
		}
	}

	/*
	public void reset() {
		try {
			httpClient.getConnectionManager().closeExpiredConnections();
		} catch (Exception x) {
			logger.warn("Exception while closing sender", x);
		}
	}
	*/

	/**
	 * Backdoor
	 */
	public HttpClient getHttpClient() {
		return httpClient;
	}

	@Override
	public HttpClient4Response doExecute(SenderRequest request, String path, String query) throws IOException {
		/*
		String path = buildPath(config.getUrl().getPath(), request.getUrlPath());

		Multival parameters = request.getParameters();
		List<NameValuePair> nvQuParams = null;
		StringBuilder sbMxParams = null;
		if (parameters != null && parameters.size() != 0) {
			nvQuParams = new LinkedList<NameValuePair>();
			sbMxParams = new StringBuilder();
			for (String name : parameters) {
				if (name.charAt(0) == ';') { //matrix parameter
					List<String> values = parameters.get(name);
					for (String value : values) {
						sbMxParams.append(URLEncoder.encode(name, config.getEncoding()));
						sbMxParams.append('=');
						sbMxParams.append(URLEncoder.encode(value, config.getEncoding()));
					}
				} else { //query parameter
					List<String> values = parameters.get(name);
					for (String value : values) {
						nvQuParams.add(new BasicNameValuePair(name, value));
					}
				}
			}
		}

		//append matrix parameters if any
		if (sbMxParams != null && sbMxParams.length() != 0) {
			path = path + sbMxParams;
		}

		//append query parameters if there are any and if apropriate
		if (nvQuParams != null && nvQuParams.size() != 0) {
			if (!request.getMethod().canHaveBody()) {
				path = path + "?" + URLEncodedUtils.format(nvQuParams, config.getCharset());
			} else if (request.hasBody()) {
				// POST, PUT with body
				path = path + "?" + URLEncodedUtils.format(nvQuParams, config.getCharset());
			}
		}
		*/

		HttpRequestBase httpRequest;
		switch (request.getMethod()) {
		case GET:
			httpRequest = new HttpGet(path);
			break;
		case DELETE:
			httpRequest = new HttpDelete(path);
			break;
		case POST:
			HttpPost httpPost = new HttpPost(path);
			HttpEntity entityPost = buildEntity(request, query);
			httpPost.setEntity(entityPost);
			httpRequest = httpPost;
			break;
		case PUT:
			HttpPut httpPut = new HttpPut(path);
			HttpEntity entityPut = buildEntity(request, query);
			httpPut.setEntity(entityPut);
			httpRequest = httpPut;
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}

		Multival headers = request.getHeaders();
		if (headers != null && headers.size() != 0) {
			for (String name : headers) {
				List<String> values = headers.get(name);
				for (String value : values) {
					httpRequest.addHeader(name, value);
				}
			}
		}

		HttpResponse httpResponse = call(httpRequest);

		Header[] responseHeaders = httpResponse.getAllHeaders();
		Multival outHeaders = new Multival();
		for (Header header : responseHeaders) {
			outHeaders.add(header.getName(), header.getValue());
		}
		HttpEntity entity = httpResponse.getEntity();
		StatusLine statusLine = httpResponse.getStatusLine();
		//Entity is null - 304 Not Modified
		InputStream stream = entity != null ? entity.getContent() : null;
		HttpClient4Response response = new HttpClient4Response(statusLine.getStatusCode(), statusLine.getReasonPhrase(),
				outHeaders, stream, httpResponse);
		return response;
	}

	private HttpEntity buildEntity(SenderRequest request, String query) throws UnsupportedEncodingException {
		HttpEntity entity;
		if (request.hasBody()) {
			InputStream stream = ((BodyRequest) request).getBodyStream();
			if (stream instanceof StringWrappingStream) {
				entity = new StringEntity(((StringWrappingStream) stream).getString(), config.getCharset());
			} else {
				entity = new InputStreamEntity(stream, -1);
			}
		} else if (query != null && query.length() != 0) {
			entity = new StringEntity(query, ContentType.create(URLEncodedUtils.CONTENT_TYPE, config.getCharset()));
		} else {
			logger.debug("POST request does not have any parameters or body");
			entity = new StringEntity("", ContentType.create(URLEncodedUtils.CONTENT_TYPE, config.getCharset()));
			//throw new IllegalArgumentException("POST request does not have any parameters or body");
		}
		return entity;
	}

	private HttpEntity buildEntity(SenderRequest request, List<NameValuePair> nvQuParams)
			throws UnsupportedEncodingException {
		HttpEntity entity;
		if (request.hasBody()) {
			InputStream stream = ((BodyRequest) request).getBodyStream();
			if (stream instanceof StringWrappingStream) {
				entity = new StringEntity(((StringWrappingStream) stream).getString(), config.getCharset());
			} else {
				entity = new InputStreamEntity(stream, -1);
			}
		} else if (nvQuParams != null && nvQuParams.size() != 0) {
			entity = new UrlEncodedFormEntity(nvQuParams, config.getCharset());
		} else {
			logger.debug("POST request does not have any parameters or body");
			entity = new StringEntity("");
			//throw new IllegalArgumentException("POST request does not have any parameters or body");
		}
		return entity;
	}

	protected HttpResponse call(HttpRequestBase httpRequest) throws IOException {
		try {
			if (config.getAuthContext() != null) {
				return this.httpClient.execute(httpRequest, config.getAuthContext());
			} else {
				return this.httpClient.execute(httpRequest);
			}
		} catch (Exception x) {
			//connection might be already open so release it
			httpRequest.releaseConnection();
			if (x instanceof ConnectionPoolTimeoutException) {
				ConnectException ctx = new ConnectException("Pool timeout " + config.getPoolAcquireTimeout() + " ms");
				ctx.setStackTrace(x.getStackTrace());
				throw ctx;
			} else if (x instanceof ConnectTimeoutException) {
				ConnectException ctx = new ConnectException("Connect timeout " + config.getConnectTimeout() + " ms");
				ctx.setStackTrace(x.getStackTrace());
				throw ctx;
			} else if (x instanceof SocketTimeoutException) {
				SocketTimeoutException stx = new SocketTimeoutException("Read timeout " + config.getReadTimeout() + " ms");
				stx.setStackTrace(x.getStackTrace());
				throw stx;
				//java.net.ConnectException: Connection refused
			} else if (x instanceof IOException) {
				throw (IOException) x;//just rethrow IO
			} else {
				throw new IOException(x);//wrap others
			}
		}
	}

}
