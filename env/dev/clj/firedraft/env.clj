(ns firedraft.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [firedraft.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[firedraft started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[firedraft has shut down successfully]=-"))
   :middleware wrap-dev})
