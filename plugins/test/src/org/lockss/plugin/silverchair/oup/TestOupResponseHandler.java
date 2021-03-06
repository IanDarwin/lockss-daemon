/*
 * $Id$
 */

/*

Copyright (c) 2018 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.silverchair.oup;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.lockss.repository.LockssRepository;
import org.lockss.test.*;
import org.lockss.util.CIProperties;
import org.lockss.util.Logger;
import org.lockss.util.urlconn.CacheException;
import org.lockss.util.urlconn.HttpResultMap;
import org.lockss.plugin.*;
import org.lockss.app.LockssDaemon;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.plugin.base.DefaultUrlCacher;
import org.lockss.plugin.definable.*;
import org.lockss.plugin.silverchair.ScHtmlHttpResponseHandler.ScRetryableNetworkException;

public class TestOupResponseHandler extends LockssTestCase {

  static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
  static final String JID_KEY = ConfigParamDescr.JOURNAL_ID.getKey();
  static final String YEAR_KEY = ConfigParamDescr.YEAR.getKey();

  private static final Logger log = Logger.getLogger(TestOupResponseHandler.class);
  
  static final String PLUGIN_ID = "org.lockss.plugin.silverchair.oup.ClockssOupSilverchairPlugin";
  static final String ROOT_URL = "http://academic.oup.com/";
  private MockLockssDaemon theDaemon;
  private DefinablePlugin plugin;

  private static final String TEXT = "text that is longer than reported";
  private static final int LEN_TOOSHORT = TEXT.length() - 4;


  public void setUp() throws Exception {
    super.setUp();
    setUpDiskSpace();
    theDaemon = getMockLockssDaemon();
    theDaemon.getHashService();
    theDaemon.getRepositoryManager();
    plugin = new DefinablePlugin();
    plugin.initPlugin(theDaemon, PLUGIN_ID);
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }


  private DefinableArchivalUnit makeAuFromProps(Properties props)
      throws ArchivalUnit.ConfigurationException {
    Configuration config = ConfigurationUtil.fromProps(props);
    return (DefinableArchivalUnit)plugin.configureAu(config, null);
  }

  public void testHandlesExceptionResult() throws Exception {
    Properties props = new Properties();
    props.setProperty(BASE_URL_KEY, ROOT_URL);
    props.setProperty(YEAR_KEY, "2017");
    props.setProperty(JID_KEY, "xxx");
    DefinableArchivalUnit au = makeAuFromProps(props);
    String cdn_url = "https://cdn.jsdelivr.net/chartist.js/latest/chartist.min.css";
    String suppl_url = "https://oup.silverchair-cdn.com/oup/backfile/Content_public/Journal/biomet/Issue/105/1/1/biomet_105_1cover.png?Expires=2147483647&Signature=4zA98V__&Key-Pair-Id=APKAIE5G5CRDK6RD3PGA";
    MockLockssUrlConnection conn = new MockLockssUrlConnection();
    
    conn.setURL("http://uuu17/");
    CacheException exc =
        ((HttpResultMap)plugin.getCacheResultMap()).mapException(au, conn,
            new javax.net.ssl.SSLHandshakeException("BAD"), "foo");
    // make sure it's a retryable
    assertClass(CacheException.RetryableException.class, exc);
    
    conn.setURL(suppl_url);
    exc = ((HttpResultMap)plugin.getCacheResultMap()).mapException(au, conn, new javax.net.ssl.SSLHandshakeException("BAD"), "foo");
    assertTrue(exc instanceof ScRetryableNetworkException);

    conn.setURL(cdn_url);
    exc = ((HttpResultMap)plugin.getCacheResultMap()).mapException(au, conn, new javax.net.ssl.SSLHandshakeException("BAD"), "foo");
    assertTrue(exc instanceof ScRetryableNetworkException);
    
  }
  
  public void testShouldCacheWrongSize() throws Exception {
    Properties props = new Properties();
    props.setProperty(BASE_URL_KEY, ROOT_URL);
    props.setProperty(YEAR_KEY, "2017");
    props.setProperty(JID_KEY, "xxx");
    DefinableArchivalUnit OUPau = makeAuFromProps(props);
    LockssRepository repo =
        (LockssRepository)theDaemon.newAuManager(LockssDaemon.LOCKSS_REPOSITORY,
            OUPau);
    theDaemon.setLockssRepository(repo, OUPau);
    repo.startService();
    String url = ROOT_URL + "biomet/article/105/1/215/4742247";
    CIProperties cprops = new CIProperties();
    
    cprops.setProperty("Content-Length", Integer.toString(LEN_TOOSHORT));
    UrlData ud = new UrlData(new StringInputStream(TEXT), cprops, url);
    MyDefaultUrlCacher cacher = new MyDefaultUrlCacher(OUPau, ud);
    try {
      cacher.storeContent();
      // storeContent() should have thrown WrongLength, but is mapped to no_store/no_fail exception and returns
    } catch (CacheException e) {
      // fail("storeContent() shouldn't have thrown", e);
      assertClass(CacheException.RetryableException.class, e);
    } catch (Exception x) {
      fail("storeContent() shouldn't have thrown", x);
    }
    
    assertTrue(cacher.wasStored);
  }
  
  
  // DefaultUrlCacher that remembers that it stored
  private class MyDefaultUrlCacher extends DefaultUrlCacher {
    boolean wasStored = true;
    
    public MyDefaultUrlCacher(ArchivalUnit owner, UrlData ud) {
      super(owner, ud);
    }
    
    @Override
    protected void storeContentIn(String url, InputStream input,
                                  CIProperties headers,
                                  boolean doValidate, List<String> redirUrls)
            throws IOException {
      super.storeContentIn(url, input, headers, doValidate, redirUrls);
      if (infoException != null &&
          infoException.isAttributeSet(CacheException.ATTRIBUTE_NO_STORE)) {
        wasStored = false;
      }
    }
  }
  
}
