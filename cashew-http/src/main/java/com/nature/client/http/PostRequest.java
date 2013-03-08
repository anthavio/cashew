package com.nature.client.http;

import com.nature.client.http.HttpSender.Multival;

/**
 * 
 * @author martin.vanek
 *
 */
public class PostRequest extends BodyRequest {

	private static final Method method = Method.POST;

	//Constructors of managed request instance knowing it's Sender

	protected PostRequest(HttpSender sender) {
		super(sender, method);
	}

	protected PostRequest(HttpSender sender, String urlPath) {
		super(sender, method, urlPath);
	}

	protected PostRequest(HttpSender sender, Multival parameters) {
		super(sender, method, parameters);
	}

	protected PostRequest(HttpSender sender, String urlPath, Multival parameters) {
		super(sender, method, urlPath, parameters);
	}

	//Constructors of standalone request instance without reference to it's Sender

	public PostRequest() {
		super(null, method);
	}

	public PostRequest(String urlPath) {
		super(null, method, urlPath);
	}

	public PostRequest(Multival parameters) {
		super(null, method, parameters);
	}

	public PostRequest(String urlPath, Multival parameters) {
		super(null, method, urlPath, parameters);
	}

}
