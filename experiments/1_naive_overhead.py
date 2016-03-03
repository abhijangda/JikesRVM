import common

# This experiment measures the overhead incurred when using naive mongodb calls in the AOS
# update methods. The -use_aosdb flag is enabled on the master branch build.

benchmarks = ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']

n = 3
timelimit = 60
git_checkout = 'BulkRead'

try:
    common.init('1', git_checkout, timelimit=timelimit)
    n = common.get_repetitions()

    for b in benchmarks:
        common.open_csv(b)

        print '----', 'Running', b, n, 'times', '----'

        common.write_csv(['i', 'normal', 'use_aosdb'])

        for i in range(n):
            normal_time = common.run_dacapo(b)
            aos_time = common.run_dacapo(b, vm_args=['-use_aosdb'])
            common.write_csv([i, normal_time, aos_time])

        common.close_csv()
finally:
    common.teardown()
