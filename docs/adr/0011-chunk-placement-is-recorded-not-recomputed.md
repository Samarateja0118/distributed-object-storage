# 11. Chunk placement is recorded, not recomputed

Date: 2026-07-30
Status: Accepted

## Context

With more than one blob node, every write has to choose a node and every read has
to find that node again. Content addressing makes a tempting shortcut available:
derive the location from the digest, and store no location at all.

That shortcut has a failure mode. If location is a pure function of the digest
and the node set, then changing the node set changes where existing chunks are
*said* to be, while the bytes stay where they were written. Reads break until a
rebalance completes, and a node added by mistake is an outage.

The alternative — an index as the sole authority, with free placement — keeps
nothing tying a chunk to a node, so placement policy can be anything, at the cost
of an index consulted on every read with no way to reconstruct it if lost.

## Decision

Rendezvous (highest-random-weight) hashing over a statically configured node set
chooses a chunk's preferred node. If that node is unavailable, the orchestrator
walks the ranking to the next healthy one.

The node actually written is recorded in `object_chunks.node_id`. Reads use the
record. Placement is never recomputed to locate a chunk.

Phase 2 writes one copy. Replication is Phase 3.

## Consequences

**A fallback write is indistinguishable from a normal one on read**, because the
read follows the record rather than the ranking. A node being down degrades
write availability for the chunks that hash to it, and nothing else. No repair
job is needed to make the system consistent afterward, because it was never
inconsistent.

**Rendezvous hashing rather than a ring with virtual nodes**: no ring state to
maintain or agree on, and better distribution at the small node counts this phase
will actually run. Its usual advantage — that adding a node remaps only 1/N of
keys — matters less here, since the mapping is advisory once a chunk is written.

**Placement drifts from what the hash would say**, by design. Every fallback
write is a chunk that is not where the ranking would put it. Rebalancing in Phase
3 must therefore work from the recorded placement and cannot assume derivability;
a rebalancer that recomputes locations would move the wrong chunks.

**`object_chunks` becomes load-bearing for durability, not just for lookup.**
Lose those rows and the bytes are unreachable even though every one of them still
exists on disk — content addressing does not save you, because nothing records
which digests composed which object. Metadata backup is now as important as blob
durability. This is the sharpest cost of the decision and the least obvious.

**A static node list means adding a node is a redeploy.** A dynamic registry
belongs with the admin service, which is still a scaffold; inventing one here
would put cluster membership in the component least ready to own it.

**One copy is not durability.** Losing a node's disk in Phase 2 loses data.
"Multi-node" in this phase means distributed, not redundant, and it is recorded
here so the two are not confused before Phase 3 closes the gap.
