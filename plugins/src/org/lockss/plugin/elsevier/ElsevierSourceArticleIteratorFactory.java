/*
 * $Id$
 */

/*

Copyright (c) 2000-2014 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.elsevier;

import java.util.Iterator;
import java.util.regex.*;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.ArticleMetadataExtractorFactory;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

public class ElsevierSourceArticleIteratorFactory implements ArticleIteratorFactory, ArticleMetadataExtractorFactory {

  private static final Logger log = Logger.getLogger(ElsevierSourceArticleIteratorFactory.class);
  
  protected static final String ROOT_TEMPLATE = "\"%s%d\",base_url,year";
  
  
  /*
   * NOTE - 
   * This is a legacy plugin. It works by iterating over the deliverd tarballs and finding
   * all the main.pdf files that fit the expected pattern (depth from base/year) and then 
   * from the url of the first PDF found, finding the associated "dataset.toc" file
   * to extract metadata about this pdf. However, the dataset.toc contains the information for ALL
   * the PDF files in the same tarball and any other tarballs that are in the directory the
   * dataset.toc file lives in. The plugin uses its own ArticleMetadatExtractor to create
   * a specialized emitter that will keep track of the PDF info already emitted so it won't
   * repeat.  This has the advantage of creating ArticleFiles objects for each PDF/XML full-text pair
   * but is inefficient and the plugin would probably do better to iterate over the 
   * "dataset.toc" files, extract all the info and just verifying that the referenced PDF exists in the
   * AU before emitting. 
   * BUT I am not implementing this change because this is a legacy plugin and I don't want to 
   * destabilize the work and extraction already completed for no real additional benefit.
   * Instead, in order to pick up the "missing" information from the deliveries that didn't
   * match the expected pattern (the dataset.toc is inside the tarball and the main.pdf lives on
   * additional level down) I am simply going to expand the allowable pattern and the logic that
   * is used to find the dataset.toc
   * alexandra
   */
  //
  // The pdf will be 2 or 3 levels below the tar depending on old form OM dir or new form OX dir
  // allow the directory names to be anything but "/" - requiring them to be only digits was missing some
  protected static final String PATTERN_TEMPLATE = "\"%s%d/[^/]+/[^/]+\\.tar!/([^/]+/)?[^/]+/[^/]+/main\\.pdf$\",base_url,year";

  //do not iterate in to any potential deeper down archive file
  protected static final String NESTED_ARCHIVE_PATTERN_TEMPLATE = "\"%s%d/[^/]+/[^/]+\\.tar!/.+\\.(zip|tar|gz|tgz|tar\\.gz)$\",base_url,year";

  
  // example file names for an OM directory:
  //<base>/2008/OM08032A/OM08032A.tar!/dataset.toc <--in archive
  //<base>/2008/OM08032A/OM08032A.tar!/00992399/003405SS/07011582/main.pdf
  //<base>/2008/OM08032A/OM08032A.tar!/00992399/003405SS/07011582/main.xml

  // example file names for an OX directory:
  //<base>/2012/OXM30010/dataset.toc <-- unpacked
  //<base>/2012/OXM30010/00029343.tar!/01250008/12000332/main.pdf
  //<base>/2012/OXM30010/00029343.tar!/01250008/12000332/main.xml
  // could be ...tar!/00220004/1400079X/main.pdf - with X in last directory

  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                      MetadataTarget target)
      throws PluginException {
    return new ElsevierSourceArticleIterator(au, new SubTreeArticleIterator.Spec()
                                       .setTarget(target)
                                       .setVisitArchiveMembers(true)
                                       .setRootTemplate(ROOT_TEMPLATE)
                                       .setPatternTemplate(PATTERN_TEMPLATE, Pattern.CASE_INSENSITIVE)
                                       .setExcludeSubTreePatternTemplate(NESTED_ARCHIVE_PATTERN_TEMPLATE, Pattern.CASE_INSENSITIVE));
  }
  
  /*
   * The file metadata extractor is called when a 
   * base_url/year/TAR_DIR/TARNUM.tar!/(dir/)?/dir/dir/main.pdf is found
   * The first one found will use the URL pattern to discover the
   * associated dataset.toc which will be in either
   * base_url/year/TAR_DIR/TAR_DIR.tar!/dataset.toc (inside the archive...) 
   * base_url/year/TAR_DIR/dataset.toc (unpacked)
   * and will extract all necessary metadata information from this file
   */
  
  protected static class ElsevierSourceArticleIterator extends SubTreeArticleIterator {
	 
    //group#1 = the top dir in which the tar lives (just under year param)
    //group#2 - the chunk below the top dir from the tarball down to the main.pdf
    //group#3 - optional extra level between the tar and the main.pdf - this tells us where the dataset is
    protected static Pattern PATTERN = Pattern.compile("([^/]+)(/[^/]+\\.tar!/([^/]+/)?[^/]+/[^/]+/main\\.)pdf$", Pattern.CASE_INSENSITIVE);
    
    protected ElsevierSourceArticleIterator(ArchivalUnit au,
                                  SubTreeArticleIterator.Spec spec) {
      super(au, spec);
    }
    
    /*
     * This code never actually finds and sets metadata file to extract from
     * and uses its own ArticleMetadataExtractor to find a dataset.toc
     * based on the fullText url which then provides metadata for
     * multiple PDF files
     */
    @Override
    protected ArticleFiles createArticleFiles(CachedUrl cu) {
      String url = cu.getUrl();
      
      //this is a redundant check and we never really do anything with the pattern....
      Matcher mat = PATTERN.matcher(url);
      if (mat.find()) {
        return processFullText(cu, mat);
      }
      log.warning("Mismatch between article iterator factory and article iterator: " + url);
      return null;
    }

    protected ArticleFiles processFullText(CachedUrl cu, Matcher mat) {
      ArticleFiles af = new ArticleFiles();
      af.setFullTextCu(cu);
      
      if(spec.getTarget() != MetadataTarget.Article())
      {
		af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, cu);
		guessXml(af,cu.getUrl());
      }
      
      return af;
    }
    
    protected void guessXml(ArticleFiles af, String pdf_url_string) {
      // don't make this so hard - just replace the ".pdf" with ".xml"
      if (pdf_url_string != null) {
        String xml_url_string = pdf_url_string.substring(0,pdf_url_string.length() - 3) + "xml";
        CachedUrl xmlCu = au.makeCachedUrl(xml_url_string);

        if (xmlCu != null && xmlCu.hasContent()) {
          af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_XML, xmlCu);
        }
      }
    } 
    
  }
  
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
	      throws PluginException {
	    return new ElsevierSourceArticleMetadataExtractor();
  }
}
