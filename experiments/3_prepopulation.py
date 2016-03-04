import common

# This experiment runs the same benchmark twice, to observe the behavior with an empty external database,
# and subsequently with a populated database.

benchmarks = ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']
git_checkout = 'BulkRead'
prefix = '3'
timelimit = 60

try:
    common.drop_mongo_collection('AOSDatabase', 'AOSCollection')
    common.init('3', git_checkout, timelimit=timelimit)

    n = common.get_repetitions()

    for b in benchmarks:
        common.open_csv(b)
        common.write_csv(['i', 'base1', 'base2', 'vm1', 'vm2'])

        for i in range(n):
            print '---', b, i+1, 'of', n, '---'
            results = []

            # Clean MongoDB collection
            common.drop_mongo_collection('AOSDatabase', 'AOSCollection')
            common.drop_mongo_collection('AOSDatabase', 'DCGCollection')

            # Baseline runs
            for k in range(2):
                print '--', b, 'Baseline', k + 1, '--'
                results += [common.run_dacapo(b)]

            # AOS database enabled runs
            print '--', b, 'AOSDB VM Writing', '--'
            results += [common.run_dacapo(b, vm_args=['-use_aosdb1000'])]
            print '--', b, 'AOSDB VM Reading', '--'
            results += [common.run_dacapo(b, vm_args=['-use_aosdbread'])]

            # convert all results to strings and store them
            common.write_csv([i] + map(str, results))

        common.close_csv()
finally:
    common.teardown()
