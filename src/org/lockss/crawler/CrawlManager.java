/*
 * $Id: CrawlManager.java,v 1.4 2003-02-06 01:00:42 troberts Exp $
 */

/*

Copyright (c) 2000-2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.crawler;
import java.net.URL;
import org.lockss.daemon.*;

/**
 * This is the interface for the object which will sit between the crawler
 * and the rest of the world.  It mediates the different crawl types.
 */
public interface CrawlManager {
  /**
   * Schedules a repair crawl and calls cb.signalRepairAttemptCompleted
   * when done.
   * @param url URL that needs to be repaired
   * @param cb callback to talk to when repair attempt is done
   * @param cookie object that the callback needs to understand which
   * repair we're referring to.
   */
  public void scheduleRepair(ArchivalUnit au, URL url, 
			     CrawlManager.Callback cb, Object cookie);


  /**
   * Called at the beginning of each tree walk.  The crawler checks if there
   * is anything it needs to do on the AU (such as a new content crawl) 
   * before the treewalk begins.
   *
   * @param au ArchivalUnit that the crawl manager should check
   * @param cb callback to be called when treewalk can start, if not now
   * @param cookie cookie for the callback
   * @return true if the tree walk can begin now, false otherwise
   */

  public boolean canTreeWalkStart(ArchivalUnit au, 
				  CrawlManager.Callback cb, Object cookie);

  public interface Callback {
    /**
     * Called when the crawl is completed
     * @param success whether the crawl was successful or not
     * @param cookie object used by callback to designate which crawl
     * attempt this is
     */
    public void signalCrawlAttemptCompleted(boolean success, Object cookie);
  }
}
