package com.nature.client.http;

import com.nature.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class DeleteRequest extends SenderRequest {

	private static final Method method = Method.DELETE;

	//Constructors of managed request instance knowing it's Sender

	protected DeleteRequest(HttpSender sender) {
		super(sender, method);
	}

	protected DeleteRequest(HttpSender sender, String urlPath) {
		super(sender, method, urlPath);
	}

	protected DeleteRequest(HttpSender sender, Multival parameters) {
		super(sender, method, parameters);
	}

	protected DeleteRequest(HttpSender sender, String urlPath, Multival parameters) {
		super(sender, Method.DELETE, urlPath, parameters);
	}

	//Constructors of standalone request instance without reference to it's Sender

	public DeleteRequest() {
		super(null, method);
	}

	public DeleteRequest(String urlPath) {
		super(null, method, urlPath);
	}

	public DeleteRequest(Multival parameters) {
		super(null, method, parameters);
	}

	public DeleteRequest(String urlPath, Multival parameters) {
		super(null, method, urlPath, parameters);
	}
}
