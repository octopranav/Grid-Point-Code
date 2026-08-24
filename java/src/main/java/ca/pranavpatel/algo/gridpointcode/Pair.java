// Copyright 2020 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0
//
// Vendored from KombiN 1.0.3 so that Grid Point Code carries no external
// dependencies. Package-private; not part of the public API.

package ca.pranavpatel.algo.gridpointcode;

/**
 * Represents an ordered combination pair (ai, bi) from two finite sets.
 * This class is used to encapsulate the indices of a pair in the KombiN mapping.
 */
final class Pair {
    public final long ai;
    public final long bi;
    /**
     * Constructs a Pair object representing the indices of a combination pair.
     *
     * @param ai the index from the first set (A)
     * @param bi the index from the second set (B)
     */
    public Pair(long ai, long bi) {
        this.ai = ai;
        this.bi = bi;
    }
}
