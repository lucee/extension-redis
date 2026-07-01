#!/bin/bash

PROJECT_DIR="$(dirname "$0")"

cd "$PROJECT_DIR" || {
    echo "Failed to navigate to project directory: $PROJECT_DIR"
    exit 1
}

if [[ ! -f "pom.xml" ]]; then
    echo "No pom.xml found in the project directory: $PROJECT_DIR"
    exit 1
fi

echo "Running Maven clean install in: $PROJECT_DIR"
mvn clean install -Dgoal=install

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo "Maven build completed successfully."
else
    echo "Maven build failed with exit code $EXIT_CODE."
fi

exit $EXIT_CODE
