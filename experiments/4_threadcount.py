import common

# This experiment runs each benchmark with different number of threads
# to see whether our speedup scales or if we are still hindered by lock contention on the lookup queues.

benchmarks = ['avrora', 'jython', 'lusearch', 'sunflow', 'xalan']
git_checkout = 'PriorityQueue'
prefix = '4'
timelimit = 600
taskset = '0-15'
dacapo_args = ['-n', '1', '-s', 'large']
vm_args = ['-Xmx1024M', '-Xms512M']

dacapo_threadcount = [1, 2, 4, 8, 16, 32]

try:
    common.init(prefix, git_checkout, timelimit=timelimit, taskset=taskset)

    n = common.get_repetitions()

    for b in benchmarks:
        common.open_csv(b)

        # write CSV header row
        headers = ['i']
        for t in dacapo_threadcount:
            headers += ['base-%d' % t, 'aosdb-%d' % t, 'aosdboptcompile-%d' %t]
        common.write_csv(headers)

        # run benchmarks
        for i in range(n):
            print '---', b, i+1, 'of', n, '---'
            results = []

            for t in dacapo_threadcount:
                # Clean MongoDB collection
                common.drop_mongo_collection('AOSDatabase', 'AOSCollection')
                common.drop_mongo_collection('AOSDatabase', 'DCGCollection')

                da = dacapo_args + ['-t', str(t)]

                # Baseline run
                print '--', b, 'Baseline', '--'
                results += [common.run_dacapo(b, vm_args=vm_args, dacapo_args=da)]

                # AOS database enabled runs
                results += [common.run_dacapo(b, vm_args=vm_args + ['-use_aosdb1000'], dacapo_args=da)]

                if (results[-1] == -1):
                    results += [-1]
                else:
                    results += [common.run_dacapo(b, vm_args=vm_args + ['-use_aosdboptcompile'], dacapo_args=da)]

            # convert all results to strings and store them
            common.write_csv([i] + map(str, results))

        common.close_csv()
finally:
    common.teardown()
