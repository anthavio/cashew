package com.nature.client.http;

import com.nature.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class GetRequest extends SenderRequest {

	private static final Method method = Method.GET;

	//Constructors of managed request instance knowing it's Sender

	public GetRequest(HttpSender sender) {
		super(sender, method);
	}

	public GetRequest(HttpSender sender, String urlPath) {
		super(sender, method, urlPath);
	}

	public GetRequest(HttpSender sender, Multival parameters) {
		super(sender, method, parameters);
	}

	public GetRequest(HttpSender sender, String urlPath, Multival parameters) {
		super(sender, method, urlPath, parameters);
	}

	//Constructors of standalone request instance without reference to it's Sender

	public GetRequest() {
		super(null, method);
	}

	public GetRequest(String urlPath) {
		super(null, method, urlPath);
	}

	public GetRequest(Multival parameters) {
		super(null, method, parameters);
	}

	public GetRequest(String urlPath, Multival parameters) {
		super(null, method, urlPath, parameters);
	}
}
