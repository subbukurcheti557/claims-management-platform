# AI Working Journal

Running log of what was asked of the AI (Claude Code), what was accepted, challenged and overridden — with reasoning.
Entries are appended in order; not polished on purpose.

---

## 2026-11-15 — Entry 1: Requirements extraction and architecture plan

**Asked:** Read the assessment brief (.docx), the drive deck (.pptx) and a video; act as solution architect; design Java 17 /
Spring Boot microservices (3 business services, gateway, Eureka, Kafka, Postgres, JWT) + Angular, and produce a complete plan and flow before coding.

**AI did:**
- Extracted text from the docx/pptx (the .mp4 could not be processed — flagged rather than guessed).
- Pointed out that the rubric slide weighs *MFE architecture* at 15 pts → proposed Native Federation (shell + claimant + staff remotes) instead of a single SPA.
- Proposed the service split claims / policy / notification (+ identity as a platform service), REST for coverage check, Kafka for lifecycle events, transactional outbox.

**Accepted:** _(fill in after review)_
**Challenged:** _(e.g. whether identity counts as one of the 3 business services; HS256 vs RS256; refuse-FNOL-on-policy-outage)_
**Overrode:** _(record any decision changed by the developer and why)_

---
