(ns firedraft.routes.channel-socket
  (:require [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [ring.middleware.keyword-params :as ring.mw.keyword-params]
            [ring.middleware.params :as ring.mw.params]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit  :refer [get-sch-adapter]]))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter)
                                  ;; TODO: add CSRF protection
                                  {:csrf-token-fn nil})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  ;; ChannelSocket's receive channel
  (def ch-chsk ch-recv)
  ;; ChannelSocket's send API fn
  (def chsk-send! send-fn)
  ;; watchable, read-only atom
  (def connected-uids connected-uids))

(add-watch connected-uids
           :connected-uids
           (fn [_ _ old new]
             (when (not= old new)
               (println new)
               (log/info "UID connected: " new))))

(defmulti handle-event
  "Multimethod to handle Sente `event-msg`s. Dispatch on event ID."
  :id)

(defmethod handle-event :default
  [{:keys [event ?reply-fn]}]
  (log/errorf "Unhandled event: %s" event)
  (when ?reply-fn
    (?reply-fn {:umatched-event-as-echoed-from-server event})))

(defn event-handler
  "Non-blocking event handler for Sente event messages"
  [msg]
  ;; Handle event-msgs on a single thread
  (handle-event msg)
  ;; (future (handle-event msg)) ; Handle event-msgs on a thread pool
  )

(defstate chsk-router
  :start (sente/start-chsk-router! ch-chsk event-handler)
  :stop (chsk-router))

(defn routes []
  ["/chsk"
   {:middleware [ring.mw.params/wrap-params
                 ring.mw.keyword-params/wrap-keyword-params
                 ;; middleware/wrap-csrf
                 ;; middleware/wrap-formats
                 ]
    :get (fn [req] (ring-ajax-get-or-ws-handshake req))
    :post (fn [req] (ring-ajax-post req))}])
