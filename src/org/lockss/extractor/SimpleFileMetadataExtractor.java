/*
 * $Id: SimpleFileMetadataExtractor.java,v 1.3 2011-06-20 07:02:52 tlipkis Exp $
 */

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

package org.lockss.extractor;

import java.io.*;

import org.lockss.daemon.*;
import org.lockss.plugin.*;

/** Base class for metadata extractors that return a single ArticleMetadata
 * or null.  This was the previous FileMetadataExtractor interface. */
public abstract class SimpleFileMetadataExtractor
  implements FileMetadataExtractor {

  /** Call the simplified extract() method and emit what it returns.  This
   * method is final to prevent SimpleFileMetadataExtractors from
   * accidentally implementing a method that would allow them to emit more
   * than one ArticleMetadata */
  public final void extract(MetadataTarget target, 
			    CachedUrl cu,
			    FileMetadataExtractor.Emitter emitter)
      throws IOException, PluginException {
    ArticleMetadata md = extract(target, cu);
    if (md != null) {
      emitter.emitMetadata(cu, md);
    }
  }

  /**
   * Parse content on CachedUrl,  Return a Metadata object describing it
   * @param target the purpose for which metadata is being extracted
   * @param cu the CachedUrl to extract from
   */
  public abstract ArticleMetadata extract(MetadataTarget target, CachedUrl cu)
      throws IOException, PluginException;
}
