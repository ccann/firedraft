(ns firedraft.routes.home
  (:require [firedraft.layout :as layout]
            [firedraft.middleware :as middleware]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]])
