/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.highwire;

import java.io.*;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.extractor.MetadataField.Cardinality;
import org.lockss.plugin.*;


public class HighWirePressH20HtmlMetadataExtractorFactory implements FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("HighWirePressH20HtmlMetadataExtractorFactory");

  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
							   String contentType)
      throws PluginException {
    return new HighWirePressH20HtmlMetadataExtractor();
  }
  
  public static class HighWirePressH20HtmlMetadataExtractor 
    implements FileMetadataExtractor {
 
    // To do (PJG): Use the definitions from MetadataField once 1.59 is out
    private static final String KEY_LANGUAGE = "language";
    private static final MetadataField FIELD_LANGUAGE = new MetadataField(
        KEY_LANGUAGE, Cardinality.Single);
    private static final String KEY_FORMAT = "format";
    public static final MetadataField FIELD_FORMAT = new MetadataField(
        KEY_FORMAT, Cardinality.Single);
    
    // Map HighWire H20 HTML meta tag names to cooked metadata fields
    private static MultiMap tagMap = new MultiValueMap();
    static {
      tagMap.put("DC.Format", FIELD_FORMAT);
      tagMap.put("DC.Language", FIELD_LANGUAGE);
      tagMap.put("DC.Publisher", MetadataField.FIELD_PUBLISHER);
      tagMap.put("citation_journal_title", MetadataField.FIELD_JOURNAL_TITLE);
      tagMap.put("citation_title", MetadataField.FIELD_ARTICLE_TITLE);
      tagMap.put("citation_date", MetadataField.FIELD_DATE);
      tagMap.put("citation_authors", new MetadataField(
          MetadataField.FIELD_AUTHOR, MetadataField.splitAt(";")));
      tagMap.put("citation_issn", MetadataField.FIELD_ISSN);
      tagMap.put("citation_volume", MetadataField.FIELD_VOLUME);
      tagMap.put("citation_volume", MetadataField.DC_FIELD_CITATION_VOLUME);
      tagMap.put("citation_issue", MetadataField.FIELD_ISSUE);
      tagMap.put("citation_issue", MetadataField.DC_FIELD_CITATION_ISSUE);
      tagMap.put("citation_firstpage", MetadataField.FIELD_START_PAGE);
      tagMap.put("citation_firstpage", MetadataField.DC_FIELD_CITATION_SPAGE);
      tagMap.put("citation_lastpage", MetadataField.DC_FIELD_CITATION_EPAGE);
      tagMap.put("citation_doi", MetadataField.FIELD_DOI);
      tagMap.put("citation_public_url", MetadataField.FIELD_ACCESS_URL);
      // typical field value: "acupmed;30/1/8": extract "acupmed"
      tagMap.put("citation_mjid", new MetadataField(
          MetadataField.FIELD_PROPRIETARY_IDENTIFIER, 
          MetadataField.extract("^([^;]+);", 1)));
    }

    @Override
    public void extract(MetadataTarget target, CachedUrl cu, Emitter emitter)
        throws IOException {
      ArticleMetadata am = 
        new SimpleHtmlMetaTagMetadataExtractor().extract(target, cu);
      am.cook(tagMap);    	  
      emitter.emitMetadata(cu, am);
    }
  }
}