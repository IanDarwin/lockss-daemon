/*
 * $Id$
 */

/*

Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.georgthiemeverlag;

import org.lockss.test.LockssTestCase;

public class TestGeorgThiemeVerlagUrlNormalizer extends LockssTestCase {
  
  private GeorgThiemeVerlagUrlNormalizer norm;

  public void setUp() throws Exception {
    super.setUp();
    norm = new GeorgThiemeVerlagUrlNormalizer();
  }

  public void testGeorgThiemeNormalizer() throws Exception {
    
    assertEquals("https://www.thieme-connect.de/products/css/style-changes.css",
        norm.normalizeUrl("https://www.thieme-connect.de/products/css/style-changes.css?rel=xyz&relno=1", null));
    assertEquals("https://www.thieme-connect.de/products/css/style-changes.js",
        norm.normalizeUrl("https://www.thieme-connect.de/products/css/style-changes.js?rel=xyz&relno=1", null));
    assertEquals("https://www.thieme-connect.de/products/css/style-changes.ico",
        norm.normalizeUrl("https://www.thieme-connect.de/products/css/style-changes.ico?rel=xyz&relno=1", null));
    assertEquals("https://www.thieme-connect.de/products/images/desktop/img/logo-video.gif",
        norm.normalizeUrl("https://www.thieme-connect.de/products/images/desktop/img/logo-video.gif?_debugResources=y&n=1436640638884", null));
    
    assertEquals("https://www.thieme-connect.de/products/css/style-changes.png?rel=xyz&relno=1",
        norm.normalizeUrl("https://www.thieme-connect.de/products/css/style-changes.png?rel=xyz&relno=1", null));
    assertEquals("https://www.thieme-connect.de/products/ejournals/abstract/10.1055/s-0029-1241796?index=s11",
        norm.normalizeUrl("https://www.thieme-connect.de/products/ejournals/abstract/10.1055/s-0029-1241796?index=s11", null));
  }
  
}
