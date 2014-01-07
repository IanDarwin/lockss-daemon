/*
 * $Id: GeorgThiemeVerlagHtmlFilterFactory.java,v 1.7 2014-01-07 00:36:08 etenbrink Exp $
 */
/*

Copyright (c) 2000-2013 Board of Trustees of Leland Stanford Jr. University,
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

Except as contained in this notice, tMassachusettsMedicalSocietyHtmlFilterFactoryhe name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.plugin.georgthiemeverlag;

import java.io.*;

import org.htmlparser.*;
import org.htmlparser.filters.*;
import org.lockss.daemon.PluginException;
import org.lockss.filter.FilterUtil;
import org.lockss.filter.WhiteSpaceFilter;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.util.Logger;
import org.lockss.util.ReaderInputStream;

public class GeorgThiemeVerlagHtmlFilterFactory implements FilterFactory {
  
  Logger log = Logger.getLogger(GeorgThiemeVerlagHtmlFilterFactory.class);
  
  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    // First filter with HtmlParser
    NodeFilter[] filters = new NodeFilter[] {
        // Contains scripts and tags that change values
        new TagNameFilter("head"),
        // Remove header/footer items, requires_daemon_version 1.64.0
        new TagNameFilter("header"),
        new TagNameFilter("footer"),
        // remove ALL comments
        HtmlNodeFilters.comment(),
        // Contains ads
        HtmlNodeFilters.tagWithAttributeRegex("div", "id", "adSidebar[^\"]*"),
        // Contains navigation items
        HtmlNodeFilters.tagWithAttribute("div", "id", "navPanel"),
        // Contains functional links, not content
        HtmlNodeFilters.tagWithAttribute("div", "class", "pageFunctions"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "articleFunctions"),
        HtmlNodeFilters.tagWithAttribute("span", "class", "articleCategories"),
        // Contains non-functional anchor, not content
        HtmlNodeFilters.tagWithAttribute("a", "name"),
    };
    
    // registerTag header/footer requires_daemon_version 1.64.0
    HtmlFilterInputStream filtered = new HtmlFilterInputStream(in, encoding,
        HtmlNodeFilterTransform.exclude(new OrFilter(filters)))
    .registerTag(new HtmlTags.Header())
    .registerTag(new HtmlTags.Footer());
    
    Reader filteredReader = FilterUtil.getReader(filtered, encoding);
    return new ReaderInputStream(new WhiteSpaceFilter(filteredReader));
  }
  
}
