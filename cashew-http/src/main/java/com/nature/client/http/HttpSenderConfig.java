package com.nature.client.http;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttpSenderConfig {

	private final URL url;

	private Authentication authentication;

	private String encoding = "UTF-8";

	private Charset charset = Charset.forName(this.encoding);

	private boolean compress = false;

	private String acceptType; //default Accept header

	private int connectTimeout = 5 * 1000; //in millis

	private int readTimeout = 20 * 1000; //in millis

	private ExecutorService executorService;

	public HttpSenderConfig(String urlString) {
		if (Cutils.isBlank(urlString)) {
			throw new IllegalArgumentException("URL is blank");
		}
		if (urlString.startsWith("http") == false) {
			urlString = "http://" + urlString;
			//throw new IllegalArgumentException("URL must start with http " + urlString);
		}
		URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException mux) {
			throw new IllegalArgumentException("URL is invalid " + urlString, mux);
		}
		if (Cutils.isBlank(url.getHost())) {
			throw new IllegalArgumentException("URL has no host " + urlString);
		}
		if (Cutils.isBlank(url.getPath())) {
			try {
				url = new URL(url.getProtocol(), url.getHost(), url.getPort(), "/");
			} catch (MalformedURLException mux) {
				throw new IllegalArgumentException(mux);
			}
		}
		this.url = url;
	}

	public HttpSender buildSender() {
		return new SimpleHttpSender(this);
	}

	public String getEncoding() {
		return this.encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
		this.charset = Charset.forName(encoding);
	}

	public Charset getCharset() {
		return this.charset;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public int getConnectTimeout() {
		return this.connectTimeout;
	}

	public void setConnectTimeout(int millis) {
		this.connectTimeout = millis;
	}

	public int getReadTimeout() {
		return this.readTimeout;
	}

	public void setReadTimeout(int millis) {
		this.readTimeout = millis;
	}

	public URL getUrl() {
		return this.url;
	}

	public Authentication getAuthentication() {
		return this.authentication;
	}

	public void setAuthentication(Authentication authentication) {
		this.authentication = authentication;
	}

	public ExecutorService getExecutorService() {
		return this.executorService;
	}

	public void setExecutorService(ExecutorService executorService) {
		this.executorService = executorService;
	}

	public boolean getCompress() {
		return compress;
	}

	public void setCompress(boolean compress) {
		this.compress = compress;
	}

	public String getAcceptType() {
		return acceptType;
	}

	public void setAcceptType(String acceptType) {
		this.acceptType = acceptType;
	}

}
