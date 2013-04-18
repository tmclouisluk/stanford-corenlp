package edu.stanford.nlp.trees;

import java.util.regex.Pattern;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.util.Filter;

/**
 * A location for general implementations of Filter&lt;Tree&gt;.  For
 * example, we provide a tree which filters trees so they are only
 * accepted if it has a child with a label that matches a particular
 * regex.
 *
 * @author John Bauer
 */
public class TreeFilters {
  public static class HasMatchingChild implements Filter<Tree> {
    TreebankLanguagePack tlp;

    Pattern pattern;

    public HasMatchingChild(TreebankLanguagePack tlp, String regex) {
      this.pattern = Pattern.compile(regex);
      this.tlp = tlp;
    }

    public boolean accept(Tree tree) {
      if (tree == null) {
        return false;
      }
      for (Tree child : tree.children()) {
        Label label = child.label();
        String value = label == null ? null : label.value();
        if (value == null) {
          continue;
        }
        if (pattern.matcher(value).matches()) {
          return true;
        }
        String basic = tlp.basicCategory(value);
        if (pattern.matcher(basic).matches()) {
          return true;
        }
      }
      return false;
    }

    private static final long serialVersionUID = 1L;    
  }

  private TreeFilters() {}
}
