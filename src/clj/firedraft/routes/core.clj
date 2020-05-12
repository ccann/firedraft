(ns firedraft.routes.core
  (:require [firedraft.env :refer [defaults]]
            [firedraft.layout :refer [error-page]]
            [firedraft.middleware :as middleware]
            [firedraft.routes.games :as games]
            [firedraft.routes.home :as home]
            [firedraft.routes.packs :as packs]
            [firedraft.routes.ws :as ws]
            [mount.core :as mount]
            firedraft.events.game
            firedraft.events.games
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.webjars :refer [wrap-webjars]]))

(mount/defstate init-app
  :start ((or (:init defaults) (fn [])))
  :stop  ((or (:stop defaults) (fn []))))

(mount/defstate app-routes
  :start
  (ring/ring-handler
   (ring/router
    [(home/routes)
     (ws/routes)
     (games/routes)
     (packs/routes)]
    {:data {:middleware [coercion/coerce-request-middleware]}})
   (ring/routes
    (ring/create-resource-handler
     {:path "/"})
    (wrap-content-type
     (wrap-webjars (constantly nil)))
    (ring/create-default-handler
     {:not-found
      (constantly (error-page {:status 404, :title "404 - Page not found"}))
      :method-not-allowed
      (constantly (error-page {:status 405, :title "405 - Not allowed"}))
      :not-acceptable
      (constantly (error-page {:status 406, :title "406 - Not acceptable"}))}))))

(defn app []
  (middleware/wrap-base #'app-routes))
