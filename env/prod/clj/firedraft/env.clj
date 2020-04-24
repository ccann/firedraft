(ns firedraft.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[firedraft started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[firedraft has shut down successfully]=-"))
   :middleware identity})
