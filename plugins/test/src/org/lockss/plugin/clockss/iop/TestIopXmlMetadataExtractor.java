/*
 * $Id:$
 */
/*

/*

 Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
 all rights reserved.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of his software and associated documentation files (the "Software"), to deal
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

package org.lockss.plugin.clockss.iop;
import java.io.InputStream;
import java.util.*;

import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.config.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;


public class TestIopXmlMetadataExtractor extends LockssTestCase {

  private static final Logger log = Logger.getLogger(TestIopXmlMetadataExtractor.class);

  private MockLockssDaemon theDaemon;
  private MockArchivalUnit mau;

  private static String PLUGIN_NAME = "org.lockss.plugin.clockss.iop.ClockssIopSourcePlugin";
  private static String BASE_URL = "http://www.source.org/";
  private static String TAR_GZ = "0022-3727.tar.gz!/";
  private static final String xml_url = BASE_URL + "2015/" + TAR_GZ + "0022-3727/48/35/355104/d_48_35_355104.xml";
  private static final String pdf_url = BASE_URL + "2015/" + TAR_GZ + "0022-3727/48/35/355104/d_48_35_355104.pdf";

  public void setUp() throws Exception {
    super.setUp();
    setUpDiskSpace(); // you need this to have startService work properly...

    theDaemon = getMockLockssDaemon();
    mau = new MockArchivalUnit();

    theDaemon.getAlertManager();
    theDaemon.getPluginManager().setLoadablePluginsReady(true);
    theDaemon.setDaemonInited(true);
    theDaemon.getPluginManager().startService();
    theDaemon.getCrawlManager();
    mau.setConfiguration(auConfig());

  }

  public void tearDown() throws Exception {
    theDaemon.stopDaemon();
    super.tearDown();
  }

  /**
   * Configuration method. 
   * @return
   */
  Configuration auConfig() {
    Configuration conf = ConfigManager.newConfiguration();
    conf.put("base_url", BASE_URL);
    conf.put("year", "2015");
    return conf;
  }


  private static final String realXMLFile = "IOPSourceTest.xml";

  public void testFromJatsPublishingXMLFile() throws Exception {
    InputStream file_input = null;
    try {
      file_input = getResourceAsStream(realXMLFile);
      String string_input = StringUtil.fromInputStream(file_input);
      IOUtil.safeClose(file_input);

      CIProperties xmlHeader = new CIProperties();    
      xmlHeader.put(CachedUrl.PROPERTY_CONTENT_TYPE, "text/xml");
      MockCachedUrl mcu = mau.addUrl(xml_url, true, true, xmlHeader);
      // Now add all the pdf files in our AU since we check for them before emitting
      mau.addUrl(pdf_url, true, true, xmlHeader);

      mcu.setContent(string_input);
      mcu.setContentSize(string_input.length());
      mcu.setProperty(CachedUrl.PROPERTY_CONTENT_TYPE, "text/xml");

      FileMetadataExtractor me = new  IopJatsXmlMetadataExtractorFactory().createFileMetadataExtractor(MetadataTarget.Any(), "text/xml");
      FileMetadataListExtractor mle =
          new FileMetadataListExtractor(me);
      List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any(), mcu);
      assertNotEmpty(mdlist);
      assertEquals(1, mdlist.size());

      // check each returned md against expected values
      Iterator<ArticleMetadata> mdIt = mdlist.iterator();
      ArticleMetadata mdRecord = null;
      while (mdIt.hasNext()) {
        mdRecord = (ArticleMetadata) mdIt.next();
        //log.info(mdRecord.ppString(2));
        compareMetadata(mdRecord);
      }
    }finally {
      IOUtil.safeClose(file_input);
    }

  }

  private static final int ISSN_INDEX = 0;
  private static final int DOI_INDEX = 1;
  private static final int AUTHOR_INDEX = 2;
  private static final int JOURNAL_TITLE = 3;
  private static final int ARTICLE_DATE = 4;
  private static final int ARTICLE_TITLE = 5;
  private static final int VOLUME = 6;
  private static final int ISSUE = 7;
  private static final int SPAGE = 8;
  private static final int EPAGE = 9;
  private static final int EISSN_INDEX = 10;



  private static final ArrayList md1 = (ArrayList) ListUtil.list(
      "0022-3727",
      "10.1088/0022-3727/48/35/355XXX",
      "[Foo, B., Barr, D. J., Pixel, M, Widget, Hee Chul]",
      "Journal of Physics D: Applied Physics",
      "2015",
      "JoPD article title",
      "48",
      "35",
      null,
      null,
      "1361-6463");

  // Find the matching expected data by matching against ISSN
  static private final Map<String, List> expectedMD =
      new HashMap<String,List>();
  static {
    expectedMD.put("0022-3727", md1);
  }

  private void compareMetadata(ArticleMetadata AM) {
    String issn = AM.get(MetadataField.FIELD_ISSN);
    ArrayList expected = (ArrayList) expectedMD.get(issn);

    assertNotNull(expected);
    assertEquals(expected.get(DOI_INDEX), AM.get(MetadataField.FIELD_DOI));
    assertEquals(expected.get(AUTHOR_INDEX), AM.getList(MetadataField.FIELD_AUTHOR).toString());
    assertEquals(expected.get(JOURNAL_TITLE), AM.get(MetadataField.FIELD_PUBLICATION_TITLE));
    assertEquals(expected.get(ARTICLE_DATE), AM.get(MetadataField.FIELD_DATE));
    assertEquals(expected.get(ARTICLE_TITLE), AM.get(MetadataField.FIELD_ARTICLE_TITLE));
    assertEquals(expected.get(VOLUME), AM.get(MetadataField.FIELD_VOLUME));
    assertEquals(expected.get(ISSUE), AM.get(MetadataField.FIELD_ISSUE));
    assertEquals(expected.get(SPAGE), AM.get(MetadataField.FIELD_START_PAGE));
    assertEquals(expected.get(EPAGE), AM.get(MetadataField.FIELD_END_PAGE));
    assertEquals(expected.get(EISSN_INDEX), AM.get(MetadataField.FIELD_EISSN));

  }
  
  private static final ArrayList real_pdflist = (ArrayList) ListUtil.list(
      "978-1-6270-5469-0.pdf",
      "978-0-7503-1104-5.pdf",
      "978-0-750-31040-6.pdf",
      "978-0-750-31044-4.pdf",
      "978-0-7503-1052-9.pdf"
      );      

  private static final String TwoXMLFile = "Onix2Test.xml";

  public void testAllOnix2File() throws Exception {
    InputStream file_input = null;
    try {
      file_input = getResourceAsStream(TwoXMLFile);
      String string_input = StringUtil.fromInputStream(file_input);
      IOUtil.safeClose(file_input);

      CIProperties xmlHeader = new CIProperties();    
      xmlHeader.put(CachedUrl.PROPERTY_CONTENT_TYPE, "text/xml");
      MockCachedUrl mcu = mau.addUrl(xml_url, true, true, xmlHeader);

      // Now add all the pdf files in our AU since we check for them before emitting
      String pdf_url_start = BASE_URL + "2015/" + TAR_GZ + "0022-3727/48/35/355104/";
      Iterator<String> pdfIterator = real_pdflist.iterator();
      while (pdfIterator.hasNext()) {
        String tail = pdfIterator.next();
        //System.out.println("adding " + pdf_url_start + tail);
        mau.addUrl(pdf_url_start + tail, true, true, xmlHeader);
      }    

      mcu.setContent(string_input);
      mcu.setContentSize(string_input.length());
      mcu.setProperty(CachedUrl.PROPERTY_CONTENT_TYPE, "text/xml");

      FileMetadataExtractor me = new  IopOnixXmlMetadataExtractorFactory().createFileMetadataExtractor(MetadataTarget.Any(), "text/xml");
      FileMetadataListExtractor mle =
          new FileMetadataListExtractor(me);
      List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any(), mcu);
      assertNotEmpty(mdlist);
      assertEquals(1, mdlist.size());
      //log.info("SIZE for onix2: " + mdlist.size());
      

      // check each returned md against expected values
      Iterator<ArticleMetadata> mdIt = mdlist.iterator();
      ArticleMetadata mdRecord = null;
      while (mdIt.hasNext()) {
        mdRecord = (ArticleMetadata) mdIt.next();
        //log.info(mdRecord.ppString(2));
        //compareMetadata(mdRecord);
      }
    }finally {
      IOUtil.safeClose(file_input);
    }

  }

  private static final String ThreeLongXMLFile = "Onix3LongTest.xml";

  public void testAllOnix3File() throws Exception {
    InputStream file_input = null;
    try {
      file_input = getResourceAsStream(ThreeLongXMLFile);
      String string_input = StringUtil.fromInputStream(file_input);
      IOUtil.safeClose(file_input);

      CIProperties xmlHeader = new CIProperties();    
      xmlHeader.put(CachedUrl.PROPERTY_CONTENT_TYPE, "text/xml");
      MockCachedUrl mcu = mau.addUrl(xml_url, true, true, xmlHeader);

      // Now add all the pdf files in our AU since we check for them before emitting
      String pdf_url_start = BASE_URL + "2015/" + TAR_GZ + "0022-3727/48/35/355104/";
      Iterator<String> pdfIterator = real_pdflist.iterator();
      while (pdfIterator.hasNext()) {
        String tail = pdfIterator.next();
        //System.out.println("adding " + pdf_url_start + tail);
        mau.addUrl(pdf_url_start + tail, true, true, xmlHeader);
      }    

      mcu.setContent(string_input);
      mcu.setContentSize(string_input.length());
      mcu.setProperty(CachedUrl.PROPERTY_CONTENT_TYPE, "text/xml");

      FileMetadataExtractor me = new  IopOnixXmlMetadataExtractorFactory().createFileMetadataExtractor(MetadataTarget.Any(), "text/xml");
      FileMetadataListExtractor mle =
          new FileMetadataListExtractor(me);
      List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any(), mcu);
      assertNotEmpty(mdlist);
      assertEquals(1, mdlist.size());
      //log.info("SIZE for onix3Long: " + mdlist.size());

      // check each returned md against expected values
      Iterator<ArticleMetadata> mdIt = mdlist.iterator();
      ArticleMetadata mdRecord = null;
      while (mdIt.hasNext()) {
        mdRecord = (ArticleMetadata) mdIt.next();
        //log.info(mdRecord.ppString(2));
        //compareMetadata(mdRecord);
      }
    }finally {
      IOUtil.safeClose(file_input);
    }

  }    
  private static final String ThreeShortXMLFile = "Onix3ShortTest.xml";

  public void testOnix3ShrotFile() throws Exception {
    InputStream file_input = null;
    try {
      file_input = getResourceAsStream(ThreeShortXMLFile);
      String string_input = StringUtil.fromInputStream(file_input);
      IOUtil.safeClose(file_input);

      CIProperties xmlHeader = new CIProperties();    
      xmlHeader.put(CachedUrl.PROPERTY_CONTENT_TYPE, "text/xml");
      MockCachedUrl mcu = mau.addUrl(xml_url, true, true, xmlHeader);

      // Now add all the pdf files in our AU since we check for them before emitting
      String pdf_url_start = BASE_URL + "2015/" + TAR_GZ + "0022-3727/48/35/355104/";
      Iterator<String> pdfIterator = real_pdflist.iterator();
      while (pdfIterator.hasNext()) {
        String tail = pdfIterator.next();
        //System.out.println("adding " + pdf_url_start + tail);
        mau.addUrl(pdf_url_start + tail, true, true, xmlHeader);
      }    

      mcu.setContent(string_input);
      mcu.setContentSize(string_input.length());
      mcu.setProperty(CachedUrl.PROPERTY_CONTENT_TYPE, "text/xml");

      FileMetadataExtractor me = new  IopOnixXmlMetadataExtractorFactory().createFileMetadataExtractor(MetadataTarget.Any(), "text/xml");
      FileMetadataListExtractor mle =
          new FileMetadataListExtractor(me);
      List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any(), mcu);
      assertNotEmpty(mdlist);
      assertEquals(3, mdlist.size());
      //log.info("SIZE for onix3short: " + mdlist.size());

      // check each returned md against expected values
      Iterator<ArticleMetadata> mdIt = mdlist.iterator();
      ArticleMetadata mdRecord = null;
      while (mdIt.hasNext()) {
        mdRecord = (ArticleMetadata) mdIt.next();
        //log.info(mdRecord.ppString(2));
        //compareMetadata(mdRecord);
      }
    }finally {
      IOUtil.safeClose(file_input);
    }
  }
}
