
package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.gedge.radixtree.RadixTree;
import edu.stanford.nlp.ie.machinereading.common.SimpleTokenize;
import edu.stanford.nlp.ie.machinereading.common.StringDictionary;
import edu.stanford.nlp.trees.Span;
import javolution.util.FastMap;

public class AceToken {
    private static final Pattern COMPILE = Pattern.compile(" ");
    /**
   * The actual token bytes
   * Normally we work with mWord (see below), but mLiteral is needed when
   *   we need to check if a sequence of tokens exists in a gazetteer
   */
  private String mLiteral;

  /** The index of the literal in the WORDS hash */
  private int mWord;

  /** Case of mWord */
  private int mCase;

  /** Suffixes of mWord */
  private int[] mSuffixes;

  private int mLemma;

  private int mPos;

  private int mChunk;

  private int mNerc;

  private Span mByteOffset;

  /** Raw byte offset in the SGM doc */
  private Span mRawByteOffset;

  private int mSentence;

  /** Entity class from Massi */
  private String mMassiClass;
  /** Entity label from the BBN corpus */
  private String mMassiBbn;
  /** WordNet super-senses detected by Massi */
  private String mMassiWnss;

  /** Dictionary for all words in the corpus */
  public static final StringDictionary WORDS;

  /** Dictionary for all lemmas in the corpus */
  public static final StringDictionary LEMMAS;

  /** Dictionary for all other strings in the corpus */
  public static final StringDictionary OTHERS;

  /** Map of all proximity classes */
  public static final Map<Integer, ArrayList<Integer>> PROX_CLASSES;
  /** How many elements per proximity class */
  private static final int PROXIMITY_CLASS_SIZE = 5;

  /** The location gazetteer */
  private static RadixTree< String> LOC_GAZ;

  /** The person first name dictionary */
  private static RadixTree< String> FIRST_GAZ;

  /** The person last name dictionary */
  private static RadixTree< String> LAST_GAZ;

  /** List of trigger words */
  private static RadixTree< String> TRIGGER_GAZ;

  private final static Pattern SGML_PATTERN;

  static {
    WORDS = new StringDictionary("words");
    LEMMAS = new StringDictionary("lemmas");
    OTHERS = new StringDictionary("others");
    WORDS.setMode(true);
    LEMMAS.setMode(true);
    OTHERS.setMode(true);
      PROX_CLASSES = new FastMap<>();

    SGML_PATTERN = Pattern.compile("<[^<>]+>");
  }

  public static void loadGazetteers(String dataPath) throws IOException {

    System.err.print("Loading location gazetteer... ");
      LOC_GAZ = new RadixTree<>();
    loadDictionary(LOC_GAZ, dataPath + File.separator + "world_small.gaz.nonambiguous");
    System.err.println("done.");

    System.err.print("Loading first-name gazetteer... ");
      FIRST_GAZ = new RadixTree<>();
    loadDictionary(FIRST_GAZ, dataPath + File.separator + "per_first.gaz");
    System.err.println("done.");

    System.err.print("Loading last-name gazetteer... ");
      LAST_GAZ = new RadixTree<>();
    loadDictionary(LAST_GAZ, dataPath + File.separator + "per_last.gaz");
    System.err.println("done.");

    System.err.print("Loading trigger-word gazetteer... ");
      TRIGGER_GAZ = new RadixTree<>();
    loadDictionary(TRIGGER_GAZ, dataPath + File.separator + "triggers.gaz");
    System.err.println("done.");
  }

  /** Loads one dictionary from disk */
  private static void loadDictionary(RadixTree< String> dict, String file) throws
          IOException {

    BufferedReader in = new BufferedReader(new FileReader(file));

    String line;
    while ((line = in.readLine()) != null) {
      ArrayList<String> tokens = SimpleTokenize.tokenize(line);
      if (!tokens.isEmpty()) {
        String lower = tokens.get(0).toLowerCase();
        if (tokens.size() == 1)
          dict.put(lower, "true");
        else
          dict.put(lower, tokens.get(1));
      }
    }
  }

  public static boolean isLocation(String lower) {
    return exists(LOC_GAZ, lower);
  }

  public static boolean isFirstName(String lower) {
    return exists(FIRST_GAZ, lower);
  }

  public static boolean isLastName(String lower) {
    return exists(LAST_GAZ, lower);
  }

  public static String isTriggerWord(String lower) {
    return TRIGGER_GAZ.get(lower);
  }

  /**
   * Verifies if the given string exists in the given dictionary
   */
  public static boolean exists(RadixTree< String> dict, String elem) {
      return dict.get(elem) != null;
  }

  /**
   * Loads all proximity classes from the hard disk The WORDS map must be
   * created before!
   */
  public static void loadProximityClasses(String proxFileName) throws IOException {

    System.err.println("Loading proximity classes...");

    BufferedReader in = null;
    try {
      in = new BufferedReader(new FileReader(proxFileName));
    } catch (IOException e) {
      System.err.println("Warning: no proximity database found.");
      return;
    }

    String line;
    while ((line = in.readLine()) != null) {
      ArrayList<String> tokens = SimpleTokenize.tokenize(line);
      if (!tokens.isEmpty()) {
        Integer key = WORDS.get(tokens.get(0));
        ArrayList<Integer> value = new ArrayList<>();

        for (int i = 0; i < tokens.size() && i < PROXIMITY_CLASS_SIZE; i++) {
          Integer word = WORDS.get(tokens.get(i));
          value.add(word);
        }

        PROX_CLASSES.put(key, value);
      }
    }

    in.close();
    System.err.println("Finished loading proximity classes.");
  }

  public String getLiteral() {
    return mLiteral;
  }

  public int getWord() {
    return mWord;
  }

  public int getCase() {
    return mCase;
  }

  public int[] getSuffixes() {
    return mSuffixes;
  }

  public int getLemma() {
    return mLemma;
  }

  public int getPos() {
    return mPos;
  }

  public int getChunk() {
    return mChunk;
  }

  public int getNerc() {
    return mNerc;
  }

  public Span getByteOffset() {
    return mByteOffset;
  }

  public int getByteStart() {
    return mByteOffset.start();
  }

  public int getByteEnd() {
    return mByteOffset.end();
  }

  public int getSentence() {
    return mSentence;
  }

  public Span getRawByteOffset() {
    return mRawByteOffset;
  }

  public int getRawByteStart() {
    return mRawByteOffset.start();
  }

  public int getRawByteEnd() {
    return mRawByteOffset.end();
  }

  public void setMassiClass(String i) {
    mMassiClass = i;
  }

  public String getMassiClass() {
    return mMassiClass;
  }

  public void setMassiBbn(String i) {
    mMassiBbn = i;
  }

  public String getMassiBbn() {
    return mMassiBbn;
  }

  public void setMassiWnss(String i) {
    mMassiWnss = i;
  }

  public String getMassiWnss() {
    return mMassiWnss;
  }

  public static boolean isSgml(String s) {
    Matcher match = SGML_PATTERN.matcher(s);
    return match.find(0);
  }

  public static String removeSpaces(String s) {
    if (s == null)
      return s;
    return COMPILE.matcher(s).replaceAll("_");
  }

  public static final int CASE_OTHER = 0;
  public static final int CASE_ALLCAPS = 1;
  public static final int CASE_ALLCAPSORDOTS = 2;
  public static final int CASE_CAPINI = 3;
  public static final int CASE_INCAP = 4;
  public static final int CASE_ALLDIGITS = 5;
  public static final int CASE_ALLDIGITSORDOTS = 6;

  private static int detectCase(String word) {

    //
    // is the word all caps? (e.g. IBM)
    //
    boolean isAllCaps = true;
    for (int i = 0; i < word.length(); i++) {
      if (!Character.isUpperCase(word.charAt(i))) {
        isAllCaps = false;
        break;
      }
    }
    if (isAllCaps)
      return CASE_ALLCAPS;

    //
    // is the word all caps or dots?(e.g. I.B.M.)
    //
    boolean isAllCapsOrDots = true;
    if (Character.isUpperCase(word.charAt(0))) {
      for (int i = 0; i < word.length(); i++) {
        if (!Character.isUpperCase(word.charAt(i)) && word.charAt(i) != '.') {
          isAllCapsOrDots = false;
          break;
        }
      }
    } else {
      isAllCapsOrDots = false;
    }
    if (isAllCapsOrDots)
      return CASE_ALLCAPSORDOTS;

    //
    // does the word start with a cap?(e.g. Tuesday)
    //
    boolean isInitialCap = false;
    if (Character.isUpperCase(word.charAt(0)))
      isInitialCap = true;
    if (isInitialCap)
      return CASE_CAPINI;

    //
    // does the word contain a capitalized letter?
    //
    boolean isInCap = false;
    for (int i = 1; i < word.length(); i++) {
      if (Character.isUpperCase(word.charAt(i))) {
        isInCap = true;
        break;
      }
    }
    if (isInCap)
      return CASE_INCAP;

    //
    // is the word all digits? (e.g. 123)
    //
    boolean isAllDigits = false;
    for (int i = 0; i < word.length(); i++) {
      if (!Character.isDigit(word.charAt(i))) {
        isAllDigits = false;
        break;
      }
    }
    if (isAllDigits)
      return CASE_ALLDIGITS;

    //
    // is the word all digits or . or ,? (e.g. 1.3)
    //
    boolean isAllDigitsOrDots = true;
    if (Character.isDigit(word.charAt(0))) {
      for (int i = 0; i < word.length(); i++) {
        if (!Character.isDigit(word.charAt(i)) && word.charAt(i) != '.' && word.charAt(i) != ',') {
          isAllDigitsOrDots = false;
          break;
        }
      }
    } else {
      isAllDigitsOrDots = false;
    }
    if (isAllDigitsOrDots)
      return CASE_ALLDIGITSORDOTS;

    return CASE_OTHER;
  }

  private static int[] extractSuffixes(String word) {
    String lower = word.toLowerCase();
    ArrayList<Integer> suffixes = new ArrayList<>();
    for (int i = 2; i <= 4; i++) {
      if (lower.length() >= i) {
        try {
          String suf = lower.substring(lower.length() - i);
          suffixes.add(WORDS.get(suf));
        } catch (RuntimeException e) {
          // unknown suffix
        }
      } else {
        break;
      }
    }

    int[] sufs = new int[suffixes.size()];
    for (int i = 0; i < suffixes.size(); i++) {
      sufs[i] = suffixes.get(i);
    }

    return sufs;
  }

  /**
   * Constructs an AceToken from a tokenized line generated by Tokey
   */
  public AceToken(String word, String lemma, String pos, String chunk, String nerc, String start, String end,
      int sentence) {
    mLiteral = word;
    if (word == null) {
      mWord = -1;
      mCase = -1;
      mSuffixes = null;
    } else {
      mWord = WORDS.get(removeSpaces(word), false);
      mCase = detectCase(word);
      mSuffixes = extractSuffixes(word);
    }

      mLemma = lemma == null ? -1 : LEMMAS.get(removeSpaces(lemma), false);

      mPos = pos == null ? -1 : OTHERS.get(pos, false);

      mChunk = chunk == null ? -1 : OTHERS.get(chunk, false);

      mNerc = nerc == null ? -1 : OTHERS.get(nerc, false);

    if (start != null && end != null) {
      mByteOffset = new Span(Integer.parseInt(start), Integer.parseInt(end));
      mRawByteOffset = new Span(Integer.parseInt(start), Integer.parseInt(end));
    }
    mSentence = sentence;

    mMassiClass = "";
    mMassiBbn = "";
    mMassiWnss = "";
  }

  /**
   * Recomputes start/end phrase positions by removing SGML tag strings This is
   * required because ACE annotations skip over SGML tags when computing
   * positions in stream, hence annotations do not match with our preprocessing
   * positions, which count everything
   */
  public int adjustPhrasePositions(int offsetToSubtract, String word) {
    if (isSgml(word)) {
      // offsetToSubtract += word.length();
      // the token length may be different than (end - start)!
      // i.e. QUOTE_PREVIOUSPOST is cleaned in Tokey!
      offsetToSubtract += mByteOffset.end() - mByteOffset.start();
      mByteOffset.setStart(-1);
      mByteOffset.setEnd(-1);
    } else {
      mByteOffset.setStart(mByteOffset.start() - offsetToSubtract);
      mByteOffset.setEnd(mByteOffset.end() - offsetToSubtract);
    }

    return offsetToSubtract;
  }

  /** Pretty display */
  public String display() {
    if (mByteOffset != null) {
      return "['" + WORDS.get(mWord) + "', " + OTHERS.get(mPos) + ", " + mByteOffset.start() + ", "
          + mByteOffset.end() + ']';
    }

    return "['" + WORDS.get(mWord) + "', " + OTHERS.get(mPos) + ']';
  }

  public String toString() {
    return display();
  }
}
