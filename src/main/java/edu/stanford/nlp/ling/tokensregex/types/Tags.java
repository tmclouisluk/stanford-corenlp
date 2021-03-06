package edu.stanford.nlp.ling.tokensregex.types;

import ca.gedge.radixtree.RadixTree;
import edu.stanford.nlp.ling.CoreAnnotation;
import javolution.util.FastMap;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * Tags that can be added to values or annotations
 */
public class Tags implements Serializable {
  public static class TagsAnnotation implements CoreAnnotation<Tags> {
    public Class<Tags> getType() {
      return Tags.class;
    }
  }

  RadixTree< Value> tags;

  public Tags(String... tags) {
    if (tags != null) {
        this.tags = new RadixTree<>();
      for (String tag:tags) {
        this.tags.put(tag, null);
      }
    }
  }

  public Collection<String> getTags() {
    return tags.keySet();
  }

  public boolean hasTag(String tag) {
    return tags != null && tags.containsKey(tag);
  }

  public void addTag(String tag) {
    addTag(tag, null);
  }

  public void addTag(String tag, Value v) {
    if (tags == null) {
        tags = new RadixTree<>(); }
    tags.put(tag, v);
  }

  public void removeTag(String tag) {
    if (tags != null) { tags.remove(tag); }
  }

  public Value getTag(String tag) {
    return tags != null ? tags.get(tag): null;
  }
  
  private static final long serialVersionUID = 2;
}
