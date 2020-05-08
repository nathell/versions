library(ggplot2)

data.g1 <- read.csv("graph1.csv")
data.g1$version = factor(data.g1$version, levels = unique(data.g1$version))
levels(data.g1$version)[1] <- "N/A"

g1 <- ggplot(data.g1, aes(x = version, y = count, fill = deps.type)) + geom_col() + theme_bw(base_size = 18) +
    xlab("Clojure version") + ylab("Number of project definitions") +
    scale_fill_discrete(name = "Tooling") +
    theme(panel.background = element_rect(fill = "#F5EBB0", colour = NA),
          plot.background = element_rect(fill = "#F5EBB0", colour = NA),
          legend.background = element_rect(fill = "#F5EBB0", colour = NA),
          panel.grid.major = element_line(size = 0.2, linetype = 'solid', colour = "#eddc78"),
          panel.grid.minor = element_line(size = 0.1, linetype = 'solid', colour = "#eddc78"),
          axis.text.x = element_text(angle = 45, hjust = 1))

ggsave("graph1.png", g1, width = 10, height = 7, units = "in")
