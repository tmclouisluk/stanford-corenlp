package edu.stanford.nlp.parser.lexparser;

import java.util.Map;
import java.util.Set;

import ca.gedge.radixtree.RadixTree;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;
import javolution.util.FastMap;
import javolution.util.FastSet;

/**
 * An unknown word model for German; relies on BaseUnknownWordModel plus number matching.
 * An assumption of this model is that numbers (arabic digit sequences)
 * are tagged CARD. This is correct for all of NEGRA/Tiger/TuebaDZ.
 *
 * @author Roger Levy
 * @author Greg Donaker (corrections and modeling improvements)
 * @author Christopher Manning (generalized and improved what Greg did)
 */
public class GermanUnknownWordModel extends BaseUnknownWordModel {

  private static final long serialVersionUID = 221L;

  private static final String numberMatch = "[0-9]+(?:\\.[0-9]*)";

  public GermanUnknownWordModel(Options op, Lexicon lex,
                                Index<String> wordIndex,
                                Index<String> tagIndex,
                                ClassicCounter<IntTaggedWord> unSeenCounter,
                                Map<Label,ClassicCounter<String>> tagHash,
                                RadixTree<Float> unknownGT,
                                Set<String> seenEnd) {
    super(op, lex, wordIndex, tagIndex, 
          unSeenCounter, tagHash, unknownGT, seenEnd);
  }


  /**
   * This constructor creates an UWM with empty data structures.  Only
   * use if loading in the data separately, such as by reading in text
   * lines containing the data.
   */
  public GermanUnknownWordModel(Options op, Lexicon lex,
                                Index<String> wordIndex, 
                                Index<String> tagIndex) {
      this(op, lex, wordIndex, tagIndex,
         new ClassicCounter<IntTaggedWord>(),
              new FastMap<Label, ClassicCounter<String>>(),
              new  RadixTree< Float>(),
              (Set<String>) new FastMap<>());
  }


  /** Calculate the log-prob score of a particular TaggedWord in the
   *  unknown word model.
   *
   *  @param itw the tag->word production in IntTaggedWord form
   *  @return The log-prob score of a particular TaggedWord.
   */
  @Override
  public float score(IntTaggedWord itw, String word) {
    String tag = itw.tagString(tagIndex);

    if (word.matches(numberMatch)) {
      //EncodingPrintWriter.out.println("Number match for " + word,encoding);
        return tag.equals("CARD") ? 0.0f : Float.NEGATIVE_INFINITY;
    } else {
      return super.score(itw, word);
    }
  }

}

