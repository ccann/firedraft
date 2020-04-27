(ns firedraft.routes.rooms
  (:require [clojure.spec.alpha :as s]
            [crypto.random :as random]
            [firedraft.cards :as cards]
            [hiccup.core :as hiccup]
            [medley.core :refer [find-first]]
            [mount.core :refer [defstate]]
            [ring.util.http-response :as http]))

(defn hex? [s]
  (boolean (re-find #"[0-9a-fA-F]+" s)))

(s/def ::set-code
  (s/and string? cards/supported-sets))

(s/def ::booster
  (s/coll-of ::set-code))

(s/def ::cubecobra-id
  (s/and string?
         hex?
         (fn right-size? [s] (= 24 (count s)))))

(s/def ::cube ::cubecobra-id)

(s/def ::game-opts
  (s/keys :opt-un [::booster ::cube]))

;; (defn routes []
;;   ["/rooms" {:middleware [middleware/wrap-csrf
;;                           middleware/wrap-formats]}
;;    [["" {:parameters {:body {:game-title string?
;;                              :game-mode (s/and string? game-modes)
;;                              :game-opts ::game-opts}}
;;          :post {:handler create-room!}}]
;;     ["/:id" {:parameters {:path {:id string?}}}
;;      ["/join" {:post {:handler join-room!}}]]]])
