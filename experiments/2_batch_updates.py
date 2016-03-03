import common

# This experiment uses different batch-update sizes for each benchmark, and
# compares these to the base line implementation. update methods.

# The BulkRead branch is used, and the -use_aosdb<n> flag defines how many
# updates are packed into one batch.

git_checkout = 'BulkRead'
timelimit = 60

benchmarks = ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']
batch_sizes = [1, 10, 100, 1000, 10000]

# The number of benchmarks to be run will be len(benchmarks) * (1 +
# len(batch_sizes)) * n, therefore, the expected maximum time with these setting
# is 7 * 6 * 3 * 60 seconds = 7560 seconds = ~ 2 hours.

try:
    common.init('2', git_checkout, timelimit=timelimit)
    n = common.get_repetitions()

    for b in benchmarks:
        common.open_csv(b)

        print '----', 'Running', b, n, '*', len(batch_sizes) + 1, 'times', '----'
        common.write_csv(['baseline'] + map(str, batch_sizes))

        for i in range(n):
            baseline_time = common.run_dacapo(b)

            aos_time = []
            for bs in batch_sizes:
                aos_time += [common.run_dacapo(b, vm_args=['-use_aosdb'+str(bs)])]

            r.writerow([baseline_time] + map(str, aos_time))

        common.close_csv()
finally:
    common.teardown()
