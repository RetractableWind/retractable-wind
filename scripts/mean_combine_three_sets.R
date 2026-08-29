library(data.table)
library(ggplot2)

#METRIC = "NetNorm"; ola_num_vector <- c("1", "3", "5")
METRIC = "MQNetNorm"; ola_num_vector <- c("2", "4", "6") 
OLA_SET = "Beta"
ola_set_vector <- c("Alpha", "Beta", "Gamma")

#regular_expression <- paste0("^specialALL_OLA_", OLA_SET, ".*_", METRIC, "\\.csv")
regular_expression <- paste0("^ALL_OLA_", OLA_SET, ".*_", METRIC, "\\.csv")
path_to_files <- "~/retractable-wind/reports"

column_name_for_metric <- paste0("d", ifelse(METRIC == "MQNetNorm", "MarketNetNorm", METRIC)) 
cols_to_load <- c("station", column_name_for_metric)
COL_FOR_BOXPLOT_Y_AXIS <- 2

bFirstIteration = TRUE

for (i in seq_along(ola_set_vector)) {

    regular_expression <- paste0("^ALL_OLA_", ola_set_vector[i], ".*_", METRIC, "\\.csv")

# Get list of files
files <- sort(list.files(path = path_to_files, pattern = regular_expression, full.names = TRUE), decreasing = TRUE)

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

if (bFirstIteration) {
   accumulator <- paste0(all_means$source_file, ", &, ", round(all_means$mean_value, digits = 3))
} else { 
   accumulator <- paste0(accumulator, ", &, ", all_means$source_file, ", &, ", round(all_means$mean_value, digits = 3))
}

bFirstIteration = FALSE

}

shorten_labels <- function(text) {
  case_when(
    str_detect(text, "Static-") ~ "S",
    str_detect(text, "Static_with") ~ "S*",
    str_detect(text, "Fuzzy-") ~ "F",
    str_detect(text, "Fuzzy_with") ~ "F*",
    str_detect(text, "Aging-") ~ "A",
    str_detect(text, "Aging_with") ~ "A*",
    TRUE ~ "default_string"
  )
}

sink(paste0("mean_output_", METRIC, ".csv"))
print(accumulator)
sink()