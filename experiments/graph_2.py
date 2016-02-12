# python 2.7
# may require numpy and matplotlib installation

import numpy as np
import matplotlib.pyplot as plt
import os
import csv

# Output directory (relative to working dir)
graphs_dir = 'figures'

# Create output directory
if not os.path.exists(graphs_dir):
    os.makedirs(graphs_dir)

# 1) naive mongodb calls to measure overhead.
# A single bar graph with a pair of bars for each benchmark.
basename = '2_batch_updates'
csv_dir = 'results'
csv_prefix = '2_'
#benchmarks = ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']
benchmarks = ['fake', 'fake'] # TODO using fake data to prepare the plot until real data is available

fig, axarr = plt.subplots(len(benchmarks))

i = 0;
for b in benchmarks:
    # Select the current sub plot
    ax = axarr[i]
    i += 1

    # get data from csv file
    csvname = csv_prefix + b + '.csv'
    raw_data = np.recfromcsv(os.path.join(csv_dir, csvname))

    # get column names, giving size of batch used in that series
    names = raw_data.dtype.names

    # Add to the X, Y series for each column after the first
    X, Y = [], []

    for k in range(1, len(names)):
        # Filter out -1 values and scale to seconds.
        values = raw_data[names[k]]
        values = values[values > 0] / 1000.0

        # calculate the mean
        mean = np.mean(values)
        X += [int(k)]
        Y += [mean]

    # compute the mean for the baseline column
    baseline = raw_data['baseline']
    baseline = baseline[baseline > 0] / 1000.0
    baseline_mean = np.mean(baseline)

    # Draw bars with errors
    batch_plot    = ax.plot(X, Y,             color='#CC6666')
    baseline_plot = ax.plot(X, [baseline_mean] * len(Y), color='#6666CC')

    # set sub plot title and axis labels
    ax.set_title(b)
    ax.set_ylabel('runtime/seconds')
    ax.set_xlabel('batch update size')

# Draw legend using default settings
# ax.legend((normal_bars[0], aosdb_bars[0]), ('rvm', 'rvm -use_aosdb'))

plt.tight_layout()
plt.savefig(os.path.join(graphs_dir, basename + '.pdf'))
plt.show()
