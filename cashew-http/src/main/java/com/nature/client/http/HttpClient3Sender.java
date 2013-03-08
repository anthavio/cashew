package com.nature.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.ConnectionPoolTimeoutException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.util.EncodingUtil;

import com.nature.client.http.BodyRequest.StringWrappingStream;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpClient3Sender extends HttpSender {

	private final HttpClient httpClient;

	private final HttpClient3Config config;

	public HttpClient3Sender(String baseUrl) {
		this(new HttpClient3Config(baseUrl));
	}

	public HttpClient3Sender(HttpClient3Config config) {
		super(config);
		this.config = config;
		this.httpClient = config.buildHttpClient();
	}

	@Override
	public HttpClient3Config getConfig() {
		return config;
	}

	@Override
	public void close() {
		try {
			if (httpClient.getHttpConnectionManager() instanceof MultiThreadedHttpConnectionManager) {
				MultiThreadedHttpConnectionManager connectionManager = (MultiThreadedHttpConnectionManager) httpClient
						.getHttpConnectionManager();
				connectionManager.closeIdleConnections(0); //shutdown do not empty connection pool
				connectionManager.shutdown();
			}
		} catch (Exception x) {
			logger.warn("Exception while closing sender", x);
		}
	}

	/**
	 * Backdoor
	 */
	public HttpClient getHttpClient() {
		return httpClient;
	}

	@Override
	public HttpClient3Response doExecute(SenderRequest request, String path, String query) throws IOException {
		/*
		String path = buildPath(config.getUrl().getPath(), request.getUrlPath());

		Multival parameters = request.getParameters();
		List<NameValuePair> nvQuParams = null;
		StringBuilder sbMxParams = null;
		if (parameters != null && parameters.size() != 0) {
			nvQuParams = new LinkedList<NameValuePair>();
			sbMxParams = new StringBuilder();
			for (String name : parameters) {
				if (name.charAt(0) == ';') {
					//matrix parameter
					List<String> values = parameters.get(name);
					for (String value : values) {
						sbMxParams.append(name);
						sbMxParams.append('=');
						sbMxParams.append(URLEncoder.encode(value, config.getEncoding()));
					}
				} else {
					//query parameter
					List<String> values = parameters.get(name);
					for (String value : values) {
						nvQuParams.add(new NameValuePair(name, value));
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
				// GET, DELETE
				NameValuePair[] nvArray = nvQuParams.toArray(new NameValuePair[nvQuParams.size()]);
				path = path + "?" + EncodingUtil.formUrlEncode(nvArray, config.getEncoding());
			} else if (request.hasBody()) {
				// POST, PUT with body
				NameValuePair[] nvArray = nvQuParams.toArray(new NameValuePair[nvQuParams.size()]);
				path = path + "?" + EncodingUtil.formUrlEncode(nvArray, config.getEncoding());
			}
		}
		*/

		HttpMethodBase httpMethod;
		switch (request.getMethod()) {
		case GET:
			httpMethod = new GetMethod(path);
			break;
		case DELETE:
			httpMethod = new DeleteMethod(path);
			break;
		case POST:
			PostMethod httpPost = new PostMethod(path);
			RequestEntity entityPost = buildEntity(request, query);
			httpPost.setRequestEntity(entityPost);
			httpMethod = httpPost;
			break;
		case PUT:
			PutMethod httpPut = new PutMethod(path);
			RequestEntity entityPut = buildEntity(request, query);
			httpPut.setRequestEntity(entityPut);
			httpMethod = httpPut;
			break;
		default:
			throw new IllegalArgumentException("Unsupported method " + request.getMethod());
		}

		Multival headers = request.getHeaders();
		if (headers != null && headers.size() != 0) {
			for (String name : headers) {
				List<String> values = headers.get(name);
				for (String value : values) {
					httpMethod.addRequestHeader(name, value);
				}
			}
		}

		int statusCode = call(httpMethod);

		Header[] responseHeaders = httpMethod.getResponseHeaders();

		Multival outHeaders = new Multival();
		for (Header header : responseHeaders) {
			outHeaders.add(header.getName(), header.getValue());
		}
		StatusLine statusLine = httpMethod.getStatusLine();
		HttpClient3Response response = new HttpClient3Response(statusCode, statusLine.getReasonPhrase(), outHeaders,
				httpMethod.getResponseBodyAsStream(), httpMethod);
		return response;
	}

	private RequestEntity buildEntity(SenderRequest request, String query) throws UnsupportedEncodingException {
		RequestEntity entity;
		if (request.hasBody()) {
			InputStream stream = ((BodyRequest) request).getBodyStream();
			if (stream instanceof StringWrappingStream) {
				//request.getContentType();
				entity = new StringRequestEntity(((StringWrappingStream) stream).getString(), null, config.getEncoding());
			} else {
				entity = new InputStreamRequestEntity(stream);
			}
		} else if (query != null && query.length() != 0) {
			entity = new ByteArrayRequestEntity(EncodingUtil.getBytes(query, config.getEncoding()),
					PostMethod.FORM_URL_ENCODED_CONTENT_TYPE);
		} else {
			logger.debug("POST request does not have any parameters or body");
			entity = new StringRequestEntity("", null, config.getEncoding());
			//throw new IllegalArgumentException("POST request does not have any parameters or body");
		}
		return entity;
	}

	private RequestEntity buildEntity(SenderRequest request, List<NameValuePair> nvQuParams)
			throws UnsupportedEncodingException {
		RequestEntity entity;
		if (request.hasBody()) {
			InputStream stream = ((BodyRequest) request).getBodyStream();
			if (stream instanceof StringWrappingStream) {
				entity = new StringRequestEntity(((StringWrappingStream) stream).getString(), null, config.getEncoding());
			} else {
				entity = new InputStreamRequestEntity(stream);
			}
		} else if (nvQuParams != null && nvQuParams.size() != 0) {
			String content = EncodingUtil.formUrlEncode(nvQuParams.toArray(new NameValuePair[nvQuParams.size()]),
					config.getEncoding());
			entity = new ByteArrayRequestEntity(EncodingUtil.getBytes(content, config.getEncoding()),
					PostMethod.FORM_URL_ENCODED_CONTENT_TYPE);
		} else {
			logger.debug("POST request does not have any parameters or body");
			entity = new StringRequestEntity("", null, config.getEncoding());
			//throw new IllegalArgumentException("POST request does not have any parameters or body");
		}
		return entity;
	}

	protected int call(HttpMethodBase httpRequest) throws IOException {
		try {
			return this.httpClient.executeMethod(httpRequest);
		} catch (Exception x) {
			//connection might be already open so release request
			httpRequest.releaseConnection();
			//now try to 
			if (x instanceof ConnectionPoolTimeoutException) {
				ConnectException cx = new ConnectException("Pool timeout " + config.getPoolAcquireTimeout() + " ms");
				cx.setStackTrace(x.getStackTrace());
				throw cx;
			} else if (x instanceof ConnectTimeoutException) {
				ConnectException cx = new ConnectException("Connect timeout " + config.getConnectTimeout() + " ms");
				cx.setStackTrace(x.getStackTrace());
				throw cx;
			} else if (x instanceof SocketTimeoutException) {
				SocketTimeoutException stx = new SocketTimeoutException("Read timeout " + config.getReadTimeout() + " ms");
				stx.setStackTrace(x.getStackTrace());
				throw stx;
			} else if (x instanceof IOException) {
				throw (IOException) x;//just rethrow IO
			} else {
				throw new IOException(x.getMessage(), x);//wrap others
			}
		}
	}

}
