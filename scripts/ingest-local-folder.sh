#!/usr/bin/env bash
# Ingest a local folder of .md, .txt, .html or .pdf files into the running chat-assistant.
# Usage: ./scripts/ingest-local-folder.sh /path/to/docs

set -euo pipefail
DIR="${1:-./sample-docs}"
API="${API:-http://localhost:8080}"

if [ ! -d "$DIR" ]; then
  echo "Not a folder: $DIR"
  exit 1
fi

count=0
find "$DIR" -type f \( -name "*.md" -o -name "*.txt" -o -name "*.html" \) | while read -r f; do
  title=$(basename "$f")
  content=$(cat "$f" | jq -Rs .)
  url="file://$f"
  payload=$(jq -n \
    --arg id "$(uuidgen)" \
    --arg url "$url" \
    --arg title "$title" \
    --arg content "$(cat "$f")" \
    --arg src "local" \
    '{ id:$id, sourceUrl:$url, sourceType:$src, title:$title, content:$content, fetchedAt:"1970-01-01T00:00:00Z", metadata:{} }')
  curl -sS -X POST "$API/admin/ingest/text" \
    -H 'Content-Type: application/json' \
    -d "$payload" > /dev/null
  count=$((count+1))
  echo "ingested: $f"
done

find "$DIR" -type f -name "*.pdf" | while read -r f; do
  curl -sS -X POST "$API/admin/ingest/pdf" \
    -F "file=@$f" > /dev/null
  echo "ingested pdf: $f"
done

echo "done."
