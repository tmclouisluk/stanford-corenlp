package edu.stanford.nlp.pipeline;

import ca.gedge.radixtree.RadixTree;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.ArrayMap;
import edu.stanford.nlp.util.CoreMap;
import javolution.text.TextBuilder;
import javolution.util.FastMap;

import java.util.*;

/**
* Functions for aggregating token attributes
*
* @author Angel Chang
*/
public abstract class CoreMapAttributeAggregator
{
  public static Map<Class, CoreMapAttributeAggregator> getDefaultAggregators()
  {
    return DEFAULT_AGGREGATORS;
  }

    abstract public Object aggregate(Class key, List<? extends CoreMap> in);

  public final static CoreMapAttributeAggregator FIRST_NON_NIL = new CoreMapAttributeAggregator() {
      public Object aggregate(Class key, List<? extends CoreMap> in) {
        if (in == null) return null;
        for (CoreMap cm:in) {
          Object obj = cm.get(key);
          if (obj != null) {
            return obj;
          }
        }
        return null;
      }
    };

  public final static CoreMapAttributeAggregator FIRST = new CoreMapAttributeAggregator() {
      public Object aggregate(Class key, List<? extends CoreMap> in) {
        if (in == null) return null;
        for (CoreMap cm:in) {
          Object obj = cm.get(key);
          return obj;
        }
        return null;
      }
    };

  public final static CoreMapAttributeAggregator LAST_NON_NIL = new CoreMapAttributeAggregator() {
      public Object aggregate(Class key, List<? extends CoreMap> in) {
        if (in == null) return null;
        for (int i = in.size()-1; i >= 0; i--) {
          CoreMap cm = in.get(i);
          Object obj = cm.get(key);
          if (obj != null) {
            return obj;
          }
        }
        return null;
      }
    };

  public final static CoreMapAttributeAggregator LAST = new CoreMapAttributeAggregator() {
      public Object aggregate(Class key, List<? extends CoreMap> in) {
        if (in == null) return null;
        for (int i = in.size()-1; i >= 0; i--) {
          CoreMap cm = in.get(i);
          return cm.get(key);
        }
        return null;
      }
    };

  public final static class ConcatListAggregator<T> extends CoreMapAttributeAggregator {
      public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      List<T> res = new ArrayList<>();
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null) {
          if (obj instanceof List) {
            res.addAll( (List<T>) obj);
          }
        }
      }
      return res;
    }
  }
  public final static class ConcatCoreMapListAggregator<T extends CoreMap> extends CoreMapAttributeAggregator {
    boolean concatSelf;
    public ConcatCoreMapListAggregator()
    {
    }
    public ConcatCoreMapListAggregator(boolean concatSelf)
    {
      this.concatSelf = concatSelf;
    }
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      List<T> res = new ArrayList<>();
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        boolean added = false;
        if (obj != null) {
          if (obj instanceof List) {
            res.addAll( (List<T>) obj);
            added = true;
          }
        }
        if (!added && concatSelf) {
          res.add((T) cm);
        }
      }
      return res;
    }
  }
  public final static ConcatCoreMapListAggregator<CoreLabel> CONCAT_TOKENS = new ConcatCoreMapListAggregator<>(true);
  public final static ConcatCoreMapListAggregator<CoreMap> CONCAT_COREMAP = new ConcatCoreMapListAggregator<>(true);


  public final static class ConcatAggregator extends CoreMapAttributeAggregator {
    String delimiter;
    public ConcatAggregator(String delimiter)
    {
      this.delimiter = delimiter;
    }
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      TextBuilder sb = new TextBuilder();
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null) {
          if (sb.length() > 0) {
            sb.append(delimiter);
          }
          sb.append(obj);
        }
      }
      return sb.toString();
    }
  }
  public final static CoreMapAttributeAggregator CONCAT = new ConcatAggregator(" ");
  public final static CoreMapAttributeAggregator COUNT = new CoreMapAttributeAggregator() {
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      return in.size();
    }
  };
  public final static CoreMapAttributeAggregator SUM = new CoreMapAttributeAggregator() {
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      double sum = 0;
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null) {
          if (obj instanceof Number) {
            sum += ((Number) obj).doubleValue();
          } else if (obj instanceof String) {
            sum += Double.parseDouble((String) obj);
          } else {
            throw new RuntimeException("Cannot sum attribute " + key + ", object of type: " + obj.getClass());
          }
        }
      }
      return sum;
    }
  };
  public final static CoreMapAttributeAggregator MIN = new CoreMapAttributeAggregator() {
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      Comparable min = null;
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null) {
          if (obj instanceof Comparable) {
            Comparable c = (Comparable) obj;
            if (min == null) {
              min = c;
            } else if (c.compareTo(min) < 0) {
              min = c;
            }
          } else {
            throw new RuntimeException("Cannot get min of attribute " + key + ", object of type: " + obj.getClass());
          }
        }
      }
      return min;
    }
  };
  public final static CoreMapAttributeAggregator MAX = new CoreMapAttributeAggregator() {
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      Comparable max = null;
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null) {
          if (obj instanceof Comparable) {
            Comparable c = (Comparable) obj;
            if (max == null) {
              max = c;
            } else if (c.compareTo(max) > 0) {
              max = c;
            }
          } else {
            throw new RuntimeException("Cannot get max of attribute " + key + ", object of type: " + obj.getClass());
          }
        }
      }
      return max;
    }
  };

  public final static class MostFreqAggregator extends CoreMapAttributeAggregator {
    Set<Object> ignoreSet;
    public MostFreqAggregator()
    {
    }

    public MostFreqAggregator(Set<Object> set)
    {
      ignoreSet = set;
    }

    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      IntCounter<Object> counter = new IntCounter<>();
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null && (ignoreSet == null || !ignoreSet.contains(obj))) {
          counter.incrementCount(obj);
        }
      }
        return !counter.isEmpty() ? counter.argmax() : null;
    }
  }
  public final static CoreMapAttributeAggregator MOST_FREQ = new MostFreqAggregator();

  private static final RadixTree< CoreMapAttributeAggregator> AGGREGATOR_LOOKUP = new RadixTree<>();

    static {
    AGGREGATOR_LOOKUP.put("FIRST", FIRST);
    AGGREGATOR_LOOKUP.put("FIRST_NON_NIL", FIRST_NON_NIL);
    AGGREGATOR_LOOKUP.put("LAST", LAST);
    AGGREGATOR_LOOKUP.put("LAST_NON_NIL", LAST_NON_NIL);
    AGGREGATOR_LOOKUP.put("MIN", MIN);
    AGGREGATOR_LOOKUP.put("MAX", MAX);
    AGGREGATOR_LOOKUP.put("COUNT", COUNT);
    AGGREGATOR_LOOKUP.put("SUM", SUM);
    AGGREGATOR_LOOKUP.put("CONCAT", CONCAT);
    AGGREGATOR_LOOKUP.put("CONCAT_TOKENS", CONCAT_TOKENS);
    AGGREGATOR_LOOKUP.put("MOST_FREQ", MOST_FREQ);
  }

  public static final Map<Class, CoreMapAttributeAggregator> DEFAULT_AGGREGATORS;
  public static final Map<Class, CoreMapAttributeAggregator> DEFAULT_NUMERIC_AGGREGATORS;
  public static final Map<Class, CoreMapAttributeAggregator> DEFAULT_NUMERIC_TOKENS_AGGREGATORS;

  static {
    Map<Class, CoreMapAttributeAggregator> defaultAggr = new ArrayMap<>();
    defaultAggr.put(CoreAnnotations.TextAnnotation.class, CoreMapAttributeAggregator.CONCAT);
    defaultAggr.put(CoreAnnotations.CharacterOffsetBeginAnnotation.class, CoreMapAttributeAggregator.FIRST);
    defaultAggr.put(CoreAnnotations.CharacterOffsetEndAnnotation.class, CoreMapAttributeAggregator.LAST);
    defaultAggr.put(CoreAnnotations.TokenBeginAnnotation.class, CoreMapAttributeAggregator.FIRST);
    defaultAggr.put(CoreAnnotations.TokenEndAnnotation.class, CoreMapAttributeAggregator.LAST);
    defaultAggr.put(CoreAnnotations.TokensAnnotation.class, CoreMapAttributeAggregator.CONCAT_TOKENS);
    DEFAULT_AGGREGATORS = Collections.unmodifiableMap(defaultAggr);

    Map<Class, CoreMapAttributeAggregator> defaultNumericAggr = new ArrayMap<>(DEFAULT_AGGREGATORS);
    defaultNumericAggr.put(CoreAnnotations.NumericCompositeTypeAnnotation.class, CoreMapAttributeAggregator.FIRST_NON_NIL);
    defaultNumericAggr.put(CoreAnnotations.NumericCompositeValueAnnotation.class, CoreMapAttributeAggregator.FIRST_NON_NIL);
    defaultNumericAggr.put(CoreAnnotations.NamedEntityTagAnnotation.class, CoreMapAttributeAggregator.FIRST_NON_NIL);
    defaultNumericAggr.put(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, CoreMapAttributeAggregator.FIRST_NON_NIL);
    DEFAULT_NUMERIC_AGGREGATORS = Collections.unmodifiableMap(defaultNumericAggr);

    Map<Class, CoreMapAttributeAggregator> defaultNumericTokensAggr = new ArrayMap<>(DEFAULT_NUMERIC_AGGREGATORS);
    defaultNumericTokensAggr.put(CoreAnnotations.NumerizedTokensAnnotation.class, CoreMapAttributeAggregator.CONCAT_COREMAP);
    DEFAULT_NUMERIC_TOKENS_AGGREGATORS = Collections.unmodifiableMap(defaultNumericTokensAggr);
  }


}
