## Table of contents

## FAQ

### What is this?

This is a website showing package dependency information for the Python ecosystem.

For feature requests, bug reports, and general contact, this is project is open source and available
on <https://github.com/UoB-HPC/python-atlas>.

### Why isn't PyPI included in the graph?

While we really wanted to include PyPI for completeness, there were multiple factors that made
including PyPI difficult.

* PyPI currently hosts more than 380k packages and only around 200k packages have wheel
  files so dependency information for the rest will be incomplete. For context, dependency
  resolution in pip requires execution of the `setup.py` script
  through [setuptools](https://github.com/pypa/setuptools) -- a process that requires downloading
  all required packages recursively.
* PyPI's [simple API](https://peps.python.org/pep-0503/) doesn't expose a way to query wheel
  information in an efficient way; we need to request each package's metadata, meaning that we'll
  need to send 380K HTTP requests to PyPI.
* The combined data size for just package metadata is around 1.7GiB which is beyond the recommended
  maximum repository size for GitHub pages, the service we use for hosting (see [API](#API)).

For now, we only retrieve the list of packages available on PyPI through
the [Warehouse endpoints](https://warehouse.pypa.io/api-reference/legacy.html).
Even in this case, the package names doesn't exclude ones that are registered but no longer exist.

We may change this in the future should the situation improve.

### What are markers? / Why are some packages missing?

To improve the graph's usefulness and reduce excessive edges, we treat dependencies to the following packages as *markers*:

* `python,python_abi` = `Python`
* `r-base` = `R language`
* `anaconda,_anaconda_depends` = `Anaconda`
* `vc` = `Visual Studio`

Markers are node attributes that behave similar to subdirs and channels.  

### What is this written in?

This website is implemented in Scala 3 for both the static site generation and the *frontend*.

For static site generation, a Scala program makes the appropriate REST calls to `conda-build`
servers hosted on Anaconda to retrieve channel and repository data.
This data is then used to generate the dependency graph using the *sfdp* algorithm
from [Graphviz](https://graphviz.org/).
The graph, along with the package metadata, is written to several JSON files (see [API](#API)) for
frontend consumption.

For the frontend, we use [Laminar](https://github.com/raquo/Laminar) together
with [Bulma](https://bulma.io/) for UI.
The main dependency graph uses [deck.gl](https://deck.gl/) to visualise large amounts of data via
WebGL.

## API

This website is currently hosted on GitHub pages using only static content.
The data is sourced from PyPI and Anaconda directly and updated using GitHub Action on a daily
basis.

As everything is static, rate limits are inherited
from [GitHub page's limit](https://docs.github.com/en/pages/getting-started-with-github-pages/about-github-pages#limits-on-use-of-github-pages)
.

To access these data, use the following URLs:

### Atlas

* `atlas_layout.json` gives the dependency graph in JSON format with metadata.
* `atlas.dot` gives the graph layout in [DOT](https://graphviz.org/doc/info/lang.html). This is
  used at site generation stage for layout only and does not contain metadata such as
  package descriptions.
* `pypi_names.json` returns the names of all [PyPI](https://pypi.org/) packages.
