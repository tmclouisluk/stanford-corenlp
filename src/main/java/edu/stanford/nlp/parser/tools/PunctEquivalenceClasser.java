package edu.stanford.nlp.parser.tools;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

import javolution.util.FastSet;

/**
 * Performs equivalence classing of punctuation per PTB guidelines. Many of the multilingual
 * treebanks mark all punctuation with a single POS tag, which is bad for parsing.
 * <p>
 * PTB punctuation POS tag set (12 tags):
 * 
 * 37. #  Pound sign 
 * 38. $  Dollar sign 
 * 39. .  Sentence-final punctuation 
 * 40. ,  Comma 
 * 41. :  Colon, semi-colon 
 * 42. (  Left bracket character 
 * 43. )  Right bracket character 
 * 44. "  Straight double quote 
 * 45. `  Left open single quote 
 * 46. "  Left open double quote 
 * 47. '  Right close single quote 
 * 48. "  Right close double quote
 * <p>
 * See http://www.ldc.upenn.edu/Catalog/docs/LDC95T7/cl93.html
 * 
 * @author Spence Green
 *
 */
public class PunctEquivalenceClasser {

  private static final String[] eolClassRaw = {".","?","!"};
  private static final Set<String> sfClass = new FastSet<>((Set<? extends String>) Arrays.asList(eolClassRaw));

    private static final String[] colonClassRaw = {":",";","-","_"};
  private static final Set<String> colonClass = new FastSet<>((Set<? extends String>) Arrays.asList(colonClassRaw));

    private static final String[] commaClassRaw = {",","ر"};
  private static final Set<String> commaClass = new FastSet<>((Set<? extends String>) Arrays.asList(commaClassRaw));

    private static final String[] currencyClassRaw = {"$","#","="};
  private static final Set<String> currencyClass = new FastSet<>((Set<? extends String>) Arrays.asList(currencyClassRaw));

    private static final Pattern pEllipsis = Pattern.compile("\\.\\.+");
  
  private static final String[] slashClassRaw = {"/","\\"};
  private static final Set<String> slashClass = new FastSet<>((Set<? extends String>) Arrays.asList(slashClassRaw));

    private static final String[] lBracketClassRaw = {"-LRB-","(","[","<"};
  private static final Set<String> lBracketClass = new FastSet<>((Set<? extends String>) Arrays.asList(lBracketClassRaw));

    private static final String[] rBracketClassRaw = {"-RRB-",")","]",">"};
  private static final Set<String> rBracketClass = new FastSet<>((Set<? extends String>) Arrays.asList(rBracketClassRaw));

    private static final String[] quoteClassRaw = {"\"","``","''","'","`"};
  private static final Set<String> quoteClass = new FastSet<>((Set<? extends String>) Arrays.asList(quoteClassRaw));

    /**
   * Return the equivalence class of the argument. If the argument is not contained in
   * and equivalence class, then an empty string is returned.
   * 
   * @param punc
   * @return The class name if found. Otherwise, an empty string.
   */
  public static String getPunctClass(String punc) {
    if(punc.equals("%") || punc.equals("-PLUS-"))//-PLUS- is an escape for "+" in the ATB
      return "perc";
      if(!punc.isEmpty() && punc.charAt(0) == '*')
        return "bullet";
      if(sfClass.contains(punc))
        return "sf";
      if(colonClass.contains(punc) || pEllipsis.matcher(punc).matches())
        return "colon";
      if(commaClass.contains(punc))
        return "comma";
      if(currencyClass.contains(punc))
        return "curr";
      if(slashClass.contains(punc))
        return "slash";
      if(lBracketClass.contains(punc))
        return "lrb";
      if(rBracketClass.contains(punc))
        return "rrb";
      if(quoteClass.contains(punc))
        return "quote";

      return "";
  }
}
