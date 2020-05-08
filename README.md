# versions

This is companion code to my blog posts [“Clojure as a dependency”][1] and [“Making of ‘Clojure as a dependency’”][2].

 [1]: http://blog.danieljanus.pl/2020/05/02/clojure-dependency/
 [2]: http://blog.danieljanus.pl/2020/05/08/making-of-clojure-dependency/

## Prerequisites

Data are downloaded using the GitHub API. You’ll need a personal access token to authenticate (you’ll be hitting GitHub’s rate limits otherwise).

Go to [GitHub Settings → Personal Access Tokens][3] and click “Generate new token”. Enter some meaningful name and select `public_repo` as a scope. Click “Generate token”.

 [3]: https://github.com/settings/tokens

You’ll be shown an alphanumeric sequence. Create a `$HOME/.secrets.edn` with the following content (substitute the token with the one you’ve just generated):

```clojure
{:github-api-token "YOUR_API_TOKEN_GOES_HERE"}
```

If you wish to skip the download process, you can reuse my download cache. Just untar [this tarball][4] into your `$HOME`. You won’t need the GitHub token in this case.

 [4]: http://pliki.danieljanus.pl/versions-cache.tar.xz

## Running it

Run a REPL (`clj` or from within your editor of choice). Then:

```clojure
(require 'versions.analyze)
(in-ns 'versions.analyze)
(def result (main))
```

This will download the data if you don’t have it cached already, generate data for the plot in the article (`graph1.csv`) and produce a graph of computations, some of which are cited in the article. For example, to see how many repositories (actually project definition files) there are, grouped by type:

```clojure
(:repos-count-by-deps-type result)
;=> {:leiningen 828, :cli-tools 140}
```

To actually draw the plot, evaluate `src/R/visualize.R` in R. You will need ggplot2 (if you don’t have it, do `install.packages('ggplot2')` first).

## License

Copyright (C) 2020 Daniel Janus, http://danieljanus.pl.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
