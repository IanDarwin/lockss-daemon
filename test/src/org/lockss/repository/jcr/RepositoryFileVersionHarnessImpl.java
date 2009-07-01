/**
 * 
 */
package org.lockss.repository.jcr;

import java.io.*;
import java.util.*;

import javax.jcr.*;

import org.lockss.protocol.*;
import org.lockss.repository.*;
import org.lockss.repository.v2.*;
import org.lockss.util.*;

/**
 * @author edwardsb
 *
 */
public class RepositoryFileVersionHarnessImpl extends RepositoryFileVersionImpl implements
    RepositoryFileVersionHarness {
  private static final int k_sizeDeferredStream = 10240;
  private static final long k_sizeMax = 1000000;
  private static final String k_urlDefault = "http://www.example.com/example.html";
  
  /**
   * @param session
   * @param node
   * @param fileContent
   * @throws IOException
   */
  public RepositoryFileVersionHarnessImpl(Session session, Node node,
      String stemFile, RepositoryFileImpl rfiParent, IdentityManager idman)
      throws IOException, LockssRepositoryException {
    super(session, node, stemFile, k_sizeMax, k_urlDefault, rfiParent,
        k_sizeDeferredStream, idman);
  }

  /**
   * @param session
   * @param node
   * @param fileContent
   * @param sizeMax
   * @throws IOException
   */
  public RepositoryFileVersionHarnessImpl(Session session, Node node,
      String stemFile, long sizeMax, RepositoryFileImpl rfiParent,
      IdentityManager idman) 
      throws IOException, LockssRepositoryException {
    super(session, node, stemFile, sizeMax, k_urlDefault, rfiParent,
        k_sizeDeferredStream, idman);
  }
  
  /**
   * @param session
   * @param node
   * @param fileContent
   * @param sizeMax
   * @param URL
   * @throws IOException
   */
  public RepositoryFileVersionHarnessImpl(Session session, Node node,
      String stemFile, long sizeMax, String url, RepositoryFileImpl
      rfiParent, IdentityManager idman) 
      throws IOException, LockssRepositoryException {
    super(session, node, stemFile, sizeMax, url, rfiParent,
        k_sizeDeferredStream, idman);
  }

  
  
  /**
   * @param session
   * @param node
   */
  public RepositoryFileVersionHarnessImpl(Session session, Node node,
      RepositoryFileImpl rfiParent, IdentityManager idman) 
  throws LockssRepositoryException, NoUrlException {
    super(session, node, rfiParent, idman);
  }
  
  /**
   * You can set the content through input streams. You can also set the content
   * through byte arrays.
   * 
   * This routine will be used more by testing than by the main program.
   * 
   * A future version of this software should use the RepositoryFileImpl
   * methods, rather than set protected values.
   */
  public void setContent(byte[] arbyContent) throws NullPointerException, LockssRepositoryException {
    
    if (!m_isLocked) {
      // Write the content.
      try {
        if (arbyContent != null) {
          m_deffileTempContent = new DeferredTempFileOutputStream(m_sizeDeferredStream);
          
          m_deffileTempContent.write(arbyContent);
          m_deffileTempContent.flush();
          
          m_sizeEditing = arbyContent.length;
        } else {
          logger
              .error("RepositoryFileVersionHarnessImpl.setContent(null) no longer" +
              		" removes content.  Please use clearContent() " +
              		"instead.");
          throw new NullPointerException();
        }
      } catch (FileNotFoundException e) {
        logger
            .error("RepositoryFileVersionHarnessImpl.setContent(): on writing " +
            		"string, File Not Found Exception: "
                + e.getMessage());
      } catch (IOException e) {
        logger
            .error("RepositoryFileVersionHarnessImpl.setContent(): on writing string, " +
                          "IO Exception: "
                + e.getMessage());
      } finally {
        if (m_deffileTempContent != null) {
          try {
            m_deffileTempContent.close();
          } catch (IOException e) {
            logger
                .error("RepositoryFileVersionHarnessImpl.setContent(): on closing, " +
                          "IO Exception: "
                    + e.getMessage());
          }
        } // if oosContent != null.
      }
    } else {
      // It's locked.  Like setInputStream, it should throw an exception.
      throw new LockssRepositoryException("setContent should not be called after commit().");
    }
  }

  
  /**
   * This method retrieves the content as a string.
   */
  public byte[] getContent() throws LockssRepositoryException {
    Byte ByTemp;
    byte arbyContent[] = null;
    int intByte;
    int i;
    InputStream isContent = null;
    Vector<Byte> veByContent = null;
    
    if (hasContent()) {
      if (!isDeleted()) {
        veByContent = new Vector<Byte>();
        
        try {
          isContent = getInputStream();
          
          if (isContent == null) {
            return null;
          }
          
          for (;;) { // FOREVER
            intByte = isContent.read();
            if (intByte == -1) {
              break;
            }
            
            ByTemp = new Byte((byte) intByte);
            veByContent.add(ByTemp);
          }
          
          arbyContent = new byte[veByContent.size()];
          for (i = 0; i < veByContent.size(); i++) {
            arbyContent[i] = veByContent.get(i).byteValue();
          }
        } catch (IOException e) {
          logger.error("RepositoryFileVersionHarnessImpl.getContent: IO Exception: "
              + e.getMessage());
        } finally {
          try {
            if (isContent != null) {
              isContent.close();
            }
          } catch (IOException e) {
            logger
                .error("RepositoryFileVersionHarnessImpl.getContent: in close, IO "
                    + "Exception: "
                    + e.getMessage());
          }
        }
      } else { // if isDeleted...
        logger
            .error("RepositoryFileVersionHarnessImpl.getContent: Called when the content "
                + "was deleted.");
      } // if !isDeleted
    } else { // if !hasContent...
      logger
          .error("RepositoryFileVersionHarnessImpl.getContent: Called when there was no "
              + "content.");
    } // if hasContent...
    
    return arbyContent;
  }
}
