package edu.stanford.nlp.tagger.maxent;

import ca.gedge.radixtree.RadixTree;
import edu.stanford.nlp.io.RuntimeIOException;
import javolution.util.FastMap;
import javolution.util.FastSet;

import java.io.*;
import java.util.Map;
import java.util.Set;



public class CTBunkDict {

  private final static String defaultFilename = "ctb_amb";
  private static CTBunkDict CTBunkDictSingleton;

  private static RadixTree< Set<String>> CTBunk_dict;


  private static CTBunkDict getInstance() {

    if (CTBunkDictSingleton == null) {
      CTBunkDictSingleton = new CTBunkDict();
    }
    return CTBunkDictSingleton;
  }


  private CTBunkDict() {
    readCTBunkDict("/u/nlp/data/pos-tagger/dictionary" + '/' + defaultFilename);
  }


  private static void readCTBunkDict(String filename)   {
      CTBunk_dict = new RadixTree<>();

    try{

      BufferedReader CTBunkDetectorReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "GB18030"));
      for (String CTBunkDetectorLine; (CTBunkDetectorLine = CTBunkDetectorReader.readLine()) != null; ) {
        String[] fields = CTBunkDetectorLine.split(" ");
        String tag=fields[1];
        Set<String> words=CTBunk_dict.get(tag);

        if(words==null){
            words = new FastSet<>();
          CTBunk_dict.put(tag,words);
        }
        words.add(fields[0]);

      }

    } catch (FileNotFoundException e) {
      throw new RuntimeIOException("CTBunk file not found: " + filename, e);
    } catch (IOException e) {
      throw new RuntimeIOException("CTBunk I/O error: " + filename, e);
    }
  }



  /**
   * Returns "1" as true if the dictionary listed this word with this tag,
   *  and "0" otherwise.
   *
   * @param tag  The POS tag
   * @param word The word
   * @return "1" as true if the dictionary listed this word with this tag,
   *  and "0" otherwise.
   */
  protected static String getTag(String tag, String word) {
    CTBunkDict dict = CTBunkDict.getInstance();
    Set<String> words = get(tag);
      return words != null && words.contains(word) ? "1" : "0";
  }


  private static Set<String> get(String a) {
    return CTBunk_dict.get(a);
  }

} // end class CTBunkDict
