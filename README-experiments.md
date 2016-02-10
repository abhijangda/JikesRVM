# Distributed JikesRVM profiling experiments

The experimental platform described here, residing on the experiments branch and
in the `experiments` folder, aims to make the published experiments
reproducible. Each experiment is normally run on a fresh build of jikes, using a
specified git commit hash or branch.

In addition to this file and the scripts, we will publish machine specifications
and provide a Vagrant (or similar) provisioning script describing the build
environment, including the required third-party package versions.

## Fully Automated Runs

The full experimental scripts in `experiments/` of the form
`<n>_<experiment-name>.py` can be run as is and will produce CSV files, which
can later be used by the graph creation scripts.

As each experiment may require code from a different branch, these scripts will
clone the repository, check out a specified commit, and rebuild the Jikes
binaries before starting the experiments. They will also download the dacapo.jar
file if it does not already exist.

Each experiment script includes a default number of repetitions corresponding to
that used in generating code for our published graphs. This can be overridden by
specifying a number as the first argument to the script.

Example:

```
cd experiments/
python 1_naive_overhead.py 10
```

This script should produce its resulting csv files in `results/1_naive_overhead` (not implemented yet).

## Graphs

_Not implemented yet._

The scripts used to generate all graphs and tables for our publications will also be provided here. We will use either R or Python's matplotlib package.

## Manual Experimental Runs

Use a python shell and the common module provided in `experiments/`.

The example provided below will run a single benchmark with the provided vm
flags, assuming the correct version of the Jikes binaries already exists in the
current repository - that is, no additional checkout or build will be performed.

```
cd experiments/
python
> import common
> common.reset_root()
> common.run_dacapo('avrora', vm_args=['-use_aosdb'])
```

The `run_dacapo` method prints the output produced by the benchmark and returns
the runtime in milliseconds.
