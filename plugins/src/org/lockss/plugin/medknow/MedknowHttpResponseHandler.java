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

package org.lockss.plugin.medknow;

import org.lockss.plugin.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;


public class MedknowHttpResponseHandler implements CacheResultHandler {
  
  private static final Logger logger = Logger.getLogger(MedknowHttpResponseHandler.class);
  
  @Override
  public void init(CacheResultMap crmap) {
    logger.warning("Unexpected call to init()");
    throw new UnsupportedOperationException("Unexpected call to MedknowHttpResponseHandler.init()");
  }
  
  @Override
  public CacheException handleResult(ArchivalUnit au,
                                     String url,
                                     int responseCode) {
    switch (responseCode) {
      case 502:
        logger.debug2("502: " + url);
        return new CacheException.RetryableNetworkException_3_5M("502 Bad Gateway");
        
      case 503:
        logger.debug2("503: " + url);
        return new CacheException.RetryableNetworkException_3_5M("503 Service Unavailable");
        
      default:
        logger.warning("Unexpected responseCode (" + responseCode + ") in handleResult(): AU " + au.getName() + "; URL " + url);
        throw new UnsupportedOperationException("Unexpected responseCode (" + responseCode + ")");
    }
  }
  
  @Override
  public CacheException handleResult(ArchivalUnit au,
                                     String url,
                                     Exception ex) {
    logger.warning("Unexpected call to handleResult(): AU " + au.getName() + "; URL " + url, ex);
    throw new UnsupportedOperationException("Unexpected call to handleResult(): AU " + au.getName() + "; URL " + url, ex);
  }
  
}
