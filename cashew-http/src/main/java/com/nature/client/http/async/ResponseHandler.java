package com.nature.client.http.async;

import java.io.IOException;

import com.nature.client.http.SenderResponse;

/**
 * 
 * @author martin.vanek
 *
 */
public interface ResponseHandler {

	public void handle(SenderResponse response) throws IOException;
}
