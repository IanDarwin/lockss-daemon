/*
 * $Id: TestUrlUtil.java,v 1.21 2005-06-20 04:03:57 tlipkis Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.util;

import java.net.*;
import junit.framework.TestCase;
import org.lockss.test.*;
import org.lockss.util.*;

public class TestUrlUtil extends LockssTestCase {
  private static Logger log = Logger.getLogger("TestUrlUtil");

  // For testing against the behavior of code extracted from Java 1.4 URI class
  static String normalizePath(String path) {
    switch (1) {
    case 1:
      try {
	return UrlUtil.normalizePath(path, true);
      } catch (MalformedURLException e) {
	throw new RuntimeException(e.toString());
      }
    case 2:
      try {
	Object urin =
	  PrivilegedAccessor.invokeConstructor("JavaUriNormalizer");
	return (String)PrivilegedAccessor.invokeMethod(urin,
						       "normalizePath",
						       ListUtil.list(path).toArray());
      } catch (Exception e) {
	log.warning("Couldn't invoke JavaUriNormalizer", e);
	return null;
      }
    }
    throw new RuntimeException();
  }

  public void assertSameNormalizePath(String s) {
    assertSame(s, normalizePath(s));
  }

  public void testNormalizePath() throws Exception {
    assertSameNormalizePath("");
    assertSameNormalizePath("a");
    assertSameNormalizePath("a/");
    assertSameNormalizePath("/a");
    assertSameNormalizePath("/");
    assertSameNormalizePath("/a/b/");
    assertSameNormalizePath("/a/.b/");
    assertSameNormalizePath("/a/b./");
    assertSameNormalizePath("/a/..b/");
    assertSameNormalizePath("/a/b../");
    assertSameNormalizePath("/a/.b");
    assertSameNormalizePath("/a/b.");
    assertSameNormalizePath("/a/..b");
    assertSameNormalizePath("/a/b..");
    assertSameNormalizePath("/a/b/c/");

    assertEquals("", normalizePath("."));
    assertEquals("..", normalizePath(".."));
    assertEquals("/", normalizePath("/."));
    assertEquals("", normalizePath("./"));
    assertEquals("../", normalizePath("../"));
    assertEquals("/..", normalizePath("/.."));
    assertEquals("../a", normalizePath("../a"));
    assertEquals("/..", normalizePath("/a/../.."));

    assertEquals("/a/b", normalizePath("/a/./b"));
    assertEquals("/a/b", normalizePath("/a/c/../b"));
    assertEquals("/a/b", normalizePath("//a/b"));
    assertEquals("/a/b", normalizePath("//a//b"));
    assertEquals("/a/b", normalizePath("//a///b"));

    assertEquals("/a/b/", normalizePath("/a/./b/"));
    assertEquals("/a/b/", normalizePath("/a/c/../b/"));
    assertEquals("/a/b/", normalizePath("//a/b/"));
    assertEquals("/a/b/", normalizePath("//a//b/"));
    assertEquals("/a/b/", normalizePath("//a///b/"));

    assertEquals("a/b", normalizePath("a/./b"));
    assertEquals("a/b", normalizePath("a/c/../b"));
    assertEquals("a/b", normalizePath("a//b"));
    assertEquals("a/b", normalizePath("a///b"));

    assertEquals("/", normalizePath("/a/.."));
    assertEquals("/a/", normalizePath("/a/b/.."));
    assertEquals("/a/", normalizePath("/a/b/../"));
    assertEquals("/", normalizePath("/a/b/../.."));
    assertEquals("/", normalizePath("/a/b/../../"));

    assertEquals("/a/b", normalizePath("/a/c/d/../../b"));
    assertEquals("/a/", normalizePath("/a/c/d/../../b/../"));
    assertEquals("/a/", normalizePath("/a/c/d/../../b/.."));
    assertEquals("/..", normalizePath("/a/c/../../.."));
    assertEquals("/../..", normalizePath("/a/c/../../../.."));
    assertEquals("/../", normalizePath("/a/c/../../../"));
    assertEquals("/../../", normalizePath("/a/c/../../../../"));
    assertEquals("/", normalizePath("/a/.."));

    assertEquals("/../x", normalizePath("/a/c/../../../x"));
  }

  public void assertSameNormalizeUrl(String s) throws MalformedURLException {
    assertSame(s, UrlUtil.normalizeUrl(s));
  }

  public void testNormalizeUrl() throws MalformedURLException {
    assertSameNormalizeUrl("http://a/b");

    assertEquals("http://a.com/b", UrlUtil.normalizeUrl("HTTP://A.COM/b"));
    assertEquals("http://a.com/B", UrlUtil.normalizeUrl("HTTP://A.COM/B"));

    assertEquals("http://a.com/", UrlUtil.normalizeUrl("http://a.com/"));
    assertEquals("http://a.com/", UrlUtil.normalizeUrl("http://a.com"));
    assertEquals("http://a.com/xy", UrlUtil.normalizeUrl("http://a.com/xy"));
    assertEquals("http://a.com/xy/", UrlUtil.normalizeUrl("http://a.com/xy/"));
    assertEquals("http://a.com/xy/",
		 UrlUtil.normalizeUrl("http://a.com/xy/ab/.."));
    assertEquals("http://a.com/xy/",
		 UrlUtil.normalizeUrl("http://a.com/xy/ab/../"));
    assertEquals("http://a.com/",
		 UrlUtil.normalizeUrl("http://a.com/xy/ab/../../"));
    // port
    assertEquals("http://a.com/b",
		 UrlUtil.normalizeUrl("HTTP://A.COM:80/b"));
    assertEquals("http://a.com:8000/b",
		 UrlUtil.normalizeUrl("HTTP://A.COM:8000/b"));
    assertEquals("ftp://example.com/",
		 UrlUtil.normalizeUrl("ftp://example.com:21/"));
    // query not removed
    assertEquals("http://a.com/dd?foo=bar",
		 UrlUtil.normalizeUrl("http://a.com/dd?foo=bar"));

    try {
      String s = "http://a.com/xy/ab/../../../";
      UrlUtil.normalizeUrl(s);
      fail(s);
    } catch (MalformedURLException e) {
    }
    try {
      String s = "http://a.com/xy/ab/../../../a/b/c/d";
      UrlUtil.normalizeUrl(s);
      fail(s);
    } catch (MalformedURLException e) {
    }
  }

  public void testNormalizeUrlQueryString() throws MalformedURLException {
    assertEquals("http://a.com/dd?foo=bar",
		 UrlUtil.normalizeUrl("http://A.com/dd?foo=bar"));
  }

  public void testNormalizeUrlRemovesHash() throws MalformedURLException {
    assertEquals("http://a.com/xy",
		 UrlUtil.normalizeUrl("http://a.com/xy#blah"));

    assertEquals("http://www.bioone.org/perlserv/?request=archive-lockss&issn=0044-7447&volume=033",
		 UrlUtil.normalizeUrl("http://www.bioone.org/perlserv/?request=archive-lockss&issn=0044-7447&volume=033#content"));
  }

  public void testEqualUrls() throws MalformedURLException {
    assertTrue(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				 new URL("http://foo.bar/xyz#tag")));
    assertTrue(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				 new URL("HTTP://FOO.bar/xyz#tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("ftp://foo.bar/xyz#tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("http://foo.baz/xyz#tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("http://foo.bar/xyzz#tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("http://foo.bar/xYz#tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("http://foo.bar/xyz#tag2")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("http://foo.bar/xyz#Tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http:80//foo.bar/xyz#tag"),
				  new URL("http:81//foo.bar/xyz#tag")));
  }

  public void testIsHttpUrl() {
    assertTrue(UrlUtil.isHttpUrl("http://foo"));
    assertTrue(UrlUtil.isHttpUrl("https://foo"));
    assertTrue(UrlUtil.isHttpUrl("HTTP://foo"));
    assertTrue(UrlUtil.isHttpUrl("HTTPS://foo"));
    assertFalse(UrlUtil.isHttpUrl("ftp://foo"));
    assertFalse(UrlUtil.isHttpUrl("file://foo"));
  }

  public void testGetUrlPrefixNullUrl(){
    try{
      UrlUtil.getUrlPrefix(null);
      fail("Should have thrown MalformedURLException");
    }
    catch(MalformedURLException mue){
    }
  }

  public void testGetUrlPrefixNotHttpUrl(){
    try{
      UrlUtil.getUrlPrefix("bad test string");
      fail("Should have thrown MalformedURLException");
    }
    catch(MalformedURLException mue){
    }
  }

  public void testGetUrlPrefixRootHighWireUrl() throws MalformedURLException{
    String root = "http://shadow8.stanford.edu/";
    String url = root + "lockss-volume327.shtml";
    assertEquals(root, UrlUtil.getUrlPrefix(url));
  }

  public void testGetUrlPrefixRootHighWireUrlWithOddPort()
      throws MalformedURLException{
    String root = "http://shadow8.stanford.edu:8080/";
    String url = root + "lockss-volume327.shtml";
    assertEquals(root, UrlUtil.getUrlPrefix(url));
  }

  public void testGetUrlPrefixPrefixUrl() throws MalformedURLException{
    String root0 = "http://shadow8.stanford.edu";
    String root = root0 + "/";
    assertEquals(root, UrlUtil.getUrlPrefix(root));
    assertEquals(root, UrlUtil.getUrlPrefix(root0));
  }

  public void testGetUrlPrefixSame() throws MalformedURLException{
    String root = "http://shadow8.stanford.edu:8080/";
    assertSame(root, UrlUtil.getUrlPrefix(root));
  }

  public void testGetHost() throws Exception {
    assertEquals("xx.foo.bar", UrlUtil.getHost("http://xx.foo.bar/123"));
    assertEquals("foo", UrlUtil.getHost("http://foo/123"));
    assertEquals("foo.", UrlUtil.getHost("http://foo./123"));
    try{
      UrlUtil.getHost("garbage://xx.foo.bar/123");
      fail("Should have thrown MalformedURLException");
    }
    catch (MalformedURLException mue) {
    }
  }

  public void testGetDomain() throws Exception {
    assertEquals("foo.bar", UrlUtil.getDomain("http://xx.foo.bar/123"));
    assertEquals("foo", UrlUtil.getDomain("http://foo/123"));
    assertEquals("foo.", UrlUtil.getDomain("http://foo./123"));
    try{
      UrlUtil.getDomain("garbage://xx.foo.bar/123");
      fail("Should have thrown MalformedURLException");
    }
    catch (MalformedURLException mue) {
    }
  }

  public void testMinimallyEncode() throws Exception {
    try {
      assertEquals(null, UrlUtil.minimallyEncodeUrl(null));
      fail("minimallyEncodeUrl(null) didn't throw");
    } catch (NullPointerException e) {}    
    assertEquals("", UrlUtil.minimallyEncodeUrl(""));
    assertEquals("foo", UrlUtil.minimallyEncodeUrl("foo"));
    assertEquals("foo%20", UrlUtil.minimallyEncodeUrl("foo "));
    assertEquals("f%22oo%20", UrlUtil.minimallyEncodeUrl("f\"oo "));
    assertEquals("%20foo%7c", UrlUtil.minimallyEncodeUrl(" foo|"));
  }

  public void testResolveUrl() throws Exception {
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("ftp://gorp.org/xxx.jpg",
				    "http://test.com/foo/bar/a.html"));
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/xxx.html",
				    "a.html"));
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/baz/xxx.html",
				    "../a.html"));
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/",
				    "a.html"));
    // ensure query string preserved
    assertEquals("http://test.com/foo/bar/a.html?foo=bar",
		 UrlUtil.resolveUri("http://test.com/foo/bar/",
				    "a.html?foo=bar"));
    try {
      UrlUtil.resolveUri("foo", "bar");
      fail("Should throw MalformedURLException");
    } catch (MalformedURLException e) {}
  }

  //should trip leading and trailing whitespace from the second arg
  public void testResolveUrlTrimsLeadingAndTrailingWhiteSpace()
      throws MalformedURLException {
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/", " a.html"));
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/", "a.html "));
    assertEquals("http://test.com/foo/bar/a.html",
 		 UrlUtil.resolveUri("http://test.com/foo/bar/", "\ta.html "));
    assertEquals("http://test.com/foo/bar/a.html",
 		 UrlUtil.resolveUri("http://test.com/foo/bar/", "\na.html "));
    assertEquals("http://test.com/foo/bar/a.html",
 		 UrlUtil.resolveUri("http://test.com/foo/bar/", "a.html\n "));
    assertEquals("http://test.com/foo/bar/a.html",
 		 UrlUtil.resolveUri("http://test.com/foo/bar/", "a.html\r "));
  }

  public void testResolveUrlEncoding() throws MalformedURLException {
    // Embedded space should be escaped
    assertEquals("http://test.com/foo/bar/a%20test.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/",
				    "a test.html"));
    // Percents should not be escaped, or risk double escapement
    assertEquals("http://test.com/foo/bar/a%20.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/",
				    "a%20.html"));
  }

  public void testIsDirectoryRedirection() {
    assertTrue(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					      "http://xx.com/foo/"));
    assertTrue(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					      "Http://xx.com/foo/"));
    assertTrue(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					      "Http://Xx.COM/foo/"));
    assertFalse(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					       "http://xx.com/FOO/"));
    assertFalse(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					       "http://xx.com/foo"));
    assertFalse(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					       "http://zz.com/foo/"));

    assertTrue(UrlUtil.isDirectoryRedirection("http://xx.com/foo?a=b",
					      "http://xx.com/foo/?a=b"));
    // slash appended to query string isn't
    assertFalse(UrlUtil.isDirectoryRedirection("http://xx.com/foo?a=b",
					      "http://xx.com/foo?a=b/"));
    // ensure doesn't totally ignore query
    assertFalse(UrlUtil.isDirectoryRedirection("http://xx.com/foo?a=b",
					      "http://xx.com/foo/"));
    // not legal URLs, so returns false
    assertFalse(UrlUtil.isDirectoryRedirection("foo", "foo/"));
  }

  public void testGetHeadersNullConnection() {
    try {
      UrlUtil.getHeaders(null);
      fail("Calling getHeaderFields with a null argument should have thrown");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testGetHeadersOneHeader() throws MalformedURLException {
    URL url = new URL("http://www.example.com");
    MockURLConnection conn = new MockURLConnection(url);
    conn.setHeaderFieldKeys(ListUtil.list("key1"));
    conn.setHeaderFields(ListUtil.list("field1"));
    assertEquals(ListUtil.list("key1;field1"), UrlUtil.getHeaders(conn));
  }

  public void testGetHeadersMultiHeaders() throws MalformedURLException {
    URL url = new URL("http://www.example.com");
    MockURLConnection conn = new MockURLConnection(url);
    conn.setHeaderFieldKeys(ListUtil.list("key1", "key2"));
    conn.setHeaderFields(ListUtil.list("field1", "field2"));

    assertEquals(ListUtil.list("key1;field1", "key2;field2"),
		 UrlUtil.getHeaders(conn));
  }

  public void testGetHeadersNullHeaders() throws MalformedURLException {
    URL url = new URL("http://www.example.com");
    MockURLConnection conn = new MockURLConnection(url);
    conn.setHeaderFieldKeys(ListUtil.list(null, "key2"));
    conn.setHeaderFields(ListUtil.list("field1", null));

    assertEquals(ListUtil.list("null;field1", "key2;null"),
		 UrlUtil.getHeaders(conn));
  }

  public void testIsAbsoluteUrl() {
    assertFalse(UrlUtil.isAbsoluteUrl(null));
    assertFalse(UrlUtil.isAbsoluteUrl(""));
    assertTrue(UrlUtil.isAbsoluteUrl("http://www.example.com/"));
    assertTrue(UrlUtil.isAbsoluteUrl("http://www.example.com/blah/"));
    assertFalse(UrlUtil.isAbsoluteUrl("www.example.com/"));
    assertFalse(UrlUtil.isAbsoluteUrl("blah/blah"));
  }

  public void testIsSameHost() {
    assertFalse(UrlUtil.isSameHost(null, null));
    assertTrue(UrlUtil.isSameHost("http://www.example.com/foo/bar",
				  "http://www.example.com/bar/bar/bar"));
    assertFalse(UrlUtil.isSameHost("http://www.example.com/foo/bar",
				   "http://www2.example.com/foo/bar"));
  }

  public void testStripsParams() throws MalformedURLException {
    assertNull(UrlUtil.stripQuery(null));
    assertEquals(null, UrlUtil.stripQuery(""));
    assertEquals("http://www.example.com/",
		 UrlUtil.stripQuery("http://www.example.com/"));
    assertEquals("http://www.example.com/blah",
		 UrlUtil.stripQuery("http://www.example.com/blah?param1=blah"));
    assertEquals("rtsp://www.example.com/blah",
		 UrlUtil.stripQuery("rtsp://www.example.com/blah?param1=blah"));
  }

  public void testResolveJavascriptUrl() {
    assertEquals("http://www.example.com/link2.html",
		 UrlUtil.parseJavascriptUrl("javascript:popup(http://www.example.com/link2.html)"));

    assertEquals("http://www.example.com/link2.html",
		 UrlUtil.parseJavascriptUrl("javascript:newWindow(http://www.example.com/link2.html)"));

    assertEquals("http://www.example.com/link2.html",
		 UrlUtil.parseJavascriptUrl("javascript:popup('http://www.example.com/link2.html')"));

    assertEquals("http://www.example.com/link2.html",
		 UrlUtil.parseJavascriptUrl("javascript:newWindow('http://www.example.com/link2.html')"));

    assertEquals("link2.html",
		 UrlUtil.parseJavascriptUrl("javascript:popup(link2.html)"));

    assertEquals("link2.html",
		 UrlUtil.parseJavascriptUrl("javascript:newWindow(link2.html)"));

  }

  public void testIsMalformedUrl() {
    assertFalse(UrlUtil.isMalformedUrl("http://www.example.com"));
    assertTrue(UrlUtil.isMalformedUrl("blah blah blah"));
    assertTrue(UrlUtil.isMalformedUrl("javascript:popup(blah)"));
  }

}
