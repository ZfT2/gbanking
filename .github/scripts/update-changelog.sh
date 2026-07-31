#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:?VERSION is required}"
CHANGELOG_FILE="${CHANGELOG_FILE:-CHANGELOG.md}"
RELEASE_NOTES_FILE="${RELEASE_NOTES_FILE:-release-notes.md}"
RELEASE_DATE="${RELEASE_DATE:-$(date +%F)}"
TARGET_TAG="v${VERSION}"

find_previous_release_tag() {
  git tag --merged HEAD --sort=-v:refname \
    | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' \
    | grep -v "^${TARGET_TAG}$" \
    | head -n 1 || true
}

normalize_commit_subject() {
  sed -E \
    -e 's/[[:space:]]+/ /g' \
    -e 's/[[:space:]]+\(#[0-9]+\)$//' \
    -e 's/[[:space:]]+\[[^]]+\]$//' \
    -e 's/^[[:space:]]+//' \
    -e 's/[[:space:]]+$//'
}

commit_category() {
  local type="$1"
  local breaking="$2"

  if [[ "$breaking" == "!" ]]; then
    echo "Breaking Changes"
    return
  fi

  case "$type" in
    feat|feature) echo "Added" ;;
    fix) echo "Fixed" ;;
    security) echo "Security" ;;
    perf|refactor|style) echo "Changed" ;;
    docs) echo "Documentation" ;;
    test) echo "Tests" ;;
    build|ci) echo "Build" ;;
    chore) echo "Maintenance" ;;
    revert) echo "Reverted" ;;
    *) echo "Other" ;;
  esac
}

format_commit_subject() {
  local subject="$1"
  local conventional_commit_regex='^([[:alpha:]]+)(\([^)]+\))?(!)?:[[:space:]]*(.+)$'

  if [[ "$subject" =~ $conventional_commit_regex ]]; then
    local type="${BASH_REMATCH[1]:-}"
    local scope="${BASH_REMATCH[2]:-}"
    local breaking="${BASH_REMATCH[3]:-}"
    local message="${BASH_REMATCH[4]:-}"
    local category
    type="${type,,}"
    category="$(commit_category "$type" "$breaking")"

    if [[ -n "$scope" ]]; then
      scope="${scope:1:${#scope}-2}"
      message="${scope}: ${message}"
    fi

    printf '%s\t%s\n' "$category" "$message"
  else
    printf 'Other\t%s\n' "$subject"
  fi
}

should_skip_commit() {
  local subject="$1"
  local version_bump_regex='^chore(\([^)]*\))?:[[:space:]]+(next|prepare)[[:space:]]+development[[:space:]]+version[[:space:]]'
  local changelog_update_regex='^chore(\([^)]*\))?:[[:space:]]+update[[:space:]]+changelog'

  [[ "$subject" =~ $version_bump_regex ]] || [[ "$subject" =~ $changelog_update_regex ]]
}

write_release_notes() {
  local previous_tag="$1"
  local range=()

  if [[ -n "$previous_tag" ]]; then
    range=("${previous_tag}..HEAD")
  fi

  {
    printf '## [%s] - %s\n\n' "$VERSION" "$RELEASE_DATE"

    git log --no-merges --format='%s' "${range[@]}" \
      | while IFS= read -r subject; do
          subject="$(printf '%s' "$subject" | normalize_commit_subject)"
          if [[ -n "$subject" ]] && ! should_skip_commit "$subject"; then
            format_commit_subject "$subject"
          fi
        done \
      | awk -F '\t' '
          {
            key = tolower($1 "|" $2)
            if (!seen[key]++) {
              categories[$1] = categories[$1] $2 "\n"
            }
          }
          END {
            orderCount = split("Breaking Changes|Added|Fixed|Security|Changed|Documentation|Tests|Build|Maintenance|Reverted|Other", order, "|")
            hasEntries = 0
            for (i = 1; i <= orderCount; i++) {
              category = order[i]
              if (categories[category] != "") {
                hasEntries = 1
                print "### " category
                entryCount = split(categories[category], entries, "\n")
                for (j = 1; j <= entryCount; j++) {
                  if (entries[j] != "") {
                    print "- " entries[j]
                  }
                }
                print ""
              }
            }
            if (!hasEntries) {
              print "### Changed"
              print "- No notable changes."
              print ""
            }
          }
        '
  } > "$RELEASE_NOTES_FILE"
}

update_changelog() {
  local temp_file
  temp_file="$(mktemp)"

  if [[ ! -s "$RELEASE_NOTES_FILE" ]]; then
    echo "Release notes file is missing or empty: $RELEASE_NOTES_FILE"
    exit 1
  fi

  awk -v notes="$RELEASE_NOTES_FILE" -v version="$VERSION" '
    BEGIN {
      inserted = 0
      skipping = 0
      skippingDuplicate = 0
    }
    /^## \[Unreleased\]/ {
      print
      print ""
      while ((getline line < notes) > 0) {
        print line
      }
      close(notes)
      inserted = 1
      skipping = 1
      next
    }
    skipping && /^## \[/ {
      skipping = 0
    }
    !skipping && $0 ~ "^## \\[" version "\\]" {
      skippingDuplicate = 1
      next
    }
    skippingDuplicate && /^## \[/ {
      skippingDuplicate = 0
    }
    !skipping && !skippingDuplicate {
      print
    }
    END {
      if (!inserted) {
        print ""
        while ((getline line < notes) > 0) {
          print line
        }
        close(notes)
      }
    }
  ' "$CHANGELOG_FILE" > "$temp_file"

  mv "$temp_file" "$CHANGELOG_FILE"
}

if [[ "${USE_EXISTING_RELEASE_NOTES:-false}" == "true" ]]; then
  echo "Updating ${CHANGELOG_FILE} from existing ${RELEASE_NOTES_FILE}"
else
  previous_tag="$(find_previous_release_tag)"
  if [[ -n "$previous_tag" ]]; then
    echo "Generating changelog from ${previous_tag} to HEAD"
  else
    echo "Generating changelog from all commits"
  fi

  write_release_notes "$previous_tag"
fi

update_changelog
