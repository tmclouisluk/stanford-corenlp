// TregexMatcher
// Copyright (c) 2004-2007 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: parser-user@lists.stanford.edu
//    Licensing: parser-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/tregex.shtml

package edu.stanford.nlp.trees.tregex;

import java.util.*;

import edu.stanford.nlp.trees.HasParent;
import edu.stanford.nlp.trees.Tree;

/**
 * A TregexMatcher can be used to match a {@link TregexPattern} against a {@link edu.stanford.nlp.trees.Tree}.
 * Usage should be similar to a {@link java.util.regex.Matcher}.
 *
 * @author Galen Andrew
 */
public abstract class TregexMatcher {

  final Tree root;
  Tree tree;
  IdentityHashMap<Tree, Tree> nodesToParents;
  final Map<String, Tree> namesToNodes;
  final VariableStrings variableStrings;

  // these things are used by "find"
  Iterator<Tree> findIterator;
  Tree findCurrent;


  TregexMatcher(Tree root, Tree tree, IdentityHashMap<Tree, Tree> nodesToParents, Map<String, Tree> namesToNodes, VariableStrings variableStrings) {
    this.root = root;
    this.tree = tree;
    this.nodesToParents = nodesToParents;
    this.namesToNodes = namesToNodes;
    this.variableStrings = variableStrings;
  }

  /**
   * Resets the matcher so that its search starts over.
   */
  public void reset() {
    findIterator = null;
    findCurrent = null;
    namesToNodes.clear();
  }

  /**
   * Resets the matcher to start searching on the given tree for matching subexpressions.
   *
   * @param tree The tree to start searching on
   */
  void resetChildIter(Tree tree) {
    this.tree = tree;
    resetChildIter();
  }

  /**
   * Resets the matcher to restart search for matching subexpressions
   */
  void resetChildIter() {
  }

  /**
   * Does the pattern match the tree?  It's actually closer to java.util.regex's
   * "lookingAt" in that the root of the tree has to match the root of the pattern
   * but the whole tree does not have to be "accounted for".  Like with lookingAt
   * the beginning of the string has to match the pattern, but the whole string
   * doesn't have to be "accounted for".
   *
   * @return whether the tree matches the pattern
   */
  public abstract boolean matches();

  /** Rests the matcher and tests if it matches on the tree when rooted at {@code node}.
   *
   *  @param node The node where the match is checked
   *  @return whether the matcher matches at node
   */
  public boolean matchesAt(Tree node) {
    resetChildIter(node);
    return matches();
  }

  /**
   * Get the last matching tree -- that is, the tree node that matches the root node of the pattern.
   * Returns null if there has not been a match.
   *
   * @return last match
   */
  public abstract Tree getMatch();


  /**
   * Find the next match of the pattern on the tree
   *
   * @return whether there is a match somewhere in the tree
   */
  public boolean find() {
    if (findIterator == null) {
      findIterator = root.iterator();
    }
    if (findCurrent != null && matches()) {
      return true;
    }
    while (findIterator.hasNext()) {
      findCurrent = findIterator.next();
      resetChildIter(findCurrent);
      if (matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Similar to find, but matches count only if {@code node} is
   * the root of the match.  All other matches are ignored.  If you
   * know you are looking for matches with a particular root, this is
   * much faster than iterating over all matches and taking only the
   * ones that work and faster than altering the tregex to match only
   * the correct node.
   * <br>
   * If called multiple times with the same node, this will return
   * subsequent matches in the same manner as find() returns
   * subsequent matches in the same tree.  If you want to call this on
   * the same TregexMatcher on more than one node, call reset() first;
   * otherwise, an AssertionError will be thrown.
   */
  public boolean findAt(Tree node) {
    if (findCurrent != null && !findCurrent.equals(node)) {
      throw new AssertionError("Error: must call reset() before changing nodes for a call to findRootedAt");
    }
    if (findCurrent != null) {
      return matches();
    }
    findCurrent = node;
    resetChildIter(findCurrent);
    return matches();
  }

  /**
   * Find the next match of the pattern on the tree such that the
   * matching node (that is, the tree node matching the root node of
   * the pattern) differs from the previous matching node.
   * @return true iff another matching node is found.
   */
  public boolean findNextMatchingNode() {
    Tree lastMatchingNode = getMatch();
    while(find()) {
      if(!getMatch().equals(lastMatchingNode))
        return true;
    }
    return false;
  }

  // todo [cdm 2013]: This just seems unused. What's it meant to do? Eliminable???
  abstract boolean getChangesVariables();

  /**
   * Returns the node labeled with {@code name} in the pattern.
   *
   * @param name the name of the node, specified in the pattern.
   * @return node labeled by the name
   */
  public Tree getNode(String name) {
    return namesToNodes.get(name);
  }

  public Set<String> getNodeNames() {
    return namesToNodes.keySet();
  }

  Tree getParent(Tree node) {
    if (node instanceof HasParent) {
      return node.parent();
    }
    if (nodesToParents == null) {
      nodesToParents = new IdentityHashMap<>();
    }
    if (nodesToParents.isEmpty()) {
      fillNodesToParents(root, null);
    }
    return nodesToParents.get(node);
  }

  private void fillNodesToParents(Tree node, Tree parent) {
    nodesToParents.put(node, parent);
    for (Tree child : node.children()) {
      fillNodesToParents(child, node);
    }
  }

  Tree getRoot() {
    return root;
  }

  /**
   * If there is a current match, and that match involves setting this
   * particular variable string, this returns that string.  Otherwise,
   * it returns null.
   */
  public String getVariableString(String var) {
    return variableStrings.getString(var);
  }
}
