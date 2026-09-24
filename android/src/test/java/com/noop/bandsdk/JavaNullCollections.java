package com.noop.bandsdk;

import java.util.ArrayList;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

final class JavaNullCollections {
    private JavaNullCollections() {}

    static <T> List<T> list() {
        List<T> values = new ArrayList<>();
        values.add(null);
        return values;
    }

    static <T> Set<T> set() {
        Set<T> values = new LinkedHashSet<>();
        values.add(null);
        return values;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static <T> List<T> listWithWrongType(Object value) {
        List values = new ArrayList();
        values.add(value);
        return (List<T>) values;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static <T> Set<T> setWithWrongType(Object value) {
        Set values = new LinkedHashSet();
        values.add(value);
        return (Set<T>) values;
    }

    static <T> List<T> classCastTraversalList() {
        return new AbstractList<>() {
            @Override
            public T get(int index) {
                throw new ClassCastException();
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public Iterator<T> iterator() {
                return throwingClassCastIterator();
            }
        };
    }

    static <T> Set<T> classCastTraversalSet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public Iterator<T> iterator() {
                return throwingClassCastIterator();
            }
        };
    }

    private static <T> Iterator<T> throwingClassCastIterator() {
        return new Iterator<>() {
            private boolean available = true;

            @Override
            public boolean hasNext() {
                return available;
            }

            @Override
            public T next() {
                if (!available) {
                    throw new NoSuchElementException();
                }
                available = false;
                throw new ClassCastException();
            }
        };
    }

    static BandDiagnosticEvent legacyDiagnosticEvent() {
        return new BandDiagnosticEvent(
            BandDiagnosticKind.CONNECTION,
            BandDiagnosticOutcome.COMPLETED,
            null,
            null,
            null
        );
    }
}
