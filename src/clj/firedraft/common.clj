(ns firedraft.common
  (:require [camel-snake-kebab.core :as case]
            [camel-snake-kebab.extras :as casex]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [jsonista.core :as json]))

(defn load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (with-open [r (io/reader source)]
    (edn/read (java.io.PushbackReader. r))))


(def json-mapper
  (json/object-mapper
   {:decode-key-fn keyword}))

(defn json-decode [decodable]
  (json/read-value decodable json-mapper))

(defn kebab-case-keys
  [m]
  (casex/transform-keys case/->kebab-case-keyword m))
