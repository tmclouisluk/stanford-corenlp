package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.Word;

import java.util.*;


/**
 * This class splits on parents using the same algorithm as the earlier
 * parent (selective) annotation algorithms, but applied AFTER the tree
 * has been annotated.
 *
 * @author Christopher Manning
 */
class PostSplitter implements TreeTransformer {

  private ClassicCounter<String> nonTerms = new ClassicCounter<>();
  private TreebankLangParserParams tlpParams;
  private TreeFactory tf;
  private HeadFinder hf;
  private final TrainOptions trainOptions;

  public Tree transformTree(Tree t) {
    tf = t.treeFactory();
    return transformTreeHelper(t, t);
  }

  public Tree transformTreeHelper(Tree t, Tree root) {
    Tree result;
    Tree parent;
    Tree grandParent;
    String parentStr;
    String grandParentStr;
    if (root == null || t.equals(root)) {
      parent = null;
      parentStr = "";
    } else {
      parent = t.parent(root);
      parentStr = parent.label().value();
    }
    if (parent == null || parent.equals(root)) {
      grandParent = null;
      grandParentStr = "";
    } else {
      grandParent = parent.parent(root);
      grandParentStr = grandParent.label().value();
    }
    String cat = t.label().value();
    String baseParentStr = tlpParams.treebankLanguagePack().basicCategory(parentStr);
    String baseGrandParentStr = tlpParams.treebankLanguagePack().basicCategory(grandParentStr);
    if (t.isLeaf()) {
      return tf.newLeaf(new Word(t.label().value()));
    }
    String word = t.headTerminal(hf).value();
    if (t.isPreTerminal()) {
      nonTerms.incrementCount(t.label().value());
    } else {
      nonTerms.incrementCount(t.label().value());
      if (trainOptions.postPA && !trainOptions.smoothing && !baseParentStr.isEmpty()) {
        String cat2;
          cat2 = trainOptions.postSplitWithBaseCategory ? cat + '^' + baseParentStr : cat + '^' + parentStr;
        if (!trainOptions.selectivePostSplit || trainOptions.postSplitters.contains(cat2)) {
          cat = cat2;
        }
      }
      if (trainOptions.postGPA && !trainOptions.smoothing && !grandParentStr.isEmpty()) {
        String cat2;
          cat2 = trainOptions.postSplitWithBaseCategory ? cat + '~' + baseGrandParentStr : cat + '~' + grandParentStr;
        if (trainOptions.selectivePostSplit) {
          if (cat.contains("^") && trainOptions.postSplitters.contains(cat2)) {
            cat = cat2;
          }
        } else {
          cat = cat2;
        }
      }
    }
    result = tf.newTreeNode(new CategoryWordTag(cat, word, cat), Collections.<Tree>emptyList());
    ArrayList<Tree> newKids = new ArrayList<>();
    Tree[] kids = t.children();
    for (Tree kid : kids) {
      newKids.add(transformTreeHelper(kid, root));
    }
    result.setChildren(newKids);
    return result;
  }

  public void dumpStats() {
    System.out.println("%% Counts of nonterminals:");
    List<String> biggestCounts = new ArrayList<>(nonTerms.keySet());
    Collections.sort(biggestCounts, Counters.toComparatorDescending(nonTerms));
    for (String str : biggestCounts) {
      System.out.println(str + ": " + nonTerms.get(str));
    }
  }

  public PostSplitter(TreebankLangParserParams tlpParams, Options op) {
    this.tlpParams = tlpParams;
    this.hf = tlpParams.headFinder();
    this.trainOptions = op.trainOptions;
  }

} // end class PostSplitter

