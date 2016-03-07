import common

# This experiment runs the same benchmark twice, to observe the behavior with an empty external database,
# and subsequently with a populated database.

benchmarks = ['avrora', 'jython', 'sunflow']
git_checkout = 'BulkRead'
prefix = '3_1'
timelimit = 600
taskset = '0-7'
dacapo_args = ['-n', '2', '-t', '8', '-s', 'large']

try:
    common.drop_mongo_collection('AOSDatabase', 'AOSCollection')
    common.init(prefix, git_checkout, timelimit=timelimit, taskset=taskset)

    n = common.get_repetitions()

    for b in benchmarks:
        common.open_csv(b)
        common.write_csv(['i', 'base', 'vm1', 'vm2'])

        for i in range(n):
            print '---', b, i+1, 'of', n, '---'
            results = []

            # Clean MongoDB collection
            common.drop_mongo_collection('AOSDatabase', 'AOSCollection')
            common.drop_mongo_collection('AOSDatabase', 'DCGCollection')

            # Baseline runs
            print '--', b, 'Baseline', '--'
            results += [common.run_dacapo(b, dacapo_args=dacapo_args)]

            # AOS database enabled runs
            print '--', b, 'AOSDB VM Writing', '--'
            results += [common.run_dacapo(b, vm_args=['-use_aosdb1000'], dacapo_args=dacapo_args)]
            print '--', b, 'AOSDB VM Reading', '--'
            results += [common.run_dacapo(b, vm_args=['-use_aosdbbulkcompile'], dacapo_args=dacapo_args)]

            # convert all results to strings and store them
            common.write_csv([i] + map(str, results))

        common.close_csv()
finally:
    common.teardown()
