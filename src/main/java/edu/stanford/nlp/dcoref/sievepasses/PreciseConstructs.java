package edu.stanford.nlp.dcoref.sievepasses;

public class PreciseConstructs extends DeterministicCorefSieve {
  public PreciseConstructs() {
      flags.USE_APPOSITION = true;
    flags.USE_PREDICATENOMINATIVES = true;
    flags.USE_ACRONYM = true;
    flags.USE_RELATIVEPRONOUN = true;
    flags.USE_ROLEAPPOSITION = true;
    flags.USE_DEMONYM = true;
  }
}
