package com.nature.client.http;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nature.client.http.ResponseExtractor.ExtractedResponse;
import com.nature.client.http.async.ResponseHandler;

/**
 * 
 * @author martin.vanek
 *
 */
public abstract class HttpSender implements Closeable {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private ExecutorService executor;

	private final HttpSenderConfig config;

	public HttpSender(HttpSenderConfig config) {
		if (config == null) {
			throw new IllegalArgumentException("null config");
		}
		this.config = config;
	}

	public HttpSender(HttpSenderConfig config, ExecutorService executor) {
		this(config);
		this.executor = executor;
	}

	public ExecutorService getExecutor() {
		return executor;
	}

	public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	public HttpSenderConfig getConfig() {
		return config;
	}

	/**
	 * Extremely important for caching -  generates proper key based on information from request and sender
	 */
	public String getCacheKey(SenderRequest request) {
		return String.valueOf(config.getUrl().toString().hashCode() * 31 + request.hashCode());
	}

	/**
	 * Response returning version. Caller must close Response
	 */
	public final SenderResponse execute(SenderRequest request) throws IOException {
		//request.setSender(this);
		String[] pathquery = getPathAndQuery(request);
		String path = pathquery[0];
		String query = pathquery[1];

		if (this.logger.isDebugEnabled()) {
			this.logger.debug(request.getMethod() + " " + path);
		}

		return doExecute(request, path, query);
	}

	protected abstract SenderResponse doExecute(SenderRequest request, String path, String query) throws IOException;

	/**
	 * Response handler version. Response will be close automaticaly
	 */
	public void execute(SenderRequest request, ResponseHandler handler) throws IOException {
		if (handler == null) {
			throw new IllegalArgumentException("null handler");
		}
		SenderResponse response = execute(request);
		try {
			handler.handle(response);
		} finally {
			Cutils.close(response);
		}
	}

	/**
	 * Extracted response version. Response is extracted, closed and result is returned to caller
	 */
	public <T extends Serializable> ExtractedResponse<T> extract(SenderRequest request, ResponseExtractor<T> extractor)
			throws IOException {
		SenderResponse response = execute(request);
		/*
		if (response.getCode() != HttpURLConnection.HTTP_OK) {
			throw new Xxx
		}
		*/
		try {
			T extracted = extractor.extract(response);
			return new ExtractedResponse<T>(response, extracted);
		} finally {
			Cutils.close(response);
		}
	}

	public ExtractedResponse<String> extract(SenderRequest request) throws IOException {
		return extract(request, ResponseExtractor.STRING);
	}

	/**
	 * Asynchronous extraction with Future as response
	 */
	public <T extends Serializable> Future<ExtractedResponse<T>> start(final SenderRequest request,
			final ResponseExtractor<T> extractor) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		return executor.submit(new Callable<ExtractedResponse<T>>() {

			@Override
			public ExtractedResponse<T> call() throws Exception {
				return extract(request, extractor);
			}
		});
	}

	/**
	 * Asynchronous execution whith ResponseHandler
	 */
	public <T extends Serializable> void start(final SenderRequest request, final ResponseHandler handler) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					execute(request, handler);
				} catch (IOException iox) {
					logger.warn("Failed asynchronous request", iox);
				}
			}
		});
	}

	/**
	 * Asynchronous execution with Future as response
	 */
	public Future<SenderResponse> start(final SenderRequest request) {
		if (executor == null) {
			throw new IllegalStateException("Executor for asynchronous requests is not configured");
		}
		return executor.submit(new Callable<SenderResponse>() {

			@Override
			public SenderResponse call() throws Exception {
				return execute(request);
			}
		});
	}

	protected String joinPath(String reqPath) {
		String cfgPath = config.getUrl().getPath();
		String path;
		if (reqPath != null) {
			if (reqPath.startsWith(cfgPath)) {
				//do not repeat config path if it is in request path
				path = reqPath;
			} else if (reqPath.startsWith("//")) {
				// double slash means don't use base path at all - little bit hackish
				path = reqPath.substring(1);
			}
			boolean s1 = cfgPath.endsWith("/");
			boolean s2 = reqPath.startsWith("/") || reqPath.startsWith("?");
			if (!s1 && !s2) {
				path = cfgPath + "/" + reqPath;
			} else if (s1 && s2) {
				path = cfgPath.substring(0, cfgPath.length() - 1) + reqPath;
			} else {
				path = cfgPath + reqPath;
			}
		} else {
			path = cfgPath;
		}
		return path;
	}

	protected String[] getPathAndQuery(SenderRequest request) {
		String path = joinPath(request.getUrlPath());
		String encoding = config.getEncoding();
		Multival parameters = request.getParameters();
		StringBuilder sbMxParams = null;
		StringBuilder sbQuParams = null;
		boolean bQp = false; //is any query parameter
		//XXX multivalue parameter encode paramA=val1,val2
		if (parameters != null && parameters.size() != 0) {
			sbMxParams = new StringBuilder();
			sbQuParams = new StringBuilder();
			for (String name : parameters) {
				if (name.charAt(0) == ';') { //matrix parameter
					List<String> values = parameters.get(name);
					for (String value : values) {
						sbMxParams.append(';');//keep unescaped
						sbMxParams.append(urlencode(name.substring(1), encoding));
						sbMxParams.append('=');
						//XXX matrix parameters may contain / and that / must be unescaped
						sbMxParams.append(urlencode(value, encoding));
					}
				} else { //query parameter
					List<String> values = parameters.get(name);
					for (String value : values) {
						sbQuParams.append(urlencode(name, encoding));
						sbQuParams.append('=');
						sbQuParams.append(urlencode(value, encoding));
						sbQuParams.append('&');
						bQp = true;
					}
				}
			}
			//remove trailing '&'
			if (bQp) {
				sbQuParams.delete(sbQuParams.length() - 1, sbQuParams.length());
			}
		}

		//append matrix parameters if any
		if (sbMxParams != null && sbMxParams.length() != 0) {
			path = path + sbMxParams;
		}

		//append query parameters if are any and if apropriate
		if (bQp) {
			if (!request.getMethod().canHaveBody()) {
				path = path + "?" + sbQuParams.toString();// GET, DELETE
			} else if (request.hasBody()) {
				path = path + "?" + sbQuParams.toString(); // POST, PUT with body
			}
		}
		String query = sbQuParams != null ? sbQuParams.toString() : null;
		return new String[] { path, query };
	}

	private final String urlencode(String string, String encoding) {
		try {
			return URLEncoder.encode(string, encoding);
		} catch (UnsupportedEncodingException uex) {
			throw new IllegalStateException("Misconfigured encoding " + encoding, uex);
		}
	}

	/**
	 * New attached Request buld methods
	 */

	public GetRequest newGetRequest() {
		return new GetRequest(this);
	}

	public GetRequest newGetRequest(String urlPath) {
		return new GetRequest(this, urlPath);
	}

	public GetRequest newGetRequest(Multival parameters) {
		return new GetRequest(this, parameters);
	}

	public GetRequest newGetRequest(String urlPath, Multival parameters) {
		return new GetRequest(this, urlPath, parameters);
	}

	public PostRequest newPostRequest() {
		return new PostRequest(this);
	}

	public PostRequest newPostRequest(String urlPath) {
		return new PostRequest(this, urlPath);
	}

	public PostRequest newPostRequest(Multival parameters) {
		return new PostRequest(this, parameters);
	}

	public PostRequest newPostRequest(String urlPath, Multival parameters) {
		return new PostRequest(this, urlPath, parameters);
	}

	public PutRequest newPutRequest() {
		return new PutRequest(this);
	}

	public PutRequest newPutRequest(String urlPath) {
		return new PutRequest(this, urlPath);
	}

	public PutRequest newPutRequest(Multival parameters) {
		return new PutRequest(this, parameters);
	}

	public PutRequest newPutRequest(String urlPath, Multival parameters) {
		return new PutRequest(this, urlPath, parameters);
	}

	public DeleteRequest newDeleteRequest() {
		return new DeleteRequest(this);
	}

	public DeleteRequest newDeleteRequest(String urlPath) {
		return new DeleteRequest(this, urlPath);
	}

	public DeleteRequest newDeleteRequest(Multival parameters) {
		return new DeleteRequest(this, parameters);
	}

	public DeleteRequest newDeleteRequest(String urlPath, Multival parameters) {
		return new DeleteRequest(this, urlPath, parameters);
	}

	@Override
	public String toString() {
		return "HttpSender [" + config.getUrl() + ", executor=" + executor + "]";
	};

	/**
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class Multival implements Iterable<String>, Serializable {

		private static final long serialVersionUID = 1L;

		private Map<String, List<String>> entries;

		public Multival() {

		}

		public Multival(Map<String, List<String>> entries) {
			this.entries = new TreeMap<String, List<String>>(COMPARATOR);// response header from HttpUrlConnection has null header name for status line
			this.entries.putAll(entries);
			Set<Entry<String, List<String>>> entrySet = this.entries.entrySet();
			for (Entry<String, List<String>> entry : entrySet) {
				List<String> values = entry.getValue();
				LinkedList<String> valuesCopy = new LinkedList<String>(values);
				if (values == null || values.size() == 0) {
					valuesCopy.add("");
				}
				entry.setValue(valuesCopy);
			}
		}

		public Multival(List<String[]> values) {
			if (values != null) {
				for (String[] value : values) {
					if (value.length == 0) {
						//continue;
					} else if (value.length == 1) {
						add(value[0], "");
					} else if (value.length == 2) {
						add(value[0], value[1]);
					} else {
						String[] others = new String[value.length - 2];
						System.arraycopy(value, 2, others, 0, value.length - 2);
						add(value[0], value[1], others);
					}
				}
			}
		}

		public void add(String name, String value, String... others) {
			put(name, value, others);
		}

		public void put(String name, String value, String... others) {
			if (Cutils.isBlank(name)) {
				throw new IllegalArgumentException("Name is blank");
			}
			List<String> list = null;
			if (this.entries == null) {
				this.entries = new TreeMap<String, List<String>>();
			} else {
				list = this.entries.get(name);
			}

			if (list == null) {
				list = new LinkedList<String>();
				this.entries.put(name, list);
			}

			if (value != null) {
				list.add(value);
			} else {
				list.add("");
			}
			for (String other : others) {
				if (other != null) {
					list.add(other);
				} else {
					list.add("");
				}
			}
		}

		public Set<String> names() {
			if (this.entries == null) {
				return Collections.emptySet();
			} else {
				return this.entries.keySet();
			}
		}

		public int size() {
			return this.entries == null ? 0 : this.entries.size();
		}

		public List<String> get(String name) {
			if (this.entries == null) {
				return null;
			} else {
				return this.entries.get(name);
			}
		}

		public String getFirst(String name) {
			List<String> values = get(name);
			if (values != null) {
				return values.get(0);
			} else {
				return null;
			}
		}

		public String getLast(String name) {
			List<String> values = get(name);
			if (values != null) {
				return values.get(values.size() - 1);
			} else {
				return null;
			}
		}

		@Override
		public Iterator<String> iterator() {
			if (this.entries == null) {
				return Collections.<String> emptySet().iterator();
			}
			return names().iterator();
		}

		@Override
		public String toString() {
			return String.valueOf(this.entries);
		}

		@Override
		public int hashCode() {
			return this.entries == null ? 0 : this.entries.hashCode();
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
			Multival other = (Multival) obj;
			if (this.entries == null) {
				if (other.entries != null) {
					return false;
				}
			} else if (!this.entries.equals(other.entries)) {
				return false;
			}
			return true;
		}
	}

	private static final Comparator<String> COMPARATOR = new NullSafeStringComparator();

	private static class NullSafeStringComparator implements Comparator<String> {

		@Override
		public int compare(String o1, String o2) {
			if (o1 == o2) {
				return 0;
			} else if (o1 == null) {
				return -1;
			} else if (o2 == null) {
				return 1;
			} else {
				return o1.compareTo(o2);
			}
		}
	}

}
