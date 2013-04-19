package edu.stanford.nlp.pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import ca.gedge.radixtree.RadixTree;
import edu.stanford.nlp.ie.crf.CRFBiasedClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.CoreMap;
import javolution.util.FastMap;

public class TrueCaseAnnotator implements Annotator {

    private static final Pattern COMPILE = Pattern.compile("\\s+");
    private CRFBiasedClassifier trueCaser;
  
  private RadixTree<String> mixedCaseMap = new RadixTree<>();

    private boolean VERBOSE = true;
  
  public static final String DEFAULT_MODEL_BIAS = "INIT_UPPER:-0.7,UPPER:-0.7,O:0";
  
  public TrueCaseAnnotator() {
    this(true);
  }

  public TrueCaseAnnotator(boolean verbose) {
    this(System.getProperty("truecase.model", DefaultPaths.DEFAULT_TRUECASE_MODEL), 
        System.getProperty("truecase.bias", DEFAULT_MODEL_BIAS),
        System.getProperty("truecase.mixedcasefile", DefaultPaths.DEFAULT_TRUECASE_DISAMBIGUATION_LIST),
        verbose);
  }

  public TrueCaseAnnotator(String modelLoc,
      String classBias,
      String mixedCaseFileName,
      boolean verbose){
    this.VERBOSE = verbose;
    
    Properties props = new Properties();
    props.setProperty("loadClassifier", modelLoc);
    props.setProperty("mixedCaseMapFile", mixedCaseFileName);
    props.setProperty("classBias", classBias);
    trueCaser = new CRFBiasedClassifier(props);
    
    if (modelLoc != null) {
      trueCaser.loadClassifierNoExceptions(modelLoc, props);
    } else {
      throw new RuntimeException("Model location not specified for true-case classifier!");
    }
    
    if(classBias != null) {
      StringTokenizer biases = new StringTokenizer(classBias,",");
      while (biases.hasMoreTokens()) {
        StringTokenizer bias = new StringTokenizer(biases.nextToken(),":");
        String cname = bias.nextToken();
        double w = Double.parseDouble(bias.nextToken());
        trueCaser.setBiasWeight(cname,w);
        if(VERBOSE) System.err.println("Setting bias for class "+cname+" to "+w);
      }
    }
    
    // Load map containing mixed-case words:
    mixedCaseMap = loadMixedCaseMap(mixedCaseFileName);
  }

  @SuppressWarnings("unchecked")
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      System.err.print("Adding true-case annotation...");
    }
    
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      // classify tokens for each sentence 
      for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        List<CoreLabel> output = this.trueCaser.classifySentence(tokens);
        for (int i = 0; i < tokens.size(); ++i) {
          
          // add the named entity tag to each token
          String neTag = output.get(i).get(CoreAnnotations.AnswerAnnotation.class);
          tokens.get(i).set(CoreAnnotations.TrueCaseAnnotation.class, neTag);
          setTrueCaseText(tokens.get(i));
        }
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }
  
  private void setTrueCaseText(CoreLabel l) {
    String trueCase = l.getString(CoreAnnotations.TrueCaseAnnotation.class);
    String text = l.word();
    String trueCaseText = text;

      switch (trueCase) {
          case "UPPER":
              trueCaseText = text.toUpperCase();
              break;
          case "LOWER":
              trueCaseText = text.toLowerCase();
              break;
          case "INIT_UPPER":
              trueCaseText = text.substring(0, 1).toUpperCase() + text.substring(1);
              break;
          case "O":
              // The model predicted mixed case, so lookup the map:
              if (mixedCaseMap.containsKey(text))
                  trueCaseText = mixedCaseMap.get(text);
              break;
      }
    
    l.set(CoreAnnotations.TrueCaseTextAnnotation.class, trueCaseText);
  }
  
  public static RadixTree<String> loadMixedCaseMap(String mapFile) {
      RadixTree<String> map = new RadixTree<>();
    try {
      InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(mapFile);
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      for(String line : ObjectBank.getLineIterator(br)) {
        line = line.trim();
        String[] els = COMPILE.split(line);
        if(els.length != 2) 
          throw new RuntimeException("Wrong format: "+mapFile);
        map.put(els[0],els[1]);
      }
      br.close();
      is.close();
    } catch(IOException e){
      throw new RuntimeException(e);
    }
    return map;
  }

  @Override
  public Set<Requirement> requires() {
    return TOKENIZE_SSPLIT_POS_LEMMA;
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(TRUECASE_REQUIREMENT);
  }
}
