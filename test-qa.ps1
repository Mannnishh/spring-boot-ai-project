# test-qa.ps1 - Automated verification for Day 18

$baseUrl = "http://localhost:8080"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " 1. Authenticating to obtain JWT Token" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$loginBody = @{
    username = "demo"
    password = "demopassword"
} | ConvertTo-Json

try {
    $loginRes = Invoke-RestMethod -Uri "$baseUrl/login" -Method Post -ContentType "application/json" -Body $loginBody
    $token = $loginRes.token
    Write-Host "Authentication Successful! Token acquired." -ForegroundColor Green
} catch {
    Write-Host "Authentication Failed! Is the Spring Boot app running on port 8080?" -ForegroundColor Red
    exit 1
}

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host " 2. Uploading Sample Documents" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$sampleDataDir = "docs\curriculum\sample-data"
$files = Get-ChildItem -Path "$sampleDataDir\*.*" -Include *.txt, *.pdf

foreach ($file in $files) {
    Write-Host "Uploading $($file.Name)... " -NoNewline
    $uploadOutput = curl.exe -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $token" -F "file=@$($file.FullName)" "$baseUrl/documents/upload"
    if ($uploadOutput -eq "201") {
        Write-Host "Uploaded (201 Created)" -ForegroundColor Green
    } else {
        Write-Host "Status: $uploadOutput" -ForegroundColor Yellow
    }
}

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host " 3. Running Grounding Verification Questions" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

$testCases = @(
    @{
        Number = 1
        Question = "What is the equipment stipend for remote work?"
        Expected = "$500 (remote-work-policy.txt)"
    },
    @{
        Number = 2
        Question = "How many requests per minute is the API rate limit?"
        Expected = "60 requests per minute (api-changelog.txt)"
    },
    @{
        Number = 3
        Question = "Who assigns the onboarding buddy, and when?"
        Expected = "The hiring manager, in Week 1 (onboarding-checklist.pdf)"
    },
    @{
        Number = 4
        Question = "What is the travel per diem?"
        Expected = "$75 per day (expense-reimbursement-policy.pdf)"
    },
    @{
        Number = 5
        Question = "How soon after a Sev1 incident is resolved must the postmortem be written?"
        Expected = "Within 5 business days (incident-response-runbook.pdf)"
    },
    @{
        Number = 6
        Question = "How quickly must the engineering director be notified of a Sev1 incident?"
        Expected = "Within 15 minutes of detection (incident-response-runbook.pdf)"
    },
    @{
        Number = 7
        Question = "Which expenses need manager approval before payout?"
        Expected = "Expenses of $150 or more (expense-reimbursement-policy.pdf)"
    },
    @{
        Number = 8
        Question = "What is the liability cap in the vendor contract?"
        Expected = "I don't have enough information to answer that. (vendor-contract-summary-scanned.pdf has no OCR)"
    }
)

$markdownOutput = @"
# Sample Questions and Answers (Grounding Cache)

Recorded $(Get-Date -Format 'yyyy-MM-dd') against the sample documents in docs/curriculum/sample-data/.
Model output varies between runs. These are a reference and a fallback, not a test.

"@

foreach ($item in $testCases) {
    Write-Host "`n--- [Question $($item.Number)] $($item.Question) ---" -ForegroundColor Yellow
    Write-Host "Expected Fact : $($item.Expected)" -ForegroundColor DarkGray
    
    $body = @{ question = $item.Question } | ConvertTo-Json
    
    try {
        $ans = Invoke-RestMethod -Uri "$baseUrl/ask" -Method Post -Headers $headers -Body $body
        Write-Host "Answer        : $($ans.answer)" -ForegroundColor Green
        Write-Host "Citations     : $($ans.citedDocuments -join ', ')" -ForegroundColor Cyan

        $markdownOutput += @"

## $($item.Number). $($item.Question)
**Expected key fact:** $($item.Expected)  
**Answer returned:** $($ans.answer)  
**Cited documents:** $($ans.citedDocuments -join ', ')  

"@
    } catch {
        Write-Host "Error calling /ask: $_" -ForegroundColor Red
    }

    # Respect rate limits between calls
    Start-Sleep -Seconds 2
}

$markdownOutput | Out-File -FilePath "sample-qa.md" -Encoding utf8
Write-Host "`n==========================================" -ForegroundColor Green
Write-Host " All done! Results automatically saved to sample-qa.md" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
