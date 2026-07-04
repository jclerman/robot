package org.obolibrary.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Tests for {@link LanguagePreference}. */
public class LanguagePreferenceTest {

  /** Build a candidate list from alternating (lang, value) arguments. */
  private static List<LanguagePreference.Candidate> candidates(String... langValuePairs) {
    if (langValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException("expected pairs of (lang, value)");
    }
    LanguagePreference.Candidate[] cs = new LanguagePreference.Candidate[langValuePairs.length / 2];
    for (int i = 0; i < cs.length; i++) {
      cs[i] = new LanguagePreference.Candidate(langValuePairs[2 * i], langValuePairs[2 * i + 1]);
    }
    return Arrays.asList(cs);
  }

  /** Parsing splits, trims, drops blanks, and maps the no-lang token to the empty string. */
  @Test
  public void testParse() {
    assertEquals(Arrays.asList("en-GB", "en", "fr"), LanguagePreference.parse("en-GB, en ,fr"));
    // The no-lang token becomes the empty string; case-insensitive.
    assertEquals(Arrays.asList("en", ""), LanguagePreference.parse("en,NONE"));
    // Blank entries (e.g. from a trailing comma) are dropped.
    assertEquals(Arrays.asList("en", "fr"), LanguagePreference.parse("en,,fr,"));
    // Null or empty means "no preference".
    assertEquals(Collections.emptyList(), LanguagePreference.parse(null));
    assertEquals(Collections.emptyList(), LanguagePreference.parse("  "));
  }

  /** An exact language match is preferred over other languages. */
  @Test
  public void testExactMatch() {
    List<LanguagePreference.Candidate> cs = candidates("de", "Hund", "en", "dog", "fr", "chien");
    assertEquals("dog", LanguagePreference.selectValue(cs, Arrays.asList("en", "fr")));
    assertEquals("chien", LanguagePreference.selectValue(cs, Arrays.asList("fr", "en")));
  }

  /** A broad preference cascades to a more specific tag (en matches en-GB). */
  @Test
  public void testCascadeMatch() {
    List<LanguagePreference.Candidate> cs = candidates("en-GB", "colour", "de", "Farbe");
    assertEquals("colour", LanguagePreference.selectValue(cs, Collections.singletonList("en")));
  }

  /** An exact match beats a cascade match regardless of list position. */
  @Test
  public void testExactBeatsCascade() {
    List<LanguagePreference.Candidate> cs = candidates("en-GB", "colour", "en", "generic");
    // "en" (index 0) cascades to en-GB, but the exact "en-GB" (index 1) wins.
    assertEquals("generic", LanguagePreference.selectValue(cs, Arrays.asList("en", "en-GB")));
    // The en-GB literal should be attributed to its own exact entry, not swept up by "en".
    List<LanguagePreference.Candidate> gbOnly = candidates("en-GB", "colour");
    assertEquals("colour", LanguagePreference.selectValue(gbOnly, Arrays.asList("en", "en-GB")));
  }

  /** Among cascade matches, the more specific preference entry wins. */
  @Test
  public void testCascadeSpecificity() {
    List<LanguagePreference.Candidate> cs = candidates("en-GB-oxendict", "posh");
    // Both "en" and "en-GB" cascade; the more specific "en-GB" should be chosen even though "en"
    // appears earlier in the list.
    assertEquals("posh", LanguagePreference.selectValue(cs, Arrays.asList("en", "en-GB")));
  }

  /** The no-lang token matches an untagged literal. */
  @Test
  public void testNoLangToken() {
    List<LanguagePreference.Candidate> cs = candidates("en", "tagged", null, "untagged");
    List<String> prefs = LanguagePreference.parse("none,en");
    assertEquals("untagged", LanguagePreference.selectValue(cs, prefs));
    // An empty preference entry must not cascade-match everything.
    List<LanguagePreference.Candidate> onlyTagged = candidates("de", "Hund");
    assertNull(LanguagePreference.selectPreferred(onlyTagged, LanguagePreference.parse("none")));
  }

  /** Multiple values in the same (preferred) language are broken alphanumerically. */
  @Test
  public void testSameLanguageAlphaTieBreak() {
    List<LanguagePreference.Candidate> cs = candidates("en", "zebra", "en", "apple", "en", "mango");
    assertEquals("apple", LanguagePreference.selectValue(cs, Collections.singletonList("en")));
  }

  /** When no candidate matches a preferred language, fall back to the alphanumerically-first. */
  @Test
  public void testFallbackWhenNoPreferredMatch() {
    List<LanguagePreference.Candidate> cs = candidates("de", "Zebra", "de", "Apfel");
    // No German preference given: selectPreferred finds nothing...
    assertNull(LanguagePreference.selectPreferred(cs, Arrays.asList("en", "fr")));
    // ...but selectValue still returns a deterministic, non-null label.
    assertEquals("Apfel", LanguagePreference.selectValue(cs, Arrays.asList("en", "fr")));
  }

  /**
   * An empty preference list yields no preferred match; selectValue falls back deterministically.
   */
  @Test
  public void testEmptyPreferenceList() {
    List<LanguagePreference.Candidate> cs = candidates("en", "beta", "fr", "alpha");
    assertNull(LanguagePreference.selectPreferred(cs, Collections.emptyList()));
    assertEquals("alpha", LanguagePreference.selectValue(cs, Collections.emptyList()));
  }

  /** Language-tag matching is case-insensitive (the OWL API reports tags in lower case). */
  @Test
  public void testCaseInsensitive() {
    // Candidate tag as the OWL API would report it (lower case), preference as a user might type
    // it.
    List<LanguagePreference.Candidate> cs = candidates("en-gb", "colour", "de", "Farbe");
    assertEquals("colour", LanguagePreference.selectValue(cs, Collections.singletonList("en-GB")));
    // And the reverse casing.
    List<LanguagePreference.Candidate> cs2 = candidates("EN", "hi", "fr", "salut");
    assertEquals("hi", LanguagePreference.selectValue(cs2, Collections.singletonList("en")));
  }

  /** No candidates yields null (caller falls back to the IRI/CURIE short form). */
  @Test
  public void testNoCandidates() {
    assertNull(LanguagePreference.selectValue(Collections.emptyList(), Arrays.asList("en")));
    assertNull(LanguagePreference.selectFallback(Collections.emptyList()));
  }
}
