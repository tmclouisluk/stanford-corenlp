package edu.stanford.nlp.parser.metrics;

import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import ca.gedge.radixtree.RadixTree;
import edu.stanford.nlp.international.Languages;
import edu.stanford.nlp.international.Languages.Language;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.StringUtils;
import javolution.text.TextBuilder;
import javolution.util.FastMap;
import javolution.util.FastSet;

/**
 * Computes POS tagging P/R/F1 from guess/gold trees. This version assumes that the yields match. For
 * trees with potentially different yields, use {@link TsarfatyEval}.
 * <p>
 * This implementation assumes that the guess/gold input files are of equal length, and have one tree per
 * line.
 *
 * @author Spence Green
 *
 */
public class TaggingEval extends AbstractEval {

  private final Lexicon lex;

  private static boolean doCatLevelEval;
  private Counter<String> precisions;
  private Counter<String> recalls;
  private Counter<String> f1s;

  private Counter<String> precisions2;
  private Counter<String> recalls2;
  private Counter<String> pnums2;
  private Counter<String> rnums2;

  private Counter<String> percentOOV;
  private Counter<String> percentOOV2;

  public TaggingEval(String str) {
    this(str, true, null);
  }

  public TaggingEval(String str, boolean runningAverages, Lexicon lex) {
    super(str, runningAverages);
    this.lex = lex;

    if(doCatLevelEval) {
      precisions = new ClassicCounter<>();
      recalls = new ClassicCounter<>();
      f1s = new ClassicCounter<>();

      precisions2 = new ClassicCounter<>();
      recalls2 = new ClassicCounter<>();
      pnums2 = new ClassicCounter<>();
      rnums2 = new ClassicCounter<>();

      percentOOV = new ClassicCounter<>();
      percentOOV2 = new ClassicCounter<>();
    }
  }

  @Override
  protected Set<HasTag> makeObjects(final Tree tree) {
      return tree == null ? new FastSet<HasTag>(Collections.<HasTag>emptySet()) : new FastSet<HasTag>() {{
          addAll(tree.taggedLabeledYield());
      }};
  }

  private static RadixTree<Set<Label>> makeObjectsByCat(Tree t) {
      RadixTree<Set<Label>> catMap = new RadixTree<>();
    List<CoreLabel> tly = t.taggedLabeledYield();

    for(CoreLabel label : tly) {
      if(catMap.containsKey(label.value()))
        catMap.get(label.value()).add(label);
      else {
          Set<Label> catSet = new FastSet<>();
        catSet.add(label);
        catMap.put(label.value(), catSet);
      }
    }
    return catMap;
  }

  @Override
  public void evaluate(Tree guess, Tree gold, PrintWriter pw) {
    if(gold == null || guess == null) {
      System.err.printf("%s: Cannot compare against a null gold or guess tree!\n",this.getClass().getName());
      return;
    }

    //Do regular evaluation
    super.evaluate(guess, gold, pw);

    if(doCatLevelEval) {
      RadixTree<Set<Label>> guessCats = makeObjectsByCat(guess);
      RadixTree<Set<Label>> goldCats = makeObjectsByCat(gold);
        Set<String> allCats = new FastSet<>();
      allCats.addAll(guessCats.keySet());
      allCats.addAll(goldCats.keySet());

      for(String cat : allCats) {
        Set<Label> thisGuessCats = guessCats.get(cat);
        Set<Label> thisGoldCats = goldCats.get(cat);

        if (thisGuessCats == null)
            thisGuessCats = new FastSet<>();
        if (thisGoldCats == null)
            thisGoldCats = new FastSet<>();

        double currentPrecision = precision(thisGuessCats, thisGoldCats);
        double currentRecall = precision(thisGoldCats, thisGuessCats);

        double currentF1 = currentPrecision > 0.0 && currentRecall > 0.0 ? 2.0 / (1.0 / currentPrecision + 1.0 / currentRecall) : 0.0;

        precisions.incrementCount(cat, currentPrecision);
        recalls.incrementCount(cat, currentRecall);
        f1s.incrementCount(cat, currentF1);

        precisions2.incrementCount(cat, thisGuessCats.size() * currentPrecision);
        pnums2.incrementCount(cat, thisGuessCats.size());

        recalls2.incrementCount(cat, thisGoldCats.size() * currentRecall);
        rnums2.incrementCount(cat, thisGoldCats.size());

        if(lex != null) measureOOV(guess,gold);

        if (pw != null && runningAverages) {
          pw.println(cat + "\tP: " + (int) (currentPrecision * 10000) / 100.0 + " (sent ave " + (int) (precisions.get(cat) * 10000 / num) / 100.0 + ") (evalb " + (int) (precisions2.get(cat) * 10000 / pnums2.get(cat)) / 100.0 + ')');
          pw.println("\tR: " + (int) (currentRecall * 10000) / 100.0 + " (sent ave " + (int) (recalls.get(cat) * 10000 / num) / 100.0 + ") (evalb " + (int) (recalls2.get(cat) * 10000 / rnums2.get(cat)) / 100.0 + ')');
          double cF1 = 2.0 / (rnums2.get(cat) / recalls2.get(cat) + pnums2.get(cat) / precisions2.get(cat));
          String emit = str + " F1: " + (int) (currentF1 * 10000) / 100.0 + " (sent ave " + (int) (10000 * f1s.get(cat) / num) / 100.0 + ", evalb " + (int) (10000 * cF1) / 100.0 + ')';
          pw.println(emit);
        }
      }
      if (pw != null && runningAverages) {
        pw.println("========================================");
      }
    }
  }

  /**
   * Measures the percentage of incorrect taggings that can be attributed to OOV words.
   *
   * @param guess
   * @param gold
   */
  private void measureOOV(Tree guess, Tree gold) {
    List<CoreLabel> goldTagging = gold.taggedLabeledYield();
    List<CoreLabel> guessTagging = guess.taggedLabeledYield();

    assert goldTagging.size() == guessTagging.size();

    for(int i = 0; i < goldTagging.size(); i++) {
      if(!(goldTagging.get(i) == guessTagging.get(i))) {
        percentOOV2.incrementCount(goldTagging.get(i).tag());
        if(!lex.isKnown(goldTagging.get(i).word()))
          percentOOV.incrementCount(goldTagging.get(i).tag());
      }
    }
  }

  @Override
  public void display(boolean verbose, PrintWriter pw) {
    super.display(verbose, pw);

    if(doCatLevelEval) {
      NumberFormat nf = new DecimalFormat("0.00");
        Set<String> cats = new FastSet<>();
      Random rand = new Random();
      cats.addAll(precisions.keySet());
      cats.addAll(recalls.keySet());

      Map<Double,String> f1Map = new TreeMap<>();
      for (String cat : cats) {
        double pnum2 = pnums2.get(cat);
        double rnum2 = rnums2.get(cat);
        double prec = precisions2.get(cat) / pnum2;
        double rec = recalls2.get(cat) / rnum2;
        double f1 = 2.0 / (1.0 / prec + 1.0 / rec);

        if(new Double(f1).equals(Double.NaN)) f1 = -1.0;
        if(f1Map.containsKey(f1))
          f1Map.put(f1 + rand.nextDouble()/1000.0, cat);
        else
          f1Map.put(f1, cat);
      }

      pw.println("============================================================");
      pw.println("Tagging Performance by Category -- final statistics");
      pw.println("============================================================");

      for (String cat : f1Map.values()) {
        double pnum2 = pnums2.get(cat);
        double rnum2 = rnums2.get(cat);
        double prec = precisions2.get(cat) / pnum2;
        prec *= 100.0;
        double rec = recalls2.get(cat) / rnum2;
        rec *= 100.0;
        double f1 = 2.0 / (1.0 / prec + 1.0 / rec);

        double oovRate = lex == null ? -1.0 : percentOOV.get(cat) / percentOOV2.get(cat);

        pw.println(cat + "\tLP: " + (pnum2 == 0.0 ? " N/A": nf.format(prec)) + "\tguessed: " + (int) pnum2 +
            "\tLR: " + (rnum2 == 0.0 ? " N/A": nf.format(rec)) + "\tgold:  " + (int) rnum2 +
            "\tF1: " + (pnum2 == 0.0 || rnum2 == 0.0 ? " N/A": nf.format(f1)) +
            "\tOOV: " + (lex == null ? " N/A" : nf.format(oovRate)));
      }

      pw.println("============================================================");
    }
  }

  private static final int minArgs = 2;
  private static final TextBuilder usage = new TextBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] gold guess\n\n",TaggingEval.class.getName()));
    usage.append("Options:\n");
    usage.append("  -v         : Verbose mode.\n");
    usage.append("  -l lang    : Select language settings from ").append(Languages.listOfLanguages()).append('\n');
    usage.append("  -y num     : Skip gold trees with yields longer than num.\n");
    usage.append("  -c         : Compute LP/LR/F1 by category.\n");
    usage.append("  -e         : Input encoding.\n");
  }

  public static final RadixTree<Integer> optionArgDefs = new RadixTree<>();

    static {
    optionArgDefs.put("-v", 0);
    optionArgDefs.put("-l", 1);
    optionArgDefs.put("-y", 1);
    optionArgDefs.put("-c", 0);
    optionArgDefs.put("-e", 0);
  }

  /**
   * Run the scoring metric on guess/gold input. This method performs "Collinization."
   * The default language is English.
   *
   * @param args
   */
  public static void main(String... args) {

    if(args.length < minArgs) {
      System.out.println(usage.toString());
      System.exit(-1);
    }

    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    int maxGoldYield = Integer.MAX_VALUE;
    boolean VERBOSE = false;
    String encoding = "UTF-8";

    String guessFile = null;
    String goldFile = null;

    RadixTree< String[]> argsMap = StringUtils.argsToMap(args, optionArgDefs);

    for(Map.Entry<String, String[]> opt : argsMap.entrySet()) {
      if(opt.getKey() == null) continue;
      if(opt.getKey().equals("-l")) {
        Language lang = Language.valueOf(opt.getValue()[0].trim());
        tlpp = Languages.getLanguageParams(lang);

      } else if(opt.getKey().equals("-y")) {
        maxGoldYield = Integer.parseInt(opt.getValue()[0].trim());

      } else if(opt.getKey().equals("-v")) {
        VERBOSE = true;

      } else if(opt.getKey().equals("-c")) {
        TaggingEval.doCatLevelEval = true;

      } else if(opt.getKey().equals("-e")) {
        encoding = opt.getValue()[0];

      } else {
        System.err.println(usage.toString());
        System.exit(-1);
      }

      //Non-option arguments located at key null
      String[] rest = argsMap.get(null);
      if(rest == null || rest.length < minArgs) {
        System.err.println(usage.toString());
        System.exit(-1);
      }
      goldFile = rest[0];
      guessFile = rest[1];
    }

    tlpp.setInputEncoding(encoding);
    PrintWriter pwOut = tlpp.pw();

    Treebank guessTreebank = tlpp.diskTreebank();
    guessTreebank.loadPath(guessFile);
    pwOut.println("GUESS TREEBANK:");
    pwOut.println(guessTreebank.textualSummary());

    Treebank goldTreebank = tlpp.diskTreebank();
    goldTreebank.loadPath(goldFile);
    pwOut.println("GOLD TREEBANK:");
    pwOut.println(goldTreebank.textualSummary());

    TaggingEval metric = new TaggingEval("Tagging LP/LR");

    TreeTransformer tc = tlpp.collinizer();

    //The evalb ref implementation assigns status for each tree pair as follows:
    //
    //   0 - Ok (yields match)
    //   1 - length mismatch
    //   2 - null parse e.g. (()).
    //
    //In the cases of 1,2, evalb does not include the tree pair in the LP/LR computation.
    Iterator<Tree> goldItr = goldTreebank.iterator();
    Iterator<Tree> guessItr = guessTreebank.iterator();
    int goldLineId = 0;
    int guessLineId = 0;
    int skippedGuessTrees = 0;
    while( guessItr.hasNext() && goldItr.hasNext() ) {
      Tree guessTree = guessItr.next();
      List<Label> guessYield = guessTree.yield();
      guessLineId++;

      Tree goldTree = goldItr.next();
      List<Label> goldYield = goldTree.yield();
      goldLineId++;

      // Check that we should evaluate this tree
      if(goldYield.size() > maxGoldYield) {
        skippedGuessTrees++;
        continue;
      }

      // Only trees with equal yields can be evaluated
      if(goldYield.size() != guessYield.size()) {
        pwOut.printf("Yield mismatch gold: %d tokens vs. guess: %d tokens (lines: gold %d guess %d)%n", goldYield.size(), guessYield.size(), goldLineId, guessLineId);
        skippedGuessTrees++;
        continue;
      }

      Tree evalGuess = tc.transformTree(guessTree);
      Tree evalGold = tc.transformTree(goldTree);

      metric.evaluate(evalGuess, evalGold, VERBOSE ? pwOut : null);
    }

    if(guessItr.hasNext() || goldItr.hasNext()) {
      System.err.printf("Guess/gold files do not have equal lengths (guess: %d gold: %d)%n.", guessLineId, goldLineId);
    }

    pwOut.println("================================================================================");
    if(skippedGuessTrees != 0) pwOut.printf("%s %d guess trees\n", "Unable to evaluate", skippedGuessTrees);
    metric.display(true, pwOut);
    pwOut.println();
    pwOut.close();
  }
}