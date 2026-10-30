# Sample Questions and Answers (Grounding Cache)

Recorded 2026-10-30 against the sample documents in docs/curriculum/sample-data/.
Model output varies between runs. These are a reference and a fallback, not a test.

## 1. What is the equipment stipend for remote work?
**Expected key fact:**  (remote-work-policy.txt)  
**Answer returned:** The equipment stipend for remote employees is a oneâtime annual amount of **$500**.â¯[remote-work-policy.txt]  
**Cited documents:** remote-work-policy.txt, onboarding-checklist.pdf, expense-reimbursement-policy.pdf, api-changelog.txt  

## 2. How many requests per minute is the API rate limit?
**Expected key fact:** 60 requests per minute (api-changelog.txt)  
**Answer returned:** The API rate limit isâ¯**60 requests per minute** per API key.  
Source: [api-changelog.txt]  
**Cited documents:** api-changelog.txt, expense-reimbursement-policy.pdf, incident-response-runbook.pdf, remote-work-policy.txt  

## 3. Who assigns the onboarding buddy, and when?
**Expected key fact:** The hiring manager, in Week 1 (onboarding-checklist.pdf)  
**Answer returned:** The hiring manager assigns the onboarding buddy during the new hireâs first week.  

Source:â¯onboarding-checklist.pdf  
**Cited documents:** onboarding-checklist.pdf, remote-work-policy.txt, incident-response-runbook.pdf, api-changelog.txt, expense-reimbursement-policy.pdf  

## 4. What is the travel per diem?
**Expected key fact:**  per day (expense-reimbursement-policy.pdf)  
**Answer returned:** The travel per diem is **$75 per day**.

Source: [expense-reimbursement-policy.pdf]  
**Cited documents:** expense-reimbursement-policy.pdf, remote-work-policy.txt, onboarding-checklist.pdf, incident-response-runbook.pdf  

## 5. How soon after a Sev1 incident is resolved must the postmortem be written?
**Expected key fact:** Within 5 business days (incident-response-runbook.pdf)  
**Answer returned:** The postmortem must be written and submitted withinâ¯5â¯business days after the Sev1 incident is resolved. ãincident-response-runbook.pdfã  
**Cited documents:** incident-response-runbook.pdf, expense-reimbursement-policy.pdf, remote-work-policy.txt, onboarding-checklist.pdf  

## 6. How quickly must the engineering director be notified of a Sev1 incident?
**Expected key fact:** Within 15 minutes of detection (incident-response-runbook.pdf)  
**Answer returned:** The engineering director must be notified within **15â¯minutes** of detection of a Sev1 incident. ãincident-response-runbook.pdfã  
**Cited documents:** incident-response-runbook.pdf, remote-work-policy.txt, onboarding-checklist.pdf, expense-reimbursement-policy.pdf  

## 7. Which expenses need manager approval before payout?
**Expected key fact:** Expenses of  or more (expense-reimbursement-policy.pdf)  
**Answer returned:** Expenses that are **$150 or more** require explicit approval from the employeeâs manager before the payout is processed.

Source:â¯expense-reimbursement-policy.pdf  
**Cited documents:** expense-reimbursement-policy.pdf, remote-work-policy.txt, incident-response-runbook.pdf, onboarding-checklist.pdf  

## 8. What is the liability cap in the vendor contract?
**Expected key fact:** I don't have enough information to answer that. (vendor-contract-summary-scanned.pdf has no OCR)  
**Answer returned:** I don't have enough information to answer that.  
**Cited documents:** incident-response-runbook.pdf, expense-reimbursement-policy.pdf, remote-work-policy.txt, api-changelog.txt  

