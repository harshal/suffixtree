/**
 * Copyright 2012 Alessandro Bahgat Shehata
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.abahgat.suffixtree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A Generalized Suffix Tree, based on the Ukkonen's paper "On-line construction of suffix trees"
 * http://www.cs.helsinki.fi/u/ukkonen/SuffixT1withFigs.pdf
 *
 * Allows for fast storage and fast(er) retrieval by creating a tree-based index out of a set of strings.
 * Unlike common suffix trees, which are generally used to build an index out of one (very) long string,
 * a Generalized Suffix Tree can be used to build an index over many strings.
 *
 * Its main operations are put and search:
 * Put adds the given key to the index, allowing for later retrieval of the given value.
 * Search can be used to retrieve the set of all the values that were put in the index with keys that contain a given input.
 *
 * In particular, after put(K, V), search(H) will return a set containing V for any string H that is substring of K.
 *
 * The overall complexity of the retrieval operation (search) is O(m) where m is the length of the string to search within the index.
 *
 * Although the implementation is based on the original design by Ukkonen, there are a few aspects where it differs significantly.
 * 
 * The tree is composed of a set of nodes and labeled edges. The labels on the edges can have any length as long as it's greater than 0.
 * The only constraint is that no two edges going out from the same node will start with the same character.
 * 
 * Because of this, a given (startNode, stringSuffix) pair can denote a unique path within the tree, and it is the path (if any) that can be
 * composed by sequentially traversing all the edges (e1, e2, ...) starting from startNode such that (e1.label + e2.label + ...) is equal
 * to the stringSuffix.
 * See the search method for details.
 * 
 * The union of all the edge labels from the root to a given leaf node denotes the set of the strings explicitly contained within the GST.
 * In addition to those Strings, there are a set of different strings that are implicitly contained within the GST, and it is composed of
 * the strings built by concatenating e1.label + e2.label + ... + $end, where e1, e2, ... is a proper path and $end is prefix of any of
 * the labels of the edges starting from the last node of the path.
 *
 * This kind of "implicit path" is important in the testAndSplit method.
 *  
 */
public class GeneralizedSuffixTree<T extends Comparable<T>> {

    /**
     * The index of the last item that was added to the GST
     */
    private int last = 0;
    /**
     * The root of the suffix tree
     */
    private final Node root = new Node();
    /**
     * The last leaf that was added during the update operation
     */
    private Node activeLeaf = root;

    /**
     * Searches for the given word within the GST.
     *
     * Returns all the indexes for which the key contains the <tt>word</tt> that was
     * supplied as input.
     *
     * @param word the key to search for
     * @return the collection of indexes associated with the input <tt>word</tt>
     */
    public Collection<Integer> search(List<T> word) {
        return search(word, -1);
    }

    /**
     * Searches for the given word within the GST and returns at most the given number of matches.
     *
     * @param word the key to search for
     * @param results the max number of results to return
     * @return at most <tt>results</tt> values for the given word
     */
    public Collection<Integer> search(List<T> word, int results) {
        Node tmpNode = searchNode(word);
        if (tmpNode == null) {
            return null;
        }
        return tmpNode.getData(results);
    }

    /**
     * Searches for the given word within the GST and returns at most the given number of matches.
     *
     * @param word the key to search for
     * @param to the max number of results to return
     * @return at most <tt>results</tt> values for the given word
\     */
    public ResultInfo searchWithCount(List<T> word, int to) {
        Node tmpNode = searchNode(word);
        if (tmpNode == null) {
            return new ResultInfo(Collections.EMPTY_LIST, 0);
        }

        return new ResultInfo(tmpNode.getData(to), tmpNode.getResultCount());
    }


    private boolean arrayRegionMatches(List<T> word, List<T> other,
                                       int toffset,
                                       int ooffset,
                                       int len)
    {
        List<T> ta = word;
        int to = toffset;
        List<T> pa = other;
        int po = ooffset;
        // Note: toffset, ooffset, or len might be near -1>>>1.
        if ((ooffset < 0) || (toffset < 0)
                || (toffset > (long)word.size() - len)
                || (ooffset > (long)other.size() - len)) {
            return false;
        }
        while (len-- > 0) {
            if (!ta.get(to++).equals(pa.get(po++))) {
                return false;
            }
        }
        return true;
    }


    public boolean startsWith(List<T> str, List<T> prefix, int toffset) {
        List<T> ta = str;
        int to = toffset;
        List<T> pa = prefix;
        int po = 0;
        int pc = prefix.size();
        // Note: toffset might be near -1>>>1.
        if ((toffset < 0) || (toffset > str.size() - pc)) {
            return false;
        }
        while (--pc >= 0) {
            if (!ta.get(to++).equals(pa.get(po++))) {
                return false;
            }
        }
        return true;
    }

    public boolean startsWith(List<T> str, List<T> prefix) {
        return startsWith(str, prefix, 0);
    }

    /**
     * Returns the tree node (if present) that corresponds to the given string.
     */
    private Node searchNode(List<T> word) {
        /*
         * Verifies if exists a path from the root to a node such that the concatenation
         * of all the labels on the path is a superstring of the given word.
         * If such a path is found, the last node on it is returned.
         */
        Node currentNode = root;
        Edge currentEdge;

        for (int i = 0; i < word.size(); ++i) {
            T ch = word.get(i);
            // follow the edge corresponding to this char
            currentEdge = currentNode.getEdge(ch);
            if (null == currentEdge) {
                // there is no edge starting with this char
                return null;
            } else {
                List<T> label = currentEdge.getLabel();
                int lenToMatch = Math.min(word.size() - i, label.size());
                if (arrayRegionMatches(word, label, i, 0, lenToMatch)) {
                    // the label on the edge does not correspond to the one in the string to search
                    return null;
                }

                if (label.size() >= word.size() - i) {
                    return currentEdge.getDest();
                } else {
                    // advance to next node
                    currentNode = currentEdge.getDest();
                    i += lenToMatch - 1;
                }
            }
        }

        return null;
    }

    /**
     * Adds the specified <tt>index</tt> to the GST under the given <tt>key</tt>.
     *
     * Entries must be inserted so that their indexes are in non-decreasing order,
     * otherwise an IllegalStateException will be raised.
     *
     * @param key the string key that will be added to the index
     * @param index the value that will be added to the index
     * @throws IllegalStateException if an invalid index is passed as input
     */
    public void put(List<T> key, int index) throws IllegalStateException {
        if (index < last) {
            throw new IllegalStateException("The input index must not be less than any of the previously inserted ones. Got " + index + ", expected at least " + last);
        } else {
            last = index;
        }

        // reset activeLeaf
        activeLeaf = root;

        List<T> remainder = key;
        Node s = root;

        // proceed with tree construction (closely related to procedure in
        // Ukkonen's paper)
        List<T> text = new ArrayList<T>();
        // iterate over the string, one char at a time
        for (int i = 0; i < remainder.size(); i++) {
            // line 6
            text.add(remainder.get(i));
            // use intern to make sure the resulting string is in the pool.

            // line 7: update the tree with the new transitions due to this new char
            Pair<Node, List<T>> active = update(s, text, remainder.subList(i, remainder.size()), index);
            // line 8: make sure the active pair is canonical
            active = canonize(active.getFirst(), active.getSecond());
            
            s = active.getFirst();
            text = active.getSecond();
        }

        // add leaf suffix link, is necessary
        if (null == activeLeaf.getSuffix() && activeLeaf != root && activeLeaf != s) {
            activeLeaf.setSuffix(s);
        }

    }

    /**
     * Tests whether the string stringPart + t is contained in the subtree that has inputs as root.
     * If that's not the case, and there exists a path of edges e1, e2, ... such that
     *     e1.label + e2.label + ... + $end = stringPart
     * and there is an edge g such that
     *     g.label = stringPart + rest
     * 
     * Then g will be split in two different edges, one having $end as label, and the other one
     * having rest as label.
     *
     * @param inputs the starting node
     * @param stringPart the string to search
     * @param t the following character
     * @param remainder the remainder of the string to add to the index
     * @param value the value to add to the index
     * @return a pair containing
     *                  true/false depending on whether (stringPart + t) is contained in the subtree starting in inputs
     *                  the last node that can be reached by following the path denoted by stringPart starting from inputs
     *         
     */
    private Pair<Boolean, Node<T>> testAndSplit(final Node inputs, final List<T> stringPart, final T t, final List<T> remainder, final int value) {
        // descend the tree as far as possible
        Pair<Node, List<T>> ret = canonize(inputs, stringPart);
        Node s = ret.getFirst();
        List<T> str = ret.getSecond();

        if (!str.isEmpty()) {
            Edge<T> g = s.getEdge(str.get(0));

            List<T> label = g.getLabel();
            // must see whether "str" is substring of the label of an edge
            if (label.size() > str.size() && label.get(str.size()).equals(t)) {
                return new Pair<Boolean, Node<T>>(true, s);
            } else {
                // need to split the edge
                List<T> newlabel = label.subList(str.size(), label.size());
                assert (startsWith(label, str));

                // build a new node
                Node r = new Node();
                // build a new edge
                Edge<T> newedge = new Edge<T>(str, r);

                g.setLabel(newlabel);

                // link s -> r
                r.addEdge(newlabel.get(0), g);
                s.addEdge(str.get(0), newedge);

                return new Pair<Boolean, Node<T>>(false, r);
            }

        } else {
            Edge<T> e = s.getEdge(t);
            if (null == e) {
                // if there is no t-transtion from s
                return new Pair<Boolean, Node<T>>(false, s);
            } else {
                if (remainder.equals(e.getLabel())) {
                    // update payload of destination node
                    e.getDest().addRef(value);
                    return new Pair<Boolean, Node<T>>(true, s);
                } else if (startsWith(remainder, e.getLabel())) {
                    return new Pair<Boolean, Node<T>>(true, s);
                } else if (startsWith(e.getLabel(), remainder)) {
                    // need to split as above
                    Node<T> newNode = new Node<T>();
                    newNode.addRef(value);

                    Edge<T> newEdge = new Edge<T>(remainder, newNode);

                    e.setLabel(e.getLabel().subList(remainder.size(), e.getLabel().size()));

                    newNode.addEdge(e.getLabel().get(0), e);

                    s.addEdge(t, newEdge);

                    return new Pair<Boolean, Node<T>>(false, s);
                } else {
                    // they are different words. No prefix. but they may still share some common substr
                    return new Pair<Boolean, Node<T>>(true, s);
                }
            }
        }

    }

    /**
     * Return a (Node, String) (n, remainder) pair such that n is a farthest descendant of
     * s (the input node) that can be reached by following a path of edges denoting
     * a prefix of inputstr and remainder will be string that must be
     * appended to the concatenation of labels from s to n to get inpustr.
     */
    private Pair<Node, List<T>> canonize(final Node s, final List<T> inputstr) {

        if (inputstr.isEmpty()) {
            return new Pair<Node, List<T>>(s, inputstr);
        } else {
            Node currentNode = s;
            List<T> str = new ArrayList<T>(inputstr);
            Edge g = s.getEdge(str.get(0));
            // descend the tree as long as a proper label is found
            while (g != null && startsWith(str, g.getLabel())) {
                str = str.subList(g.getLabel().size(), str.size());
                currentNode = g.getDest();
                if (str.size() > 0) {
                    g = currentNode.getEdge(str.get(0));
                }
            }

            return new Pair<Node, List<T>>(currentNode, str);
        }
    }

    /**
     * Updates the tree starting from inputNode and by adding stringPart.
     * 
     * Returns a reference (Node, String) pair for the string that has been added so far.
     * This means:
     * - the Node will be the Node that can be reached by the longest path string (S1)
     *   that can be obtained by concatenating consecutive edges in the tree and
     *   that is a substring of the string added so far to the tree.
     * - the String will be the remainder that must be added to S1 to get the string
     *   added so far.
     * 
     * @param inputNode the node to start from
     * @param stringPart the string to add to the tree
     * @param rest the rest of the string
     * @param value the value to add to the index
     */
    private Pair<Node, List<T>> update(final Node inputNode, final List<T> stringPart, final List<T> rest, final int value) {
        Node s = inputNode;
        List<T> tempstr = stringPart;
        T newChar = stringPart.get(stringPart.size() - 1);

        // line 1
        Node oldroot = root;

        // line 1b
        Pair<Boolean, Node<T>> ret = testAndSplit(s, tempstr.subList(0, tempstr.size() - 1), newChar, rest, value);

        Node<T> r = ret.getSecond();
        boolean endpoint = ret.getFirst();

        Node leaf;
        // line 2
        while (!endpoint) {
            // line 3
            Edge tempEdge = r.getEdge(newChar);
            if (null != tempEdge) {
                // such a node is already present. This is one of the main differences from Ukkonen's case:
                // the tree can contain deeper nodes at this stage because different strings were added by previous iterations.
                leaf = tempEdge.getDest();
            } else {
                // must build a new leaf
                leaf = new Node();
                leaf.addRef(value);
                Edge newedge = new Edge(rest, leaf);
                r.addEdge(newChar, newedge);
            }

            // update suffix link for newly created leaf
            if (activeLeaf != root) {
                activeLeaf.setSuffix(leaf);
            }
            activeLeaf = leaf;

            // line 4
            if (oldroot != root) {
                oldroot.setSuffix(r);
            }

            // line 5
            oldroot = r;

            // line 6
            if (null == s.getSuffix()) { // root node
                assert (root == s);
                // this is a special case to handle what is referred to as node _|_ on the paper
                tempstr = tempstr.subList(1, tempstr.size());
            } else {
                Pair<Node, List<T>> canret = canonize(s.getSuffix(), safeCutLastChar(tempstr));
                s = canret.getFirst();
                // use intern to ensure that tempstr is a reference from the string pool
                canret.getSecond().add(tempstr.get(tempstr.size() - 1));
            }

            // line 7
            ret = testAndSplit(s, safeCutLastChar(tempstr), newChar, rest, value);
            r = ret.getSecond();
            endpoint = ret.getFirst();

        }

        // line 8
        if (oldroot != root) {
            oldroot.setSuffix(r);
        }
        oldroot = root;

        return new Pair<Node, List<T>>(s, tempstr);
    }

    Node getRoot() {
        return root;
    }

    private List<T> safeCutLastChar(List<T> seq) {
        if (seq.size() == 0) {
            return Collections.emptyList();
        }
        return seq.subList(0, seq.size() - 1);
    }

    public int computeCount() {
        return root.computeAndCacheCount();
    }

    /**
     * An utility object, used to store the data returned by the GeneralizedSuffixTree GeneralizedSuffixTree.searchWithCount method.
     * It contains a collection of results and the total number of results present in the GST.
     * @see GeneralizedSuffixTree#searchWithCount(java.util.List, int)
     */
    public static class ResultInfo {

        /**
         * The total number of results present in the database
         */
        public int totalResults;
        /**
         * The collection of (some) results present in the GST
         */
        public Collection<Integer> results;

        public ResultInfo(Collection<Integer> results, int totalResults) {
            this.totalResults = totalResults;
            this.results = results;
        }
    }

    /**
     * A private class used to return a tuples of two elements
     */
    private class Pair<A, B> {

        private final A first;
        private final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        public A getFirst() {
            return first;
        }

        public B getSecond() {
            return second;
        }
    }
}
