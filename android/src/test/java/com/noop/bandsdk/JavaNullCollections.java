package com.noop.bandsdk;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
