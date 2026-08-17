library(data.table)
library(ggplot2)
library(patchwork)
library(dplyr)
library(stringr)


METRIC = "NetNorm"; ola_num_vector <- c("1", "3", "5")
#METRIC = "MQNetNorm"; ola_num_vector <- c("2", "4", "6") 
ola_set_vector <- c("Alpha", "Beta", "Gamma")


plot_list <- list()

path_to_files <- "~/retractable-wind/reports"
column_name_for_metric <- ifelse(METRIC == "MQNetNorm", "MarketNetNorm", METRIC) 
cols_to_load <- c("station", paste0("d", column_name_for_metric))
COL_FOR_BOXPLOT_Y_AXIS <- 2




for (i in seq_along(ola_set_vector)) {

    regular_expression <- paste0("^ALL_OLA_", ola_set_vector[i], ".*_", METRIC, "\\.csv")

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


#browser()

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

#browser() 

plot_list[[i]] <- ggplot(combined_data, aes(y = factor(source_file), x = .data[[cols_to_load[COL_FOR_BOXPLOT_Y_AXIS]]])) +
   geom_boxplot(fill = "lightblue", color = "black")  +
   theme(aspect.ratio = 0.5) +
   scale_y_discrete(labels = shorten_labels) +
   labs(x = paste0("OLA ", ola_num_vector[i], ": ", METRIC), y = "Algorithm") # Changes the x-axis title

}

combined_plots <- wrap_plots(plot_list, ncol = length(plot_list))

OUTPUT_FILENAME <- paste0("boxplots_combined_", METRIC, ".pdf")

cairo_pdf(OUTPUT_FILENAME, width = 7, height = 5, family = "TeX Gyre Termes")

print(combined_plots)

dev.off()

system(paste("pdfcrop", OUTPUT_FILENAME, OUTPUT_FILENAME))

# Print confirmation
#print(paste("Combined boxplots saved as" , OUTPUT_FILENAME))