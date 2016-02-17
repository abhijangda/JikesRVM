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
basename = '3_prepopulation'
csv_dir = 'results'
csv_prefix = '3_'
benchmarks = ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']

fig, ax = plt.subplots()
X = np.arange(len(benchmarks))
width = 0.2

# Gather and prepare the data
base1, base2, vm1, vm2 = [], [], [], []
for b in benchmarks:
    # get data columns from csv file
    csvname = csv_prefix + b + '.csv'
    raw_data = np.recfromcsv(os.path.join(csv_dir, csvname))

    # Filter out -1 values and scale to seconds,
    # compute the mean and standard dev
    def prepareData(data):
        data = data[data > 0] / 1000.0

        mean = np.mean(data)
        std  = np.std(data)

        return (mean, std)

    base1 += [prepareData(raw_data['base1'])]
    base2 += [prepareData(raw_data['base2'])]
    vm1   += [prepareData(raw_data['vm1'])]
    vm2   += [prepareData(raw_data['vm2'])]

# Draw bars with errors
mean, std = zip(*base1)
base1_bars = ax.bar(X, mean, width, color='#CC6666', yerr=std, ecolor='black')

mean, std = zip(*base2)
base2_bars = ax.bar(X + width, mean, width, color='#FF9999', yerr=std, ecolor='black')

mean, std = zip(*vm1)
vm1_bars   = ax.bar(X + 2 * width, mean, width, color='#6666CC', yerr=std, ecolor='black')

mean, std = zip(*vm2)
vm2_bars   = ax.bar(X + 3 * width, mean, width, color='#9999FF', yerr=std, ecolor='black')

# Draw axis labels
ax.set_title('')
ax.set_ylabel('runtime/seconds')
ax.set_xlabel('benchmark')

# Draw benchmark name labels at beginning of every fourth bar
ax.set_xticks(X + 2*width)
ax.set_xticklabels(benchmarks)

# Draw legend using default settings
ax.legend((base1_bars[0], base2_bars[0], vm1_bars[0], vm2_bars[0]), ('base1', 'base2', 'vm1', 'vm2'))

plt.tight_layout()
plt.savefig(os.path.join(graphs_dir, basename + '.pdf'))
plt.show()
