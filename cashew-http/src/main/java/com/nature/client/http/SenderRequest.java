package com.nature.client.http;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import com.nature.client.http.HttpSender.Multival;
import com.nature.client.http.ResponseExtractor.ExtractedResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class SenderRequest {

	public static enum Method {
		GET(false), DELETE(false), POST(true), PUT(true);

		private boolean canHaveBody;

		private Method(boolean canHaveBody) {
			this.canHaveBody = canHaveBody;
		}

		public boolean canHaveBody() {
			return this.canHaveBody;
		}
	}

	private transient HttpSender sender;

	private final Method method;

	private final String urlPath;

	private Multival headers;

	private Multival parameters;

	// Constructors of managed request instance knowing it's Sender

	protected SenderRequest(HttpSender sender, Method method) {
		this(sender, method, null, null);
	}

	protected SenderRequest(HttpSender sender, Method method, Multival parameters) {
		this(sender, method, null, parameters);
	}

	protected SenderRequest(HttpSender sender, Method method, String urlPath) {
		this(sender, method, urlPath, null);
	}

	protected SenderRequest(HttpSender sender, Method method, String urlPath, Multival parameters) {
		this.sender = sender; //can be null

		if (method == null) {
			throw new IllegalArgumentException("Method must not be null");
		}
		this.method = method;

		this.urlPath = urlPath;
		this.parameters = parameters;
	}

	public <T extends Serializable> ExtractedResponse<T> extract(ResponseExtractor<T> extractor) throws IOException {
		if (sender == null) {
			throw new IllegalStateException("Request is not attached to HttpSender. Use HttpSender instance to execute it");
		}
		return sender.extract(this, extractor);
	}

	public SenderResponse execute() throws IOException {
		if (sender == null) {
			throw new IllegalStateException("Request is not attached to HttpSender. Use HttpSender instance to execute it");
		}
		return sender.execute(this);
	}

	// Constructors of standalone request instance without reference to it's Sender

	public SenderRequest(Method method) {
		this(null, method, null, null);
	}

	public SenderRequest(Method method, Multival parameters) {
		this(null, method, null, parameters);
	}

	public SenderRequest(Method method, String urlPath) {
		this(null, method, urlPath, null);
	}

	public SenderRequest(Method method, String urlPath, Multival parameters) {
		this(null, method, urlPath, parameters);
	}

	/**
	 * Way to attach Request to Sender if it was not done through constructor
	 */
	public void setSender(HttpSender sender) {
		if (sender == null) {
			throw new IllegalArgumentException("sender is null");
		}
		/*
		//protect from attaching to another sender
		if (this.sender != null && this.sender != sender) {
			throw new IllegalArgumentException("Request is already attached to another " + sender);
		}
		*/
		this.sender = sender;
	}

	public HttpSender getSender() {
		return sender;
	}

	/**
	 * POST or PUT requests should override this
	 */
	public boolean hasBody() {
		return false;
	}

	public Method getMethod() {
		return this.method;
	}

	public String getUrlPath() {
		return this.urlPath;
	}

	public Multival getHeaders() {
		return this.headers;
	}

	public SenderRequest setHeaders(Multival headers) {
		this.headers = headers;
		return this;
	}

	public SenderRequest setHeader(String name, String value) {
		if (this.headers == null) {
			this.headers = new Multival();
		}
		List<String> values = this.headers.get(name);
		if (values != null) {
			values.set(0, value); //replace existing
		} else {
			this.headers.add(name, value);
		}
		return this;
	}

	public SenderRequest setAcceptHeader(String mimeType) {
		if (Cutils.isEmpty(mimeType)) {
			throw new IllegalArgumentException("mimeType is null");
		}
		setHeader("Accept", mimeType);
		return this;
	}

	/*
		public URI getUri() throws MalformedURLException {
			if (sender == null) {
				return URI.create(urlPath);
			} else {
				URL url = sender.getConfig().getUrl();
				String path = getPathAndQuery()[0];
				return new URI(url.getProtocol(), null, url.getHost(), url.getPort(), path);
				//return new URL(url.getProtocol(), url.getHost(), url.getPort(), path);
			}
		}
	*/

	public SenderRequest setHeader(String name, Object value) {
		setHeader(name, String.valueOf(value));
		return this;
	}

	public String getFirstHeader(String name) {
		if (headers != null) {
			return headers.getFirst(name);
		} else {
			return null;
		}
	}

	public Multival getParameters() {
		return this.parameters;
	}

	public SenderRequest setParameters(Multival parameters) {
		this.parameters = parameters;
		return this;
	}

	public SenderRequest addParameter(String name, String value) {
		if (this.parameters == null) {
			this.parameters = new Multival();
		}
		this.parameters.add(name, value);
		return this;
	}

	public SenderRequest addParameter(String name, Object value) {
		addParameter(name, String.valueOf(value));
		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.method == null ? 0 : this.method.hashCode());
		result = prime * result + (this.urlPath == null ? 0 : this.urlPath.hashCode());
		result = prime * result + (this.parameters == null ? 0 : this.parameters.hashCode());
		result = prime * result + (this.headers == null ? 0 : this.headers.hashCode());
		//body is stream and have only Object hashcode
		//result = prime * result + (this.bodyStream == null ? 0 : this.bodyStream.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		SenderRequest other = (SenderRequest) obj;
		if (this.method != other.method) {
			return false;
		}
		if (this.urlPath == null) {
			if (other.urlPath != null) {
				return false;
			}
		} else if (!this.urlPath.equals(other.urlPath)) {
			return false;
		}
		if (this.parameters == null) {
			if (other.parameters != null) {
				return false;
			}
		} else if (!this.parameters.equals(other.parameters)) {
			return false;
		}
		if (this.headers == null) {
			if (other.headers != null) {
				return false;
			}
		} else if (!this.headers.equals(other.headers)) {
			return false;
		}
		//body is stream and have only Object equals
		/*
		if (this.bodyStream == null) {
			if (other.bodyStream != null) {
				return false;
			}
		} else if (!this.bodyStream.equals(other.bodyStream)) {
			return false;
		}
		*/
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(method);
		sb.append(' ');
		if (sender == null) {
			sb.append(sender.joinPath(urlPath));
		} else {
			sb.append(urlPath);
		}
		sb.append(' ');
		sb.append(parameters);
		return sb.toString();
	}
}
