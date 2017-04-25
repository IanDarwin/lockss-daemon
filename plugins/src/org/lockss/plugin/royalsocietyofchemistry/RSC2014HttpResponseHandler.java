/*
 *
 *  * $Id:$
 *
 *
 *
 * Copyright (c) 2017 Board of Trustees of Leland Stanford Jr. University,
 * all rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Except as contained in this notice, the name of Stanford University shall not
 * be used in advertising or otherwise to promote the sale, use or other dealings
 * in this Software without prior written authorization from Stanford University.
 *
 * /
 */

package org.lockss.plugin.royalsocietyofchemistry
;

import org.lockss.daemon.PluginException;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ContentValidationException;
import org.lockss.util.Constants;
import org.lockss.util.Logger;

import org.lockss.util.urlconn.CacheException;
import org.lockss.util.urlconn.CacheResultHandler;
import org.lockss.util.urlconn.CacheResultMap;
import org.lockss.util.urlconn.CacheSuccess;
import org.lockss.util.urlconn.CacheException.RetryableNetworkException_2_10S;

public class RSC2014HttpResponseHandler implements CacheResultHandler {
  private static final Logger logger = Logger.getLogger(RSC2014HttpResponseHandler.class);

  @Override
  public void init(final CacheResultMap map) throws PluginException {
    logger.warning("Unexpected call to init()");
    throw new UnsupportedOperationException("Unexpected call to RSC2014HttpResponseHandler.init()");

  }

  // currently this is only called on 400 codes by the books plugin
  @Override
  public CacheException handleResult(final ArchivalUnit au, final String url, int responseCode) throws PluginException {
    logger.debug(responseCode + ": " + url);
    switch (responseCode) {
      case 400:
          return new CacheException.RetrySameUrlException();
      default:
        logger.warning("Unexpected responseCode (" + responseCode + ") in handleResult(): AU " + au.getName() + "; URL " + url);
        throw new UnsupportedOperationException("Unexpected responseCode (" + responseCode + ")");
    }
  }
  
  
  @Override
  public CacheException handleResult(final ArchivalUnit au, final String url, final Exception ex)
    throws PluginException {
    logger.debug(ex.getMessage() + ": " + url);
    
    // this checks for the specific exceptions before going to the general case and retry
    if (ex instanceof ContentValidationException.WrongLength) {
      logger.debug3("Ignoring Wrong length - storing file");
      // ignore and continue
      return new CacheSuccess();
    }
    
    // handle retryable exceptions ; URL MIME type mismatch for pages like 
    //   http://pubs.rsc.org/en/content/articlepdf/2014/gc/c4gc90017k will never return good PDF content
    if (ex instanceof ContentValidationException) {
      logger.debug3("Warning - retry/no fail " + url);
      // no store cache exception and continue
      return new RetryableNoFailException_2_10S(ex);
    }
    
    // Unmapped exception: org.lockss.util.StreamUtil$InputException: java.net.SocketTimeoutException: Read timed out
    if (ex instanceof org.lockss.util.StreamUtil.InputException) {
      logger.debug3("Warning - InputException " + url);
      return new RetryableNoFailException_2_10S(ex);
    }
    
    // we should only get in her cases that we specifically map...be very unhappy
    logger.warning("Unexpected call to handleResult(): AU " + au.getName() + "; URL " + url, ex);
    throw new UnsupportedOperationException();
  }
  
  /** Retryable & no fail network error; two tries with 10 second delay */
  protected static class RetryableNoFailException_2_10S
    extends RetryableNetworkException_2_10S {
    
    public RetryableNoFailException_2_10S() {
      super();
    }
    
    public RetryableNoFailException_2_10S(String message) {
      super(message);
    }
    
    /** Create this if details of causal exception are more relevant. */
    public RetryableNoFailException_2_10S(Exception e) {
      super(e);
    }
    
    public long getRetryDelay() {
      return 10 * Constants.SECOND;
    }
    
  }
  
}
