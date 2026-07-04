package org.obolibrary.robot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utilities for choosing an annotation value (typically an rdfs:label) for an entity based on a
 * user-supplied, ordered list of preferred language tags.
 *
 * <p>Given a set of candidate values (each with a language tag), selection proceeds as:
 *
 * <ol>
 *   <li>Prefer a value whose language tag matches an entry earlier in the preference list.
 *   <li>An <em>exact</em> language-tag match is preferred over a <em>cascade</em> match (e.g. a
 *       preference of {@code en} matches a value tagged {@code en-GB}); among cascade matches, a
 *       more specific preference entry (e.g. {@code en-GB}) is preferred over a less specific one
 *       (e.g. {@code en}). Consequently {@code en} only "captures" {@code en-GB} when {@code en-GB}
 *       is not itself listed.
 *   <li>Among values that tie on language, the alphanumerically-first value is chosen (a
 *       deterministic tie-break, matching ROBOT's existing convention in {@code
 *       OntologyHelper.getAnnotationString}).
 *   <li>If no value matches any preferred language but at least one value exists, the
 *       alphanumerically-first value is chosen, so that a labelled entity never regresses to its
 *       IRI merely because its language is unlisted.
 * </ol>
 *
 * <p>The token {@link #NO_LANG_TOKEN} in a preference list refers to values that have no language
 * tag (the OWL API represents these with an empty language string).
 *
 * <p>Both the CLI token for "no language" and the command-line option name are defined here as
 * constants so that the spelling can be changed in one place.
 */
public class LanguagePreference {

  /** The token, used within a preference list, that matches values with no language tag. */
  public static final String NO_LANG_TOKEN = "none";

  /** The long name of the command-line option used to supply a language preference list. */
  public static final String OPTION_NAME = "label-langs-priority";

  /** The OWL API's internal representation of "no language tag". */
  private static final String NO_LANG = "";

  /** A candidate annotation value together with its language tag. */
  public static class Candidate {

    /** The language tag, or the empty string for no language tag. Never null. */
    final String lang;

    /** The lexical value. */
    final String value;

    /**
     * Create a candidate value.
     *
     * @param lang the language tag (a null is treated as no language tag)
     * @param value the lexical value
     */
    public Candidate(String lang, String value) {
      this.lang = (lang == null) ? NO_LANG : lang;
      this.value = value;
    }
  }

  /** Prevent instantiation. */
  private LanguagePreference() {}

  /**
   * Parse a comma-separated preference string into an ordered list of language tags. The special
   * token {@link #NO_LANG_TOKEN} (case-insensitive) is mapped to the empty language tag. Blank
   * entries are ignored. A null or empty input yields an empty list, which means "no preference".
   *
   * @param csv comma-separated language tags, or null
   * @return an ordered list of language tags (the empty string represents "no language tag")
   */
  public static List<String> parse(String csv) {
    List<String> result = new ArrayList<>();
    if (csv == null) {
      return result;
    }
    for (String raw : csv.split(",")) {
      String token = raw.trim();
      if (token.isEmpty()) {
        continue;
      }
      if (token.equalsIgnoreCase(NO_LANG_TOKEN)) {
        result.add(NO_LANG);
      } else {
        result.add(token);
      }
    }
    return result;
  }

  /**
   * Choose the best value from the candidates according to the preferred languages, falling back to
   * the alphanumerically-first value if none match a preferred language.
   *
   * @param candidates the candidate values (may be empty)
   * @param preferredLangs the ordered list of preferred language tags (may be empty)
   * @return the chosen value, or null if there are no candidates
   */
  public static String selectValue(List<Candidate> candidates, List<String> preferredLangs) {
    String preferred = selectPreferred(candidates, preferredLangs);
    if (preferred != null) {
      return preferred;
    }
    return selectFallback(candidates);
  }

  /**
   * Choose the best value whose language tag matches one of the preferred languages. Returns null
   * if no candidate matches any preferred language (including when the preference list is empty).
   *
   * @param candidates the candidate values (may be empty)
   * @param preferredLangs the ordered list of preferred language tags (may be empty)
   * @return the best-matching value, or null if none matches a preferred language
   */
  public static String selectPreferred(List<Candidate> candidates, List<String> preferredLangs) {
    if (candidates == null || preferredLangs == null || preferredLangs.isEmpty()) {
      return null;
    }
    Candidate best = null;
    int[] bestKey = null;
    for (Candidate c : candidates) {
      int[] key = matchKey(c.lang, preferredLangs);
      if (key == null) {
        continue;
      }
      if (best == null || compare(key, c.value, bestKey, best.value) < 0) {
        best = c;
        bestKey = key;
      }
    }
    return (best == null) ? null : best.value;
  }

  /**
   * Choose the alphanumerically-first value, ignoring language. Used as a deterministic fallback
   * when no candidate matches a preferred language.
   *
   * @param candidates the candidate values (may be empty)
   * @return the alphanumerically-first value, or null if there are no candidates
   */
  public static String selectFallback(List<Candidate> candidates) {
    if (candidates == null) {
      return null;
    }
    Candidate best = null;
    for (Candidate c : candidates) {
      if (best == null || c.value.compareTo(best.value) < 0) {
        best = c;
      }
    }
    return (best == null) ? null : best.value;
  }

  /**
   * Compute a sortable ranking key describing how well a language tag matches a preference list, or
   * null if it does not match at all. Lower keys rank better. The key components are, in order:
   *
   * <ol>
   *   <li>match type: 0 for an exact match, 1 for a cascade (prefix) match;
   *   <li>specificity: 0 for exact, otherwise the negated length of the matching prefix so that a
   *       longer (more specific) preference entry ranks better;
   *   <li>the index of the matching entry in the preference list.
   * </ol>
   *
   * @param lang the language tag to score (the empty string for no language tag)
   * @param preferredLangs the ordered list of preferred language tags
   * @return a ranking key, or null if the tag matches no preference entry
   */
  private static int[] matchKey(String lang, List<String> preferredLangs) {
    // Language tags are case-insensitive (BCP 47), and the OWL API reports them in lower case, so
    // compare case-insensitively.
    String lowerLang = lang.toLowerCase(Locale.ROOT);
    int[] best = null;
    for (int i = 0; i < preferredLangs.size(); i++) {
      String pref = preferredLangs.get(i).toLowerCase(Locale.ROOT);
      int[] key;
      if (lowerLang.equals(pref)) {
        // Exact match.
        key = new int[] {0, 0, i};
      } else if (!pref.isEmpty() && lowerLang.startsWith(pref + "-")) {
        // Cascade match: a broader preference matches a more specific tag.
        key = new int[] {1, -pref.length(), i};
      } else {
        continue;
      }
      if (best == null || compare(key, best) < 0) {
        best = key;
      }
    }
    return best;
  }

  /**
   * Compare two ranking keys with the candidate value as a final tie-break.
   *
   * @param keyA first key
   * @param valueA value for the first key
   * @param keyB second key
   * @param valueB value for the second key
   * @return negative if A ranks before B, positive if after, zero if identical
   */
  private static int compare(int[] keyA, String valueA, int[] keyB, String valueB) {
    int cmp = compare(keyA, keyB);
    if (cmp != 0) {
      return cmp;
    }
    return valueA.compareTo(valueB);
  }

  /**
   * Compare two ranking keys component by component.
   *
   * @param keyA first key
   * @param keyB second key
   * @return negative if A ranks before B, positive if after, zero if identical
   */
  private static int compare(int[] keyA, int[] keyB) {
    for (int i = 0; i < keyA.length && i < keyB.length; i++) {
      int cmp = Integer.compare(keyA[i], keyB[i]);
      if (cmp != 0) {
        return cmp;
      }
    }
    return Integer.compare(keyA.length, keyB.length);
  }
}
