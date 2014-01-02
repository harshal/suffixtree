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

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

class EdgeBag<T extends Comparable> {
    private T[] chars;
    private Edge[] values;
    private static final int BSEARCH_THRESHOLD = 6;

    private final Comparator<T> C = new Comparator<T>() {
        public int compare(T one, T two)
        {
            return one.compareTo(two);
        }
    };

    void put(T c, Edge e) {
        if (chars == null) {
            chars = (T[]) Array.newInstance(c.getClass(), 0);
            values = new Edge[0];
        }
        int idx = search(c);

        if (idx < 0) {
            int currsize = chars.length;
            T[] copy = (T[]) Array.newInstance(c.getClass(), currsize + 1);
            System.arraycopy(chars, 0, copy, 0, currsize);
            chars = copy;
            Edge[] copy1 = new Edge[currsize + 1];
            System.arraycopy(values, 0, copy1, 0, currsize);
            values = copy1;
            chars[currsize] = c;
            values[currsize] = e;
            currsize++;
            if (currsize > BSEARCH_THRESHOLD) {
                sortArrays();
            }
        } else {
            values[idx] = e;
        }
    }

    Edge get(T c) {

        int idx = search(c);
        if (idx < 0) {
            return null;
        }
        return values[idx];
    }

    private int search(T c) {
        if (chars == null)
            return -1;
        
        if (chars.length > BSEARCH_THRESHOLD) {
            return java.util.Arrays.binarySearch(chars, c, C);
        }

        for (int i = 0; i < chars.length; i++) {
            if (c == chars[i]) {
                return i;
            }
        }
        return -1;
    }

    Edge[] values() {
        return values == null ? new Edge[0] : values;
    }
    
    /**
     * A trivial implementation of sort, used to sort chars[] and values[] according to the data in chars.
     * 
     * It was preferred to faster sorts (like qsort) because of the small sizes (<=36) of the collections involved.
     */
    private void sortArrays() {
        for (int i = 0; i < chars.length; i++) {
         for (int j = i; j > 0; j--) {
            if (chars[j-1].compareTo(chars[j]) > 0) {
               T swap = chars[j];
               chars[j] = chars[j-1];
               chars[j-1] = swap;

               Edge swapEdge = values[j];
               values[j] = values[j-1];
               values[j-1] = swapEdge;
            }
         }
      }
    }
}
