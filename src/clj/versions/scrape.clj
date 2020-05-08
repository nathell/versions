(ns versions.scrape
  (:require [cheshire.core :as json]
            [clj-http.headers :as http-headers]
            [clojure.string :as string]
            [skyscraper.core :as skyscraper :refer [defprocessor]]
            [taoensso.timbre :as timbre]
            [versions.config :as config]))

(defn parse-json [headers body]
  (json/parse-string (skyscraper/parse-string headers body) true))

(defn add-auth-header [context]
  (let [{:keys [username api-token]} (config/get :github)]
    (assoc context :http/basic-auth [username api-token])))

;; Not using clojure.edn here since project.clj files are not edn.
;; Parses all Clojure forms in body as a list.
(defn parse-clj [headers body]
  (let [body-str (skyscraper/parse-string headers body)]
    (try
      (binding [*read-eval* false]
        (read-string (str "(" body-str "\n)")))
      (catch Exception e
        (timbre/warn e (str "Unable to parse: " body-str))
        nil))))

(def seed
  [{:url "https://api.github.com/search/repositories?q=language:clojure&sort=stars&order=desc"
    :page 1
    :processor :repos}])

(defn parse-link-header [link]
  (let [items (string/split link #", *")]
    (into {}
          (map (fn [item]
                 (let [[_ url rel] (first (re-seq #"<(.*)>; rel=\"(.*)\"" item))]
                   [(keyword rel) url])))
          items)))

(defn github-page-number [url]
  (Long/parseLong (last (last (re-seq #"page=(.*)$" url)))))

(defprocessor :repos
  :cache-template "versions/repos/:page"
  :parse-fn parse-json
  :process-fn (fn [res ctx]
                (let [repos (for [{:keys [name full_name]} (:items res)]
                              (add-auth-header
                               {:name name
                                :full-name full_name
                                :url (str "/repos/" full_name "/contents/")
                                :processor :repo-contents}))
                      ;; TODO: fix Skyscraper to make this coerce unnecessary
                      headers (into (http-headers/header-map) (get-in ctx [:skyscraper.core/response :headers]))
                      links (parse-link-header (headers "Link"))
                      next-page (:next links)]
                  (cond-> repos
                    next-page (conj (add-auth-header
                                     {:url next-page,
                                      :processor :repos,
                                      :page (github-page-number next-page)}))))))

(def filename->deps-type
  {"deps.edn" :cli-tools
   "project.clj" :leiningen})

(defprocessor :repo-contents
  :cache-template "versions/repo/:full-name/contents"
  :parse-fn parse-json
  :process-fn (fn [res ctx]
                (for [{:keys [name download_url]} res
                      :let [deps-type (filename->deps-type name)]
                      :when deps-type]
                  {:deps-type deps-type
                   :url download_url
                   :processor deps-type})))

(defn parse-lein-dependency
  [[name version & rst :as arg]]
  (let [[version rst] (if (contains? #{:classifier :exclusions} version)
                        [nil (into [version] rst)]
                        [version rst])]
    [name (into {:mvn/version version} (apply hash-map rst))]))

(defn parse-lein-dependencies
  [dependencies]
  (when dependencies
    (into {}
          (map parse-lein-dependency)
          dependencies)))

(defprocessor :leiningen
  :cache-template "versions/repo/:full-name/project.clj"
  :parse-fn parse-clj
  :process-fn (fn [res ctx]
                (if (nil? res)
                  {:unparsable true}
                  (let [defproject (first (filter #(and (list? %) (= (first %) 'defproject)) res))]
                    (try
                      (let [project-map (apply hash-map (drop 3 defproject)) ; assume it's (defproject name version & project-map)
                            dependencies (:dependencies project-map)
                            profile-deps (into {}
                                               (for [[k v] (:profiles project-map)]
                                                 [k (parse-lein-dependencies (:dependencies v))]))]
                        {:deps (parse-lein-dependencies dependencies)
                         :profile-deps profile-deps})
                      (catch Exception e
                        (timbre/warn e (str "Unable to parse defproject form: " (pr-str defproject)))
                        {:unparsable true}))))))

(defprocessor :cli-tools
  :cache-template "versions/repo/:full-name/deps.edn"
  :parse-fn parse-clj
  :process-fn (fn [[res] ctx]
                {:deps (:deps res)
                 :profile-deps (into {}
                                     (for [[k v] (:aliases res)]
                                       [k (:extra-deps v)]))}))

(defn run []
  (timbre/set-level! :info)
  (skyscraper/scrape seed
                     :sleep 2000
                     :parallelism 1
                     :html-cache true))
