#!/bin/bash

DEPENDENCY_DIR="$1"
M2_REPO="${HOME}/.m2/repository"
COPIED_TRACKER="${DEPENDENCY_DIR}/.parent_poms_copied"

echo "========================================"
echo "Copying parent POMs from local Maven repository"
echo "========================================"

mkdir -p "$DEPENDENCY_DIR"
> "$COPIED_TRACKER"

is_already_copied() {
    grep -qx "$1" "$COPIED_TRACKER" 2>/dev/null
}

mark_as_copied() {
    echo "$1" >> "$COPIED_TRACKER"
}

copy_parent_chain() {
    pom_file="$1"

    if [ ! -f "$pom_file" ]; then
        return
    fi

    parent_block=$(sed -n '/<parent>/,/<\/parent>/p' "$pom_file" | tr '\n' ' ')

    if [ -z "$parent_block" ]; then
        return
    fi

    parent_group=$(echo "$parent_block" | sed -n 's/.*<groupId>\([^<]*\)<\/groupId>.*/\1/p' | head -1)
    parent_artifact=$(echo "$parent_block" | sed -n 's/.*<artifactId>\([^<]*\)<\/artifactId>.*/\1/p' | head -1)
    parent_version=$(echo "$parent_block" | sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' | head -1)

    if [ -z "$parent_group" ] || [ -z "$parent_artifact" ] || [ -z "$parent_version" ]; then
        return
    fi

    pom_key="${parent_group}__${parent_artifact}__${parent_version}"

    if is_already_copied "$pom_key"; then
        return
    fi

    parent_path=$(echo "$parent_group" | tr '.' '/')
    parent_pom="$M2_REPO/$parent_path/$parent_artifact/$parent_version/$parent_artifact-$parent_version.pom"
    target_dir="$DEPENDENCY_DIR/$parent_path/$parent_artifact/$parent_version"

    if [ -f "$parent_pom" ]; then
        mkdir -p "$target_dir"
        cp "$parent_pom" "$target_dir/"
        echo "✓ Copied: $parent_artifact:$parent_version"
        mark_as_copied "$pom_key"
        copy_parent_chain "$parent_pom"
    else
        echo "✗ NOT FOUND in local repo: $parent_artifact:$parent_version"
        echo "  Expected at: $parent_pom"
    fi
}

find "$DEPENDENCY_DIR" -name "*.pom" -type f | while read pom; do
    copy_parent_chain "$pom"
done

rm -f "$COPIED_TRACKER"

echo "========================================"
echo "Parent POM copy complete"
echo "========================================"
