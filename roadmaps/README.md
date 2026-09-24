# Roadmaps

Roadmaps are planning records. Their acceptance boxes preserve the proposal as it
was written and are not retroactively used as release-completion evidence.
Execution status is recorded in the sibling checklist when one exists and in the
matching `CHANGELOG.md` release section. An unchecked mutually exclusive no-go
branch after a go decision, or an item explicitly labeled deferred/superseded,
is historical context rather than active work.

V2 predates the separate execution-checklist convention and intentionally has no
`CHECKLIST.md`. V1-V32 are completed release records. V32 was released as `4.4.1`.
V33 is the active execution roadmap. The reactor is `4.5.0-SNAPSHOT`, with
F001/F002/F003 approved for correction and no next release scope selected.
Public/API/consumer/benchmark baselines are verified `4.4.1`.

[V33](v33/ROADMAP.md) and its [execution checklist](v33/CHECKLIST.md) revisit
supported-extension and AOT selection gaps V32-F001, V32-F002 and V32-F003.
The [Priority 2.3 decision](v33/FIX-DECISION.md) approves all three bounded
corrections. [Priority 3](v33/BUILDER-OWNERSHIP.md) implements F001 and
[Priority 4](v33/STATIC-METADATA.md) implements F002. F003 remains pending in
Priority 5. No release is selected.

[V32 publication and closure](v32/CLOSURE-EVIDENCE.md#post-publication-closure)
records signing, Central artifact verification and assembled-consumer evidence.
F004/F005 are implemented. V32 deferred F001-F003 with documented workarounds;
V33 now selects them for correction without reopening the V32 release record.
[Priority 10 evidence](v32/COMPATIBILITY-VERIFICATION.md) retains original
JVM/API/consumer/matrix/AOT/native results and failures. The
[maintainer entry point](v32/MAINTAINER-GUIDANCE.md) links the reviewed architecture,
extension guidance and operating limits. No new memory or performance claim is made.

Deferred design work that is not part of an active execution roadmap is kept
under [`proposals/`](proposals/). These proposals do not add release gates until
they are deliberately adopted by a future roadmap.

[V32](v32/ROADMAP.md) and its [completed checklist](v32/CHECKLIST.md) adopted the
[post-4.4.0 architecture review proposal](proposals/POST_4_4_ARCHITECTURE_REVIEW.md).
The proposal remains historical design input; deferred findings do not
automatically create another execution roadmap or reopen V32.

| Version | Roadmap | Checklist | Archive status |
|---|---|---|---|
| V1 | [Roadmap](v1/ROADMAP.md) | [Checklist](v1/CHECKLIST.md) | Completed before V2 |
| V2 | [Roadmap](v2/ROADMAP.md) | Not created (pre-convention) | Completed and released as `2.0.0` |
| V3 | [Roadmap](v3/ROADMAP.md) | [Checklist](v3/CHECKLIST.md) | Completed across `2.1.0` and `2.2.0` |
| V4 | [Roadmap](v4/ROADMAP.md) | [Checklist](v4/CHECKLIST.md) | Completed and released as `2.3.0` |
| V5 | [Roadmap](v5/ROADMAP.md) | [Checklist](v5/CHECKLIST.md) | Completed and released as `2.4.0` |
| V6 | [Roadmap](v6/ROADMAP.md) | [Checklist](v6/CHECKLIST.md) | Completed and released as `2.5.0` |
| V7 | [Roadmap](v7/ROADMAP.md) | [Checklist](v7/CHECKLIST.md) | Completed and released as `2.6.0` |
| V8 | [Roadmap](v8/ROADMAP.md) | [Checklist](v8/CHECKLIST.md) | Completed and released as `2.7.0` |
| V9 | [Roadmap](v9/ROADMAP.md) | [Checklist](v9/CHECKLIST.md) | Completed and released as `2.8.0` |
| V10 | [Roadmap](v10/ROADMAP.md) | [Checklist](v10/CHECKLIST.md) | Completed and released as `2.9.0` |
| V11 | [Roadmap](v11/ROADMAP.md) | [Checklist](v11/CHECKLIST.md) | Completed and released as `2.9.0` |
| V12 | [Roadmap](v12/ROADMAP.md) | [Checklist](v12/CHECKLIST.md) | Completed in the `2.10.0` release cycle |
| V13 | [Roadmap](v13/ROADMAP.md) | [Checklist](v13/CHECKLIST.md) | Completed in the `2.10.0` release cycle |
| V14 | [Roadmap](v14/ROADMAP.md) | [Checklist](v14/CHECKLIST.md) | Completed after the `2.10.0` release |
| V15 | [Roadmap](v15/ROADMAP.md) | [Checklist](v15/CHECKLIST.md) | Completed and released as `2.11.0` |
| V16 | [Roadmap](v16/ROADMAP.md) | [Checklist](v16/CHECKLIST.md) | Completed and released as `2.12.0` |
| V17 | [Roadmap](v17/ROADMAP.md) | [Checklist](v17/CHECKLIST.md) | Completed and released as `2.13.0` |
| V18 | [Roadmap](v18/ROADMAP.md) | [Checklist](v18/CHECKLIST.md) | Completed and released as `2.14.0` |
| V19 | [Roadmap](v19/ROADMAP.md) | [Checklist](v19/CHECKLIST.md) | Completed with a `3.0.0` no-go decision |
| V20 | [Roadmap](v20/ROADMAP.md) | [Checklist](v20/CHECKLIST.md) | Completed and released as `3.0.0` |
| V21 | [Roadmap](v21/ROADMAP.md) | [Checklist](v21/CHECKLIST.md) | Completed and released as `3.1.0` |
| V22 | [Roadmap](v22/ROADMAP.md) | [Checklist](v22/CHECKLIST.md) | Completed and released as `3.2.0` |
| V23 | [Roadmap](v23/ROADMAP.md) | [Checklist](v23/CHECKLIST.md) | Completed and released as `3.3.0` |
| V24 | [Roadmap](v24/ROADMAP.md) | [Checklist](v24/CHECKLIST.md) | Completed and released as `3.4.0` |
| V25 | [Roadmap](v25/ROADMAP.md) | [Checklist](v25/CHECKLIST.md) | Completed and released as `3.5.0` |
| V26 | [Roadmap](v26/ROADMAP.md) | [Checklist](v26/CHECKLIST.md) | Completed and released as `3.6.0` |
| V27 | [Roadmap](v27/ROADMAP.md) | [Checklist](v27/CHECKLIST.md) | Completed and released as `4.0.0` |
| V28 | [Roadmap](v28/ROADMAP.md) | [Checklist](v28/CHECKLIST.md) | Completed and released as `4.1.0` |
| V29 | [Roadmap](v29/ROADMAP.md) | [Checklist](v29/CHECKLIST.md) | Completed and released as `4.2.0` |
| V30 | [Roadmap](v30/ROADMAP.md) | [Checklist](v30/CHECKLIST.md) | Completed and released as `4.3.0` |
| V31 | [Roadmap](v31/ROADMAP.md) | [Checklist](v31/CHECKLIST.md) | Completed and released as `4.4.0` |
| V32 | [Roadmap](v32/ROADMAP.md) | [Checklist](v32/CHECKLIST.md) | Completed and released as `4.4.1` |
| V33 | [Roadmap](v33/ROADMAP.md) | [Checklist](v33/CHECKLIST.md) | Active |
