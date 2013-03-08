package com.nature.client.http;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.security.BasicAuthenticator;
import org.mortbay.jetty.security.Constraint;
import org.mortbay.jetty.security.ConstraintMapping;
import org.mortbay.jetty.security.DigestAuthenticator;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.Password;
import org.mortbay.jetty.security.SecurityHandler;
import org.mortbay.jetty.security.UserRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JokerServer {

	private Logger logger = LoggerFactory.getLogger(getClass());

	private Server jetty;

	private int httpPort;

	private ServerSocket serverSocket;

	private int frozenPort;

	private int nonHttpPort;

	private String etag = "\"this_is_computed_in_real_life\"";

	private AtomicInteger requestCount = new AtomicInteger();

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");

	public JokerServer() {
		try {
			//httper
			jetty = new Server(0);
			RequestHandler requestHandler = new RequestHandler();
			//jetty.setHandler(requestHandler);//maps to /*
			jetty.setStopAtShutdown(true);

			//realm
			HashUserRealm realm = new HashUserRealm("myrealm");
			realm.put("lajka", new Password("haf!haf!"));
			realm.addUserToRole("lajka", "kosmonaut");
			UserRealm[] realms = { realm };
			jetty.setUserRealms(realms);

			//security
			SecurityHandler basicHandler = getBasicSecurityHandler(realm);
			//basicHandler.setHandler(requestHandler); //response handler
			SecurityHandler digestHandler = getDigestSecurityHandler(realm);
			//digestHandler.setHandler(requestHandler);//response handler
			jetty.setHandlers(new Handler[] { basicHandler, digestHandler, requestHandler });

			//server socket with single element backlog queue and dynamicaly allocated port
			serverSocket = new ServerSocket(0, 1);
			frozenPort = serverSocket.getLocalPort();
			//fill backlog queue by this request so any other request will hang
			new Socket().connect(serverSocket.getLocalSocketAddress());
		} catch (Exception x) {
			throw new RuntimeException(x);
		}
	}

	public JokerServer start() {
		try {
			jetty.start();
		} catch (Exception x) {
			throw new RuntimeException(x);
		}
		httpPort = jetty.getConnectors()[0].getLocalPort();
		logger.info("Http is listening on port " + httpPort);
		logger.info("Freezer is listening on port " + frozenPort);
		return this;
	}

	public void stop() {
		try {
			jetty.stop();
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	public String getEtag() {
		return etag;
	}

	public void setEtag(String etag) {
		this.etag = etag;
	}

	public int getHttpPort() {
		return httpPort;
	}

	public int getRequestCount() {
		return requestCount.get();
	}

	public int getFrozenPort() {
		return frozenPort;
	}

	private class RequestHandler extends AbstractHandler {

		@Override
		public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
				throws IOException, ServletException {
			requestCount.incrementAndGet();
			Date now = new Date();

			response.setCharacterEncoding(request.getCharacterEncoding());

			String hsleep = request.getHeader("sleep");
			if (hsleep != null) {
				sleep(hsleep, request);
			}
			String psleep = request.getParameter("sleep");
			if (psleep != null) {
				sleep(psleep, request);
			}

			String docache = request.getParameter("docache");
			if (docache != null) {
				int seconds = Integer.parseInt(docache);
				Calendar calendar = GregorianCalendar.getInstance();
				calendar.setTime(now);
				calendar.add(Calendar.SECOND, seconds);
				//Older Expire header
				response.setDateHeader("Date", now.getTime());
				response.setDateHeader("Expire", calendar.getTimeInMillis());
				//Newer Cache-Control
				response.setHeader("Cache-Control", "private, max-age=" + seconds);
				//we need unique content - put date into it
				response.setContentType("text/html");
				response.getWriter().println("<h1>DoCache at " + sdf.format(now) + " for " + seconds + " seconds</h1>");
				((Request) request).setHandled(true);
				return;
			}

			String doetag = request.getParameter("doetag");
			if (doetag != null) {
				String cetag = request.getHeader("If-None-Match");
				if (etag.equals(cetag)) {
					response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
					((Request) request).setHandled(true);
					return; //no resposne body...
				} else {
					response.setHeader("ETag", etag);
					//we need unique content - put date into it
					response.setContentType("text/html");
					response.getWriter().println("<h1>DoEtag at " + sdf.format(now) + " for " + etag + " ETag</h1>");
					((Request) request).setHandled(true);
					return;
				}
			}

			//request.getHeader("Content-Type");

			String pstatus = request.getParameter("dostatus");
			if (pstatus != null) {
				int status = Integer.parseInt(pstatus);
				response.setStatus(status);
				response.getWriter().println("<h1>Dostatus " + status + " " + sdf.format(now) + "</h1>");
			} else {
				response.setStatus(HttpServletResponse.SC_OK);
				response.getOutputStream().println("<h1>Hello " + sdf.format(now) + "</h1>");
				//response.getWriter().println("<h1>Hello writer</h1>");
			}
			response.setContentType("text/html");

			((Request) request).setHandled(true);
		}

		private void sleep(String value, HttpServletRequest request) {
			int seconds = Integer.parseInt(value);
			logger.info("server sleep " + seconds + " seconds");
			try {
				Thread.sleep(seconds * 1000);
			} catch (InterruptedException ix) {
				//nothing
			}
		}
	}

	private SecurityHandler getBasicSecurityHandler(UserRealm realm) {

		Constraint constraint = new Constraint(Constraint.__BASIC_AUTH, "kosmonaut");
		constraint.setAuthenticate(true);

		ConstraintMapping mapping = new ConstraintMapping();
		mapping.setConstraint(constraint);
		mapping.setPathSpec("/basic/*");

		SecurityHandler handler = new SecurityHandler();
		handler.setAuthenticator(new BasicAuthenticator());
		handler.setUserRealm(realm);
		handler.setConstraintMappings(new ConstraintMapping[] { mapping });

		return handler;
	}

	private SecurityHandler getDigestSecurityHandler(UserRealm realm) {

		Constraint constraint = new Constraint(Constraint.__DIGEST_AUTH, "kosmonaut");
		constraint.setAuthenticate(true);

		ConstraintMapping mapping = new ConstraintMapping();
		mapping.setConstraint(constraint);
		mapping.setPathSpec("/digest/*");

		SecurityHandler handler = new SecurityHandler();
		handler.setAuthenticator(new DigestAuthenticator());
		handler.setUserRealm(realm);
		handler.setConstraintMappings(new ConstraintMapping[] { mapping });

		return handler;

	}

}
