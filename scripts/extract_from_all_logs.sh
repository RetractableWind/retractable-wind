#!/bin/bash

directory="../logs"

for file in "$directory"/*.log; do
    if [ -f "$file" ]; then
        echo "Inputting: $file"
        python3 extract_results_from_log.py $(basename "$file")
    fi
done   

echo "finished"
