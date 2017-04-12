/*
Copyright (c) 2000-2017 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.util.urlconn;

import java.io.*;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import javax.net.ssl.SSLContext;

//HC3 import org.apache.commons.httpclient.*;
//HC3 import org.apache.commons.httpclient.Header;
//HC3 import org.apache.commons.httpclient.HttpConnection;
//HC3 import org.apache.commons.httpclient.HttpException;
//HC3 import org.apache.commons.httpclient.HttpState;
//HC3 import org.apache.commons.httpclient.auth.BasicScheme;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
//HC3 import org.apache.commons.httpclient.auth.*;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
//HC3 import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
//HC3 import org.apache.commons.httpclient.methods.GetMethod;
//HC3 import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.SetCookie;
//HC3 import org.apache.commons.httpclient.methods.RequestEntity;
//HC3 import org.apache.commons.httpclient.params.*;
//HC3 import org.apache.commons.httpclient.params.DefaultHttpParams;
//HC3 import org.apache.commons.httpclient.params.HttpMethodParams;
//HC3 import org.apache.commons.httpclient.params.HttpParams;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;
//HC3 import org.apache.commons.httpclient.protocol.*;
//HC3 import org.apache.commons.httpclient.protocol.Protocol;
//HC3 import org.apache.commons.httpclient.protocol.SSLProtocolSocketFactory;
//HC3 import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;
//HC3 import org.apache.commons.httpclient.util.*;
//HC3 import org.apache.commons.httpclient.util.DateParseException;
//HC3 import org.apache.commons.httpclient.util.DateUtil;
import org.apache.commons.collections4.map.*;

import org.lockss.config.*;
import org.lockss.util.*;

/** Encapsulates Jakarta HttpClient method as a LockssUrlConnection.
 * Mostly simple wrapper behavior, except cross-host redirects are handled
 * because HttpClient doesn't.
 */
public class HttpClientUrlConnection extends BaseLockssUrlConnection {
  public static Logger log = Logger.getLogger("HttpClientUrlConnection");

  /* Accept header value.  Can be overridden by plugin. */
  static final String PARAM_ACCEPT_HEADER = PREFIX + "acceptHeader";
  static final String DEFAULT_ACCEPT_HEADER =
    "text/html, image/gif, image/jpeg; q=.2, */*; q=.2";

  /** Charset to be used to encode/decode HTTP result headers. */
  static final String PARAM_HEADER_CHARSET = PREFIX + "httpHeaderCharset";
  static final String DEFAULT_HEADER_CHARSET = "ISO-8859-1";

  /** Repeated response headers normally get combined on receipe into a
   * single header with a comma-separated value.  Headers in this list do
   * not get that treatment - the value in the last occurrence of the
   * header is used. */
  static final String PARAM_SINGLE_VALUED_HEADERS =
    PREFIX + "singleValuedHeaders";
  static final List DEFAULT_SINGLE_VALUED_HEADERS = Collections.emptyList();

  /* If true, the InputStream returned from getResponseInputStream() will
   * be wrapped in an EofBugInputStream */
  static final String PARAM_USE_WRAPPER_STREAM = PREFIX + "useWrapperStream";
  static final boolean DEFAULT_USE_WRAPPER_STREAM = true;

  /* If true, any connection on which credentials are set will preemptively
   * send the credentials.  If false they will be sent only after receiving
   * 401, which currently happens on every request. */
  static final String PARAM_USE_PREEMPTIVE_AUTH = PREFIX + "usePreemptiveAuth";
  static final boolean DEFAULT_USE_PREEMPTIVE_AUTH = true;

  /** Choices for trustworthiness required of server. */
  public enum ServerTrustLevel {Trusted, SelfSigned, Untrusted};

  /** Determines the required trustworthiness of HTTPS server certificates.
   *
   * <dl><lh>Set to one of:</lh>
   *
   * <dt>Trusted</dt><dd>Server certificate must be signed by a known CA.</dd>
   *
   * <dt>SeflSigned</dt><dd>Server certificate must be self-signed or signed by a known CA.</dd>
   *
   * <dt>Untrusted</dt><dd>Any server certificate will be accepted.</dd>
   * </dl>
   */
  static final String PARAM_SERVER_TRUST_LEVEL = PREFIX + "serverTrustLevel";
  public static final ServerTrustLevel DEFAULT_SERVER_TRUST_LEVEL =
    ServerTrustLevel.Untrusted;

  /** Key used in HttpParams to communicate so_keepalive value to
   * LockssDefaultProtocolSocketFactory */
  public static String SO_KEEPALIVE = "lockss_so_keepalive";

  public static String SO_CONN_TIMEOUT = "lockss_so_conn_timeout";
  public static String SO_AUTO_CLOSE = "lockss_so_auto_close";
  public static String SO_HTTP_HOST = "lockss_http_so_host";

  // Set up a flexible SSL protocol factory.  It doesn't work to set the
  // Protocol in the HostConfiguration - HttpClient.executeMethod()
  // overwrites it.  So we must communicate the per-host policies to a
  // global factory.
  static DispatchingSSLProtocolSocketFactory DISP_FACT =
    new DispatchingSSLProtocolSocketFactory();
  static {
//HC3     // Install our http factory
//HC3     Protocol http_proto =
//HC3       new Protocol("http",
//HC3 		   LockssDefaultProtocolSocketFactory.getSocketFactory(),
//HC3 		   80);
//HC3     Protocol.registerProtocol("http", http_proto);
//HC3 
//HC3     // Install our https factory
//HC3     Protocol https_proto = new Protocol("https", DISP_FACT, 443);
//HC3     Protocol.registerProtocol("https", https_proto);
    Registry<ConnectionSocketFactory> r =
	RegistryBuilder.<ConnectionSocketFactory>create()
	.register("http", LockssDefaultProtocolSocketFactory.getSocketFactory())
	.register("https", DISP_FACT)
	.build();
    if (log.isDebug3()) log.debug3("static {} : r = " + r);

    DISP_FACT.setDefaultFactory(getDefaultSocketFactory(DEFAULT_SERVER_TRUST_LEVEL));
  }

//HC3   private static ServerTrustLevel serverTrustLevel;
  private static String acceptHeader = DEFAULT_ACCEPT_HEADER;
  private static Set<String> singleValuedHdrs =
    new HashSet(DEFAULT_SINGLE_VALUED_HEADERS);

//  private static SecureProtocolSocketFactory
  static LayeredConnectionSocketFactory
    getDefaultSocketFactory(ServerTrustLevel stl) {
    switch (stl) {
    case Trusted:
//HC3       return new SSLProtocolSocketFactory();
      SSLContext sslContext = null;
      try {
	sslContext = SSLContext.getDefault();
      } catch (NoSuchAlgorithmException e) {
	// This could happen if the JVM does not have the appropriate security
	// extensions.
	throw new RuntimeException(e);
      }
      return new SSLConnectionSocketFactory(sslContext);
    case SelfSigned:
      return new EasySSLProtocolSocketFactory();
    case Untrusted:
    default:
      return new PermissiveSSLProtocolSocketFactory();
    }
  }

  /** Called by org.lockss.config.MiscConfig
   */
  public static void setConfig(Configuration config,
			       Configuration oldConfig,
			       Configuration.Differences diffs) {
    final String DEBUG_HEADER = "setConfig(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Invoked.");

    if (log.isDebug3()) log.debug3(DEBUG_HEADER
	+ "diffs.contains(" + PREFIX + ") = " + diffs.contains(PREFIX));
    if (diffs.contains(PREFIX)) {
      acceptHeader = config.get(PARAM_ACCEPT_HEADER, DEFAULT_ACCEPT_HEADER);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "acceptHeader = " + acceptHeader);

      Set<String> set = new HashSet();
      for (String s : (List<String>)config.getList(PARAM_SINGLE_VALUED_HEADERS,
						   DEFAULT_SINGLE_VALUED_HEADERS)) {
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "s = " + s);
	set.add(s.toLowerCase());
      }

      singleValuedHdrs = set;

//HC3       HttpParams params = DefaultHttpParams.getDefaultParams();

      if (diffs.contains(PARAM_COOKIE_POLICY)) {
	String policy = config.get(PARAM_COOKIE_POLICY, DEFAULT_COOKIE_POLICY);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "policy = " + policy);
//HC3         params.setParameter(HttpMethodParams.COOKIE_POLICY,
//HC3                             getCookiePolicy(policy));
	cookiePolicy = getCookiePolicy(policy);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "cookiePolicy = " + cookiePolicy);
      }

      if (diffs.contains(PARAM_SINGLE_COOKIE_HEADER)) {
	boolean val = config.getBoolean(PARAM_SINGLE_COOKIE_HEADER,
					DEFAULT_SINGLE_COOKIE_HEADER);
	log.debug3(DEBUG_HEADER + "val = " + val);
//HC3         params.setBooleanParameter(HttpMethodParams.SINGLE_COOKIE_HEADER,
//HC3 		   val);

	if (val) {
	  // Per http://mail-archives.apache.org/mod_mbox/hc-httpclient-users/201405.mbox/ajax/%3C1399818037.8345.0.camel%40ubuntu%3E
	  //
	  // > So to my understanding after reading the code for BrowserCompatSpec [0] is
	  // > that when using the "CookieSpecs.BROWSER_COMPATIBILITY" when building the
	  // > cookie spec the single cookie header policy is already in place?
	  // >
	  // >
	  //
	  // Yes, this is correct. 
	  cookiePolicy = CookieSpecs.BROWSER_COMPATIBILITY;
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "cookiePolicy = " + cookiePolicy);
	}
      }

      if (diffs.contains(PARAM_HEADER_CHARSET)) {
	String val = config.get(PARAM_HEADER_CHARSET, DEFAULT_HEADER_CHARSET);
	log.debug3(DEBUG_HEADER + "val = " + val);

//HC3         params.setParameter(HttpMethodParams.HTTP_ELEMENT_CHARSET, val);
	charset = getCharset(val);
	log.debug3(DEBUG_HEADER + "charset = " + charset);
      }
      ServerTrustLevel stl =
	(ServerTrustLevel)config.getEnum(ServerTrustLevel.class,
					 PARAM_SERVER_TRUST_LEVEL,
					 DEFAULT_SERVER_TRUST_LEVEL);
      DISP_FACT.setDefaultFactory(getDefaultSocketFactory(stl));
    }
  }

  static String getCookiePolicy(String policy) {
    if (policy == null) {
      policy =
	  CurrentConfig.getParam(PARAM_COOKIE_POLICY, DEFAULT_COOKIE_POLICY);
    }

    if (Constants.COOKIE_POLICY_RFC_2109.equalsIgnoreCase(policy)) {
//HC3       return CookiePolicy.RFC_2109;
      return CookieSpecs.DEFAULT;
    } else if (Constants.COOKIE_POLICY_NETSCAPE.equalsIgnoreCase(policy)) {
//HC3       return CookiePolicy.NETSCAPE;
      return CookieSpecs.NETSCAPE;
    } else if (Constants.COOKIE_POLICY_IGNORE.equalsIgnoreCase(policy)) {
//HC3       return CookiePolicy.IGNORE_COOKIES;
      return CookieSpecs.IGNORE_COOKIES;
    } else if (Constants.COOKIE_POLICY_COMPATIBILITY.equalsIgnoreCase(policy)) {
//HC3       return CookiePolicy.BROWSER_COMPATIBILITY;
      return CookieSpecs.BROWSER_COMPATIBILITY;
    } else {
//HC3       log.warning("Unknown cookie policy: " + policy +
//HC3                   ", using BROWSER_COMPATIBILITY");
//HC3       return CookiePolicy.BROWSER_COMPATIBILITY;
      log.warning("Unknown cookie policy: " + policy + ", using DEFAULT");
      return CookieSpecs.DEFAULT;
    }
  }

  private static Charset getCharset(String charSetText) {
    if (charSetText == null) {
      charSetText =
	  CurrentConfig.getParam(PARAM_HEADER_CHARSET, DEFAULT_HEADER_CHARSET);
    }

    Charset charSet = Consts.ISO_8859_1;

    switch (charSetText) {
    case "US-ASCII":
      charSet = Consts.ASCII;
      break;
    case "UTF-8":
      charSet = Consts.UTF_8;
      break;
    default:
    }

    return charSet;
  }

//HC3   private HttpClient client;
  private CloseableHttpClient client;
//HC3   private HttpMethod method;
  private LockssMethodImpl method;
  private int methodCode;
  private LockssUrlConnectionPool connectionPool;
  private int responseCode;
  private HttpResponse response;
  private RequestBuilder reqBuilder = null;
  private HttpUriRequest httpUriRequest = null;
  private HttpClientBuilder clientBuilder = null;
  private HttpClientContext context = null;
  private RequestConfig.Builder requestConfigBuilder = null;
  private RequestConfig reqConfig = null;
  private static String cookiePolicy = CookieSpecs.DEFAULT;
  private static Charset charset = null;
  private ConnectionConfig.Builder connectionConfigBuilder = null;
  private HttpClientConnectionManager connManager = null;
  private boolean followRedirects = true;

  /** Create a connection object, defaulting to GET method */
//HC3   public HttpClientUrlConnection(String urlString, HttpClient client)
  public HttpClientUrlConnection(String urlString,
      HttpClientContext clientContext)
      throws IOException {
//HC3     this(LockssUrlConnection.METHOD_GET, urlString, client, null);
    this(LockssUrlConnection.METHOD_GET, urlString, clientContext, null);
  }

  /** Create a connection object, with specified method */
//HC3   public HttpClientUrlConnection(int methodCode, String urlString,
//HC3 	    HttpClient client, LockssUrlConnectionPool connectionPool)
  public HttpClientUrlConnection(int methodCode, String urlString,
      HttpClientContext clientContext, LockssUrlConnectionPool connectionPool)
	  throws IOException {
    super(urlString);
    final String DEBUG_HEADER = "HttpClientUrlConnection(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "methodCode = " + methodCode);
      log.debug2(DEBUG_HEADER + "urlString = " + urlString);
      log.debug2(DEBUG_HEADER + "clientContext = " + clientContext);
      log.debug2(DEBUG_HEADER + "connectionPool = " + connectionPool);
    }

//HC3     this.client = client != null ? client : new HttpClient();
    this.methodCode = methodCode;
    method = createMethod(methodCode, urlString);
    this.connectionPool = connectionPool;

    reqBuilder = RequestBuilder.get().setUri(urlString);

    // Handle cookies.
    CookieStore cookieStore = null;

    if (clientContext == null) {
      context = HttpClientContext.create();
    } else {
      context = clientContext;
      cookieStore = context.getCookieStore();
    }

    requestConfigBuilder = RequestConfig.custom();

    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "cookieStore = " + cookieStore);
    if (cookieStore == null) {
      cookieStore = new BasicCookieStore();
      context.setCookieStore(cookieStore);
      requestConfigBuilder.setCookieSpec(cookiePolicy);
    }

    clientBuilder = HttpClients.custom();
    clientBuilder.setDefaultCookieStore(cookieStore);

    connectionConfigBuilder = ConnectionConfig.custom();
    connectionConfigBuilder.setCharset(charset);

    if (connectionPool == null) {
      if (methodCode == LockssUrlConnection.METHOD_POST) {
	connManager = new PoolingHttpClientConnectionManager();
      } else {
	connManager = new BasicHttpClientConnectionManager();
      }
    } else {
      connManager = connectionPool.getHttpClientConnectionManager(methodCode);
    }
  }

//  private HttpMethod createMethod(int methodCode, String urlString)
  private LockssMethodImpl createMethod(int methodCode, String urlString)
      throws IOException {
    String u_str = urlString;
    try {
      if(log.isDebug2())
        log.debug2("in:"+ urlString + " isAscii:"+ StringUtil.isAscii(urlString));
      if(!StringUtil.isAscii(urlString)) {
        if(log.isDebug2()) log.debug2("in:" + u_str);
        u_str = UrlUtil.encodeUri(urlString, Constants.ENCODING_UTF_8);
        if(log.isDebug2()) log.debug2("out:" + u_str);
      }
      switch (methodCode) {
        case LockssUrlConnection.METHOD_GET:
          return new LockssGetMethodImpl(u_str);
        case LockssUrlConnection.METHOD_PROXY:
          return new LockssProxyGetMethodImpl(u_str);
        case LockssUrlConnection.METHOD_POST:
          return new LockssPostMethodImpl(u_str);
      }
      throw new RuntimeException("Unknown url method: " + methodCode);
    } catch (IllegalArgumentException e) {
      // HttpMethodBase throws IllegalArgumentException on illegal URLs
      // Canonicalize that to Java's MalformedURLException
      throw newMalformedURLException(u_str, e);
    } catch (IllegalStateException e) {
      // HttpMethodBase throws IllegalArgumentException on illegal protocols
      // Canonicalize that to Java's MalformedURLException
      throw newMalformedURLException(urlString, e);
    }
  }

  java.net.MalformedURLException newMalformedURLException(String msg,
        Throwable cause) {
    java.net.MalformedURLException e = new java.net.MalformedURLException(msg);
    e.initCause(cause);
    return e;
  }

  /** for testing */
//HC3   protected HttpClientUrlConnection(String urlString, HttpClient client,
//HC3 				      LockssGetMethod method)
//HC3       throws IOException {
//HC3     super(urlString);
//HC3     this.client = client;
//HC3     this.method = method;
//HC3   }

//HC3   protected LockssGetMethod newLockssGetMethodImpl(String urlString) {
  protected LockssGetMethodImpl newLockssGetMethodImpl(String urlString) {
      return new LockssGetMethodImpl(urlString);
  }

  public boolean isHttp() {
    return true;
  }

  /** Execute the request. */
  public void execute() throws IOException {
    final String DEBUG_HEADER = "execute(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "isExecuted = " + isExecuted);

    assertNotExecuted();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "methodCode = " + methodCode);
    if (methodCode != LockssUrlConnection.METHOD_PROXY) {
      mimicSunRequestHeaders();
    }

//HC3     HostConfiguration hostConfig = client.getHostConfiguration();
    if (proxyHost != null) {
//HC3       hostConfig.setProxy(proxyHost, proxyPort);
      requestConfigBuilder.setProxy(new HttpHost(proxyHost, proxyPort));
    } else {
//HC3       hostConfig.setProxyHost(null);
      requestConfigBuilder.setProxy(null);
    }
    if (localAddress != null) {
//HC3       hostConfig.setLocalAddress(localAddress.getInetAddr());
      requestConfigBuilder.setLocalAddress(localAddress.getInetAddr());
    } else {
//HC3       hostConfig.setLocalAddress(null);
      requestConfigBuilder.setLocalAddress(null);
    }

    if (sockFact != null) {
//HC3       SecureProtocolSocketFactory hcSockFact =
      LayeredConnectionSocketFactory hcSockFact =
	sockFact.getHttpClientSecureProtocolSocketFactory();
      String host = url.getHost();
      int port = url.getPort();
      if (port <= 0) {
	port = UrlUtil.getDefaultPort(url.getProtocol().toLowerCase());
      }
      DISP_FACT.setFactory(host, port, hcSockFact);
      // XXX Would like to check after connection is made that cert check
      // was actually done, but there's no good way to get to the socket or
      // SSLContect, etc.
      isAuthenticatedServer = sockFact.requiresServerAuth();
    }
    isExecuted = true;
    responseCode = executeOnce(method);
  }

//HC3   private int executeOnce(HttpMethod method) throws IOException {
  private int executeOnce(LockssMethodImpl method) throws IOException {
    final String DEBUG_HEADER = "executeOnce(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "method = " + method);

    try {
      ConnectionConfig connConfig = connectionConfigBuilder.build();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "connConfig = " + connConfig);

      if (connManager instanceof PoolingHttpClientConnectionManager) {
	((PoolingHttpClientConnectionManager)connManager)
	.setDefaultConnectionConfig(connConfig);
      } else {
	((BasicHttpClientConnectionManager)connManager)
	.setConnectionConfig(connConfig);
      }

      SocketConfig socketConfig = null;

      if (connectionPool != null) {
	int connectTimeout = connectionPool.getConnectTimeout();
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "connectTimeout = " + connectTimeout);

	if (connectTimeout != -1) {
	  requestConfigBuilder.setConnectTimeout(connectTimeout);
	}

	int dataTimeout = connectionPool.getDataTimeout();
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "dataTimeout = " + dataTimeout);

	if (dataTimeout != -1) {
	  requestConfigBuilder.setSocketTimeout(dataTimeout);
	}

	socketConfig = SocketConfig.custom()
	    .setSoKeepAlive(connectionPool.getKeepAlive()).build();
      } else {
	socketConfig = SocketConfig.DEFAULT;
      }

      reqConfig = requestConfigBuilder.build();
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "reqConfig = " + reqConfig);

      clientBuilder.setConnectionManager(connManager)
      .setDefaultSocketConfig(socketConfig).setDefaultRequestConfig(reqConfig);

      if (!followRedirects) {
	clientBuilder.disableRedirectHandling();
      }

      client = clientBuilder.build();

      httpUriRequest = reqBuilder.build();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "httpUriRequest = " + httpUriRequest);

      logContext(DEBUG_HEADER);

      if (log.isDebug3()) {
	for (Header header : httpUriRequest.getAllHeaders()) {
	  log.debug3(DEBUG_HEADER + "header = " + header);
	}
      }

      response = executeRequest(httpUriRequest, context);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "response = " + response);

      logContext(DEBUG_HEADER);

//HC3       return client.executeMethod(method);
      return response.getStatusLine().getStatusCode();
//HC3     } catch (ConnectTimeoutException /*| java.net.SocketTimeoutException*/ e) {
//HC3       // Thrown by HttpClient if the connect timeout elapses before
//HC3       // socket.connect() returns.
    } catch (ClientProtocolException e) {
      // Thrown by HttpClient4 for a variety of reasons.
      // TODO: Do we want to rename it?
      // Turn this into a non HttpClient-specific exception
      throw new ConnectionTimeoutException("Host did not respond", e);
      // XXX If socket.connect() returns an error because the underlying
      // socket connect times out, the behavior is platform dependent.  On
      // Linux, java.net.ConnectException is thrown (same as for connection
      // refused, and distunguishable only by the exception message).  On
      // OpenBSD, java.net.SocketException is thrown with a message like
      // "errno: 22, error: Invalid argument for fd: 3".  In lieu of a way
      // to reliably determine when to turn these into a
      // ConnectionTimeoutException, the solution for now is to use
      // java-level connect timeouts that are shorter than the underlying
      // socket connect timeout.
//HC3     } catch (NoHttpResponseException e) {
//HC3       // Thrown by HttpClient if the server closes the connection before
//HC3       // sending a response.
//HC3       // Turn this into a non HttpClient-specific exception
    } catch (UnknownHostException uhe) {
      log.error("Unknown host", uhe);
      throw uhe;
    } catch (SocketTimeoutException ste) {
      log.error("Read timed out", ste);
      throw ste;
    } catch (HttpHostConnectException hhce) {
      ConnectException ce = new ConnectException("Connection refused");
      ce.initCause(hhce);
      throw ce;
    } catch (IOException e) {
      java.net.SocketException se =
	new java.net.SocketException("Connection reset by peer");
      se.initCause(e);
      throw se;
    }
  }

  protected HttpResponse executeRequest(HttpUriRequest httpUriRequest,
	HttpClientContext context) throws ClientProtocolException, IOException {
    return client.execute(httpUriRequest, context);
  }

  public boolean canProxy() {
    return true;
  }

  public void setRequestProperty(String key, String value) {
    final String DEBUG_HEADER = "setRequestProperty(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "key = " + key);
      log.debug2(DEBUG_HEADER + "value = " + value);
    }

    assertNotExecuted();
//HC3     method.setRequestHeader(key, value);
    reqBuilder.setHeader(key, value);
  }

  public void addRequestProperty(String key, String value) {
    final String DEBUG_HEADER = "addRequestProperty(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "key = " + key);
      log.debug2(DEBUG_HEADER + "value = " + value);
    }

    assertNotExecuted();
//HC3     method.addRequestHeader(key, value);
    reqBuilder.addHeader(key, value);
  }

//HC3   public void setHeaderCharset(String charset) {
  public void setHeaderCharset(String charsetText) {
    final String DEBUG_HEADER = "setHeaderCharset(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "charsetText = " + charsetText);

    assertNotExecuted();
//HC3     HttpMethodParams params = method.getParams();
//HC3     params.setHttpElementCharset(charset);
    charset = getCharset(charsetText);
    reqBuilder.setCharset(charset);
  }

  public void setFollowRedirects(boolean followRedirects) {
    final String DEBUG_HEADER = "setFollowRedirects(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "followRedirects = " + followRedirects);

    assertNotExecuted();
//HC3     method.setFollowRedirects(followRedirects);
    this.followRedirects = followRedirects;
  }

  public void setCookiePolicy(String policy) {
    final String DEBUG_HEADER = "setCookiePolicy(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "policy = " + policy);

    assertNotExecuted();
//HC3     HttpParams params = method.getParams();
//HC3     params.setParameter(HttpMethodParams.COOKIE_POLICY,
//HC3                         getCookiePolicy(policy));
    cookiePolicy = getCookiePolicy(policy);
  }

  public void setKeepAlive(boolean val) {
    final String DEBUG_HEADER = "setKeepAlive(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "val = " + val);

    assertNotExecuted();
//HC3     HttpParams params = method.getParams();
//HC3     params.setBooleanParameter(SO_KEEPALIVE, val);
    // method params don't current work, set in connection pool also,
    // though that won't affect already-open sockets
    connectionPool.setKeepAlive(val);
  }

  public void addCookie(String domain, String path, String name, String value) {
    final String DEBUG_HEADER = "addCookie(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "domain = " + domain);
      log.debug2(DEBUG_HEADER + "path = " + path);
      log.debug2(DEBUG_HEADER + "name = " + name);
      log.debug2(DEBUG_HEADER + "value = " + value);
    }

    assertNotExecuted();
//HC3     HttpState state = client.getState();
//HC3     Cookie cook = new Cookie(domain, name, value, path, null, false);
//HC3     state.addCookie(cook);
    SetCookie cookie = new BasicClientCookie(name, value);
    cookie.setDomain(domain);
    cookie.setPath(path);
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "cookie = " + cookie);

    context.getCookieStore().addCookie(cookie);
  }

  public void setCredentials(String username, String password) {
    final String DEBUG_HEADER = "setCredentials(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "username = " + username);
      log.debug2(DEBUG_HEADER + "password = " + password);
    }

    assertNotExecuted();
//HC3     Credentials credentials = new UsernamePasswordCredentials(username,
//HC3 	      password);
//HC3     HttpState state = client.getState();
//HC3     state.setCredentials(AuthScope.ANY, credentials);
    CredentialsProvider provider = new BasicCredentialsProvider();

    provider.setCredentials(AuthScope.ANY,
	new UsernamePasswordCredentials(username, password));

    context.setCredentialsProvider(provider);

    // Check whether preemptive authentication is to be used.
    if (CurrentConfig.getBooleanParam(PARAM_USE_PREEMPTIVE_AUTH,
                                      DEFAULT_USE_PREEMPTIVE_AUTH)) {
//HC3       HttpClientParams params = client.getParams();
//HC3       params.setAuthenticationPreemptive(true);
      // Yes.
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "Using Preemptive Authorization...");

      // Set up the target host.
      String host = url.getHost();
      String protocol = url.getProtocol().toLowerCase();
      int port = url.getPort();

      if (port <= 0) {
  	port = UrlUtil.getDefaultPort(protocol);
      }

      HttpHost targetHost = new HttpHost(host, port, protocol);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "targetHost = " + targetHost);

      // The local authentication cache.
      AuthCache authCache = new BasicAuthCache();

      // Generate the BASIC authentication scheme as defined in RFC 2617.
      BasicScheme basicAuth = new BasicScheme();

      // Add it to the local authentication cache.
      authCache.put(targetHost, basicAuth);
   
      // Add local authentication cache to the execution context.
      context.setAuthCache(authCache);
    }
  }

    /** method for passing through the post content */
//HC3   public void setRequestEntity(RequestEntity entity) {
  public void setRequestEntity(HttpEntity entity) {
    final String DEBUG_HEADER = "setRequestEntity(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Invoked.");

    assertNotExecuted();
//HC3     if(method instanceof PostMethod) {
//HC3       ((PostMethod) method).setRequestEntity(entity);
//HC3     }
    if(method instanceof LockssPostMethodImpl) {
      reqBuilder.setEntity(entity);
    }
  }

//HC3   HttpClient getClient() {
//HC3     return client;
//HC3   }

  public String getResponseHeaderFieldVal(int n) {
    final String DEBUG_HEADER = "getResponseHeaderFieldVal(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "n = " + n);

    assertExecuted();
    String result = null;

    try {
//HC3       return method.getResponseHeaders()[n].getValue();
      result = response.getAllHeaders()[n].getValue();
    } catch (ArrayIndexOutOfBoundsException e) {
//HC3       return null;
      // Use the default.
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  public String getResponseHeaderFieldKey(int n) {
    final String DEBUG_HEADER = "getResponseHeaderFieldKey(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "n = " + n);

    assertExecuted();
    String result = null;

    try {
//HC3       return method.getResponseHeaders()[n].getName();
      result = response.getAllHeaders()[n].getName();
    } catch (ArrayIndexOutOfBoundsException e) {
//HC3       return null;
      // Use the default.
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  public int getResponseCode() {
    assertExecuted();
    return responseCode;
  }

  public String getResponseMessage() {
    assertExecuted();
//HC3     return method.getStatusText();
    return response.getStatusLine().getReasonPhrase();
  }

  public long getResponseDate() {
    final String DEBUG_HEADER = "getResponseDate(): ";
    assertExecuted();

    String datestr = getResponseHeaderValue("date");
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "datestr = " + datestr);
//HC3     if (datestr == null) {
//HC3       return -1;
//HC3     }
//HC3     try {
    long result = -1L;

    if (datestr != null) {
//HC3       return DateUtil.parseDate(datestr).getTime();
      Date date = DateUtils.parseDate(datestr);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "date = " + date);

      if (date != null) {
	result = date.getTime();
//HC3       } catch (DateParseException e) {
      } else {
	log.error("Error parsing response Date: header: " + datestr);
      }
//HC3     } catch (DateParseException e) {
//HC3       log.error("Error parsing response Date: header: " + datestr, e);
//HC3       return -1;
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  public long getResponseLastModified() {
    final String DEBUG_HEADER = "getResponseLastModified(): ";
    String datestr = getResponseHeaderValue("last-modified");
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "datestr = " + datestr);
//HC3     if (datestr == null) {
//HC3       return -1;
//HC3     }
//HC3     try {
    long result = -1L;

    if (datestr != null) {
//HC3       return DateUtil.parseDate(datestr).getTime();
      Date date = DateUtils.parseDate(datestr);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "date = " + date);

      if (date != null) {
	result = date.getTime();
      } else {
	log.error("Error parsing response last-modified: header: " + datestr);
      }
//HC3     } catch (DateParseException e) {
//HC3     log.error("Error parsing response last-modified: header: " + datestr, e);
//HC3     return -1;
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  public long getResponseContentLength() {
    assertExecuted();
//HC3     if (method instanceof LockssGetMethod) {
//HC3       LockssGetMethod getmeth = (LockssGetMethod)method;
//HC3       return getmeth.getResponseContentLength();
//HC3     }
//HC3  
//HC3     throw new UnsupportedOperationException(method.getClass().toString());
    return response.getEntity().getContentLength();
  }

  public String getResponseContentType() {
    return getResponseHeaderValue("Content-Type");
  }

  public String getResponseContentEncoding() {
    return getResponseHeaderValue("content-encoding");
  }

  public String getResponseHeaderValue(String name) {
    final String DEBUG_HEADER = "getResponseHeaderValue(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "name = " + name);

    assertExecuted();

    if (response == null) {
      return null;
    }

    if (log.isDebug3()) {
      org.apache.http.Header[] headers = response.getAllHeaders();
      log.debug3(DEBUG_HEADER + "headers.length = " + headers.length);
      for (int ix = 0; ix < headers.length; ix++) {
	org.apache.http.Header hdr = headers[ix];
	log.debug3(DEBUG_HEADER + "name = " + hdr.getName() + ", value = "
	    + hdr.getValue());
      }
    }
    
//HC3     Header header = method.getResponseHeader(name);
    Header header = response.getFirstHeader(name);
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "header = " + header);

    return (header != null) ? header.getValue() : null;
  }

  public InputStream getResponseInputStream() throws IOException {
    assertExecuted();
//HC3     InputStream in = method.getResponseBodyAsStream();
    InputStream in = getResponseBodyAsStream();
    if (in == null) {
      // this is a normal occurrence (e.g., with 304 response)
      log.debug2("Returning null input stream");
      return null;
    }
    if (CurrentConfig.getBooleanParam(PARAM_USE_WRAPPER_STREAM,
                                      DEFAULT_USE_WRAPPER_STREAM)) {
      return new EofBugInputStream(in);
    }
    return in;
  }

  protected InputStream getResponseBodyAsStream() throws IOException {
    if (response == null) {
      log.debug2("Returning null input stream for null response");
      return null;
    }

    HttpEntity entity = response.getEntity();
    if (entity == null) {
      log.debug2("Returning null input stream for null entity");
      return null;
    }

    return entity.getContent();
  }

  public void storeResponseHeaderInto(Properties props, String prefix) {
    // store all header properties (this is the only way to iterate)
    // first collect all values for any repeated headers.
    MultiValueMap<String,String> map = new MultiValueMap<String,String>();
//HC3     Header[] headers = method.getResponseHeaders();
    Header[] headers = getResponseHeaders();
    for (int ix = 0; ix < headers.length; ix++) {
      Header hdr = headers[ix];
      String key = hdr.getName();
      String value = hdr.getValue();
      if (value!=null) {
        // only store headers with values
        // qualify header names to avoid conflict with our properties
	if (key == null) {
	  key = "header_" + ix;
	}
	String propKey = (prefix == null) ? key : prefix + key;
	if (!singleValuedHdrs.isEmpty() &&
	    singleValuedHdrs.contains(key.toLowerCase())) {
	  map.remove(propKey);
	}
	map.put(propKey, value);
      }
    }
    // now combine multiple values into comma-separated string
    for (String key : map.keySet()) {
      Collection<String> val = map.getCollection(key);
      props.setProperty(key, ((val.size() > 1)
			      ? StringUtil.separatedString(val, ",")
			      : CollectionUtil.getAnElement(val)));
    }
  }

  protected Header[] getResponseHeaders() {
    return response.getAllHeaders();
  }

  public String getActualUrl() {
//HC3     try {
//HC3       String path = method.getPath();
//HC3       String query = method.getQueryString();
//HC3       if (!StringUtil.isNullString(query)) {
//HC3 	path = path + "?" + query;
//HC3       }
//HC3       URI uri = new URI(new URI(urlString, false), path, true);
//HC3       return uri.toString();
//HC3     } catch(URIException e) {
//HC3       log.warning("getActualUrl(): ", e);
//HC3       return urlString;
//HC3     }
    return reqBuilder.build().getRequestLine().getUri();
  }

  /** Mimic Java 1.3 HttpURLConnection default request header behavior */
  private void mimicSunRequestHeaders() {
//HC3     if (!isHeaderSet(method.getRequestHeader("Accept"))) {
    if (!isHeaderSet(reqBuilder.getFirstHeader("Accept"))) {
      setRequestProperty("Accept", acceptHeader);
    }
//HC3     if (!isHeaderSet(method.getRequestHeader("Connection"))) {
    if (!isHeaderSet(reqBuilder.getFirstHeader("Connection"))) {
      setRequestProperty("Connection", "keep-alive");
    }
  }

  private boolean isHeaderSet(Header hdr) {
    return (hdr == null) ? false : !StringUtil.isNullString(hdr.getValue());
  }

  /**
   * Release resources associated with this request.
   */
  public void release() {
    assertExecuted();
    try {
//HC3     method.releaseConnection();
      client.close();
      connectionPool.resetHttpClientConnectionManager();
    } catch (IOException ioe) {
      // Nothing to do.
    }
  }

  /**
   * Abort the request.
   */
  public void abort() {
//HC3     method.abort();
    httpUriRequest.abort();
  }

  private void logContext(String debugHeader) {
    if (log.isDebug3()) {
      log.debug3(debugHeader + "context = " + context);

      if (context != null) {
	log.debug3(debugHeader + "Cookie spec = " + context.getCookieSpec());
	log.debug3(debugHeader
	    + "Cookie origin = " + context.getCookieOrigin());
	log.debug3(debugHeader
	    + "Cookies = " + context.getCookieStore().getCookies());

	for (Cookie cookie : context.getCookieStore().getCookies()) {
	  log.debug3(debugHeader + "cookie = " + cookie);
	}
      }
    }
  }

  RequestBuilder getRequestBuilder() {
    return reqBuilder;
  }

  RequestConfig getRequestConfig() {
    return reqConfig;
  }

  boolean getFollowRedirects() {
    return followRedirects;
  }

  Charset getCharset() {
    return charset;
  }

  /** Common interface for our methods makes testing more convenient */
//HC3   interface LockssGetMethod extends HttpMethod {
  interface LockssGetMethod {
//HC3     long getResponseContentLength();
  }

  static abstract class LockssMethodImpl implements LockssGetMethod {
    public LockssMethodImpl(String url) {
    }
  }

  /** Same as GET method
   */
  static class LockssGetMethodImpl
//HC3       extends GetMethod implements LockssGetMethod {
  extends LockssMethodImpl {

    public LockssGetMethodImpl(String url) {
      super(url);
      // Establish our retry handler
//       setMethodRetryHandler(getRetryHandler());
    }
  }

  /**
   * same as the POST method
   */
//HC3 interface LockssPostMethod extends HttpMethod {
  interface LockssPostMethod {
//HC3     long getResponseContentLength();
  }

  static class LockssPostMethodImpl
//HC3     extends PostMethod implements LockssPostMethod {
  extends LockssMethodImpl implements LockssPostMethod {

    public LockssPostMethodImpl(String url) {
      super(url);
    }
  }

  /** Extends GET method to not add any default request headers
   */
  static class LockssProxyGetMethodImpl extends LockssGetMethodImpl {

    public LockssProxyGetMethodImpl(String url) {
      super(url);
    }

//HC3     protected void addRequestHeaders(HttpState state, HttpConnection conn)
//HC3 	throws IOException, HttpException {
//HC3       // Suppress this - don't want any automatic header processing when
//HC3       // acting as a proxy.
//HC3     }
  }

  /** Extension of ConnectionTimeoutException used as a wrapper for the
   * HttpClient-specific HttpConnection.ConnectionTimeoutException. */
  public class ConnectionTimeoutException
    extends LockssUrlConnection.ConnectionTimeoutException {

    public ConnectionTimeoutException(String msg) {
      super(msg);
    }
    public ConnectionTimeoutException(String msg, Throwable t) {
      super(msg, t);
    }
    public ConnectionTimeoutException(Throwable t) {
      super(t.getMessage(), t);
    }
  }
}
