#!/usr/bin/env bash
# Start the bank simulator and gateway first. Run this script from Git Bash,
# WSL, Linux, or macOS. Requires curl and jq.
set -euo pipefail

# Change this URL if your gateway runs on a different port.
base_url="http://localhost:8090"
expiry_year=$(( $(date +%Y) + 1 ))

for command in curl jq; do
  command -v "$command" >/dev/null || { echo "Missing dependency: $command"; exit 1; }
done

# curl saves the JSON body here, so we can check it separately from the HTTP code.
response_file=$(mktemp)
trap 'rm -f "$response_file"' EXIT

# One small helper: stop the script if an actual value differs from what we expect.
assert_equal() {
  if [[ "$1" != "$2" ]]; then
    echo "FAIL: $3 (expected '$2', got '$1')"
    exit 1
  fi
}

# A valid request shared by the tests. The expiry year is always next year.
payment=$(cat <<JSON
{
  "card_number": "4111111111110001",
  "expiry_month": 12,
  "expiry_year": $expiry_year,
  "currency": "USD",
  "amount": 100,
  "cvv": "123"
}
JSON
)

# Test 1: A card ending in 1 should be authorized by the bank.
# -sS: hide progress but show errors; --max-time: don't wait forever.
# -o: save the body; -w: print only the HTTP status into http_status.
# -H: say that we are sending JSON; -d: send the request body.
echo 'Test 1: Create an authorized payment'
http_status=$(curl -sS --max-time 10 -o "$response_file" -w '%{http_code}' \
  -X POST "$base_url/api/payment" \
  -H 'Content-Type: application/json' -d "$payment")
assert_equal "$http_status" '201' 'Create payment HTTP status'
assert_equal "$(jq -rj '.status' "$response_file")" 'Authorized' 'Payment status'
assert_equal "$(jq -rj '.cardNumberLastFour' "$response_file")" '0001' 'Masked card number'
# jq reads a field from JSON. -rj returns plain text without quotes or a newline
# (including no Windows carriage return when using native jq in Git Bash).
payment_id=$(jq -erj '.id | select(type == "string" and length > 0)' "$response_file")
created_payment=$(jq -cS . "$response_file")
echo 'PASS: Payment authorized and card number masked'

# Test 2: Use the ID returned by Test 1 to retrieve the stored payment.
echo 'Test 2: Retrieve the authorized payment'
http_status=$(curl -sS --max-time 10 -o "$response_file" -w '%{http_code}' \
  "$base_url/api/payment/$payment_id")
assert_equal "$http_status" '200' 'Retrieve payment HTTP status'
# -cS makes compact JSON with sorted keys, so field order does not affect comparison.
assert_equal "$(jq -cS . "$response_file")" "$created_payment" 'Stored payment matches created payment'
echo 'PASS: Stored payment matches the payment we created'

# Test 3: Change only the card number. A card ending in 2 should be declined.
echo 'Test 3: Create a declined payment'
declined_payment=$(echo "$payment" | jq '.card_number = "4111111111110002"')
http_status=$(curl -sS --max-time 10 -o "$response_file" -w '%{http_code}' \
  -X POST "$base_url/api/payment" \
  -H 'Content-Type: application/json' -d "$declined_payment")
assert_equal "$http_status" '201' 'Declined payment HTTP status'
assert_equal "$(jq -rj '.status' "$response_file")" 'Declined' 'Payment status'
echo 'PASS: Bank declined the payment'

# Test 4: Change only the amount. Zero is invalid, so the gateway should reject it.
echo 'Test 4: Reject a zero amount'
invalid_payment=$(echo "$payment" | jq '.amount = 0')
http_status=$(curl -sS --max-time 10 -o "$response_file" -w '%{http_code}' \
  -X POST "$base_url/api/payment" \
  -H 'Content-Type: application/json' -d "$invalid_payment")
assert_equal "$http_status" '400' 'Invalid payment HTTP status'
assert_equal "$(jq -rj 'has("id")' "$response_file")" 'false' 'Invalid payment has no ID'
echo 'PASS: Invalid payment rejected'

# Test 5: A well-formed UUID that was never submitted should not be found.
echo 'Test 5: Look up an unknown payment'
http_status=$(curl -sS --max-time 10 -o "$response_file" -w '%{http_code}' \
  "$base_url/api/payment/00000000-0000-0000-0000-000000000000")
assert_equal "$http_status" '404' 'Unknown payment HTTP status'
echo 'PASS: Unknown payment not found'

echo 'All 5 end-to-end tests passed.'
