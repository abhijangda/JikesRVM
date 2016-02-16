import common
import csv
import os
import sys

# This experiment uses different batch-update sizes for each benchmark, and
# compares these to the base line implementation. update methods.

# The BulkRead branch is used, and the -use_aosdb<n> flag defines how many
# updates are packed into one batch.

git_checkout = 'BulkRead'
benchmarks = ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']
batch_sizes = [1, 10, 100, 1000, 10000]

results_dir = os.path.abspath('results')
results_prefix = '2_'
n = 3
timelimit = 60

# The number of benchmarks to be run will be len(benchmarks) * (1 +
# len(batch_sizes)) * n, therefore, the expected maximum time with these setting
# is 7 * 6 * 3 * 60 seconds = 7560 seconds = ~ 2 hours.

# Allow overriding the number of repetitions for each benchmark pair with the first argument
if (len(sys.argv) > 1):
    try:
        n = int(sys.argv[1])
    except Exception:
        print "ERROR", "The first argument sets the number of repetitions and must be positive integer."
        exit(1)

try:
    common.checkout_and_build_jikes(git_checkout)

    if not os.path.exists(results_dir):
        os.makedirs(results_dir)

    for b in benchmarks:
        csvname = os.path.abspath(os.path.join(results_dir, results_prefix+b+'.csv'))

        print '----', 'Running', b, n, '*', len(batch_sizes) + 1, 'times, results in', csvname, '----'

        with open(csvname, 'wb') as csvfile:
            r = csv.writer (csvfile, delimiter = ',')
            r.writerow(['baseline'] + map(str, batch_sizes))

            for i in range(n):
                baseline_time = common.run_dacapo(b, timelimit = timelimit)

                aos_time = []
                for bs in batch_sizes:
                    aos_time += [common.run_dacapo(b, vm_args=['-use_aosdb'+str(bs)], timelimit = timelimit)]

                r.writerow([baseline_time] + map(str, aos_time))

finally:
    common.teardown()
