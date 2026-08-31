# Sample Dataset

Six short, synthetic documents for a fictional company ("Acme Robotics"),
used across the curriculum wherever a day needs something to upload,
chunk, embed, or query. Each covers a distinct, non-overlapping topic with
specific facts (numbers, dates, thresholds) so retrieval accuracy is easy
to verify by eye — if you ask a question about the expense policy, the
answer should come from `expense-reimbursement-policy.pdf`, not one of
the others.

Deliberately PDF-heavy (4 of 6 files), including one with **no text layer
at all**, because scanned-image PDFs are a classic real-world RAG failure
mode: a naive Tika/`AutoDetectParser` call returns empty text for them,
and the fix (OCR) is a separate step, not automatic. Day 6-7 has him
upload it and observe the empty extraction directly, rather than reading
about the problem secondhand.

- `remote-work-policy.txt` — eligibility (90 days), $500 equipment
  stipend, 10am-3pm core hours, 4-hour Slack response window, 6-month
  review cadence.
- `api-changelog.txt` — four dated version entries (v2.0.0-v2.3.0) with
  specific endpoint and rate-limit changes.
- `expense-reimbursement-policy.pdf` — 30-day submission deadline, $150
  auto-approval threshold, $75/day travel per diem, non-reimbursable
  items, 10-business-day payout. Real text-layer PDF (generated directly,
  extracts cleanly via Tika/`pdftotext`).
- `onboarding-checklist.pdf` — day-1/week-1/30-day/90-day milestones,
  onboarding buddy, access review. Real text-layer PDF.
- `incident-response-runbook.pdf` — severity levels (Sev1-Sev4),
  escalation timing, postmortem requirement (5 business days), status
  page update timing (10 minutes). Real text-layer PDF.
- `vendor-contract-summary-scanned.pdf` — vendor name, contract dates,
  liability cap, renewal notice period. **Image-only PDF, no text
  layer** — rendered as a picture of a page, the way a phone-scanned
  paper document would come out. `pdftotext`/Tika's default extraction
  returns nothing for this file; that's the point, not a bug in the
  dataset.

All content is fictional and written for this project — not sourced from
any real company.
