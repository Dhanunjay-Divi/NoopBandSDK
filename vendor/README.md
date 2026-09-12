# Local supplier drop

Place an immutable supplier package under `vendor/drop/` for local inspection.
The directory and all common binary formats are ignored by Git.

Do not edit the original package to remove comments or languages. Preserve its
exact bytes and record hashes so provenance remains auditable. Translate only
the NOOP-owned wrapper API, documentation, tests, and sample output.

Approved release binaries should eventually live in a restricted artifact
registry, not in this repository.
