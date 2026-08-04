#!/usr/bin/env python3
import argparse

parser = argparse.ArgumentParser(description="Extract results from log file")

parser.add_argument('logname', help="The name of the input log file in the ../logs directory")

#parser.add_argument('-L', '--LaTeX', help="Option to keep ampersands for LaTeX table")

args = parser.parse_args()

logfile_path = f"../logs/{args.logname}"

logfile_base = args.logname.removesuffix(".log")
output_netnorm = f"../reports/{logfile_base}_NetNorm.csv"
output_mqnetnorm = f"../reports/{logfile_base}_MQNetNorm.csv"

HEADER_START_STRING = "station,&"
RESULTS_LINE_PREFIX = "results: "

with (
        open(logfile_path, "r", encoding="utf-8") as src, 
        open(output_netnorm, "w", encoding="utf-8") as f1, 
        open(output_mqnetnorm, "w", encoding="utf-8") as f2
):
    file_iter = iter(src)
    # copy header to output files
    for line in file_iter:

        if line.startswith(HEADER_START_STRING):
            header = line.replace("&,", "")
            # remove any trailing comma from end of the line
            cleaned_header = header.rstrip(',\n\r') + '\n'
            f1.write(cleaned_header)
            f2.write(cleaned_header)
            break

    is_net_norms_turn = True 

    for line in file_iter:
        if line.startswith(RESULTS_LINE_PREFIX):
            if is_net_norms_turn:
                f1.write(line.removeprefix(RESULTS_LINE_PREFIX).replace("&,", ""))
                is_net_norms_turn = False
            else:
                f2.write(line.removeprefix(RESULTS_LINE_PREFIX).replace("&,", ""))
                is_net_norms_turn = True
