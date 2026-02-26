#!/usr/bin/env bash
#
# ClipVault License Key Generator
# Usage: ./generate-license-key.sh <email>
#
# Algorithm: HMAC-SHA256(email.lowercase().trim(), SECRET) -> first 8 bytes -> hex uppercase -> XXXX-XXXX-XXXX-XXXX
#

SECRET_HEX="4376246b52396d5078324c71376e5766"

if [ -z "$1" ]; then
    echo "Usage: $0 <email>"
    echo "Example: $0 test@example.com"
    exit 1
fi

EMAIL=$(echo -n "$1" | tr '[:upper:]' '[:lower:]' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

RAW=$(echo -n "$EMAIL" | openssl dgst -sha256 -mac HMAC -macopt hexkey:"$SECRET_HEX" -hex 2>/dev/null | awk '{print $NF}')

HEX16=$(echo "$RAW" | cut -c1-16 | tr '[:lower:]' '[:upper:]')

KEY="${HEX16:0:4}-${HEX16:4:4}-${HEX16:8:4}-${HEX16:12:4}"

echo "$EMAIL -> $KEY"
