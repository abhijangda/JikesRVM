# python 2.7
# may require numpy and matplotlib installation

import numpy as np
import matplotlib.pyplot as plt
import os
import csv
import sys

# Output directory (relative to working dir)
graphs_dir = 'figures'

# Create output directory
if not os.path.exists(graphs_dir):
    os.makedirs(graphs_dir)

basename = '4'
csv_dir = sys.argv[1]
benchmarks = ['avrora', 'lusearch', 'jython', 'sunflow']
threadcount = [1, 2, 4, 8, 16, 32]

subplot_cols = 2
subplot_rows = len(benchmarks) / subplot_cols
if len(benchmarks) % subplot_cols > 0:
    subplot_rows += 1

fig = plt.figure()

i = 0
for b in benchmarks:
    # Select the current sub plot
    ax = plt.subplot2grid((subplot_rows, subplot_cols), (i / subplot_cols, i % subplot_cols))
    i += 1

    # get data from csv file
    csvname = b + '.csv'
    raw_data = np.recfromcsv(os.path.join(csv_dir, csvname))

    # get column names, giving size of batch used in that series
    names = raw_data.dtype.names
    print names

    # Add to the X, Y series for each column after the first
    X, Y_base, Y_write, Y_optcompile, Yerr_base, Yerr_write, Yerr_optcompile = [], [], [], [], [], [], []

    for t in threadcount:
        # get the column name by its position
        name_base       = "base%d" % t
        name_write      = "aosdb%d" % t
        name_optcompile = "aosdboptcompile%d" % t

        if not name_base in names:
            print name_base, "not found"
            continue

        # add threadcount to X axis
        X += [t]

        # Filter out -1 values and scale to seconds.
        values_base = raw_data[name_base]
        values_base = values_base[values_base > 0] / 1000.0

        values_write = raw_data[name_write]
        values_write = values_write[values_write > 0] / 1000.0

        values_optcompile = raw_data[name_optcompile]
        values_optcompile = values_optcompile[values_optcompile > 0] / 1000.0

        # calculate the mean and standard deviation
        Y_base       += [np.mean(values_base)]
        Y_write      += [np.mean(values_write)]
        Y_optcompile += [np.mean(values_optcompile)]

        Yerr_base       += [np.std(values_base)]
        Yerr_write      += [np.std(values_write)]
        Yerr_optcompile += [np.std(values_optcompile)]

    # Draw bars with errors
    plot_base       = ax.errorbar(X, Y_base, color='#CC6666', yerr=Yerr_base, marker=".")
    plot_write      = ax.errorbar(X, Y_write, color='#66CC66', yerr=Yerr_write, marker=".")
    plot_optcompile = ax.errorbar(X, Y_optcompile, color='#6666CC', yerr=Yerr_optcompile, marker=".")

    # set sub plot scales
    ax.set_xscale('log')
    ax.set_ylim(0)

    # set sub plot title and axis labels
    ax.set_title(b)
    ax.set_xticks(X)
    ax.set_xticklabels(map(str, X))

    if (i == 1):
        ax.set_ylabel('runtime/seconds')
        ax.set_xlabel('number of threads')

# create a single shared legend
fig.legend((plot_base, plot_write, plot_optcompile), ('baseline', 'aosdb write', 'optcompile'), loc='lower right' )
plt.tight_layout()
plt.savefig(os.path.join(graphs_dir, basename + '.pdf'))
plt.show()
