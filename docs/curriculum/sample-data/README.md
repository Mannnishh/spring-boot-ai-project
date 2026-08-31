# Sample Dataset

Five short, synthetic documents for a fictional company ("Acme Robotics"),
used across the curriculum wherever a day needs something to upload,
chunk, embed, or query. Each covers a distinct, non-overlapping topic with
specific facts (numbers, dates, thresholds) so retrieval accuracy is easy
to verify by eye — if you ask a question about the expense policy, the
answer should come from `expense-reimbursement-policy.txt`, not one of
the others.

- `remote-work-policy.txt` — eligibility (90 days), $500 equipment
  stipend, 10am-3pm core hours, 4-hour Slack response window, 6-month
  review cadence.
- `expense-reimbursement-policy.txt` — 30-day submission deadline, $150
  auto-approval threshold, $75/day travel per diem, non-reimbursable
  items, 10-business-day payout.
- `onboarding-checklist.txt` — day-1/week-1/30-day/90-day milestones,
  onboarding buddy, access review.
- `api-changelog.txt` — four dated version entries (v2.0.0-v2.3.0) with
  specific endpoint and rate-limit changes.
- `incident-response-runbook.pdf` — severity levels (Sev1-Sev4),
  escalation timing, postmortem requirement (5 business days), status
  page update timing (10 minutes). Kept as a PDF specifically to exercise
  Tika's PDF parsing alongside the `.txt` files.

All content is fictional and written for this project — not sourced from
any real company.
