/*
 * $Id: MockCrawler.java,v 1.3 2003-06-26 23:54:40 eaalto Exp $
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

package org.lockss.test;

import java.util.Collection;
import org.lockss.util.Deadline;
import org.lockss.plugin.ArchivalUnit;

public class MockCrawler extends NullCrawler {
  ArchivalUnit au;
  Collection urls;
  boolean followLinks;
  boolean doCrawlCalled = false;
  Deadline deadline = null;
  boolean crawlSuccessful = true;
  int type = -1;

  public void setCrawlSuccessful(boolean crawlSuccessful) {
    this.crawlSuccessful = crawlSuccessful;
  }

  public boolean doCrawl(Deadline deadline) {
    doCrawlCalled = true;
    this.deadline = deadline;
    return crawlSuccessful;
  }

  public Deadline getDeadline() {
    return deadline;
  }

  public boolean doCrawlCalled() {
    return doCrawlCalled;
  }

  public ArchivalUnit getAU() {
    return au;
  }

  public void setAU(ArchivalUnit au) {
    this.au = au;
  }

  public void setURLs(Collection urls) {
    this.urls = urls;
  }

  public void setFollowLinks(boolean followLinks) {
    this.followLinks = followLinks;
  }

  public void setType(int type) {
    this.type = type;
  }

  public int getType() {
    return type;
  }

  public Collection getStartUrls() {
    return urls;
  }
}
