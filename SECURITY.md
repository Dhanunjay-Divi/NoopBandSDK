# Security

Report security issues privately to the project owner. Do not open a public
issue containing a vulnerability, credential, device identity, packet capture,
firmware image, or health data.

## Trust boundary

Supplier binaries and callbacks are untrusted input. The integration must:

- validate lengths, enums, ranges, ordering, and capability state;
- serialize device commands and reject stale callbacks;
- deny or detect unexpected network egress;
- exclude supplier persistence from NOOP authority;
- keep passwords, owner credentials, keys, and firmware material out of logs;
- use NOOP-owned provenance, deduplication, storage, and checkpoint rules;
- fail closed for unsupported or ambiguous capability;
- never adopt vendor medical labels as NOOP claims.

Production distribution requires an SBOM, notices, vulnerability review,
signed-artifact provenance, update ownership, and an independent mobile and
firmware security assessment.
