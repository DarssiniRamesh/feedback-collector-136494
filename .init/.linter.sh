#!/bin/bash
cd /home/kavia/workspace/code-generation/feedback-collector-136494/frontend_feedback_app
./gradlew lint
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

