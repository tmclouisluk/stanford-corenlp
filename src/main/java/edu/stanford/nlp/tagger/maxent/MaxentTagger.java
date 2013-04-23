//MaxentTagger -- StanfordMaxEnt, A Maximum Entropy Toolkit
//Copyright (c) 2002-2010 Leland Stanford Junior University


//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

//For more information, bug reports, fixes, contact:
//Christopher Manning
//Dept of Computer Science, Gates 1A
//Stanford CA 94305-9010
//USA
//Support/Questions: java-nlp-user@lists.stanford.edu
//Licensing: java-nlp-support@lists.stanford.edu
//http://www-nlp.stanford.edu/software/tagger.shtml


package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.OutDataStreamFile;
import edu.stanford.nlp.io.PrintFile;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.maxent.CGRunner;
import edu.stanford.nlp.maxent.Problem;
import edu.stanford.nlp.maxent.iis.LambdaSolve;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.ReaderIteratorFactory;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.*;
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory;
import edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter;
import edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter.OutputStyle;
import edu.stanford.nlp.tagger.io.TaggedFileRecord;
import edu.stanford.nlp.util.DataFilePaths;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.XMLUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.TextBuilder;
import javolution.util.FastMap;
import javolution.util.FastTable;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.text.DecimalFormat;


/**
 * The main class for users to run, train, and test the part of speech tagger.
 *
 * You can tag things through the Java API or from the command line.
 * The two English taggers included in this distribution are:
 * <ul>
 * <li> A bi-directional dependency network tagger in models/bidirectional-distsim-wsj-0-18.tagger.
 *      Its accuracy was 97.32% on Penn Treebank WSJ secs. 22-24.</li>
 * <li> A model using only left sequence information and similar but less
 *      unknown words and lexical features as the previous model in
 *      models/left3words-wsj-0-18.tagger. This tagger runs a lot faster.
 *      Its accuracy was 96.92% on Penn Treebank WSJ secs. 22-24.</li>
 * </ul>
 *
 * <h3>Using the Java API</h3>
 * <dl>
 * <dt>
 * A MaxentTagger can be made with a constructor taking as argument the location of parameter files for a trained tagger: </dt>
 * <dd> {@code MaxentTagger tagger = new MaxentTagger("models/left3words-wsj-0-18.tagger");}</dd>
 * <p>
 * <dt>A default path is provided for the location of the tagger on the Stanford NLP machines:</dt>
 * <dd>{@code MaxentTagger tagger = new MaxentTagger(DEFAULT_NLP_GROUP_MODEL_PATH); }</dd>
 * <p>
 * <dt>If you set the NLP_DATA_HOME environment variable,
 * DEFAULT_NLP_GROUP_MODEL_PATH will instead point to the directory
 * given in NLP_DATA_HOME.</dt>
 * <p>
 * <dt>To tag a List of HasWord and get a List of TaggedWord, you can use one of: </dt>
 * <dd>{@code List&lt;TaggedWord&gt; taggedSentence = tagger.tagSentence(List&lt;? extends HasWord&gt; sentence)}</dd>
 * <dd>{@code List&lt;TaggedWord&gt; taggedSentence = tagger.apply(List&lt;? extends HasWord&gt; sentence)}</dd>
 * <p>
 * <dt>To tag a list of sentences and get back a list of tagged sentences:
 * <dd>{@code List taggedList = tagger.process(List sentences)}</dd>
 * <p>
 * <dt>To tag a String of text and to get back a String with tagged words:</dt>
 * <dd> {@code String taggedString = tagger.tagString("Here's a tagged string.")}</dd>
 * <p>
 * <dt>To tag a string of <i>correctly tokenized</i>, whitespace-separated words and get a string of tagged words back:</dt>
 * <dd> {@code String taggedString = tagger.tagTokenizedString("Here 's a tagged string .")}</dd>
 * </dl>
 * <p>
 * The {@code tagString} method uses the default tokenizer (PTBTokenizer).
 * If you wish to control tokenization, you may wish to call
 * {@link #tokenizeText(Reader, TokenizerFactory)} and then to call
 * {@code process()} on the result.
 * </p>
 *
 * <h3>Using the command line</h3>
 *
 * Tagging, testing, and training can all also be done via the command line.
 * <h3>Training from the command line</h3>
 * To train a model from the command line, first generate a property file:
 * <pre>java edu.stanford.nlp.tagger.maxent.MaxentTagger -genprops </pre>
 *
 * This gets you a default properties file with descriptions of each parameter you can set in
 * your trained model.  You can modify the properties file , or use the default options.  To train, run:
 * <pre>java -mx1g edu.stanford.nlp.tagger.maxent.MaxentTagger -props myPropertiesFile.props </pre>
 *
 *  with the appropriate properties file specified; any argument you give in the properties file can also
 *  be specified on the command line.  You must have specified a model using -model, either in the properties file
 *  or on the command line, as well as a file containing tagged words using -trainFile.
 *
 * Useful flags for controlling the amount of output are -verbose, which prints extra debugging information,
 * and -verboseResults, which prints full information about intermediate results.  -verbose defaults to false
 * and -verboseResults defaults to true.
 *
 * <h3>Tagging and Testing from the command line</h3>
 *
 * Usage:
 * For tagging (plain text):
 * <pre>java edu.stanford.nlp.tagger.maxent.MaxentTagger -model &lt;modelFile&gt; -textFile &lt;textfile&gt; </pre>
 * For testing (evaluating against tagged text):
 * <pre>java edu.stanford.nlp.tagger.maxent.MaxentTagger -model &lt;modelFile&gt; -testFile &lt;testfile&gt; </pre>
 * You can use the same properties file as for training
 * if you pass it in with the "-props" argument. The most important
 * arguments for tagging (besides "model" and "file") are "tokenize"
 * and "tokenizerFactory". See below for more details.
 * <br>
 * Note that the tagger assumes input has not yet been tokenized and
 * by default tokenizes it using a default English tokenizer.  If your
 * input has already been tokenized, use the flag "-tokenize false".
 *
 * <p> Parameters can be defined using a Properties file
 * (specified on the command-line with {@code -prop} <i>propFile</i>),
 * or directly on the command line (by preceding their name with a minus sign
 * ("-") to turn them into a flag. The following properties are recognized:
 * </p>
 * <table border="1">
 * <tr><td><b>Property Name</b></td><td><b>Type</b></td><td><b>Default Value</b></td><td><b>Relevant Phase(s)</b></td><td><b>Description</b></td></tr>
 * <tr><td>model</td><td>String</td><td>N/A</td><td>All</td><td>Path and filename where you would like to save the model (training) or where the model should be loaded from (testing, tagging).</td></tr>
 * <tr><td>trainFile</td><td>String</td><td>N/A</td><td>Train</td>
 <td>
 Path to the file holding the training data; specifying this option puts the tagger in training mode.  Only one of 'trainFile','testFile','textFile', and 'convertToSingleFile' may be specified.<br>
 There are three formats possible.  The first is a text file of tagged data, Each line is considered a separate sentence.  In each sentence, words are separated by whitespace.  Each word must have a tag, which is separated using the specified tagSeparator.  This format is the default format.<br>
 Another possible format is a file of Penn Treebank formatted tree files.  Trees are loaded one at a time and the tagged words in the tree are used as the training sentence.  To specify this format, preface the filename with "{@code format=TREES,}".  <br>
 The final possible format is TSV files.  To specify a TSV file, set trainFile to "{@code format=TSV,wordColumn=x,tagColumn=y,filename}".  Column numbers are indexed at 0, and sentences are separated with blank lines.
 <br>
 A file can be a different encoding than the tagger's default encoding by prefacing the filename with "encoding=ENC".<br>
 Tree files can be fed through TreeTransformers and TreeNormalizers.  To specify a transformer, preface the filename with "treeTransformer=CLASSNAME".  To specify a normalizer, preface the filename with "treeNormalizer=CLASSNAME".  You can also filter trees using a Filter&lt;Tree&gt;, which can be specified with "treeFilter=CLASSNAME".  A specific range of trees to be used can be specified with treeRange=X-Y.  Multiple parts of the range can be separated by : as opposed to the normal separator of ,  For example, one could use the argument "-treeRange=25-50:75-100". <br>
 Multiple files can be specified by making a semicolon separated list of files.  Each file can have its own format specifiers as above.<br>
 You will note that none of , ; or = can be in filenames.
 </td>
 </tr>
 * <tr><td>testFile</td><td>String</td><td>N/A</td><td>Test</td><td>Path to the file holding the test data; specifying this option puts the tagger in testing mode.  Only one of 'trainFile','testFile','textFile', and 'convertToSingleFile' may be specified.  The same format as trainFile applies, but only one file can be specified.</td></tr>
 * <tr><td>textFile</td><td>String</td><td>N/A</td><td>Tag</td><td>Path to the file holding the text to tag; specifying this option puts the tagger in tagging mode.  Only one of 'trainFile','testFile','textFile', and 'convertToSingleFile' may be specified.  No file reading options may be specified for textFile</td></tr>
 * <tr><td>convertToSingleFile</td><td>String</td><td>N/A</td><td>N/A</td><td>Provided only for backwards compatibility, this option allows you to convert a tagger trained using a previous version of the tagger to the new single-file format.  The value of this flag should be the path for the new model file, 'model' should be the path prefix to the old tagger (up to but not including the ".holder"), and you should supply the properties configuration for the old tagger with -props (before these two arguments).</td></tr>
 * <tr><td>genprops</td><td>boolean</td><td>N/A</td><td>N/A</td><td>Use this option to output a default properties file, containing information about each of the possible configuration options.</td></tr>
 * <tr><td>tagSeparator</td><td>char</td><td>/</td><td>All</td><td>Separator character that separates word and part of speech tags, such as out/IN or out_IN.  For training and testing, this is the separator used in the train/test files.  For tagging, this is the character that will be inserted between words and tags in the output.</td></tr>
 * <tr><td>encoding</td><td>String</td><td>UTF-8</td><td>All</td><td>Encoding of the read files (training, testing) and the output text files.</td></tr>
 * <tr><td>tokenize</td><td>boolean</td><td>true</td><td>Tag,Test</td><td>Whether or not the file needs to be tokenized.  If this is false, the tagger assumes that white space separates words if and only if they should be tagged as separate tokens, and that the input is strictly one sentence per line.</td></tr>
 * <tr><td>tokenizerFactory</td><td>String</td><td>edu.stanford.nlp.<br>process.PTBTokenizer</td><td>Tag,Test</td><td>Fully qualified class name of the tokenizer to use.  edu.stanford.nlp.process.PTBTokenizer does basic English tokenization.</td></tr>
 * <tr><td>tokenizerOptions</td><td>String</td><td></td><td>Tag,Test</td><td>Known options for the particular tokenizer used. A comma-separated list. For PTBTokenizer, options of interest include {@code americanize=false} and {@code asciiQuotes} (for German). Note that any choice of tokenizer options that conflicts with the tokenization used in the tagger training data will likely degrade tagger performance.</td></tr>
 * <tr><td>arch</td><td>String</td><td>generic</td><td>Train</td><td>Architecture of the model, as a comma-separated list of options, some with a parenthesized integer argument written k here: this determines what features are used to build your model.  See {@link ExtractorFrames} and {@link ExtractorFramesRare} for more information.</td></tr>
 * <tr><td>wordFunction</td><td>String</td><td>(none)</td><td>Train</td><td>A function to apply to the text before training or testing.  Must inherit from edu.stanford.nlp.util.Function&lt;String, String&gt;.  Can be blank.</td></tr>
 * <tr><td>lang</td><td>String</td><td>english</td><td>Train</td><td>Language from which the part of speech tags are drawn. This option determines which tags are considered closed-class (only fixed set of words can be tagged with a closed-class tag, such as prepositions). Defined languages are 'english' (Penn tagset), 'polish' (very rudimentary), 'chinese', 'arabic', 'german', and 'medline'.  </td></tr>
 * <tr><td>openClassTags</td><td>String</td><td>N/A</td><td>Train</td><td>Space separated list of tags that should be considered open-class.  All tags encountered that are not in this list are considered closed-class.  E.g. format: "NN VB"</td></tr>
 * <tr><td>closedClassTags</td><td>String</td><td>N/A</td><td>Train</td><td>Space separated list of tags that should be considered closed-class.  All tags encountered that are not in this list are considered open-class.</td></tr>
 * <tr><td>learnClosedClassTags</td><td>boolean</td><td>false</td><td>Train</td><td>If true, induce which tags are closed-class by counting as closed-class tags all those tags which have fewer unique word tokens than closedClassTagThreshold. </td></tr>
 * <tr><td>closedClassTagThreshold</td><td>int</td><td>int</td><td>Train</td><td>Number of unique word tokens that a tag may have and still be considered closed-class; relevant only if learnClosedClassTags is true.</td></tr>
 * <tr><td>sgml</td><td>boolean</td><td>false</td><td>Tag, Test</td><td>Very basic tagging of the contents of all sgml fields; for more complex mark-up, consider using the xmlInput option.</td></tr>
 * <tr><td>xmlInput</td><td>String</td><td></td><td>Tag, Test</td><td>Give a space separated list of tags in an XML file whose content you would like tagged.  Any internal tags that appear in the content of fields you would like tagged will be discarded; the rest of the XML will be preserved and the original text of specified fields will be replaced with the tagged text.</td></tr>
 * <tr><td>outputFile</td><td>String</td><td>""</td><td>Tag</td><td>Path to write output to.  If blank, stdout is used.</td></tr>
 * <tr><td>outputFormat</td><td>String</td><td>""</td><td>Tag</td><td>Output format. One of: slashTags (default), xml, or tsv</td></tr>
 * <tr><td>outputFormatOptions</td><td>String</td><td>""</td><td>Tag</td><td>Output format options.</td></tr>
 * <tr><td>tagInside</td><td>String</td><td>""</td><td>Tag</td><td>Tags inside elements that match the regular expression given in the String.</td></tr>
 * <tr><td>search</td><td>String</td><td>cg</td><td>Train</td><td>Specify the search method to be used in the optimization method for training.  Options are 'cg' (conjugate gradient), 'iis' (improved iterative scaling), or 'qn' (quasi-newton).</td></tr>
 * <tr><td>sigmaSquared</td><td>double</td><td>0.5</td><td>Train</td><td>Sigma-squared smoothing/regularization parameter to be used for conjugate gradient search.  Default usually works reasonably well.</td></tr>
 * <tr><td>iterations</td><td>int</td><td>100</td><td>Train</td><td>Number of iterations to be used for improved iterative scaling.</td></tr>
 * <tr><td>rareWordThresh</td><td>int</td><td>5</td><td>Train</td><td>Words that appear fewer than this number of times during training are considered rare words and use extra rare word features.</td></tr>
 * <tr><td>minFeatureThreshold</td><td>int</td><td>5</td><td>Train</td><td>Features whose history appears fewer than this number of times are discarded.</td></tr>
 * <tr><td>curWordMinFeatureThreshold</td><td>int</td><td>2</td><td>Train</td><td>Words that occur more than this number of times will generate features with all of the tags they've been seen with.</td></tr>
 * <tr><td>rareWordMinFeatureThresh</td><td>int</td><td>10</td><td>Train</td><td>Features of rare words whose histories occur fewer than this number of times are discarded.</td></tr>
 * <tr><td>veryCommonWordThresh</td><td>int</td><td>250</td><td>Train</td><td>Words that occur more than this number of times form an equivalence class by themselves.  Ignored unless you are using ambiguity classes.</td></tr>
 * <tr><td>debug</td><td>boolean</td><td>boolean</td><td>All</td><td>Whether to write debugging information (words, top words, unknown words).  Useful for error analysis.</td></tr>
 * <tr><td>debugPrefix</td><td>String</td><td>N/A</td><td>All</td><td>File (path) prefix for where to write out the debugging information (relevant only if debug=true).</td></tr>
 * <tr><td>nthreads</td><td>int</td><td>1</td><td>Test,Text</td><td>Number of threads to use when processing text.</td></tr>
 * </table>
 * <p/>
 *
 * @author Kristina Toutanova
 * @author Miler Lee
 * @author Joseph Smarr
 * @author Anna Rafferty
 * @author Michel Galley
 * @author Christopher Manning
 * @author John Bauer
 */
public class MaxentTagger implements Function<List<? extends HasWord>,List<TaggedWord>>, ListProcessor<List<? extends HasWord>,List<TaggedWord>>, Serializable {

  /**
   * The directory from which to get taggers when using
   * DEFAULT_NLP_GROUP_MODEL_PATH.  Normally set to the location of
   * the latest left3words tagger on the NLP machines, but can be
   * changed by setting the environment variable NLP_DATA_HOME.
   */
  public static final String BASE_TAGGER_HOME =
    "$NLP_DATA_HOME/data/pos-tagger/distrib";
  public static final String TAGGER_HOME =
    DataFilePaths.convert(BASE_TAGGER_HOME);

  public static final String DEFAULT_NLP_GROUP_MODEL_PATH =
    new File(TAGGER_HOME, "english-left3words-distsim.tagger").getPath();
  public static final String DEFAULT_JAR_PATH =
    "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";
  public static final String DEFAULT_DISTRIBUTION_PATH =
    "models/english-left3words-distsim.tagger";


  public MaxentTagger() {
  }

  /**
   * Constructor for a tagger using a model stored in a particular file.
   * The {@code modelFile} is a filename for the model data.
   * The tagger data is loaded when the
   * constructor is called (this can be slow).
   * This constructor first constructs a TaggerConfig object, which loads
   * the tagger options from the modelFile.
   *
   * @param modelFile filename of the trained model
   * @throws RuntimeIOException if I/O errors or serialization errors
   */
  public MaxentTagger(String modelFile) {
    this(modelFile, StringUtils.argsToProperties("-model", modelFile), true);
  }

  /**
   * Constructor for a tagger using a model stored in a particular file,
   * with options taken from the supplied TaggerConfig.
   * The {@code modelFile} is a filename for the model data.
   * The tagger data is loaded when the
   * constructor is called (this can be slow).
   * This version assumes that the tagger options in the modelFile have
   * already been loaded into the TaggerConfig (if that is desired).
   *
   * @param modelFile filename of the trained model
   * @param config The configuration for the tagger
   * @throws RuntimeIOException if I/O errors or serialization errors
   */
  public MaxentTagger(String modelFile, Properties config) {
    this(modelFile, config, true);
  }

  /**
   * Initializer that loads the tagger.
   *
   * @param modelFile Where to initialize the tagger from.
   *        Most commonly, this is the filename of the trained model,
   *        for example, {@code
   *   /u/nlp/data/pos-tagger/wsj3t0-18-left3words/left3words-wsj-0-18.tagger
   *        }.  However, if it starts with "https?://" it will
   *        be interpreted as a URL.  One can also load models
   *        directly from the classpath, as in loading from
   *        edu/stanford/nlp/models/pos-tagger/wsj3t0-18-bidirectional/bidirectional-distsim-wsj-0-18.tagger
   * @param config TaggerConfig based on command-line arguments
   * @param printLoading Whether to print a message saying what model file is being loaded and how long it took when finished.
   * @throws RuntimeIOException if I/O errors or serialization errors
   */
  public MaxentTagger(String modelFile, Properties config, boolean printLoading) {
    readModelAndInit(config, modelFile, printLoading);
  }


  Dictionary dict = new Dictionary();
  TTags tags;

  byte[][] fnumArr; // TODO: move this into TaggerExperiments. It could be a private method of that class with an accessor
  LambdaSolveTagger prob;
  // For each extractor index, we have a map from possible extracted
  // feature to an array which maps from tag number to feature index.
  List <Map<CharSequence,int[]>>fAssociations = new ArrayList<>();
  //PairsHolder pairs = new PairsHolder();
  Extractors extractors;
  Extractors extractorsRare;
  AmbiguityClasses ambClasses;
  static final boolean alltags = false;
  final Map<String, Set<String>> tagTokens = new FastMap<>();

    static final int RARE_WORD_THRESH = Integer.valueOf(TaggerConfig.RARE_WORD_THRESH);
  static final int MIN_FEATURE_THRESH = Integer.valueOf(TaggerConfig.MIN_FEATURE_THRESH);
  static final int CUR_WORD_MIN_FEATURE_THRESH = Integer.valueOf(TaggerConfig.CUR_WORD_MIN_FEATURE_THRESH);
  static final int RARE_WORD_MIN_FEATURE_THRESH = Integer.valueOf(TaggerConfig.RARE_WORD_MIN_FEATURE_THRESH);
  static final int VERY_COMMON_WORD_THRESH = Integer.valueOf(TaggerConfig.VERY_COMMON_WORD_THRESH);

  static final boolean OCCURRING_TAGS_ONLY = Boolean.valueOf(TaggerConfig.OCCURRING_TAGS_ONLY);
  static final boolean POSSIBLE_TAGS_ONLY = Boolean.valueOf(TaggerConfig.POSSIBLE_TAGS_ONLY);

  double defaultScore;

  int leftContext;
  int rightContext;

  TaggerConfig config;

  /**
   * Determines which words are considered rare.  All words with count
   * in the training data strictly less than this number (standardly, &lt; 5) are
   * considered rare.
   */
  private int rareWordThresh = RARE_WORD_THRESH;

  /**
   * Determines which features are included in the model.  The model
   * includes features that occurred strictly more times than this number
   * (standardly, &gt; 5) in the training data.  Here I look only at the
   * history (not the tag), so the history appearing this often is enough.
   */
  int minFeatureThresh = MIN_FEATURE_THRESH;

  /**
   * This is a special threshold for the current word feature.
   * Only words that have occurred strictly &gt; this number of times
   * in total will generate word features with all of their occurring tags.
   * The traditional default was 2.
   */
  int curWordMinFeatureThresh = CUR_WORD_MIN_FEATURE_THRESH;

  /**
   * Determines which rare word features are included in the model.
   * The features for rare words have a strictly higher support than
   * this number are included. Traditional default is 10.
   */
  int rareWordMinFeatureThresh = RARE_WORD_MIN_FEATURE_THRESH;

  /**
   * If using tag equivalence classes on following words, words that occur
   * strictly more than this number of times (in total with any tag)
   * are sufficiently frequent to form an equivalence class
   * by themselves. (Not used unless using equivalence classes.)
   *
   * There are places in the code (ExtractorAmbiguityClass.java, for one)
   * that assume this value is constant over the life of a tagger.
   */
  int veryCommonWordThresh = VERY_COMMON_WORD_THRESH;


  int xSize;
  int ySize;
  boolean occurringTagsOnly = OCCURRING_TAGS_ONLY;
  boolean possibleTagsOnly = POSSIBLE_TAGS_ONLY;

  private boolean initted;

  boolean VERBOSE;

  /**
   * This is a function used to preprocess all text before applying
   * the tagger to it.  For example, it could be a function to
   * lowercase text, such as edu.stanford.nlp.util.LowercaseFunction
   * (which makes the tagger case insensitive).  It is applied in
   * ReadDataTagged, which loads in the training data, and in
   * TestSentence, which processes sentences for new queries.  If any
   * other classes are added or modified which use raw text, they must
   * also use this function to keep results consistent.
   * <br>
   * An alternate design would have been to use the function at a
   * lower level, such as at the extractor level.  That would have
   * require more invasive changes to the tagger, though, because
   * other data structures such as the Dictionary would then be using
   * raw text as well.  This is also more efficient, in that the
   * function is applied once at the start of the process.
   */
  Function<String, String> wordFunction;


  /* Package access - shouldn't be part of public API. */
  LambdaSolve getLambdaSolve() {
    return prob;
  }

  // TODO: make these constructors instead of init methods?
  void init(TaggerConfig config) {
      if (!initted) {

          this.config = config;

          String lang, arch;
          String[] openClassTags, closedClassTags;

          if (config == null) {
              lang = "english";
              arch = "left3words";
              openClassTags = StringUtils.EMPTY_STRING_ARRAY;
              closedClassTags = StringUtils.EMPTY_STRING_ARRAY;
              wordFunction = null;
          } else {
              this.VERBOSE = config.getVerbose();

              lang = config.getLang();
              arch = config.getArch();
              openClassTags = config.getOpenClassTags();
              closedClassTags = config.getClosedClassTags();
              if (!config.getWordFunction().isEmpty()) {
                  wordFunction =
                          ReflectionLoading.loadByReflection(config.getWordFunction());
              }

              if (openClassTags.length > 0 && !lang.isEmpty() || closedClassTags.length > 0 && !lang.isEmpty() || closedClassTags.length > 0 && openClassTags.length > 0) {
                  throw new RuntimeException("At least two of lang (\"" + lang + "\"), openClassTags (length " + openClassTags.length + ": " + Arrays.toString(openClassTags) + ")," +
                          "and closedClassTags (length " + closedClassTags.length + ": " + Arrays.toString(closedClassTags) + ") specified---you must choose one!");
              } else if (openClassTags.length == 0 && lang.isEmpty() && closedClassTags.length == 0 && !config.getLearnClosedClassTags()) {
                  System.err.println("warning: no language set, no open-class tags specified, and no closed-class tags specified; assuming ALL tags are open class tags");
              }
          }

          if (openClassTags.length > 0) {
              tags = new TTags();
              tags.setOpenClassTags(openClassTags);
          } else if (closedClassTags.length > 0) {
              tags = new TTags();
              tags.setClosedClassTags(closedClassTags);
          } else {
              tags = new TTags(lang);
          }

          defaultScore = lang.equals("english") ? 1.0 : 0.0;

          if (config != null) {
              rareWordThresh = config.getRareWordThresh();
              minFeatureThresh = config.getMinFeatureThresh();
              curWordMinFeatureThresh = config.getCurWordMinFeatureThresh();
              rareWordMinFeatureThresh = config.getRareWordMinFeatureThresh();
              veryCommonWordThresh = config.getVeryCommonWordThresh();
              occurringTagsOnly = config.occurringTagsOnly();
              possibleTagsOnly = config.possibleTagsOnly();
              // System.err.println("occurringTagsOnly: "+occurringTagsOnly);
              // System.err.println("possibleTagsOnly: "+possibleTagsOnly);

              if (config.getDefaultScore() >= 0)
                  defaultScore = config.getDefaultScore();
          }

          if (config == null || config.getMode() == TaggerConfig.Mode.TRAIN) {
              // initialize the extractors based on the arch variable
              // you only need to do this when training; otherwise they will be
              // restored from the serialized file
              extractors = new Extractors(ExtractorFrames.getExtractorFrames(arch));
              extractorsRare = new Extractors(ExtractorFramesRare.getExtractorFramesRare(arch, tags));

              setExtractorsGlobal();
          }

          ambClasses = new AmbiguityClasses(tags);

          initted = true;
      }
  }


  /**
   * Figures out what tokenizer factory might be described by the
   * config.  If it's described by name in the config, uses reflection
   * to get the factory (which may cause an exception, of course...)
   */
  protected TokenizerFactory<? extends HasWord> chooseTokenizerFactory() {
    return chooseTokenizerFactory(config.getTokenize(),
                                  config.getTokenizerFactory(),
                                  config.getTokenizerOptions(),
                                  config.getTokenizerInvertible());
  }

  protected static TokenizerFactory<? extends HasWord>
    chooseTokenizerFactory(boolean tokenize, String tokenizerFactory,
                           String tokenizerOptions, boolean invertible) {
      //return (TokenizerFactory<? extends HasWord>) Class.forName(getTokenizerFactory()).newInstance();
      if (tokenize && !tokenizerFactory.trim().isEmpty()) try {
          @SuppressWarnings("unchecked")
          Class<TokenizerFactory<? extends HasWord>> clazz = (Class<TokenizerFactory<? extends HasWord>>) Class.forName(tokenizerFactory.trim());
          Method factoryMethod = clazz.getMethod("newTokenizerFactory");
          @SuppressWarnings("unchecked")
          TokenizerFactory<? extends HasWord> factory = (TokenizerFactory<? extends HasWord>) factoryMethod.invoke(tokenizerOptions);
          return factory;
      } catch (Exception e) {
          throw new RuntimeException("Could not load tokenizer factory", e);
      }
      if (tokenize) {
        if (invertible) {
            if (!tokenizerOptions.isEmpty()) {
                if (!tokenizerOptions.matches("(^|.*,)invertible=true")) {
                  tokenizerOptions += ",invertible=true";
                }
            } else tokenizerOptions = "invertible=true";
            return PTBTokenizerFactory.newCoreLabelTokenizerFactory(tokenizerOptions);
        }
          return PTBTokenizerFactory.newWordTokenizerFactory(tokenizerOptions);
      }
      return WhitespaceTokenizer.factory();
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    DataInputStream rf = new DataInputStream(in);
    readModelAndInit(null, rf, false);
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    DataOutputStream dos = new DataOutputStream(out);
    saveModel(dos);
    dos.flush();
  }

  // serialize the ExtractorFrames and ExtractorFramesRare in filename
  private void saveExtractors(OutputStream os) throws IOException {

    ObjectOutputStream out = new ObjectOutputStream(os);
    out.writeObject(extractors);
    out.writeObject(extractorsRare);
    out.flush();
  }

  // Read the extractors from a filename.
  private void readExtractors(String filename) throws IOException, ClassNotFoundException {
    InputStream in = new BufferedInputStream(new FileInputStream(filename));
    readExtractors(in);
    in.close();
  }

  // Read the extractors from a stream.
  private void readExtractors(InputStream file) throws IOException, ClassNotFoundException {
    ObjectInputStream in = new ObjectInputStream(file);
    extractors = (Extractors) in.readObject();
    extractorsRare = (Extractors) in.readObject();
    extractors.initTypes();
    extractorsRare.initTypes();
    int left = extractors.leftContext();
    int left_u = extractorsRare.leftContext();
    if (left_u > left) {
      left = left_u;
    }
    leftContext = left;
    int right = extractors.rightContext();
    int right_u = extractorsRare.rightContext();
    if (right_u > right) {
      right = right_u;
    }
    rightContext = right;

    setExtractorsGlobal();
  }

  // Sometimes there is data associated with the tagger (such as a
  // dictionary) that we don't want saved with each extractor.  This
  // call lets those extractors get that information from the tagger
  // after being loaded from a data file.
  private void setExtractorsGlobal() {
    extractors.setGlobalHolder(this);
    extractorsRare.setGlobalHolder(this);
  }



  protected void saveModel(String filename) {
    try {
      OutDataStreamFile file = new OutDataStreamFile(filename);
      saveModel(file);
      file.close();
    } catch (IOException ioe) {
      System.err.println("Error saving tagger to file " + filename);
      throw new RuntimeIOException(ioe);
    }
  }

  protected void saveModel(DataOutputStream file) throws IOException {
      config.saveConfig(file);
      file.writeInt(xSize);
      file.writeInt(ySize);
      dict.save(file);
      tags.save(file, tagTokens);

      saveExtractors(file);

      int sizeAssoc = 0;
      for (Map<CharSequence, int[]> fValueAssociations : fAssociations) {

        for (int[] fTagAssociations : fValueAssociations.values()) {
          for (int association : fTagAssociations) {
            if (association >= 0) {
              ++sizeAssoc;
            }
          }
        }
      }
      file.writeInt(sizeAssoc);
      for (int i = 0; i < fAssociations.size(); ++i) {
          Map<CharSequence, int[]> fValueAssociations = fAssociations.get(i);
          for (Map.Entry<CharSequence, int[]> item: fValueAssociations.entrySet()) {
            CharSequence featureValue = (CharSequence) item.getKey();
          int[] fTagAssociations = (int[]) item.getValue();
          for (int j = 0; j < fTagAssociations.length; ++j) {
            int association = fTagAssociations[j];
            if (association >= 0) {
              file.writeInt(association);
              FeatureKey fk = new FeatureKey(i, (CharSequence) featureValue, tags.getTag(j));
              fk.save(file);
            }
          }
        }
      }

      LambdaSolve.save_lambdas(file, prob.lambda);
  }

  /** This reads the complete tagger from a single model stored in a file, at a URL,
   *  or as a resource
   *  in a jar file, and inits the tagger using a
   *  combination of the properties passed in and parameters from the file.
   *  <p>
   *  <i>Note for the future:</i> This assumes that the TaggerConfig in the file
   *  has already been read and used.  This work is done inside the
   *  constructor of TaggerConfig.  It might be better to refactor
   *  things so that is all done inside this method, but for the moment
   *  it seemed better to leave working code alone [cdm 2008].
   *
   *  @param config The tagger config
   *  @param modelFileOrUrl The name of the model file. This routine opens and closes it.
   *  @param printLoading Whether to print a message saying what model file is being loaded and how long it took when finished.
   *  @throws RuntimeIOException if I/O errors or serialization errors
   */
  protected void readModelAndInit(Properties config, String modelFileOrUrl, boolean printLoading) {
    try {
      // first check can open file ... or else leave with exception
      DataInputStream rf = new DataInputStream(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(modelFileOrUrl));

      readModelAndInit(config, rf, printLoading);
      rf.close();
    } catch (IOException e) {
      throw new RuntimeIOException("Unrecoverable error while loading a tagger model", e);
    }
  }



  /** This reads the complete tagger from a single model file, and inits
   *  the tagger using a combination of the properties passed in and
   *  parameters from the file.
   *  <p>
   *  <i>Note for the future: This assumes that the TaggerConfig in the file
   *  has already been read and used.  It might be better to refactor
   *  things so that is all done inside this method, but for the moment
   *  it seemed better to leave working code alone [cdm 2008].</i>
   *
   *  @param config The tagger config
   *  @param rf DataInputStream to read from.  It's the caller's job to open and close this stream.
   *  @param printLoading Whether to print a message saying what model file is being loaded and how long it took when finished.
   *  @throws RuntimeIOException if I/O errors or serialization errors
   */
  protected void readModelAndInit(Properties config, DataInputStream rf, boolean printLoading) {
    try {
      Timing t = new Timing();
      if (printLoading) {
        String source = null;
        if (config != null) {
          // TODO: "model"
          source = config.getProperty("model");
        }
        if (source == null) {
          source = "data stream";
        }
        t.doing("Reading POS tagger model from " + source);
      }
      TaggerConfig taggerConfig = TaggerConfig.readConfig(rf);
      if (config != null) {
        taggerConfig.setProperties(config);
      }
      // then init tagger
      init(taggerConfig);

      xSize = rf.readInt();
      ySize = rf.readInt();
      dict = new Dictionary();
      dict.read(rf);

      if (VERBOSE) {
        System.err.println(" dictionary read ");
      }
      tags.read(rf);
      readExtractors(rf);
      dict.setAmbClasses(ambClasses, veryCommonWordThresh, tags);

      int[] numFA = new int[extractors.getSize() + extractorsRare.getSize()];
      int sizeAssoc = rf.readInt();
      fAssociations = new ArrayList<>();
      for (int i = 0; i < extractors.getSize() + extractorsRare.getSize(); ++i) {
          fAssociations.add(new FastMap<CharSequence, int[]>());
      }
      if (VERBOSE) System.err.printf("Reading %d feature keys...\n",sizeAssoc);
      PrintFile pfVP = null;
      if (VERBOSE) {
        pfVP = new PrintFile("pairs.txt");
      }
      for (int i = 0; i < sizeAssoc; i++) {
        int numF = rf.readInt();
        FeatureKey fK = new FeatureKey();
        fK.read(rf);
        numFA[fK.num]++;

        // TODO: rewrite the writing / reading code to store
        // fAssociations in a cleaner manner?  Only do this when
        // rebuilding all the tagger models anyway.  When we do that, we
        // can get rid of FeatureKey
          Map<CharSequence, int[]> fValueAssociations = fAssociations.get(fK.num);
        int[] fTagAssociations = (int[]) fValueAssociations.get(fK.val);
        if (fTagAssociations == null) {
          fTagAssociations = new int[ySize];
          for (int j = 0; j < ySize; ++j) {
            fTagAssociations[j] = -1;
          }
          fValueAssociations.put(fK.val, fTagAssociations);
        }
        fTagAssociations[tags.getIndex(fK.tag)] = numF;
      }
      if (VERBOSE) {
        pfVP.close();
      }
      if (VERBOSE) {
        for (int k = 0; k < numFA.length; k++) {
          System.err.println(" Number of features of kind " + k + ' ' + numFA[k]);
        }
      }
      prob = new LambdaSolveTagger(rf);
      if (VERBOSE) {
        System.err.println(" prob read ");
      }
      if (printLoading) t.done();
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeIOException("Unrecoverable error while loading a tagger model", e);
    }
  }


  protected void dumpModel(PrintStream out) {
    for (int i = 0; i < fAssociations.size(); ++i) {
        Map<CharSequence, int[]> fValueAssociations = fAssociations.get(i);
        for (Map.Entry<CharSequence, int[]> item : fValueAssociations.entrySet()) {

        CharSequence featureValue = (CharSequence) item.getKey();
        int[] fTagAssociations = (int[]) item.getValue();
        for (int j = 0; j < fTagAssociations.length; ++j) {
          int association = fTagAssociations[j];
          if (association >= 0) {
            FeatureKey fk = new FeatureKey(i, featureValue, tags.getTag(j));
            out.println(fk + ": " + association);
          }
        }
      }
    }
  }


  /* Package access so it doesn't appear in public API. */
  boolean isRare(CharSequence word) {
    return dict.sum(word) < rareWordThresh;
  }

  // todo: clean this up. It seems like we'd be better off without this method. Used once in (MT's) PrefixTagger
  public TTags getTags() {
    return tags;
  }


    /**
   * Expects a sentence and returns a tagged sentence.  The input Sentence items
   *
   *
   * @param in This needs to be a Sentence
   * @return A Sentence of TaggedWord
   */
  public List<TaggedWord> apply(List<? extends HasWord> in) {
      return new TestSentence(this).tagSentence(in, false);
  }


  /**
   * Tags the Words in each Sentence in the given List with their
   * grammatical part-of-speech. The returned List contains Sentences
   * consisting of TaggedWords.
   * <p><b>NOTE: </b>The input document must contain sentences as its elements,
   * not words. To turn a Document of words into a Document of sentences, run
   * it through {@link edu.stanford.nlp.process.WordToSentenceProcessor}.
   *
   * @param sentences A List of Sentence
   * @return A List of Sentence of TaggedWord (final generification cannot be listed due to lack of complete generification of super classes)
   */
  public List<List<TaggedWord>> process(List<? extends List<? extends HasWord>> sentences) {
    List<List<TaggedWord>> taggedSentences = FastTable.newInstance();

    TestSentence testSentence = new TestSentence(this);
      for (int i = 0, sentencesSize = sentences.size(); i < sentencesSize; i++) {
          List<? extends HasWord> sentence = sentences.get(i);
          taggedSentences.add(testSentence.tagSentence(sentence, false));
      }
    return taggedSentences;
  }


    /**
   * Returns a new Sentence that is a copy of the given sentence with all the
   * words tagged with their part-of-speech. Convenience method when you only
   * want to tag a single List instead of a List of Lists.  If you
   * supply tagSentence with a List of HasTag, and set reuseTags to
   * true, the tagger will reuse the supplied tags.
   * @param sentence sentence to tag
   * @param reuseTags whether or not to reuse the given tag
   * @return tagged sentence
   */
  public List<TaggedWord> tagSentence(List<? extends HasWord> sentence,
                                      boolean reuseTags) {
    TestSentence testSentence = new TestSentence(this);
    return testSentence.tagSentence(sentence, reuseTags);
  }

  /**
   * Takes a sentence composed of CoreLabels and add the tags to the
   * CoreLabels, modifying the input sentence.
   */
  public void tagCoreLabels(List<CoreLabel> sentence) {
    tagCoreLabels(sentence, false);
  }

  /**
   * Takes a sentence composed of CoreLabels and add the tags to the
   * CoreLabels, modifying the input sentence.  If reuseTags is set to
   * true, any tags supplied with the CoreLabels are taken as correct.
   */
  public void tagCoreLabels(List<CoreLabel> sentence,
                            boolean reuseTags) {
    List<TaggedWord> taggedWords = tagSentence(sentence, reuseTags);
    if (taggedWords.size() != sentence.size())
      throw new AssertionError("Tagged word list not the same length " +
                               "as the original sentence");
    for (int i = 0, size = sentence.size(); i < size; ++i) {
      sentence.get(i).setTag(taggedWords.get(i).tag());
    }
  }

  /**
   * Adds lemmas to the given list of CoreLabels, using the given
   * Morphology object.  The input list must already have tags set.
   */
  public static void lemmatize(List<CoreLabel> sentence,
                               Morphology morpha) {
    for (CoreLabel label : sentence) {
      morpha.stem(label);
    }
  }

  /**
   * Casts a list of HasWords, which we secretly know to be
   * CoreLabels, to a list of CoreLabels.  Barfs if you didn't
   * actually give it CoreLabels.
   */
  private static List<CoreLabel> castCoreLabels(List<? extends HasWord> sent) {
    List<CoreLabel> coreLabels = new ArrayList<>();
    for (HasWord word : sent) {
      if (!(word instanceof CoreLabel)) {
        throw new ClassCastException("Expected CoreLabels");
      }
      coreLabels.add((CoreLabel) word);
    }
    return coreLabels;
  }

  /**
   * Reads data from r, tokenizes it with the default (Penn Treebank)
   * tokenizer, and returns a List of Sentence objects, which can
   * then be fed into tagSentence.
   *
   * @param r Reader where untokenized text is read
   * @return List of tokenized sentences
   */
  public static List<List<HasWord>> tokenizeText(Reader r) {
    return tokenizeText(r, null);
  }


  /**
   * Reads data from r, tokenizes it with the given tokenizer, and
   * returns a List of Lists of (extends) HasWord objects, which can then be
   * fed into tagSentence.
   *
   * @param r Reader where untokenized text is read
   * @param tokenizerFactory Tokenizer.  This can be {@code null} in which case
   *     the default English tokenizer (PTBTokenizerFactory) is used.
   * @return List of tokenized sentences
   */
  public static List<List<HasWord>> tokenizeText(Reader r,
                 TokenizerFactory<? extends HasWord> tokenizerFactory) {
    DocumentPreprocessor documentPreprocessor = new DocumentPreprocessor(r);
    if (tokenizerFactory != null) {
      documentPreprocessor.setTokenizerFactory(tokenizerFactory);
    }
    List<List<HasWord>> out = new ArrayList<>();
    for (List<HasWord> item : documentPreprocessor) {
      out.add(item);
    }
    return out;
  }


  private static void dumpModel(TaggerConfig config) {
    try {
      MaxentTagger tagger = new MaxentTagger(config.getFile(), config, false);
      System.out.println("Serialized tagger built with config:");
      tagger.config.dump(System.out);
      tagger.dumpModel(System.out);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * Tests a tagger on data with gold tags available.  This is TEST mode.
   *
   * @param config Properties giving parameters for the testing run
   */
  private static void runTest(TaggerConfig config) {
    if (config.getVerbose()) {
      System.err.println("## tagger testing invoked at " + new Date() + " with arguments:");
      config.dump();
    }

    try {
      MaxentTagger tagger = new MaxentTagger(config.getModel(), config);

      Timing t = new Timing();
      TestClassifier testClassifier = new TestClassifier(tagger);
      long millis = t.stop();
      printErrWordsPerSec(millis, testClassifier.getNumWords());
      testClassifier.printModelAndAccuracy(tagger);
    } catch (Exception e) {
      System.err.println("An error occurred while testing the tagger.");
      e.printStackTrace();
    }
  }


  /**
   * Reads in the training corpus from a filename and trains the tagger
   *
   * @param config Configuration parameters for training a model (filename, etc.
   * @throws IOException If IO problem
   */
  private static void trainAndSaveModel(TaggerConfig config) throws IOException {

    String modelName = config.getModel();
    MaxentTagger maxentTagger = new MaxentTagger();
    maxentTagger.init(config);

    // Allow clobbering.  You want it all the time when running experiments.

    TaggerExperiments samples = new TaggerExperiments(config, maxentTagger);
    TaggerFeatures feats = samples.getTaggerFeatures();
    System.err.println("Samples from " + config.getFile());
    System.err.println("Number of features: " + feats.size());
    Problem p = new Problem(samples, feats);
    LambdaSolveTagger prob = new LambdaSolveTagger(p, 0.0001, 0.00001, maxentTagger.fnumArr);
    maxentTagger.prob = prob;

    if (config.getSearch().equals("owlqn")) {
      CGRunner runner = new CGRunner(prob, config.getModel(), config.getSigmaSquared());
      runner.solveL1(config.getRegL1());
    } else if (config.getSearch().equals("owlqn2")) {
      CGRunner runner = new CGRunner(prob, config.getModel(), config.getSigmaSquared());
      runner.solveOWLQN2(config.getRegL1());
    } else if (config.getSearch().equals("cg")) {
      CGRunner runner = new CGRunner(prob, config.getModel(), config.getSigmaSquared());
      runner.solveCG();
    } else if (config.getSearch().equals("qn")) {
      CGRunner runner = new CGRunner(prob, config.getModel(), config.getSigmaSquared());
      runner.solveQN();
    } else {
      prob.improvedIterative(config.getIterations());
    }

    if (prob.checkCorrectness()) {
      System.err.println("Model is correct [empirical expec = model expec]");
    } else {
      System.err.println("Model is not correct");
    }
    maxentTagger.saveModel(modelName);
    System.err.println("Extractors list:");
    System.err.println(maxentTagger.extractors.toString() + "\nrare" + maxentTagger.extractorsRare.toString());
  }


  /**
   * Trains a tagger model.
   *
   * @param config Properties giving parameters for the training run
   */
  private static void runTraining(TaggerConfig config)
    throws IOException
  {
    Date now = new Date();

    System.err.println("## tagger training invoked at " + now + " with arguments:");
    config.dump();
    Timing tim = new Timing();

    PrintFile log = new PrintFile(config.getModel() + ".props");
    log.println("## tagger training invoked at " + now + " with arguments:");
    config.dump(log);
    log.close();

    trainAndSaveModel(config);
    tim.done("Training POS tagger");
  }


  private static void printErrWordsPerSec(long milliSec, int numWords) {
    double wordspersec = numWords / ((double) milliSec / 1000);
    NumberFormat nf = new DecimalFormat("0.00");
    System.err.println("Tagged " + numWords + " words at " +
        nf.format(wordspersec) + " words per second.");
  }


  // not so much a wrapper as a class with some various functionality
  // extending the MaxentTagger...
  // TODO: can we get rid of this? [cdm: sure. I'm not quite sure why Anna added it.  It seems like it could just be inside MaxentTagger]
  static class TaggerWrapper implements Function<String, String> {

    private final TaggerConfig config;
    private final MaxentTagger tagger;
    private TokenizerFactory<? extends HasWord> tokenizerFactory;
    private int sentNum; // = 0;

    private final boolean tokenize;
    private final boolean outputVerbosity, outputLemmas;
    private final OutputStyle outputStyle;
    private final String tagSeparator;
    private final Morphology morpha;

    protected TaggerWrapper(MaxentTagger tagger) {
      this.tagger = tagger;
      this.config = tagger.config;

      try {
        tokenizerFactory =
          chooseTokenizerFactory(config.getTokenize(),
                                 config.getTokenizerFactory(),
                                 config.getTokenizerOptions(),
                                 config.getTokenizerInvertible());
      } catch (Exception e) {
        System.err.println("Error in tokenizer factory instantiation for class: " + config.getTokenizerFactory());
        e.printStackTrace();
        tokenizerFactory = PTBTokenizerFactory.newWordTokenizerFactory(config.getTokenizerOptions());
      }

      outputStyle = OutputStyle.fromShortName(config.getOutputFormat());
      outputVerbosity = config.getOutputVerbosity();
      outputLemmas = config.getOutputLemmas();
      morpha = outputLemmas ? new Morphology() : null;
      tokenize = config.getTokenize();
      tagSeparator = config.getTagSeparator();
    }

    public String apply(String o) {
      StringWriter taggedResults = new StringWriter();

      List<List<HasWord>> sentences;
      if (tokenize) {
        sentences = tokenizeText(new StringReader(o), tokenizerFactory);
      } else {
        sentences = new ArrayList<>();
        sentences.add(Sentence.toWordList(o.split("\\s+")));
      }

      // TODO: there is another almost identical block of code elsewhere.  Refactor
        if (config.getNThreads() == 1) {
            for (List<? extends HasWord> sent : sentences) {
                Morphology morpha = outputLemmas ? new Morphology() : null;
                sent = tagger.tagCoreLabelsOrHasWords(sent, morpha, outputLemmas);
                tagger.outputTaggedSentence(sent, outputLemmas, outputStyle, outputVerbosity, sentNum++, " ", taggedResults);
            }
        } else {
            MulticoreWrapper<List<? extends HasWord>, List<? extends HasWord>> wrapper = new MulticoreWrapper<>(config.getNThreads(), new SentenceTaggingProcessor(tagger, outputLemmas));
            for (List<? extends HasWord> sentence : sentences) {
                wrapper.put(sentence);
                while (wrapper.peek()) {
                    List<? extends HasWord> taggedSentence = wrapper.poll();
                    tagger.outputTaggedSentence(taggedSentence, outputLemmas, outputStyle, outputVerbosity, sentNum++, " ", taggedResults);
                }
            }
            wrapper.join();
            while (wrapper.peek()) {
                List<? extends HasWord> taggedSentence = wrapper.poll();
                tagger.outputTaggedSentence(taggedSentence, outputLemmas, outputStyle, outputVerbosity, sentNum++, " ", taggedResults);
            }
        }
      return taggedResults.toString();
    }

  } // end class TaggerWrapper

  private static String getXMLWords(List<? extends HasWord> sentence,
                                    int sentNum, boolean outputLemmas) {
    boolean hasCoreLabels = sentence != null &&
            !sentence.isEmpty() &&
                             sentence.get(0) instanceof CoreLabel;
    TextBuilder sb = new TextBuilder();
    sb.append("<sentence id=\"").append(sentNum).append("\">\n");
    int wordIndex = 0;
    for (HasWord hw : sentence) {
      String word = hw.word();
      if (!(hw instanceof HasTag)) {
        throw new IllegalArgumentException("Expected HasTags, got " +
                                           hw.getClass());
      }
      String tag = ((HasTag) hw).tag();
      sb.append("  <word wid=\"").append(wordIndex).append("\" pos=\"").append(XMLUtils.escapeAttributeXML(tag)).append('"');
      if (outputLemmas && hasCoreLabels) {
        if (!(hw instanceof CoreLabel)) {
          throw new IllegalArgumentException("You mixed CoreLabels with " +
                                             hw.getClass() + "?  " +
                                             "Why would you do that?");
        }
        CoreLabel label = (CoreLabel) hw;
        String lemma = label.lemma();
        if (lemma != null) {
          sb.append(" lemma=\"").append(XMLUtils.escapeElementXML(lemma)).append('\"');
        }
      }
      sb.append('>').append(XMLUtils.escapeElementXML(word)).append("</word>\n");
      ++wordIndex;
    }
    sb.append("</sentence>\n");
    return sb.toString();
  }

  private static String getTsvWords(boolean verbose, boolean outputLemmas,
                                    List<? extends HasWord> sentence) {
    TextBuilder sb = new TextBuilder();
    if (verbose && !sentence.isEmpty() &&
        sentence.get(0) instanceof CoreLabel) {
      for (HasWord hw : sentence) {
        if (!(hw instanceof CoreLabel)) {
          throw new IllegalArgumentException("You mixed CoreLabels with " +
                                             hw.getClass() + "?  " +
                                             "Why would you do that?");
        }
        CoreLabel label = (CoreLabel) hw;
        sb.append(label.word());
        sb.append('\t');
        sb.append(label.originalText());
        sb.append('\t');
        if (outputLemmas) {
          sb.append(label.lemma());
          sb.append('\t');
        }
        sb.append(label.tag());
        sb.append('\t');
        sb.append(label.beginPosition());
        sb.append('\t');
        sb.append(label.endPosition());
        sb.append('\n');
      }
      sb.append('\n');
      return sb.toString();
    } // otherwise, fall through

    // either not verbose, or not CoreLabels
    for (HasWord hw : sentence) {
      String word = hw.word();
      if (!(hw instanceof HasTag)) {
        throw new IllegalArgumentException("Expected HasTags, got " +
                                           hw.getClass());
      }
      String tag = ((HasTag) hw).tag();
      sb.append(word).append('\t').append(tag).append('\n');
    }
    sb.append('\n');
    return sb.toString();
  }

  /**
   * Takes a tagged sentence and writes out the xml version.
   *
   * @param w Where to write the output to
   * @param sent A tagged sentence
   * @param sentNum The sentence index for XML printout
   * @param outputLemmas Whether to write the lemmas of words
   */
  private static void writeXMLSentence(Writer w, List<? extends HasWord> sent,
                                       int sentNum, boolean outputLemmas) {
    try {
      w.write(getXMLWords(sent, sentNum, outputLemmas));
    } catch (IOException e) {
      System.err.println("Error writing sentence " + sentNum + ": " +
                         Sentence.listToString(sent));
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Uses an XML transformer to turn an input stream into a bunch of
   * output.  Tags all of the text between xmlTags.
   *
   * The difference between using this and using runTagger in XML mode
   * is that this preserves the XML structure outside of the list of
   * elements to tag, whereas the runTagger method throws away all of
   * the surrounding structure and returns tagged plain text.
   */
  public void tagFromXML(InputStream input, Writer writer, String ... xmlTags) {
    OutputStyle outputStyle =
      OutputStyle.fromShortName(config.getOutputFormat());

    TransformXML<String> txml = new TransformXML<>();
    switch(outputStyle) {
    case XML:
    case INLINE_XML:
      txml.transformXML(xmlTags, new TaggerWrapper(this),
                        input, writer,
                        new TransformXML.NoEscapingSAXInterface<String>());
      break;
    case SLASH_TAGS:
    case TSV:
      txml.transformXML(xmlTags, new TaggerWrapper(this),
                        input, writer,
                        new TransformXML.SAXInterface<String>());
      break;
    default:
      throw new RuntimeException("Unexpected format " + outputStyle);
    }
  }

  public void tagFromXML(Reader input, Writer writer, String ... xmlTags) {
    OutputStyle outputStyle =
      OutputStyle.fromShortName(config.getOutputFormat());

    TransformXML<String> txml = new TransformXML<>();
    switch(outputStyle) {
    case XML:
    case INLINE_XML:
      txml.transformXML(xmlTags, new TaggerWrapper(this),
                        input, writer,
                        new TransformXML.NoEscapingSAXInterface<String>());
      break;
    case SLASH_TAGS:
    case TSV:
      txml.transformXML(xmlTags, new TaggerWrapper(this),
                        input, writer,
                        new TransformXML.SAXInterface<String>());
      break;
    default:
      throw new RuntimeException("Unexpected format " + outputStyle);
    }
  }

  private void tagFromXML() {
    InputStream is = null;
    Writer w = null;
    try {
      is = new BufferedInputStream(new FileInputStream(config.getFile()));
      String outFile = config.getOutputFile();
        w = !outFile.isEmpty() ? new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile),
                config.getEncoding())) : new PrintWriter(System.out);
      w.write("<?xml version=\"1.0\" encoding=\"" +
              config.getEncoding() + "\"?>\n");
      tagFromXML(is, w, config.getXMLInput());
    } catch (FileNotFoundException e) {
      System.err.println("Input file not found: " + config.getFile());
      e.printStackTrace();
    } catch (IOException ioe) {
      System.err.println("tagFromXML: mysterious IO Exception");
      ioe.printStackTrace();
    } finally {
      IOUtils.closeIgnoringExceptions(is);
      IOUtils.closeIgnoringExceptions(w);
    }
  }

  /**
   * Loads the tagger from a config file and then runs it in TAG mode.
   *
   * @param config The configuration parameters for the run.
   */
  private static void runTagger(TaggerConfig config)
    throws IOException, ClassNotFoundException,
           NoSuchMethodException, IllegalAccessException,
           java.lang.reflect.InvocationTargetException
  {
    if (config.getVerbose()) {
      Date now = new Date();
      System.err.println("## tagger invoked at " + now + " with arguments:");
      config.dump();
    }
    MaxentTagger tagger = new MaxentTagger(config.getModel(), config);
    tagger.runTagger();
  }

  private static final Pattern formatPattern = Pattern.compile("format=[a-zA-Z]+,");

  /**
   * Runs the tagger when we're in TAG mode.
   * In this mode, the config contains either the name of the file to
   * tag or stdin.  That file or input is then tagged.
   */
  private void runTagger()
    throws IOException, ClassNotFoundException,
           NoSuchMethodException, IllegalAccessException,
           java.lang.reflect.InvocationTargetException
  {
    String[] xmlInput = config.getXMLInput();
    if (xmlInput.length > 0) {
      if(xmlInput.length > 1 || !xmlInput[0].equals("null")) {
        tagFromXML();
        return;
      }
    }

    BufferedWriter writer = null;
    try {
      String outFile = config.getOutputFile();
        writer = !outFile.isEmpty() ? new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), config.getEncoding())) : new BufferedWriter(new OutputStreamWriter(System.out, config.getEncoding()));

      //Now determine if we're tagging from stdin or from a file,
      //construct a reader accordingly
      boolean stdin = config.useStdin();
      BufferedReader br;
      OutputStyle outputStyle = OutputStyle.fromShortName(config.getOutputFormat());
        if (stdin) {
            System.err.println("Type some text to tag, then EOF.");
            System.err.println("  (For EOF, use Return, Ctrl-D on Unix; Enter, Ctrl-Z, Enter on Windows.)");
            br = new BufferedReader(new InputStreamReader(System.in));

            runTaggerStdin(br, writer, outputStyle);
        } else {
            String filename = config.getFile();
            if (formatPattern.matcher(filename).find()) {
                TaggedFileRecord record = TaggedFileRecord.createRecord(config, filename);
                runTagger(record.reader(), writer, outputStyle);
            } else {
                br = IOUtils.readerFromString(config.getFile(), config.getEncoding());
                runTagger(br, writer, config.getTagInside(), outputStyle);
            }
        }
    } finally {
      if (writer != null) {
        IOUtils.closeIgnoringExceptions(writer);
      }
    }
  }

  public void runTaggerStdin(BufferedReader reader, BufferedWriter writer, OutputStyle outputStyle)
    throws IOException
  {
    TokenizerFactory<? extends HasWord> tokenizerFactory = chooseTokenizerFactory();

    //Counts
    long totalMillis = 0;
    int numWords = 0;
    int numSentences = 0;

    boolean outputVerbosity = config.getOutputVerbosity();
    boolean outputLemmas = config.getOutputLemmas();
    Morphology morpha = outputLemmas ? new Morphology() : null;

    if (outputStyle == OutputStyle.XML ||
        outputStyle == OutputStyle.INLINE_XML) {
      writer.write("<?xml version=\"1.0\" encoding=\"" +
                   config.getEncoding() + "\"?>\n");
      writer.write("<pos>\n");
    }

    while (true) {
      //Now we do everything through the doc preprocessor
      DocumentPreprocessor docProcessor;
      String line = reader.readLine();
      // this happens when we reach end of file
      if (line == null)
        break;
      docProcessor = new DocumentPreprocessor(new BufferedReader(new StringReader(line)));

      docProcessor.setTokenizerFactory(tokenizerFactory);

      for (List<HasWord> sentence : docProcessor) {
        numWords += sentence.size();

        Timing t = new Timing();
        tagAndOutputSentence(sentence, outputLemmas, morpha, outputStyle,
                             outputVerbosity, numSentences, "\n", writer);

        totalMillis += t.stop();
        writer.newLine();
        writer.flush();
        numSentences++;
      }
    }

    if (outputStyle == OutputStyle.XML ||
        outputStyle == OutputStyle.INLINE_XML) {
      writer.write("</pos>\n");
    }

    writer.flush();
    printErrWordsPerSec(totalMillis, numWords);
  }

  public void runTaggerSGML(BufferedReader reader, BufferedWriter writer, OutputStyle outputStyle)
    throws IOException
  {
    Timing t = new Timing();

    //Counts
    int numWords = 0;
    int numSentences = 0;

    if (outputStyle == OutputStyle.XML ||
        outputStyle == OutputStyle.INLINE_XML) {
      writer.write("<?xml version=\"1.0\" encoding=\"" +
                   config.getEncoding() + "\"?>\n");
      writer.write("<pos>\n");
    }

    // this uses NER codebase technology to read/write SGML-ish files
    PlainTextDocumentReaderAndWriter<CoreLabel> readerAndWriter = new PlainTextDocumentReaderAndWriter<>();
    ObjectBank<List<CoreLabel>> ob = new ObjectBank<>(new ReaderIteratorFactory(reader), readerAndWriter);
    PrintWriter pw = new PrintWriter(writer);
    for (List<CoreLabel> sentence : ob) {
      ArrayList<CoreLabel> s = new ArrayList<>(sentence);
      numWords += s.size();
      List<TaggedWord> taggedSentence = tagSentence(s, false);
      Iterator<CoreLabel> origIter = sentence.iterator();
      for (TaggedWord tw : taggedSentence) {
        CoreLabel cl = origIter.next();
        cl.set(CoreAnnotations.AnswerAnnotation.class, tw.tag());
      }
      readerAndWriter.printAnswers(sentence, pw, outputStyle, true);
      ++numSentences;
    }

    if (outputStyle == OutputStyle.XML ||
        outputStyle == OutputStyle.INLINE_XML) {
      writer.write("</pos>\n");
    }

    writer.flush();
    long millis = t.stop();
    printErrWordsPerSec(millis, numWords);
  }

  public <X extends HasWord> void runTagger(Iterable<List<X>> document,
                                            BufferedWriter writer,
                                            OutputStyle outputStyle)
    throws IOException
  {
    Timing t = new Timing();

    //Counts
    int numWords = 0;
    int numSentences = 0;

    boolean outputVerbosity = config.getOutputVerbosity();
    boolean outputLemmas = config.getOutputLemmas();

    if (outputStyle == OutputStyle.XML ||
        outputStyle == OutputStyle.INLINE_XML) {
      writer.write("<?xml version=\"1.0\" encoding=\"" +
                   config.getEncoding() + "\"?>\n");
      writer.write("<pos>\n");
    }


      if (config.getNThreads() == 1) {
          Morphology morpha = outputLemmas ? new Morphology() : null;
          for (List<X> sentence : document) {
              numWords += sentence.size();

              tagAndOutputSentence(sentence, outputLemmas, morpha, outputStyle,
                      outputVerbosity, numSentences, "\n", writer);

              numSentences++;
          }
      } else {
          MulticoreWrapper<List<? extends HasWord>, List<? extends HasWord>> wrapper = new MulticoreWrapper<>(config.getNThreads(), new SentenceTaggingProcessor(this, outputLemmas));
          for (List<X> sentence : document) {
              wrapper.put(sentence);
              while (wrapper.peek()) {
                  List<? extends HasWord> taggedSentence = wrapper.poll();
                  numWords += taggedSentence.size();
                  outputTaggedSentence(taggedSentence, outputLemmas, outputStyle, outputVerbosity, numSentences, "\n", writer);
                  numSentences++;
              }
          }
          wrapper.join();
          while (wrapper.peek()) {
              List<? extends HasWord> taggedSentence = wrapper.poll();
              numWords += taggedSentence.size();
              outputTaggedSentence(taggedSentence, outputLemmas, outputStyle, outputVerbosity, numSentences, "\n", writer);
              numSentences++;
          }
      }

    if (outputStyle == OutputStyle.XML ||
        outputStyle == OutputStyle.INLINE_XML) {
      writer.write("</pos>\n");
    }

    writer.flush();
    long millis = t.stop();
    printErrWordsPerSec(millis, numWords);
  }


  /**
   * This method runs the tagger on the provided reader and writer.
   *
   * It takes into from the given {@code reader}, applies the
   * tagger to it one sentence at a time (determined using
   * documentPreprocessor), and writes the output to the given
   * {@code writer}.
   *
   * The document is broken into sentences using the sentence
   * processor determined in the tagger's TaggerConfig.
   *
   * {@code tagInside} makes the tagger run in XML mode... if set
   * to non-empty, instead of processing the document as one large
   * text blob, it considers each region in between the given tag to
   * be a separate text blob.
   */
  public void runTagger(BufferedReader reader, BufferedWriter writer,
                        String tagInside, OutputStyle outputStyle)
    throws IOException
  {
    String sentenceDelimiter = config.getSentenceDelimiter();
    if (sentenceDelimiter != null && sentenceDelimiter.equals("newline")) {
      sentenceDelimiter = "\n";
    }
    TokenizerFactory<? extends HasWord> tokenizerFactory = chooseTokenizerFactory();

    //Now we do everything through the doc preprocessor
    DocumentPreprocessor docProcessor;
      if (tagInside.isEmpty()) {
          docProcessor = new DocumentPreprocessor(reader);
          docProcessor.setSentenceDelimiter(sentenceDelimiter);
      } else {
          docProcessor = new DocumentPreprocessor(reader, DocumentPreprocessor.DocType.XML);
          docProcessor.setElementDelimiter(tagInside);
      }
    docProcessor.setTokenizerFactory(tokenizerFactory);

    runTagger(docProcessor, writer, outputStyle);
  }

  public List<? extends HasWord> tagCoreLabelsOrHasWords(List<? extends HasWord> sentence, Morphology morpha, boolean outputLemmas) {
    if (!sentence.isEmpty() && sentence.get(0) instanceof CoreLabel) {
      List<CoreLabel> coreLabels = castCoreLabels(sentence);
      tagCoreLabels(coreLabels);
      if (outputLemmas) {
        // We may want to lemmatize things without using an existing
        // Morphology object, as Morphology objects are not
        // thread-safe, so we would make a new one here
        if (morpha == null) {
          morpha = new Morphology();
        }
        lemmatize(coreLabels, morpha);
      }
      return coreLabels;
    } else {
      List<TaggedWord> taggedSentence = tagSentence(sentence, false);
      return taggedSentence;
    }
  }

  public void tagAndOutputSentence(List<? extends HasWord> sentence,
                                   boolean outputLemmas, Morphology morpha,
                                   OutputStyle outputStyle,
                                   boolean outputVerbosity, int numSentences,
                                   String separator, Writer writer) {
    sentence = tagCoreLabelsOrHasWords(sentence, morpha, outputLemmas);
    outputTaggedSentence(sentence, outputLemmas, outputStyle, outputVerbosity, numSentences, separator, writer);
  }

  public void outputTaggedSentence(List<? extends HasWord> sentence,
                                   boolean outputLemmas, OutputStyle outputStyle,
                                   boolean outputVerbosity, int numSentences,
                                   String separator, Writer writer) {
    try {
      switch (outputStyle) {
      case TSV:
        writer.write(getTsvWords(outputVerbosity, outputLemmas, sentence));
        break;
      case XML:
      case INLINE_XML:
        writeXMLSentence(writer, sentence, numSentences, outputLemmas);
        break;
      case SLASH_TAGS:
        writer.write(Sentence.listToString(sentence, false, config.getTagSeparator()));
        writer.write(separator);
        break;
      default:
        throw new IllegalArgumentException("Unsupported output style " + outputStyle);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Command-line tagger interface.
   * Can be used to train or test taggers, or to tag text, taking input from
   * stdin or a file.
   * See class documentation for usage.
   *
   * @param args Command-line arguments
   * @throws IOException If any file problems
   */
  public static void main(String... args) throws Exception {
    TaggerConfig config = new TaggerConfig(args);

    if (config.getMode() == TaggerConfig.Mode.TRAIN) {
      runTraining(config);
    } else if (config.getMode() == TaggerConfig.Mode.TAG) {
      runTagger(config);
    } else if (config.getMode() == TaggerConfig.Mode.TEST) {
      runTest(config);
    } else if (config.getMode() == TaggerConfig.Mode.DUMP) {
      dumpModel(config);
    } else {
      System.err.println("Impossible: nothing to do. None of train, tag, test, or convert was specified.");
    }
  } // end main()


  static class SentenceTaggingProcessor implements ThreadsafeProcessor<List<? extends HasWord>, List<? extends HasWord>> {
    MaxentTagger maxentTagger;
    boolean outputLemmas;

    SentenceTaggingProcessor(MaxentTagger maxentTagger, boolean outputLemmas) {
      this.maxentTagger = maxentTagger;
      this.outputLemmas = outputLemmas;
    }

    @Override
    public List<? extends HasWord> process(List<? extends HasWord> sentence) {
      return maxentTagger.tagCoreLabelsOrHasWords(sentence, null, outputLemmas);
    }

    @Override
    public ThreadsafeProcessor<List<? extends HasWord>, List<? extends HasWord>> newInstance() {
      // MaxentTagger is threadsafe
      return this;
    }
  }

  private static final long serialVersionUID = 2;

}
