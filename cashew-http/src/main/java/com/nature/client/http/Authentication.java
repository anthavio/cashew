package com.nature.client.http;

/**
 * 
 * @author martin.vanek
 *
 */
public class Authentication {

	public static enum Scheme {
		BASIC, DIGEST;
	}

	private Scheme scheme;

	private String username;

	private String password;

	private boolean preemptive;

	private String realm;

	private String nonce;

	protected Authentication() {

	}

	/**
	 * BASIC preemptive shorthand
	 */
	public Authentication(String username, String password) {
		this(Scheme.BASIC, username, password, true);
	}

	public Authentication(Scheme scheme, String username, String password) {
		this(scheme, username, password, scheme == Scheme.BASIC);
	}

	public Authentication(Scheme scheme, String username, String password, boolean preemptive) {
		this(scheme, username, password, preemptive, null, null);
	}

	public Authentication(Scheme scheme, String username, String password, boolean preemptive, String realm) {
		this(scheme, username, password, preemptive, realm, null);
	}

	public Authentication(Scheme scheme, String username, String password, boolean preemptive, String realm, String nonce) {
		if (scheme == null) {
			throw new IllegalArgumentException("scheme is null");
		}
		this.scheme = scheme;

		if (Cutils.isBlank(username)) {
			throw new IllegalArgumentException("username is blank");
		}
		this.username = username;

		if (password == null) {
			throw new IllegalArgumentException("password is blank");//leave option for blank password
		}
		this.password = password;

		this.preemptive = preemptive;

		this.realm = realm;

		if (preemptive && scheme == Scheme.DIGEST) {
			if (Cutils.isBlank(realm)) {
				throw new IllegalArgumentException("Parameter realm must be known when using preemptive DIGEST");
			}
			this.realm = realm;
			if (Cutils.isBlank(nonce)) {
				throw new IllegalArgumentException("Parameter nonce must be known when using preemptive DIGEST");
			}
			this.nonce = nonce;
		}
	}

	public Scheme getScheme() {
		return this.scheme;
	}

	public void setScheme(Scheme scheme) {
		this.scheme = scheme;
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean getPreemptive() {
		return this.preemptive;
	}

	public void setPreemptive(boolean preemptive) {
		this.preemptive = preemptive;
	}

	public String getRealm() {
		return this.realm;
	}

	public void setRealm(String realm) {
		this.realm = realm;
	}

	public String getNonce() {
		return this.nonce;
	}

	public void setNonce(String nonce) {
		this.nonce = nonce;
	}

}
