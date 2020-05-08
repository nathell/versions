(ns versions.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io])
  (:refer-clojure :exclude [get]))

(defn get [& path]
  (get-in (aero/read-config (io/resource "config.edn")) path))
