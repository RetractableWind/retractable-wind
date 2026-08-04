library(data.table)
library(ggplot2)
#library(dplyr)
#library(stringr)

METRIC = "NetNorm"
#METRIC = "MQNetNorm"
OLA_SET = "Alpha"

regular_expression <- paste0("^ALL_OLA_", OLA_SET, ".*_", METRIC, "\\.csv")
path_to_files <- "~/retractable-wind/reports"

column_name_for_metric <- ifelse(METRIC == "MQNetNorm", "MarketNetNorm", METRIC) 
cols_to_load <- c("station", paste0("d", column_name_for_metric))
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

# shorten_labels <- function(text) {
#   case_when(
#     str_detect(text, "Static-") ~ "S",
#     str_detect(text, "Static_with") ~ "S*",
#     str_detect(text, "Fuzzy-") ~ "F",
#     str_detect(text, "Fuzzy_with") ~ "F*",
#     str_detect(text, "Aging-") ~ "A",
#     str_detect(text, "Aging_with") ~ "A*",
#     TRUE ~ "default_string"
#   )
# }

p <- ggplot(combined_data, aes(y = factor(source_file), x = .data[[cols_to_load[COL_FOR_BOXPLOT_Y_AXIS]]])) +
   geom_boxplot(fill = "lightblue", color = "black")  +
   theme(aspect.ratio = 0.5) +
   # scale_y_discrete(labels = shorten_labels)
   labs(x = column_name_for_metric, y = "Algorithm") # Changes the x-axis title
  
boxplot_output_filename <- paste0("boxplot_", OLA_SET, "_", METRIC, ".png")
ggsave(boxplot_output_filename, plot = p,
       width = 10,    # Increase width to fit labels
       height = 4,    # Adjust height as needed
       units = "in",  # Options: "in", "cm", "mm", "px"
       dpi = 300)     # High resolution for print


# Print confirmation
print(paste("Boxplot generated and saved as" , boxplot_output_filename))