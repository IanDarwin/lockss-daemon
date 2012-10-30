/*
 * $Id: TestPatternIntMap.java,v 1.1 2012-10-30 00:10:19 tlipkis Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.util;

import java.util.*;
import org.lockss.util.*;
import org.lockss.test.*;

public class TestPatternIntMap extends LockssTestCase {

  public void testToString() {
    PatternIntMap ppm1 = new PatternIntMap("a.*b,2;ccc,4");
    assertEquals("[pm: [a.*b: 2], [ccc: 4]]", ppm1.toString());

    PatternIntMap ppm2 =
      new PatternIntMap(ListUtil.list("xx,1", "yy,2"));
    assertEquals("[pm: [xx: 1], [yy: 2]]", ppm2.toString());
  }

  public void testGetMatch() {
    PatternIntMap ppm1 = new PatternIntMap("a.*b,2;ccc,-4");
    assertEquals(0, ppm1.getMatch("a123c"));
    assertEquals(123, ppm1.getMatch("a123c", 123));
    assertEquals(2, ppm1.getMatch("a123b"));
    assertEquals(2, ppm1.getMatch("accccb"));
    assertEquals(-4, ppm1.getMatch("bccccb"));
    assertEquals(-4, ppm1.getMatch("bccccb", 111));
    assertEquals(-4, ppm1.getMatch("bccccb", 111, 10));
    assertEquals(111, ppm1.getMatch("bccccb", 111, -10));
  }

  public void testEmpty() {
    PatternIntMap ppm = PatternIntMap.EMPTY;
    assertEquals(0, ppm.getMatch("a"));
    assertEquals(42, ppm.getMatch("a", 42));
    assertEquals("[pm: EMPTY]", ppm.toString());
  }

  public void testIll() {
    try {
      PatternIntMap ppm1 = new PatternIntMap("a[)2");
      fail("Should throw: Malformed");
    } catch (IllegalArgumentException e) {
      assertMatchesRE("no comma", e.getMessage());
    }
    try {
      PatternIntMap ppm1 = new PatternIntMap("a,1;bbb");
      fail("Should throw: Malformed");
    } catch (IllegalArgumentException e) {
      assertMatchesRE("no comma", e.getMessage());
    }
    try {
      PatternIntMap ppm1 = new PatternIntMap("a.*b,2;ccc,xyz");
      fail("Should throw: illegal priority");
    } catch (IllegalArgumentException e) {
      assertMatchesRE("Illegal priority", e.getMessage());
    }
    try {
      PatternIntMap ppm1 = new PatternIntMap("a[),2");
      fail("Should throw: illegal priority");
    } catch (IllegalArgumentException e) {
      assertMatchesRE("Illegal regexp", e.getMessage());
    }
  }

}
