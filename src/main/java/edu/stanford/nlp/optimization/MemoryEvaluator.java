package edu.stanford.nlp.optimization;

import edu.stanford.nlp.util.MemoryMonitor;
import javolution.text.TextBuilder;

/**
 * Evaluate current memory usage
 *
 * @author Angel Chang
 */
public class MemoryEvaluator implements Evaluator {
  private MemoryMonitor memMonitor;

  public MemoryEvaluator()
  {
    memMonitor = new MemoryMonitor();
  }

  public String toString()
  {
    return "Memory Usage";
  }

  public double evaluate(double... x) {
    TextBuilder sb = new TextBuilder("Memory Usage: ");
    sb.append(" used(KB):").append(memMonitor.getUsedMemory(false));
    sb.append(" maxAvailable(KB):").append(memMonitor.getMaxAvailableMemory(false));
    sb.append(" max(KB):").append(memMonitor.getMaxMemory());
    String memString = sb.toString();
    System.err.println(memString);
    return 0;
  }
}