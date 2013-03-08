package com.nature.client.http;

import java.io.IOException;
import java.io.Serializable;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseExtractor<T extends Serializable> {

	public static final ResponseExtractor<String> STRING = new ResponseExtractor<String>() {

		@Override
		public String extract(SenderResponse response) throws IOException {
			return HttpHeaderUtil.readAsString(response);
		}
	};

	public static final ResponseExtractor<byte[]> BYTES = new ResponseExtractor<byte[]>() {

		@Override
		public byte[] extract(SenderResponse response) throws IOException {
			return HttpHeaderUtil.readAsBytes(response);
		}
	};

	public T extract(SenderResponse response) throws IOException;

	/**
	 * Used as wrapper of extracted response
	 * 
	 * @author martin.vanek
	 * 
	 */
	public static class ExtractedResponse<T extends Serializable> {

		private final SenderResponse response;

		private final T extracted;

		public ExtractedResponse(SenderResponse response, T extracted) {
			this.response = response;
			this.extracted = extracted;
		}

		public SenderResponse getResponse() {
			return response;
		}

		public T getExtracted() {
			return extracted;
		}

	}

}
