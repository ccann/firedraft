(ns firedraft.ws
  (:require [firedraft.common.state :refer [session]]
            [reagent.core :as r]
            [taoensso.sente :as sente :refer [cb-success?]]
            [taoensso.timbre :as log]))

(let [{:keys [chsk ch-recv send-fn state]}
      ;; assigns a client ID to this browser tab
      (sente/make-channel-socket! "/ws"
                                  ;; ?csrf-token
                                  {:type :auto})]
  (def chsk chsk)
  ;; receive channel
  (def ch-chsk ch-recv)
  ;;send function
  (def send! send-fn)
  ;; Watchable, read-only atom
  (def chsk-state state))

(defmulti handle-event :id)

(defmulti handle-message :id)

(defmethod handle-message :default
  [{:keys [id]}]
  (log/info :unhandled-message id))

(defmethod handle-event :chsk/recv
  [{[event message] :?data}]
  (log/info :event (pr-str event))
  (r/with-let [errors (r/cursor session [:errors])]
    (if-let [response-errors (:errors message)]
      (do (log/error response-errors)
          (reset! errors response-errors))
      (do
        (reset! errors nil)
        (handle-message {:id event
                         :message message})))))

(defmethod handle-event :chsk/state
  [{:keys [?data]}]
  (log/info :state-change (pr-str ?data)))

(defmethod handle-event :chsk/handshake
  [{:keys [?data]}]
  (log/info :conn-established (pr-str ?data)))

(defmethod handle-event :default
  [{:keys [event]}]
  (log/warn :unhandled-event (first event)))

(defn event-handler [msg] (handle-event msg))

(def router (atom nil))

(defn stop-router! []
  (when-let [stop-f @router] (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router (sente/start-chsk-router! ch-chsk event-handler)))
