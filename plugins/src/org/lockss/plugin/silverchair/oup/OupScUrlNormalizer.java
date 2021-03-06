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

import java.util.regex.*;

import org.lockss.daemon.*;
import org.lockss.plugin.*;

/**
 * <p>
 * URLs on OUP meeting abstract articles have param ?searchresult=1, which can be removed
 * </p>
 * <ul>
 * <li><code>https://academic.oup.com/ageing/article/46/suppl_1/i39/3828923?searchresult=1</code></li>
 * <li>https://academic.oup.com/ageing/article/46/suppl_1/i39/3828923</li>
 * </ul>
 */
public class OupScUrlNormalizer extends BaseUrlHttpHttpsUrlNormalizer {

  private static final Pattern RESULT_PATTERN = Pattern.compile("[?]searchresult=\\d+$", Pattern.CASE_INSENSITIVE);
  private static final String RESULT_CANONICAL = "";
  
  
  @Override
  public String additionalNormalization(String url, ArchivalUnit au) throws PluginException {
    url = RESULT_PATTERN.matcher(url).replaceFirst(RESULT_CANONICAL);
    return url;
  }
  
}
