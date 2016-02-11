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
basename = '1_naive_overhead'
csv_dir = 'results'
csv_prefix = '1_'
benchmarks = ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']

fig, ax = plt.subplots()
X = np.arange(len(benchmarks))
width = 0.4

# Gather and prepare the data
normal_mean, normal_std = [], []
aosdb_mean, aosdb_std = [], []
for b in benchmarks:
    # get data columns from csv file
    csvname = csv_prefix + b + '.csv'
    raw_data = np.recfromcsv(os.path.join(csv_dir, csvname))

    # Filter out -1 values and scale to seconds.
    normal = raw_data['normal']
    normal = normal[normal > 0] / 1000.0

    aosdb = raw_data['use_aosdb']
    aosdb = aosdb[aosdb > 0] / 1000.0

    # compute the mean and standard dev
    normal_mean += [np.mean(normal)]
    normal_std += [np.std(normal)]
    aosdb_mean += [np.mean(aosdb)]
    aosdb_std += [np.std(aosdb)]

# Draw bars with errors
normal_bars = ax.bar(X,         normal_mean, width, color='#CC6666', yerr=normal_std, ecolor='black')
print normal_bars
aosdb_bars  = ax.bar(X + width,  aosdb_mean, width, color='#6666CC', yerr=aosdb_std,  ecolor='black')
print aosdb_bars

# Draw axis labels
ax.set_title('Overhead of MongoDB calls on every AOSUpdate')
ax.set_ylabel('runtime/seconds')
ax.set_xlabel('benchmark')

# Draw benchmark name labels at beginning of every second bar
ax.set_xticks(X + width)
ax.set_xticklabels(benchmarks)

# Draw legend using default settings
ax.legend((normal_bars[0], aosdb_bars[0]), ('rvm', 'rvm -use_aosdb'))

plt.tight_layout()
plt.savefig(os.path.join(graphs_dir, basename + '.pdf'))
