/*

Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.taylorandfrancis;

import java.io.IOException;
import java.util.List;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang.StringEscapeUtils;
import org.lockss.daemon.PluginException;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.FileMetadataExtractor;
import org.lockss.extractor.FileMetadataExtractorFactory;
import org.lockss.extractor.MetadataField;
import org.lockss.extractor.MetadataTarget;
import org.lockss.extractor.SimpleHtmlMetaTagMetadataExtractor;
import org.lockss.plugin.CachedUrl;
import org.lockss.util.Logger;
import org.lockss.util.MetadataUtil;


public class TaylorAndFrancisHtmlMetadataExtractorFactory implements FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("TaylorAndFrancisHtmlMetadataExtractorFactory");

  @Override
  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
							   String contentType)
      throws PluginException {
    return new TaylorAndFrancisHtmlMetadataExtractor();
  }

  public static class TaylorAndFrancisHtmlMetadataExtractor
    implements FileMetadataExtractor {

    // Map Taylor & Francis DublinCore HTML meta tag names to cooked metadata fields
    private static MultiMap tagMap = new MultiValueMap();
    static {
      tagMap.put("dc.Date", MetadataField.FIELD_DATE);
      tagMap.put("dc.Date", MetadataField.DC_FIELD_DATE);
      tagMap.put("dc.Title", MetadataField.FIELD_ARTICLE_TITLE);
      tagMap.put("dc.Title", MetadataField.DC_FIELD_TITLE);
      tagMap.put("dc.Creator", MetadataField.FIELD_AUTHOR);
      tagMap.put("dc.Creator", MetadataField.DC_FIELD_CREATOR);
      tagMap.put("dc.Identifier", MetadataField.DC_FIELD_IDENTIFIER);
      tagMap.put("dc.Subject", MetadataField.DC_FIELD_SUBJECT);
      tagMap.put("dc.Description", MetadataField.DC_FIELD_DESCRIPTION);
      // do not pick these up - we will be overriding them and need the values blank
      // once MetadataField.FIELD_PUBLISHER_IMPRINT is implemented, we'll use that instead for this value
      //tagMap.put("dc.Publisher", MetadataField.DC_FIELD_PUBLISHER);
      //tagMap.put("dc.Publisher", MetadataField.FIELD_PUBLISHER);
      tagMap.put("dc.Type", MetadataField.DC_FIELD_TYPE);
      tagMap.put("dc.Format", MetadataField.DC_FIELD_FORMAT);
      tagMap.put("dc.Source", MetadataField.DC_FIELD_SOURCE);
      tagMap.put("dc.Language", MetadataField.DC_FIELD_LANGUAGE);
      tagMap.put("dc.Coverage", MetadataField.DC_FIELD_COVERAGE);
      tagMap.put("keywords", new MetadataField(MetadataField.FIELD_KEYWORDS, MetadataField.splitAt(";")));
    }

    @Override
    public void extract(MetadataTarget target, CachedUrl cu, Emitter emitter)
        throws IOException {
      ArticleMetadata am =
          new SimpleHtmlMetaTagMetadataExtractor().extract(target, cu);
      am.cook(tagMap);

      // Strip the extra whitespace found in the HTML around and within the "dc.Creator" and "dc.Publisher" fields
      TrimWhitespace(am, MetadataField.DC_FIELD_CREATOR);
      TrimWhitespace(am, MetadataField.FIELD_AUTHOR);
      TrimWhitespace(am, MetadataField.DC_FIELD_PUBLISHER);
      TrimWhitespace(am, MetadataField.FIELD_PUBLISHER);

      // Parse the dc.Identifier fields with scheme values "publisher-id", "doi", and "coden".
      List<String> cookedIdentifierList = am.getList(MetadataField.DC_FIELD_IDENTIFIER);
      List<String> rawIdentifierList = am.getRawList("dc.Identifier");

      String journalTitle = "";
      String volume = "";
      String issue = "";
      String spage = "";
      String epage = "";
      String doi = "";

      for (int j = 0; j < cookedIdentifierList.size(); j++) {

    	  // If our dc.Identifier field has a comma in it, its content is a comma-delimited list of
    	  // the journal title, volume, issue, and page range associated with the article.
    	  // The journal title itself may contain commas, so the list is parsed backwards and all content
    	  // before ", Vol." is assumed to be part of the journal title.
        // Cannot assume that the split is ", " (with space), so split on "," only and remove leading spaces later

// This whole thing isn't ideal.  We're counting on the metadata always having standard formatting
        // not to mention that the doi could have a comma in it...
        // While I'll go with the comma split, I am adding regexp checking to try to 
        // find stuff wherever possible. Ultimately there is only so much we can do - for example when a title is missing entirely
        // Here are some real examples (sanitized to protect the innocent):
        
        // comma in title
        //<meta name=\"dc.Identifier\" scheme=\"coden\" content=\"One, Two &amp; Three, Vol. 19, No. 6, December 2010, pp. 555-567\"></meta>"                
        // no comma after title
        //<meta name=\"dc.Identifier\" scheme=\"coden\" content=\"Title Name Vol. 31, No. 2, June 2012, pp. 175�190\"></meta>
        // no title at all and alternately formatted info
        //<meta name="dc.Identifier" scheme="coden" content="Volume 17, Comment 1 � January 2011"></meta>
        // no Vol., because it was preprint
        //<meta name="dc.Identifier" scheme="coden" content="Language and Education, preprint, 2012, pp. 1�24"></meta>
        if (cookedIdentifierList.get(j).contains(",")) {
    		  String content = cookedIdentifierList.get(j);
    		  String[] biblioInfo = content.split(",");

    		  for (int k = biblioInfo.length-1; k >= 0; k--) {
  		    //If the data was left with leading spaces after the split, remove 'em
    		    biblioInfo[k] = biblioInfo[k].trim();
    		    // get the page range
                  if (biblioInfo[k].startsWith("pp. ")) {
    				  // page range separated by hyphen
    				  if (biblioInfo[k].contains("-")) {
    					  spage = biblioInfo[k].substring("pp. ".length(), biblioInfo[k].indexOf("-"));
    					  epage = biblioInfo[k].substring(biblioInfo[k].indexOf('-')+1, biblioInfo[k].length());
    				  }
    	                      // page range separated by en-dash - unicode 2013
                                  else if (biblioInfo[k].contains("\u2013")) {
                          spage = biblioInfo[k].substring("pp. ".length(), biblioInfo[k].indexOf("\u2013"));
                          epage = biblioInfo[k].substring(biblioInfo[k].indexOf("\u2013")+1, biblioInfo[k].length());
                                  }
                              // page range separated by three characters (decimal 226 218 147)
    			      // (e.g. "Journal of Pharmacy Teaching" Vol. 1, No. 2 dc.Identifier)
                                  else if (biblioInfo[k].contains("\u00E2\u0080\u0093")) {
                          spage = biblioInfo[k].substring("pp. ".length(), biblioInfo[k].indexOf("\u00E2\u0080\u0093"));
                          epage = biblioInfo[k].substring(biblioInfo[k].indexOf("\u00E2\u0080\u0093")+3, biblioInfo[k].length());
    				  }
    				  // page range is single page
    				  else {
    					  spage = biblioInfo[k].substring("pp. ".length(), biblioInfo[k].length());
    				  }
    			  }
    			  // get the issue number
    			  else if (biblioInfo[k].startsWith("No. ")) {
    				 issue = biblioInfo[k].substring("No. ".length(), biblioInfo[k].length());
    			  }
    			  // get the volume number
    			  else if (biblioInfo[k].startsWith("Vol. ")) {
     				 volume = biblioInfo[k].substring("Vol. ".length(), biblioInfo[k].length());
    			  }
                          // we might be at the title, but let's see if, because of a missing comma, it includes the volume
    			  else if (biblioInfo[k].contains("Vol. ")) {
    			    volume = biblioInfo[k].substring(biblioInfo[k].indexOf("Vol. ") + "Vol. ".length(), biblioInfo[k].length());
    			    // and the rest would be the title...but check to make sure this isn't just part of it
    			    // If we're not at the beginning of the comma-separated list
    			    // (i.e. the journal title itself contains commas),
    			    // reinsert the comma that we lost in content.split(", ").
    			    String titleBit = biblioInfo[k].substring(0,biblioInfo[k].indexOf("Vol. "));
    			    journalTitle = titleBit.concat(journalTitle);
    			    if (k != 0) journalTitle = ", ".concat(journalTitle);
    			  }
    			  // by this point, we've come backwards in our comma-delimited list and reached
    			  // the journal title.
    			  else if (!volume.isEmpty()) {
    				  journalTitle = biblioInfo[k].concat(journalTitle);

    				  // If we're not at the beginning of the comma-separated list
    				  // (i.e. the journal title itself contains commas),
    				  // reinsert the comma that we lost in content.split(", ").
    				  if (k != 0) journalTitle = ", ".concat(journalTitle);
    			  } 
    		  }

    		  // org.apache.commons.lang.StringEscapeUtils contains a method for unescaping HTML codes
    		  // (like &amp;) that may appear in the journal title
    		  journalTitle = StringEscapeUtils.unescapeHtml(journalTitle);
    		  journalTitle = journalTitle.trim(); // mal-formatted identifier could end up with trailing white space
    		  
    		  // Only put values in to metadata if they have valid content; no value will allow it to look elsewhere
    		  if ( !journalTitle.isEmpty()) am.put(MetadataField.FIELD_JOURNAL_TITLE, journalTitle);
    		  if ( !volume.isEmpty()) am.put(MetadataField.FIELD_VOLUME, volume);
    		  if ( !issue.isEmpty()) am.put(MetadataField.FIELD_ISSUE, issue);
    		  if ( !spage.isEmpty()) am.put(MetadataField.FIELD_START_PAGE, spage);
    		  if ( !epage.isEmpty()) am.put(MetadataField.FIELD_END_PAGE, epage);
    	  }
    	  else if (MetadataUtil.isDoi(cookedIdentifierList.get(j))) {
    		  doi = cookedIdentifierList.get(j);
    		  am.put(MetadataField.FIELD_DOI, doi);
    	  }

      }
     
      // for right now, manually set all T&F titles to the Taylor & Francis Group
      // once available, we'll use FIELD_PUBLISHER_IMPRINT for the sub publisher (eg. Routledge, Garland, etc)
      am.put(MetadataField.FIELD_PUBLISHER, "Taylor & Francis Group");
      am.put(MetadataField.DC_FIELD_PUBLISHER, "Taylor & Francis Group");
      emitter.emitMetadata(cu, am);
    }

    private void TrimWhitespace(ArticleMetadata am, MetadataField md) {
    	List<String> list = am.getList(md);
    	for (int i = 0; i < list.size(); i++) {
      	  String curEntry = list.get(i);
          curEntry = curEntry.trim();
      	  curEntry = curEntry.replace("   ", " ");
      	  list.set(i, curEntry);
    	}
    }
  }
}