(ns firedraft.core
  (:require [firedraft.ajax :as fd.ajax]
            [firedraft.lobby :as lobby]
            [firedraft.game :as game]
            [firedraft.ws :as ws]
            [reagent.dom :as dom]
            [firedraft.common.state :refer [session]]
            [taoensso.timbre :as log]
            [ajax.core :as ajax]
            [reitit.coercion.spec :as rss]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend :as rf]
            [reagent.core :as r]))

(defonce match (r/atom nil))

(defn page []
  (when @match
    (let [view (:view (:data @match))]
      [view @match session])))

(def routes
  [["/"
    {:name :lobby
     :view lobby/page}]
   ["/draft/:id"
    {:name :draft
     :view game/page
     :parameters {:path {:id string?}}}]])

(defn- fetch-available-games! [session]
  (ajax/GET "/games"
      {:response-format :json
       :keywords? true
       :handler (fn [data]
                  (log/info :available-games data)
                  (swap! session assoc :games data))}))


(defn init! []
  (ws/start-router!)
  (fd.ajax/load-interceptors!)
  (rfe/start!
   (rf/router routes {:data {:coercion rss/coercion}})
   (fn [m] (reset! match m))
   ;; set to false to enable HistoryAPI
   {:use-fragment true})
  (dom/render [#'page] (.getElementById js/document "app"))
  (fetch-available-games! session))
