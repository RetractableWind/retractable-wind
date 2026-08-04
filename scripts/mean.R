library(data.table)
library(ggplot2)

METRIC = "NetNorm"
#METRIC = "MQNetNorm"
OLA_SET = "Beta"

regular_expression <- paste0("^specialALL_OLA_", OLA_SET, ".*_", METRIC, "\\.csv")
#regular_expression <- paste0("^ALL_OLA_", OLA_SET, ".*_", METRIC, "\\.csv")
path_to_files <- "~/retractable-wind/reports"

column_name_for_metric <- paste0("d", ifelse(METRIC == "MQNetNorm", "MarketNetNorm", METRIC)) 
cols_to_load <- c("station", column_name_for_metric)
COL_FOR_BOXPLOT_Y_AXIS <- 2

# Get list of files
files <- list.files(path = path_to_files, pattern = regular_expression, full.names = TRUE)

# Read ONLY selected columns from each file and combine immediately
combined_data <- rbindlist(
  lapply(files, function(f) {
    # Read specific columns and add a 'source_file' column
    dt <- fread(f, select = cols_to_load)
    dt[, source_file := basename(f)]
    return(dt)
  }),
  use.names = TRUE,
  fill = TRUE # Fills with NA if columns are missing in some files
)

all_means <- combined_data[, .(mean_value = mean(get(column_name_for_metric))), by = source_file]

print(paste(all_means$source_file, " & ", round(all_means$mean_value, digits = 3)))

