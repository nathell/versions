(ns versions.analyze
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [plumbing.core :refer [fnk defnk]]
            [plumbing.graph :as graph]
            [versions.scrape :as scrape]))

(defn has-deps? [repo]
  (not (nil? (:deps repo))))

(defn get-clojure-version [repo]
  (get-in repo [:deps 'org.clojure/clojure :mvn/version]))

(defn released-clojure-version [v]
  (when (string? v)
    (if (re-matches #"^(\d)+\.(\d)+\.(\d)+$" v) v "other")))

(defn get-released-clojure-version [v]
  (released-clojure-version (get-clojure-version v)))

(defn dataset->csv [x]
  (let [columns (keys (first x))]
    (into [(mapv name columns)]
          (for [row x]
            (mapv #(get row %) columns)))))

(defn version->vector [v]
  (when v
    (mapv #(Long/parseLong %) (string/split v #"\."))))

(defn compare-versions [v1 v2]
  (cond (and (= v1 "other") (= v2 "other")) 0
        (= v1 "other") 1
        (= v2 "other") -1
        :otherwise (compare (version->vector v1) (version->vector v2))))

(defn has-clojure-in-profiles? [{:keys [profile-deps]}]
  (some #(contains? % 'org.clojure/clojure) (vals profile-deps)))

(def g
  {:raw-repos
   (fnk []
     (scrape/run))

   :unparsable-repos
   (fnk [raw-repos]
     (filter :unparsable raw-repos))

   :repos
   (fnk [raw-repos]
     (remove :unparsable raw-repos))

   :repos-without-deps
   (fnk [repos]
     (remove has-deps? repos))

   :repos-with-deps
   (fnk [repos]
     (filter has-deps? repos))

   :repos-without-clojure
   (fnk [repos]
     (remove #(get-in % [:deps 'org.clojure/clojure]) repos))

   :repos-without-clojure-but-having-it-in-profiles
   (fnk [repos-without-clojure]
     (filter has-clojure-in-profiles? repos-without-clojure))

   :repos-without-clojure-not-even-in-profiles
   (fnk [repos-without-clojure]
     (remove has-clojure-in-profiles? repos-without-clojure))

   :repos-by-name
   (fnk [repos]
     (group-by :full-name repos))

   :doubles
   (fnk [repos-by-name]
     (->> repos-by-name
          (filter (fn [[k v]] (= (count v) 2)))
          (map first)
          set))

   :empty-deps-not-in-doubles
   (fnk [repos-without-deps doubles]
     (remove #(contains? doubles (:full-name %))
             repos-without-deps))

   :repos-count-by-deps-type
   (fnk [repos]
     (frequencies (map :deps-type repos)))

   :clojure-versions
   (fnk [repos]
     (frequencies (map get-released-clojure-version repos)))

   :clojure-versions-by-deps-type
   (fnk [repos]
     (->> (frequencies (map (juxt :deps-type get-released-clojure-version) repos))
          (map (fn [[[deps-type version] count]]
                 {:deps-type (name deps-type)
                  :version version
                  :count count}))
          (sort-by :version compare-versions)))

   :clojure-versions-by-scope
   (fnk [repos-with-deps]
     (frequencies (map (juxt :deps-type #(get-in % [:deps 'org.clojure/clojure :scope])) repos-with-deps)))

   :number-of-projects-not-depending-on-clojure
   (fnk [clojure-versions-by-deps-type]
     (->> clojure-versions-by-deps-type
          (remove :version)
          (map (juxt (comp keyword :deps-type) :count))
          (into {})))

   :percentage-of-projects-not-depending-on-clojure
   (fnk [number-of-projects-not-depending-on-clojure repos-count-by-deps-type]
     (into {}
           (for [k (keys number-of-projects-not-depending-on-clojure)]
             [k (double
                 (/ (number-of-projects-not-depending-on-clojure k)
                    (repos-count-by-deps-type k)))])))

   :what-other-things-besides-versions
   (fnk [repos]
     (distinct (mapcat #(keys (second %)) (mapcat :deps repos))))})

(defn main []
  (let [res ((graph/lazy-compile g) {})]
    (with-open [w (io/writer "graph1.csv")]
      (csv/write-csv w (dataset->csv (:clojure-versions-by-deps-type res))))
    res))
