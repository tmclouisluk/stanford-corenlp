package edu.stanford.nlp.ie.machinereading.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IdentityHashSet;

/**
 * 
 * @author Andrey Gusev
 * @author Mihai
 * 
 */
public class EventMention extends RelationMention {

  private static final long serialVersionUID = 1L;

  /** Modifier argument: used for BioNLP */
  private String eventModification;
  
  private final ExtractionObject anchor;
  
  // this is set if we're a subevent
  // we might have multiple parents for the same event (at least in the reader before sanity check 4)!
  private Set<ExtractionObject> parents;

  public EventMention(String objectId, 
      CoreMap sentence,
      Span span,
      String type,
      String subtype,
      ExtractionObject anchor,
      List<ExtractionObject> args,
      List<String> argNames) {
    super(objectId, sentence, span, type, subtype, args, argNames);
    this.anchor = anchor;
    this.parents = new IdentityHashSet<>();
    
    // set ourselves as the parent of any EventMentions in our args 
    for (ExtractionObject arg : args) {
      if (arg instanceof EventMention) {
        ((EventMention) arg).addParent(this);
      }
    }
  }
  
  public void resetArguments() {
    args = new ArrayList<>();
    argNames = new ArrayList<>();
  }
  
  public void removeFromParents() {
    // remove this from the arg list of all parents
    for(ExtractionObject parent: parents){
      if(parent instanceof RelationMention){
        ((RelationMention) parent).removeArgument(this, false);
      }
    }
    // reset the parent links
    parents.clear();
  }
  
  public void removeParent(ExtractionObject p) {
    parents.remove(p);
  }
  
  public String getModification() {
    return eventModification;
  }

  public void setModification(String eventModification) {
    this.eventModification = eventModification;
  }

  public ExtractionObject getAnchor() {
    return anchor;
  }
  
  /**
   * If this EventMention is a subevent, this will return the parent event.
   * 
   * @return the parent EventMention or null if this isn't a subevent.
   */
  public Set<ExtractionObject> getParents() {
    return parents;
  }
  
  public ExtractionObject getSingleParent(CoreMap sentence) {
    if(parents.size() > 1){
      Set<ExtractionObject> parents = this.parents;
      System.err.println("This event has multiple parents: " + this);
      int count = 1;
      for(ExtractionObject po: parents){
        System.err.println("PARENT #" + count + ": " + po);
        count ++;
      }
      System.err.println("DOC " + sentence.get(CoreAnnotations.DocIDAnnotation.class));
      System.err.print("SENTENCE:");
      for(CoreLabel t: sentence.get(CoreAnnotations.TokensAnnotation.class)){
        System.err.print(' ' + t.word());
      }
      System.err.println("EVENTS IN SENTENCE:");
      count = 1;
      for(EventMention e: sentence.get(MachineReadingAnnotations.EventMentionsAnnotation.class)){
        System.err.println("EVENT #" + count + ": " + e);
        count ++;
      }
    }
    
    assert parents.size() <= 1;
    for(ExtractionObject p: parents){
      return p;
    }
    return null;
  }
  
  public void addParent(EventMention p) {
    parents.add(p);
  }

  @Override
  public String toString() {
    return "EventMention [objectId=" + getObjectId() + ", type=" + type + ", subType=" + subType
      + ", start=" + getExtentTokenStart() + ", end=" + getExtentTokenEnd()
      + (anchor != null ? ", anchor=" + anchor : "")
      + (args != null ? ", args=" + args : "") 
      + (argNames != null ? ", argNames=" + argNames : "") + ']';
  }
  
  public boolean contains(EventMention e) {
    if(this.equals(e)) return true;
    
    for(ExtractionObject a: getArgs()){
      if(a instanceof EventMention){
        EventMention ea = (EventMention) a;
        if(ea.contains(e)){
          return true;
        }
      }
    }
    
    return false;
  }
  
  public void addArg(ExtractionObject a, String an, boolean discardSameArgDifferentName) {
    // only add if not already an argument
    for(int i = 0; i < getArgs().size(); i ++){
      ExtractionObject myArg = getArg(i);
      String myArgName = getArgNames().get(i);
      if(myArg.equals(a)){
        if(myArgName.equals(an)){
          // safe to discard this arg: we already have it with the same name
          return;
        } else {
          logger.info("Trying to add one argument: " + a + " with name " + an + " when this already exists with a different name: " + this + " in sentence: " + getSentence().get(CoreAnnotations.TextAnnotation.class));
          if(discardSameArgDifferentName) return;
        }
      }
    }
    
    this.args.add(a);
    this.argNames.add(an);
    if(a instanceof EventMention){
      ((EventMention) a).addParent(this);
    }
  }
  
  @Override
  public void setArgs(List<ExtractionObject> args) {
    this.args = args;
    // set ourselves as the parent of any EventMentions in our args 
    for (ExtractionObject arg : args) {
      if (arg instanceof EventMention) {
        ((EventMention) arg).addParent(this);
      }
    }
  }
  
  public void addArgs(List<ExtractionObject> args, List<String> argNames, boolean discardSameArgDifferentName){
    if(args == null) return;
    assert args.size() == argNames.size();
    for(int i = 0; i < args.size(); i ++){
      addArg(args.get(i), argNames.get(i), discardSameArgDifferentName);
    }
  }
  
  public void mergeEvent(EventMention e, boolean discardSameArgDifferentName){
    // merge types if necessary
    String oldType = type;
    type = ExtractionObject.concatenateTypes(type, e.getType());
    if(! type.equals(oldType)){
      // This is not important: we use anchor types in the parser, not event types
      // This is done just for completeness of code
      logger.fine("Type changed from " + oldType + " to " + type + " during check 3 merge.");
    }
    
    // add e's arguments
    for(int i = 0; i < e.getArgs().size(); i ++){
      ExtractionObject a = e.getArg(i);
      String an = e.getArgNames().get(i);
      // TODO: we might need more complex cycle detection than just contains()...
      if(a instanceof EventMention && ((EventMention) a).contains(this)){
        logger.info("Found event cycle during merge between e1 " + this + " and e2 " + e);
      } else {
        // remove e from a's parents
        if(a instanceof EventMention) ((EventMention) a).removeParent(e);
        // add a as an arg to this
        addArg(a, an, discardSameArgDifferentName);
      }
    }
    
    // remove e's arguments. they are now attached to this, so we don't want them moved around during removeEvents
    e.resetArguments();
    // remove e from its parent(s) to avoid using this argument in other merges of the parent
    e.removeFromParents();
  }
}
