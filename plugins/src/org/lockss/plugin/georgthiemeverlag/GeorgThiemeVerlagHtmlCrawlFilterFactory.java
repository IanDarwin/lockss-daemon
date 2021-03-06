/*
 * $Id$
 */

/*

Copyright (c) 2000-2018 Board of Trustees of Leland Stanford Jr. University,
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

import java.io.InputStream;
import java.util.regex.Pattern;

import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.filters.*;
import org.htmlparser.nodes.TagNode;
import org.htmlparser.tags.CompositeTag;
import org.htmlparser.tags.Div;
import org.htmlparser.tags.LinkTag;
import org.lockss.daemon.PluginException;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;

public class GeorgThiemeVerlagHtmlCrawlFilterFactory implements FilterFactory {
 private static final Pattern LINKOUT = Pattern.compile("dx\\.doi\\.org", Pattern.CASE_INSENSITIVE);
  
  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
	  
    NodeFilter[] filters = new NodeFilter[] {
        // Aggressive filtering of non-content tags
        // Do not crawl header or footer for links
        new TagNameFilter("header"),
        new TagNameFilter("footer"),
        // Contains navigation items that can link off AU
        HtmlNodeFilters.tagWithAttribute("div", "id", "navPanel"),
        HtmlNodeFilters.tagWithAttribute("ul", "id", "overviewNavigation"),
        // Contains links to non-relevant pages 
        HtmlNodeFilters.tagWithAttribute("div", "class", "pageFunctions"),
        // Can contain links to original articles from Errata as well as other links
        HtmlNodeFilters.tagWithAttribute("div", "class", "relatedArticles"),
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "articleToggleMenu"),
        // No need to crawl ad links
        HtmlNodeFilters.tagWithAttributeRegex("div", "id", "adSidebar"),
        // No need to crawl reference list 
        HtmlNodeFilters.tagWithAttribute("ul", "class", "literaturliste"),
        // Appears that correction anchors have class="anchorc" 
        // eg. https://www.thieme-connect.de/products/ejournals/abstract/10.1055/s-0030-1249709
        HtmlNodeFilters.tagWithAttribute("a", "class", "anchorc"),
        //We've been overrawling due to links to other articles from boxes on the abstract pages
        //https://www.thieme-connect.de/products/ejournals/abstract/10.1055/s-0030-1255783
        //<div class="articleBox supmat related">
        HtmlNodeFilters.tagWithAttributeRegex("div",  "class",  "articleBox.*related"),
        HtmlNodeFilters.tagWithAttributeRegex("div",  "class",  "articleBox.*backinfo"),
        // this will not conflict with true supplementary material which is in its own tab
        // <section id="supmat"...div id="supportingMaterial..."
        // see an article with suppl at 
        // https://www.thieme-connect.de/products/ejournals/abstract/10.1055/s-0034-1390442
        
        // MULTIPLE THINGS:
        // 1. THey also have an unmarked link to the related article with text that includes a dx.doi.org reference
        // even though it's an in-house cross-journal link
        //<a href="/products/ejournals/abstractfoo">FOO: http://dx.doi.org/foo</a>
        // 2. Per request from Thieme - they *used to* have hidden sfx links that were valid links to our crawler but
        // caused huge number of 500 errors on their servers.  They've since modified them to be data-href instead of href
        // until visible, but we promised to crawl filter out the old style to avoid the errors in their logs
        // WAS (filter out):
        //<div id="sfx10.1055/s-0034-1384567" style="display: none”>
        // <a target="sfx" href="?id=doi:10.1055/s-0034-1384567&amp;sid=Thieme:Connect&amp;jtitle=Das..." id="sfxServer”>
        // NOW:
        // <a target="sfx" data-href="?id=doi:10.1055/s-0034-1384567&amp;sid=Thieme:Connect&amp;jtitle=Das..." id="sfxServer">
        // exclude <a href with parent of display:none and attr of
        new NodeFilter() {
          @Override public boolean accept(Node node) {
            if (!(node instanceof LinkTag)) return false;
            String allText = ((CompositeTag)node).toPlainTextString();
            if (LINKOUT.matcher(allText).find()) {
            		return true;
            } else { 
                String id = ((LinkTag) node).getAttribute("id");
                if ("sfxServer".equals(id)) { // null safe
                  Node parent = node.getParent();
                  if(parent instanceof Div) {
                      String pStyle = ((TagNode) parent).getAttribute("style");
                      return (pStyle != null && !pStyle.isEmpty() && pStyle.matches("display\\s*:\\s*none"));
                  }
                }                  
            }
            return false;
          }
        },        
        
        
        
    };
    InputStream filtered = new HtmlFilterInputStream(in, encoding,
        HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
    return filtered;
  }

}
