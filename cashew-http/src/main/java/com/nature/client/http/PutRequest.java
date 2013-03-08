package com.nature.client.http;

import com.nature.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class PutRequest extends SenderRequest {

	private static final Method method = Method.POST;

	//Constructors of managed request instance knowing it's Sender

	protected PutRequest(HttpSender sender) {
		super(sender, Method.PUT);
	}

	protected PutRequest(HttpSender sender, String urlPath) {
		super(sender, Method.PUT, urlPath);
	}

	protected PutRequest(HttpSender sender, Multival parameters) {
		super(sender, Method.PUT, parameters);
	}

	protected PutRequest(HttpSender sender, String urlPath, Multival parameters) {
		super(sender, Method.PUT, urlPath, parameters);
	}

	//Constructors of standalone request instance without reference to it's Sender

	public PutRequest() {
		super(null, method);
	}

	public PutRequest(String urlPath) {
		super(null, method, urlPath);
	}

	public PutRequest(Multival parameters) {
		super(null, method, parameters);
	}

	public PutRequest(String urlPath, Multival parameters) {
		super(null, method, urlPath, parameters);
	}
}
